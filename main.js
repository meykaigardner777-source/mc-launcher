const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const zlib = require('zlib');
const { pathToFileURL } = require('url');
const { spawn, exec, execSync } = require('child_process');

if (!app.requestSingleInstanceLock()) app.exit(0);
process.on('uncaughtException', (err) => {
  if (err && (err.code === 'EEXIST' || err.message?.includes('EEXIST'))) return;
  console.error('[Uncaught Exception]', err);
});

let mainWindow = null, activeGameProcess = null, isLaunchingGame = false;
let configCache = null, configWriteTimer = null, cachedManifest = null;

const APP_DIR = path.join(app.getPath('appData'), '.aetheria-launcher');
const CONFIG_FILE = path.join(APP_DIR, 'launcher_config.json');
const RUNTIMES_DIR = path.join(APP_DIR, 'runtimes');
const JDK_DIR = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64');
const MINECRAFT_DIR = path.join(app.getPath('appData'), '.minecraft');
const PROFILES_DIR = path.join(APP_DIR, 'profiles');
const EXTENSIONS_USER_DIR = path.join(APP_DIR, 'extensions');
const _rawExtDir = path.join(__dirname, 'extensions');
const _unpackedExtDir = _rawExtDir.replace(/app\.asar([\\/])/i, 'app.asar.unpacked$1');
const EXTENSIONS_BUNDLED_DIR = fs.existsSync(_unpackedExtDir) ? _unpackedExtDir : _rawExtDir;

const CF_API_KEY = '$2a$10$bL4bIL5pUWqfcO7KQtnMRehkWyMmRKon46u.85/QV/MQTgC70O80C';
const DEFAULT_ORYON_JVM = '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=20 -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:G1NewSizePercent=25';
const GRID_SIZE = 21;
const CF_LOADER_MAP = { any: 0, forge: 1, cauldron: 2, liteloader: 3, fabric: 4, quilt: 5, neoforge: 6 };
const MS_CLIENT_ID = '00000000402b5328';
const MS_REDIRECT_URI = 'https://login.live.com/oauth20_desktop.srf';

const DEFAULT_OVERVIEW_WIDGETS = [
  { id: 'hero', col: 5, row: 5, colSpan: 13, rowSpan: 5, borderRadius: 12, bgOpacity: 0.25, showBorder: true, settings: { showSubtitle: true, showConfigBtn: true } },
  { id: 'profile', col: 5, row: 11, colSpan: 8, rowSpan: 5, borderRadius: 12, bgOpacity: 0.55, showBorder: true, settings: {} },
  { id: 'play', col: 13, row: 11, colSpan: 5, rowSpan: 5, borderRadius: 12, bgOpacity: 0.92, showBorder: true, settings: {} }
];

const WIDGET_BOUNDS = Object.fromEntries([
  ['hero', 13, 5, 3, 21, 2, 12], ['profile', 8, 5, 3, 21, 2, 14], ['play', 5, 5, 2, 21, 1, 10],
  ['status', 9, 3, 2, 21, 1, 10], ['account', 9, 5, 3, 21, 2, 14], ['ram', 9, 4, 2, 21, 2, 10],
  ['java', 9, 5, 3, 21, 2, 12], ['quicklaunch', 10, 4, 3, 21, 2, 12], ['mods', 9, 4, 2, 21, 2, 10],
  ['updates', 9, 4, 2, 21, 2, 10], ['worlds', 9, 5, 3, 21, 2, 14], ['screenshots', 10, 5, 3, 21, 2, 14],
  ['console', 11, 6, 3, 21, 2, 18], ['telemetry', 9, 4, 2, 21, 2, 10], ['folders', 9, 4, 2, 21, 2, 10],
  ['loader', 9, 3, 2, 21, 1, 10], ['jvm', 10, 3, 2, 21, 1, 10], ['skin', 6, 5, 2, 16, 2, 14],
  ['session', 7, 3, 2, 21, 1, 10], ['clock', 8, 5, 2, 21, 2, 14]
].map(([id, dc, dr, mc, xc, mr, xr]) => [id, { defaultColSpan: dc, defaultRowSpan: dr, minColSpan: mc, maxColSpan: xc, minRowSpan: mr, maxRowSpan: xr }]));

const MANAGED_JDKS = {
  '8': { name: 'OpenJDK 8 (Win64 via ARM64)', url: 'https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jdk/hotspot/normal/eclipse', dirName: 'java-8-arm64', version: '8', major: 8, desc: 'Legacy Minecraft (< 1.17)' },
  '17': { name: 'Microsoft OpenJDK 17 (ARM64)', url: 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-aarch64.zip', dirName: 'java-17-arm64', version: '17', major: 17, desc: 'Modern Minecraft (1.17 – 1.20.4)' },
  '21': { name: 'Microsoft OpenJDK 21 (ARM64)', url: 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-aarch64.zip', dirName: 'java-21-arm64', version: '21', major: 21, desc: 'Current Minecraft (1.20.5 – 1.21.x)' },
  '25': { name: 'Microsoft OpenJDK 25 (ARM64)', url: 'https://aka.ms/download-jdk/microsoft-jdk-25-windows-aarch64.zip', dirName: 'java-25-arm64', version: '25', major: 25, desc: 'Latest Minecraft (26.x+ / Next-Gen)' }
};

const clampInt = (v, min, max) => Math.max(min, Math.min(max, Math.round(v)));
const clampFloat = (v, min, max) => Number.isFinite(Number(v)) ? Math.max(min, Math.min(max, Number(v))) : min;
const rectsOverlap = (a, b) => a.col < b.col + b.colSpan && a.col + a.colSpan > b.col && a.row < b.row + b.rowSpan && a.row + a.rowSpan > b.row;

function findFirstAvailableSlot(existing, colSpan, rowSpan) {
  const sc = clampInt(colSpan, 1, GRID_SIZE), sr = clampInt(rowSpan, 1, GRID_SIZE);
  for (let r = 1; r <= GRID_SIZE - sr + 1; r++) {
    for (let c = 1; c <= GRID_SIZE - sc + 1; c++) {
      const cand = { col: c, row: r, colSpan: sc, rowSpan: sr };
      if (!existing.some(o => rectsOverlap(cand, o))) return { col: c, row: r };
    }
  }
  return { col: 1, row: 1 };
}

function resolveCollisionsPushDown(items, anchorId) {
  const clone = items.map(w => ({ ...w })), queue = [anchorId], maxIter = clone.length ** 2 + 40;
  for (let iter = 0; queue.length && iter < maxIter; iter++) {
    const moverId = queue.shift(), mover = clone.find(w => w.id === moverId);
    if (!mover) continue;
    for (const other of clone.filter(w => w.id !== moverId).sort((a, b) => a.row - b.row || a.col - b.col)) {
      if (!rectsOverlap(mover, other)) continue;
      const nextRow = mover.row + mover.rowSpan;
      if (nextRow + other.rowSpan - 1 <= GRID_SIZE) { other.row = nextRow; queue.push(other.id); }
      else Object.assign(other, findFirstAvailableSlot(clone.filter(w => w.id !== other.id), other.colSpan, other.rowSpan));
    }
  }
  return clone;
}

function normalizeOverviewWidgets(rawList, gridVersion = 21) {
  const def = () => DEFAULT_OVERVIEW_WIDGETS.map(w => ({ ...w, settings: { ...(w.settings || {}) } }));
  if (!Array.isArray(rawList)) return def();
  if (rawList.length === 3 && rawList[0]?.id === 'hero' && rawList[1]?.id === 'profile' && rawList[2]?.id === 'play' && gridVersion !== 21) return def();

  const seen = new Set();
  let placed = [];
  for (const raw of rawList) {
    if (!raw?.id || seen.has(raw.id)) continue;
    seen.add(raw.id);
    const meta = WIDGET_BOUNDS[raw.id] || { defaultColSpan: 6, defaultRowSpan: 4, minColSpan: 2, maxColSpan: 21, minRowSpan: 1, maxRowSpan: 14 };
    const borderRadius = clampInt(Number.isFinite(raw.borderRadius) ? raw.borderRadius : 12, 0, 28);
    const bgOpacity = clampFloat(Number.isFinite(raw.bgOpacity) ? raw.bgOpacity : (raw.id === 'hero' ? 0.25 : raw.id === 'play' ? 0.92 : 0.55), 0, 1);
    const showBorder = typeof raw.showBorder === 'boolean' ? raw.showBorder : true;
    const settings = raw.settings && typeof raw.settings === 'object' ? { ...raw.settings } : {};
    const full = [raw.col, raw.row, raw.colSpan, raw.rowSpan].every(Number.isFinite);

    let colSpan = meta.defaultColSpan, rowSpan = meta.defaultRowSpan, col, row;
    if (full && gridVersion === 21) {
      colSpan = clampInt(raw.colSpan, meta.minColSpan, meta.maxColSpan);
      rowSpan = clampInt(raw.rowSpan, meta.minRowSpan, meta.maxRowSpan);
      col = clampInt(raw.col, 1, GRID_SIZE - colSpan + 1);
      row = clampInt(raw.row, 1, GRID_SIZE - rowSpan + 1);
    } else if (full) {
      colSpan = clampInt(Math.round((raw.colSpan * GRID_SIZE) / 12), meta.minColSpan, meta.maxColSpan);
      rowSpan = clampInt(raw.rowSpan * 2, meta.minRowSpan, meta.maxRowSpan);
      col = clampInt(Math.round(((raw.col - 1) * GRID_SIZE) / 12) + 1, 1, GRID_SIZE - colSpan + 1);
      row = clampInt((raw.row - 1) * 2 + 1, 1, GRID_SIZE - rowSpan + 1);
    } else {
      ({ col, row } = findFirstAvailableSlot(placed, colSpan, rowSpan));
    }
    placed.push({ id: raw.id, col, row, colSpan, rowSpan, borderRadius, bgOpacity, showBorder, settings });
  }
  for (const w of placed) placed = resolveCollisionsPushDown(placed, w.id);
  return placed;
}

const LOG_BUFFER_MAX = 5000, gameLogs = [];
function appendLog(line, stream = 'stdout') {
  const entry = { time: new Date().toLocaleTimeString(), stream, text: String(line).trimEnd() };
  gameLogs.push(entry);
  if (gameLogs.length > LOG_BUFFER_MAX) gameLogs.shift();
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('game-log-stream', entry);
}

function killActiveGameProcessSync() {
  if (!activeGameProcess?.pid) return;
  try {
    if (process.platform === 'win32') execSync(`taskkill /pid ${activeGameProcess.pid} /T /F`);
    else activeGameProcess.kill('SIGKILL');
  } catch (e) {}
  activeGameProcess = null;
  isLaunchingGame = false;
}

function flushConfigToDisk() {
  if (configWriteTimer) { clearTimeout(configWriteTimer); configWriteTimer = null; }
  if (!configCache) return;
  try { safeMkdirSync(APP_DIR); fs.writeFileSync(CONFIG_FILE, JSON.stringify(configCache, null, 2)); } catch (e) {}
}

function forceCleanExit() {
  flushConfigToDisk();
  killActiveGameProcessSync();
  app.exit(0);
}

function safeMkdirSync(targetPath) {
  if (!targetPath) return;
  try {
    const lstat = fs.existsSync(targetPath) ? fs.lstatSync(targetPath) : null;
    if (lstat) {
      if (lstat.isDirectory()) return;
      if (lstat.isSymbolicLink()) {
        try { if (fs.statSync(targetPath).isDirectory()) return; } catch (e) {}
      }
      try { fs.unlinkSync(targetPath); } catch (e) {}
    }
    fs.mkdirSync(targetPath, { recursive: true });
  } catch (err) {
    try { fs.mkdirSync(path.resolve(targetPath), { recursive: true }); } catch (e) {}
  }
}

function ensureProfileSubdirs(profPath) {
  if (!profPath) return;
  safeMkdirSync(profPath);
  ['mods', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots', 'config'].forEach(s => safeMkdirSync(path.join(profPath, s)));
}

function copyFolderRecursiveSync(src, dest) {
  try {
    if (!fs.existsSync(src)) return;
    safeMkdirSync(dest);
    for (const item of fs.readdirSync(src)) {
      const s = path.join(src, item), d = path.join(dest, item);
      try {
        const ls = fs.lstatSync(s);
        const isDir = ls.isSymbolicLink() ? fs.statSync(s).isDirectory() : ls.isDirectory();
        if (isDir) copyFolderRecursiveSync(s, d);
        else fs.copyFileSync(s, d);
      } catch (e) {}
    }
  } catch (e) {}
}

async function runPool(items, concurrency, worker) {
  let idx = 0;
  await Promise.all(Array.from({ length: Math.min(concurrency, Math.max(1, items.length)) }, async () => {
    while (idx < items.length) await worker(items[idx], idx++);
  }));
}

// FD-based ZIP reader (reads EOCD + Central Directory only, inflates entries on demand)
function readZipEntries(zipFilePath) {
  let fd;
  try {
    fd = fs.openSync(zipFilePath, 'r');
    const size = fs.fstatSync(fd).size;
    const tailLen = Math.min(size, 65557);
    const tail = Buffer.allocUnsafe(tailLen);
    fs.readSync(fd, tail, 0, tailLen, size - tailLen);

    let eocdInTail = -1;
    for (let i = tailLen - 22; i >= 0; i--) {
      if (tail.readUInt32LE(i) === 0x06054b50) { eocdInTail = i; break; }
    }
    if (eocdInTail === -1) return [];

    const total = tail.readUInt16LE(eocdInTail + 10);
    const cdSize = tail.readUInt32LE(eocdInTail + 12);
    const cdOffset = tail.readUInt32LE(eocdInTail + 16);
    const cdBuf = Buffer.allocUnsafe(cdSize);
    fs.readSync(fd, cdBuf, 0, cdSize, cdOffset);

    const entries = [];
    for (let i = 0, cur = 0; i < total && cur + 46 <= cdBuf.length; i++) {
      if (cdBuf.readUInt32LE(cur) !== 0x02014b50) break;
      const method = cdBuf.readUInt16LE(cur + 10);
      const compSize = cdBuf.readUInt32LE(cur + 20);
      const nameLen = cdBuf.readUInt16LE(cur + 28);
      const extraLen = cdBuf.readUInt16LE(cur + 30);
      const commentLen = cdBuf.readUInt16LE(cur + 32);
      const localOffset = cdBuf.readUInt32LE(cur + 42);
      const fileName = cdBuf.toString('utf8', cur + 46, cur + 46 + nameLen);
      cur += 46 + nameLen + extraLen + commentLen;
      const isDir = fileName.endsWith('/');

      entries.push({
        fileName, isDir,
        getBuffer: () => {
          if (isDir) return null;
          let efd;
          try {
            efd = fs.openSync(zipFilePath, 'r');
            const lh = Buffer.allocUnsafe(30);
            fs.readSync(efd, lh, 0, 30, localOffset);
            const dataStart = localOffset + 30 + lh.readUInt16LE(26) + lh.readUInt16LE(28);
            const raw = Buffer.allocUnsafe(compSize);
            fs.readSync(efd, raw, 0, compSize, dataStart);
            if (method === 0) return raw;
            if (method === 8) return zlib.inflateRawSync(raw);
            return null;
          } catch (e) { return null; }
          finally { if (efd !== undefined) try { fs.closeSync(efd); } catch (e) {} }
        }
      });
    }
    return entries;
  } catch (e) { return []; }
  finally { if (fd !== undefined) try { fs.closeSync(fd); } catch (e) {} }
}

function extractDllsFromZip(zipFilePath, destDir) {
  safeMkdirSync(destDir);
  let stat;
  try { stat = fs.statSync(zipFilePath); } catch (e) { return; }
  const stampFile = path.join(destDir, '.natives-stamp.json');
  let stamps = {};
  try { stamps = JSON.parse(fs.readFileSync(stampFile, 'utf8')); } catch (e) {}
  const key = path.basename(zipFilePath), sig = `${stat.size}:${Math.trunc(stat.mtimeMs)}`;
  if (stamps[key] === sig) return;

  for (const entry of readZipEntries(zipFilePath)) {
    if (entry.isDir || !entry.fileName.toLowerCase().endsWith('.dll')) continue;
    const data = entry.getBuffer();
    if (data) try { fs.writeFileSync(path.join(destDir, path.basename(entry.fileName)), data); } catch (e) {}
  }
  stamps[key] = sig;
  try { fs.writeFileSync(stampFile, JSON.stringify(stamps)); } catch (e) {}
}

function setHighPerformanceGpu(javaPath) {
  if (!javaPath || process.platform !== 'win32') return;
  const targets = [javaPath, javaPath.replace(/java\.exe$/i, 'javaw.exe')];
  for (const p of new Set(targets)) {
    if (p === javaPath || fs.existsSync(p)) {
      exec(`reg add "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences" /v "${p}" /t REG_SZ /d "GpuPreference=2;" /f`, () => {});
    }
  }
}

function normalizeConfig(cfg) {
  if (!cfg) {
    cfg = {
      accounts: [], activeAccount: null, offlineUsername: 'Player', selectedVersion: '1.20.4',
      bgPreset: 'waves', bgScreenshot: { path: '', blur: 6, dim: 0.55 }, colorPanel: 'standard', animSpeed: 1,
      minRam: 2048, maxRam: 4096, customJavaPath: '', globalJavaRuntime: 'auto', envVars: '', closeOnLaunch: false,
      jvmArgs: DEFAULT_ORYON_JVM, profiles: [], activeProfileId: 'default', overviewGridVersion: 21,
      overviewWidgets: DEFAULT_OVERVIEW_WIDGETS.map(w => ({ ...w, settings: { ...(w.settings || {}) } })), extensions: {}
    };
  }
  if (Array.isArray(cfg.instances) && !Array.isArray(cfg.profiles)) {
    cfg.profiles = cfg.instances.map(i => ({ ...i, path: i.id === 'default' ? MINECRAFT_DIR : path.join(PROFILES_DIR, i.id) }));
    cfg.activeProfileId = cfg.activeInstanceId || 'default';
    delete cfg.instances; delete cfg.activeInstanceId;
  }
  if (!Array.isArray(cfg.profiles) || !cfg.profiles.length) {
    cfg.profiles = [{ id: 'default', name: 'Default Profile (.minecraft)', path: MINECRAFT_DIR, version: '1.20.4', loader: 'vanilla', loaderVersion: '', javaRuntime: 'auto', minRam: 2048, maxRam: 4096, jvmArgs: DEFAULT_ORYON_JVM, customJavaPath: '', envVars: '' }];
    cfg.activeProfileId = 'default';
  }
  if (!cfg.colorPanel) cfg.colorPanel = 'standard';
  if (!Number.isFinite(cfg.animSpeed)) cfg.animSpeed = 1;
  if (!cfg.bgScreenshot || typeof cfg.bgScreenshot !== 'object') cfg.bgScreenshot = { path: '', blur: 6, dim: 0.55 };
  if (!cfg.extensions || typeof cfg.extensions !== 'object') cfg.extensions = {};
  cfg.overviewWidgets = normalizeOverviewWidgets(cfg.overviewWidgets, cfg.overviewGridVersion);
  cfg.overviewGridVersion = 21;
  return cfg;
}

function loadConfig() {
  if (configCache) return configCache;
  safeMkdirSync(APP_DIR);
  let cfg = null;
  if (fs.existsSync(CONFIG_FILE)) try { cfg = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')); } catch (e) {}
  cfg = normalizeConfig(cfg);
  for (const prof of cfg.profiles) if (prof.path && prof.id !== 'default') ensureProfileSubdirs(prof.path);
  return (configCache = cfg);
}

function saveConfig(cfg) {
  configCache = normalizeConfig(cfg);
  if (configWriteTimer) clearTimeout(configWriteTimer);
  configWriteTimer = setTimeout(flushConfigToDisk, 120);
}

function getActiveProfile(cfg) {
  const id = cfg.activeProfileId || 'default';
  return (cfg.profiles || []).find(p => p.id === id) || cfg.profiles?.[0] || {
    id: 'default', name: 'Default Profile (.minecraft)', path: MINECRAFT_DIR, version: cfg.selectedVersion || '1.20.4',
    loader: 'vanilla', loaderVersion: '', javaRuntime: 'auto', minRam: 2048, maxRam: 4096, jvmArgs: DEFAULT_ORYON_JVM, customJavaPath: '', envVars: ''
  };
}

function getActiveProfileDir(cfg) {
  const prof = getActiveProfile(cfg);
  if (prof.path && prof.id !== 'default') {
    if (!fs.existsSync(prof.path)) ensureProfileSubdirs(prof.path);
    return prof.path;
  }
  return MINECRAFT_DIR;
}

// -------------------------------------------------------------
// UNIFIED HTTP CLIENT & DOWNLOAD PIPELINE
// -------------------------------------------------------------
function httpReq(url, { method = 'GET', headers = {}, body = null, raw = false, timeout = 15000, maxRedirects = 8 } = {}) {
  return new Promise((resolve, reject) => {
    const go = (targetUrl, depth) => {
      if (depth > maxRedirects) return reject(new Error('Too many redirects'));
      let u;
      try { u = new URL(targetUrl); } catch (e) { return reject(e); }
      const client = u.protocol === 'http:' ? http : https;
      const reqHeaders = { 'User-Agent': 'Aetheria-ARM64-Launcher/1.0', ...headers };
      let payload = body;
      if (payload != null && typeof payload !== 'string' && !Buffer.isBuffer(payload)) {
        payload = JSON.stringify(payload);
        if (!reqHeaders['Content-Type']) reqHeaders['Content-Type'] = 'application/json';
      } else if (typeof payload === 'string' && !reqHeaders['Content-Type']) {
        reqHeaders['Content-Type'] = 'application/x-www-form-urlencoded';
      }
      if (payload != null) reqHeaders['Content-Length'] = Buffer.byteLength(payload);

      const req = client.request(targetUrl, { method, headers: reqHeaders, timeout }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          return go(new URL(res.headers.location, targetUrl).toString(), depth + 1);
        }
        if (method === 'HEAD') {
          res.resume();
          return resolve({ statusCode: res.statusCode });
        }
        const chunks = [];
        res.on('data', c => chunks.push(c));
        res.on('end', () => {
          const buf = Buffer.concat(chunks);
          if (res.statusCode < 200 || res.statusCode >= 300) {
            return reject(Object.assign(new Error(`HTTP ${res.statusCode}`), { statusCode: res.statusCode, body: buf.toString('utf8') }));
          }
          resolve(raw ? buf : buf.toString('utf8'));
        });
      });
      req.on('timeout', () => req.destroy(new Error('Request timeout')));
      req.on('error', reject);
      if (payload != null) req.write(payload);
      req.end();
    };
    go(url, 0);
  });
}

const httpGetText = (url, extraHeaders = {}, maxRedirects = 8) =>
  httpReq(url, { headers: { Accept: 'application/json, text/html, */*', ...extraHeaders }, maxRedirects });

async function httpGet(url, token = null) {
  const txt = await httpReq(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  try { return JSON.parse(txt); } catch (e) { return txt; }
}

async function httpPost(url, data, headers = {}) {
  const txt = await httpReq(url, { method: 'POST', body: data, headers });
  try { return JSON.parse(txt); } catch (e) { return txt; }
}

async function checkUrlReachable(url) {
  try {
    const res = await httpReq(url, { method: 'HEAD', timeout: 10000 });
    return res.statusCode === 405 || (res.statusCode >= 200 && res.statusCode < 300);
  } catch (e) { return false; }
}

function downloadFile(url, dest, onProgress = null, maxRedirects = 8) {
  return new Promise((resolve, reject) => {
    safeMkdirSync(path.dirname(dest));
    const get = (targetUrl, depth) => {
      if (depth > maxRedirects) return reject(new Error(`Too many redirects for ${url}`));
      let u;
      try { u = new URL(targetUrl); } catch (e) { return reject(e); }
      const req = (u.protocol === 'https:' ? https : http).get(targetUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher/1.0' }, timeout: 30000 }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          res.resume();
          return get(new URL(res.headers.location, targetUrl).toString(), depth + 1);
        }
        if (res.statusCode !== 200) { res.resume(); return reject(new Error(`HTTP ${res.statusCode} from ${targetUrl}`)); }
        const total = parseInt(res.headers['content-length'] || '0', 10);
        let cur = 0;
        const ws = fs.createWriteStream(dest);
        if (onProgress && total) res.on('data', c => { cur += c.length; onProgress(cur / total); });
        res.pipe(ws);
        ws.on('finish', () => ws.close(() => resolve(dest)));
        ws.on('error', (err) => { try { fs.unlinkSync(dest); } catch (e) {} reject(err); });
      });
      req.on('timeout', () => req.destroy(new Error(`Download timeout: ${targetUrl}`)));
      req.on('error', (err) => { try { fs.unlinkSync(dest); } catch (e) {} reject(err); });
    };
    get(url, 0);
  });
}

function extractMcVersion(str) {
  if (!str) return '1.20.4';
  const m = str.match(/(?:fabric-loader-[^-]+-|neoforge-|forge-)?([0-9]+\.[0-9]+(?:\.[0-9]+)?)/);
  return m ? m[1] : str;
}

function mavenToPath(coord) {
  const [base, ext = 'jar'] = coord.split('@');
  const [g, a, v, cls] = base.split(':');
  return `${g.replace(/\./g, '/')}/${a}/${v}/${a}-${v}${cls ? '-' + cls : ''}.${ext}`;
}

// -------------------------------------------------------------
// PHASE B EXTENSION LOADER
// -------------------------------------------------------------
const extensionRegistry = {
  loaded: [], tabs: [], widgets: [], beforeLaunchHooks: [], gameExitHooks: [],
  registeredHandleChannels: new Set(), registeredOnChannels: new Set()
};

function resolveExtensionSafePath(baseDir, relPath) {
  if (!relPath || typeof relPath !== 'string') return null;
  const target = path.resolve(baseDir, relPath);
  return target.startsWith(path.resolve(baseDir)) ? target : null;
}

function discoverExtensionEntries() {
  const found = new Map();
  for (const [dir, source] of [[EXTENSIONS_BUNDLED_DIR, 'bundled'], [EXTENSIONS_USER_DIR, 'user']]) {
    if (!fs.existsSync(dir)) continue;
    let items = [];
    try { items = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
    for (const entry of items) {
      if (entry.name.startsWith('.') || entry.name.startsWith('_')) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        const mf = ['extension.json', 'manifest.json'].map(n => path.join(full, n)).find(p => fs.existsSync(p));
        let manifest = { id: entry.name, name: entry.name, version: '1.0.0', main: 'index.js' };
        if (mf) {
          try { const p = JSON.parse(fs.readFileSync(mf, 'utf-8')); manifest = { ...manifest, ...p, id: p.id || entry.name }; }
          catch (err) { appendLog(`[Extensions] Invalid manifest in ${full}: ${err.message}`, 'stderr'); continue; }
        }
        const mainFile = resolveExtensionSafePath(full, manifest.main || 'index.js');
        if (mainFile && fs.existsSync(mainFile)) found.set(manifest.id, { ...manifest, dir: full, mainFile, source });
      } else if (entry.isFile() && entry.name.endsWith('.js')) {
        const id = entry.name.replace(/\.js$/i, '');
        found.set(id, { id, name: id, version: '1.0.0', dir, mainFile: full, source });
      }
    }
  }
  return Array.from(found.values());
}

function createExtensionApi(extMeta) {
  const readRel = (rel) => {
    const p = resolveExtensionSafePath(extMeta.dir, rel);
    return p && fs.existsSync(p) ? fs.readFileSync(p, 'utf-8') : '';
  };
  return {
    extensionId: extMeta.id,
    extensionDir: extMeta.dir,
    paths: { appDir: APP_DIR, extensionsUserDir: EXTENSIONS_USER_DIR, extensionsBundledDir: EXTENSIONS_BUNDLED_DIR, minecraftDir: MINECRAFT_DIR, profilesDir: PROFILES_DIR, runtimesDir: RUNTIMES_DIR },
    getConfig: () => loadConfig(),
    saveConfig: (cfg) => saveConfig(cfg),
    getActiveProfile: () => getActiveProfile(loadConfig()),
    getActiveProfileDir: () => getActiveProfileDir(loadConfig()),
    getMainWindow: () => mainWindow,
    sendToRenderer: (ch, payload) => { if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(ch, payload); },
    appendLog: (msg, stream = 'stdout') => appendLog(`[Ext:${extMeta.id}] ${msg}`, stream),
    registerIpc: (ch, handler) => {
      if (!ch || typeof handler !== 'function') return;
      if (extensionRegistry.registeredHandleChannels.has(ch)) try { ipcMain.removeHandler(ch); } catch (e) {}
      extensionRegistry.registeredHandleChannels.add(ch);
      ipcMain.handle(ch, handler);
    },
    registerIpcOn: (ch, listener) => {
      if (!ch || typeof listener !== 'function') return;
      if (extensionRegistry.registeredOnChannels.has(ch)) ipcMain.removeAllListeners(ch);
      extensionRegistry.registeredOnChannels.add(ch);
      ipcMain.on(ch, listener);
    },
    registerTab: (tabDef) => {
      if (!tabDef?.id) return;
      const rec = {
        id: String(tabDef.id), extensionId: extMeta.id, label: tabDef.label || tabDef.id,
        order: Number.isFinite(tabDef.order) ? tabDef.order : 100,
        html: tabDef.html || readRel(tabDef.htmlFile),
        css: tabDef.css || readRel(tabDef.cssFile),
        script: tabDef.script || readRel(tabDef.rendererFile)
      };
      const idx = extensionRegistry.tabs.findIndex(t => t.id === rec.id);
      if (idx !== -1) extensionRegistry.tabs[idx] = rec; else extensionRegistry.tabs.push(rec);
    },
    registerWidget: (w) => {
      if (!w?.id) return;
      const wId = String(w.id);
      const bounds = {
        defaultColSpan: clampInt(w.defaultColSpan || 6, 2, GRID_SIZE), defaultRowSpan: clampInt(w.defaultRowSpan || 4, 1, GRID_SIZE),
        minColSpan: clampInt(w.minColSpan || 2, 1, GRID_SIZE), maxColSpan: clampInt(w.maxColSpan || 21, 1, GRID_SIZE),
        minRowSpan: clampInt(w.minRowSpan || 1, 1, GRID_SIZE), maxRowSpan: clampInt(w.maxRowSpan || 16, 1, GRID_SIZE)
      };
      WIDGET_BOUNDS[wId] = bounds;
      const rec = { id: wId, extensionId: extMeta.id, name: w.name || wId, desc: w.desc || 'Extension Widget', ...bounds, renderJs: w.renderJs || w.script || readRel(w.renderFile) };
      const idx = extensionRegistry.widgets.findIndex(x => x.id === wId);
      if (idx !== -1) extensionRegistry.widgets[idx] = rec; else extensionRegistry.widgets.push(rec);
    },
    onBeforeLaunch: (fn) => { if (typeof fn === 'function') extensionRegistry.beforeLaunchHooks.push({ extensionId: extMeta.id, fn }); },
    onGameExit: (fn) => { if (typeof fn === 'function') extensionRegistry.gameExitHooks.push({ extensionId: extMeta.id, fn }); },
    utils: { httpGetText, httpGet, httpPost, downloadFile, readZipEntries, safeMkdirSync, extractMcVersion }
  };
}

async function loadExtensions() {
  Object.assign(extensionRegistry, { loaded: [], tabs: [], widgets: [], beforeLaunchHooks: [], gameExitHooks: [] });
  for (const extMeta of discoverExtensionEntries()) {
    try {
      try { delete require.cache[require.resolve(extMeta.mainFile)]; } catch (e) {}
      const mod = require(extMeta.mainFile), api = createExtensionApi(extMeta);
      if (typeof mod === 'function') await mod(api);
      else if (typeof mod?.activate === 'function') await mod.activate(api);
      else if (typeof mod?.init === 'function') await mod.init(api);
      else if (mod) {
        if (typeof mod.onBeforeLaunch === 'function') api.onBeforeLaunch(mod.onBeforeLaunch);
        if (typeof mod.onGameExit === 'function') api.onGameExit(mod.onGameExit);
      }
      const rPath = extMeta.renderer ? resolveExtensionSafePath(extMeta.dir, extMeta.renderer) : null;
      const rendererScript = rPath && fs.existsSync(rPath) ? fs.readFileSync(rPath, 'utf-8') : '';
      extensionRegistry.loaded.push({ id: extMeta.id, name: extMeta.name || extMeta.id, version: extMeta.version || '1.0.0', description: extMeta.description || '', source: extMeta.source, dir: extMeta.dir, rendererScript, status: 'active' });
      appendLog(`[Extensions] Loaded "${extMeta.name || extMeta.id}" v${extMeta.version || '1.0.0'} (${extMeta.source})`);
    } catch (err) {
      extensionRegistry.loaded.push({ id: extMeta.id, name: extMeta.name || extMeta.id, version: extMeta.version || '1.0.0', source: extMeta.source, dir: extMeta.dir, rendererScript: '', status: 'error', error: err.message });
      appendLog(`[Extensions] Failed to load "${extMeta.id}": ${err.message}`, 'stderr');
    }
  }
  extensionRegistry.tabs.sort((a, b) => a.order - b.order);
  configCache = null;
}

ipcMain.handle('get-loaded-extensions', () => ({ extensions: extensionRegistry.loaded, tabs: extensionRegistry.tabs, widgets: extensionRegistry.widgets, userExtensionsDir: EXTENSIONS_USER_DIR, bundledExtensionsDir: EXTENSIONS_BUNDLED_DIR }));
ipcMain.handle('open-extensions-dir', () => { safeMkdirSync(EXTENSIONS_USER_DIR); shell.openPath(EXTENSIONS_USER_DIR); return EXTENSIONS_USER_DIR; });
ipcMain.handle('reload-extensions', async () => { await loadExtensions(); return { extensions: extensionRegistry.loaded, tabs: extensionRegistry.tabs, widgets: extensionRegistry.widgets }; });

// -------------------------------------------------------------
// WINDOW, MEDIA, SKIN & TELEMETRY
// -------------------------------------------------------------
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1260, height: 860, minWidth: 1040, minHeight: 700, frame: false, backgroundColor: '#000000', show: false,
    webPreferences: { nodeIntegration: true, contextIsolation: false, spellcheck: false }
  });
  mainWindow.loadFile('index.html');
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('closed', () => { mainWindow = null; forceCleanExit(); });
}

app.on('second-instance', () => {
  if (mainWindow) { if (mainWindow.isMinimized()) mainWindow.restore(); mainWindow.focus(); }
});

ipcMain.on('window-min', () => mainWindow?.minimize());
ipcMain.on('window-max', () => mainWindow && (mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize()));
ipcMain.on('window-close', forceCleanExit);
ipcMain.on('open-path', (e, target) => {
  const baseDir = getActiveProfileDir(loadConfig());
  const p = target ? path.join(baseDir, target) : baseDir;
  safeMkdirSync(p);
  shell.openPath(p);
});
ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (e, cfg) => { saveConfig(cfg); return true; });

function fileToImageDataUrl(filePath) {
  if (!filePath || !fs.existsSync(filePath)) return null;
  const ext = path.extname(filePath).toLowerCase();
  const mime = (ext === '.jpg' || ext === '.jpeg') ? 'image/jpeg' : ext === '.webp' ? 'image/webp' : 'image/png';
  return `data:${mime};base64,${fs.readFileSync(filePath).toString('base64')}`;
}

async function openDialogFocused(opts, save = false) {
  const res = await (save ? dialog.showSaveDialog(mainWindow, opts) : dialog.showOpenDialog(mainWindow, opts));
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.focus();
  return res;
}

ipcMain.handle('load-image-data-url', async (e, filePath) => { try { return fileToImageDataUrl(filePath); } catch (err) { return null; } });

ipcMain.handle('get-all-screenshots', async () => {
  const cfg = loadConfig(), seen = new Set(), results = [], dirs = [];
  const activeDir = getActiveProfileDir(cfg), activeProf = getActiveProfile(cfg);
  dirs.push({ name: activeProf.name || 'Active', dir: activeDir });
  for (const p of cfg.profiles || []) {
    const pDir = p.path && p.id !== 'default' ? p.path : MINECRAFT_DIR;
    if (!dirs.some(x => x.dir === pDir)) dirs.push({ name: p.name || p.id, dir: pDir });
  }
  for (const entry of dirs) {
    const ssDir = path.join(entry.dir, 'screenshots');
    if (!fs.existsSync(ssDir)) continue;
    try {
      const files = (await fs.promises.readdir(ssDir)).filter(f => /\.(png|jpe?g|webp)$/i.test(f)).slice(-24).reverse();
      for (const f of files) {
        const full = path.join(ssDir, f);
        if (seen.has(full)) continue;
        seen.add(full);
        results.push({ fileName: f, profileName: entry.name, path: full, url: pathToFileURL(full).href });
      }
    } catch (e) {}
  }
  return results;
});

ipcMain.handle('pick-background-image', async () => {
  const res = await openDialogFocused({ title: 'Select Background Image or Screenshot', filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }, { name: 'All Files', extensions: ['*'] }], properties: ['openFile'] });
  if (res.canceled || !res.filePaths.length) return null;
  const p = res.filePaths[0];
  return { path: p, fileName: path.basename(p), url: pathToFileURL(p).href };
});

ipcMain.handle('import-skin-dialog', async () => {
  const res = await openDialogFocused({ title: 'Open Minecraft Skin PNG (64x64 or 64x32)', filters: [{ name: 'PNG Skin Texture', extensions: ['png'] }, { name: 'All Files', extensions: ['*'] }], properties: ['openFile'] });
  if (res.canceled || !res.filePaths.length) return null;
  return { fileName: path.basename(res.filePaths[0]), url: fileToImageDataUrl(res.filePaths[0]) };
});

ipcMain.handle('save-skin-dialog', async (e, { base64Png, defaultName = 'aetheria-skin.png' }) => {
  const res = await openDialogFocused({ title: 'Save Edited Skin PNG', defaultPath: defaultName, filters: [{ name: 'PNG Image', extensions: ['png'] }] }, true);
  if (res.canceled || !res.filePath) return null;
  fs.writeFileSync(res.filePath, Buffer.from(String(base64Png || '').replace(/^data:image\/png;base64,/, ''), 'base64'));
  return res.filePath;
});

ipcMain.handle('upload-mojang-skin', async (e, { base64Png, variant = 'classic' }) => {
  const cfg = loadConfig();
  let account = cfg.activeAccount;
  if (!account?.token || account.token === 'null') throw new Error('Sign in with a Microsoft account in Accounts to apply skins directly to your Minecraft profile.');
  if (account.refreshToken) account = await refreshMicrosoftAccountIfNeeded(account, cfg);

  const pngBuf = Buffer.from(String(base64Png || '').replace(/^data:image\/png;base64,/, ''), 'base64');
  const boundary = '----AetheriaSkinBoundary' + crypto.randomBytes(8).toString('hex');
  const body = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${variant === 'slim' ? 'slim' : 'classic'}\r\n--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`),
    pngBuf,
    Buffer.from(`\r\n--${boundary}--\r\n`)
  ]);
  try {
    await httpReq('https://api.minecraftservices.com/minecraft/profile/skins', {
      method: 'POST', body, headers: { Authorization: `Bearer ${account.token}`, 'Content-Type': `multipart/form-data; boundary=${boundary}` }
    });
    return { success: true, profileName: account.name };
  } catch (err) {
    let msg = err.message;
    try { const p = JSON.parse(err.body || '{}'); msg = p.errorMessage || p.error || msg; } catch (e) {}
    throw new Error(msg);
  }
});

ipcMain.handle('get-system-telemetry', () => {
  const total = os.totalmem(), free = os.freemem(), cpus = os.cpus() || [];
  return {
    totalMemMb: Math.round(total / 1048576), freeMemMb: Math.round(free / 1048576), usedMemMb: Math.round(Math.max(0, total - free) / 1048576),
    cpuModel: cpus[0] ? cpus[0].model.trim() : 'Qualcomm Snapdragon ARM64', cpuCores: cpus.length || 8,
    arch: process.arch, platform: process.platform, uptimeSec: Math.round(os.uptime())
  };
});
ipcMain.handle('get-active-game-state', () => ({ running: !!activeGameProcess, pid: activeGameProcess?.pid || null }));
ipcMain.handle('kill-active-game', () => { const had = !!activeGameProcess; killActiveGameProcessSync(); return { stopped: had }; });
// -------------------------------------------------------------
// ARM64 RUNTIMES
// -------------------------------------------------------------
function getManagedJavaPathForKey(key) {
  const def = MANAGED_JDKS[key];
  if (!def) return null;
  const p = path.join(RUNTIMES_DIR, def.dirName, 'bin', 'java.exe');
  if (fs.existsSync(p)) return p;
  if (key === '21') {
    const leg = path.join(JDK_DIR, 'bin', 'java.exe');
    if (fs.existsSync(leg)) return leg;
  }
  return null;
}

function getManagedJdkStatus() {
  return Object.entries(MANAGED_JDKS).map(([key, def]) => {
    const p = getManagedJavaPathForKey(key);
    return { key, name: def.name, version: def.version, desc: def.desc, installed: !!p, path: p };
  });
}

const execAsync = (cmd, timeout = 2000) => new Promise(r => exec(cmd, { timeout }, (e, out, err) => r((out || '') + (err || ''))));

async function scanSystemJavaRuntimesAsync() {
  const results = getManagedJdkStatus().filter(m => m.installed).map(m => ({ name: `Managed ${m.name}`, path: m.path, arch: 'ARM64', version: m.key }));
  const tasks = [];
  for (const root of ['Microsoft', 'Eclipse Adoptium', 'Zulu', 'BellSoft', 'Java'].map(d => `C:\\Program Files\\${d}`)) {
    if (!fs.existsSync(root)) continue;
    try {
      for (const entry of fs.readdirSync(root)) {
        const bin = path.join(root, entry, 'bin', 'java.exe');
        if (fs.existsSync(bin)) {
          tasks.push((async () => {
            const out = await execAsync(`"${bin}" -XshowSettings:properties -version 2>&1`, 1500);
            const arch = out.includes('aarch64') ? 'ARM64' : (out.includes('x86_64') || out.includes('amd64') ? 'x64' : 'x86');
            results.push({ name: `${entry} (${arch})`, path: bin, arch });
          })());
        }
      }
    } catch (e) {}
  }
  await Promise.all(tasks);
  return results;
}

ipcMain.handle('get-managed-jdks', getManagedJdkStatus);
ipcMain.handle('get-system-javas', scanSystemJavaRuntimesAsync);
ipcMain.handle('browse-java-path', async () => {
  const res = await openDialogFocused({ title: 'Select Java Executable (java.exe / javaw.exe)', filters: [{ name: 'Java Executable', extensions: ['exe'] }, { name: 'All Files', extensions: ['*'] }], properties: ['openFile'] });
  if (res.canceled || !res.filePaths.length) return null;
  setHighPerformanceGpu(res.filePaths[0]);
  return res.filePaths[0];
});

async function resolveJdkDownloadCandidates(version) {
  const major = String(version), out = [];
  const tryJson = async (url, pick) => { try { const d = JSON.parse(await httpGetText(url)); if (Array.isArray(d)) d.forEach(x => { const u = pick(x); if (u) out.push(u); }); } catch (e) {} };

  if (major === '8') {
    await tryJson('https://api.adoptium.net/v3/assets/latest/8/hotspot?architecture=x64&image_type=jdk&os=windows', i => i?.binary?.package?.link?.toLowerCase().endsWith('.zip') ? i.binary.package.link : null);
    await tryJson('https://api.azul.com/metadata/v1/zulu/packages/?java_version=8&os=windows&arch=x86&hw_bitness=64&archive_type=zip&java_package_type=jdk&javafx_bundled=false&latest=true&release_status=ga', p => p.download_url);
    await tryJson('https://api.bell-sw.com/v1/liberica/releases?version-feature=8&os=windows&arch=x86&bitness=64&package-type=zip&bundle-type=jdk', i => i.downloadUrl);
    out.push(MANAGED_JDKS['8'].url);
    return Array.from(new Set(out.filter(Boolean)));
  }

  if (['17', '21', '25'].includes(major)) out.push(`https://aka.ms/download-jdk/microsoft-jdk-${major}-windows-aarch64.zip`);
  await tryJson(`https://api.azul.com/metadata/v1/zulu/packages/?java_version=${encodeURIComponent(major)}&os=windows&archive_type=zip&java_package_type=jdk&javafx_bundled=false&latest=true`, p => {
    const lbl = `${p.name || ''} ${p.download_url || ''}`.toLowerCase();
    return (lbl.includes('aarch64') || lbl.includes('arm64')) && !lbl.includes('crac') ? p.download_url : null;
  });
  await tryJson(`https://api.bell-sw.com/v1/liberica/releases?version-feature=${encodeURIComponent(major)}&os=windows&arch=arm&bitness=64&package-type=zip&bundle-type=jdk`, i => i.downloadUrl);
  for (const ep of ['latest/' + encodeURIComponent(major) + '/hotspot', 'feature_releases/' + encodeURIComponent(major) + '/ea']) {
    await tryJson(`https://api.adoptium.net/v3/assets/${ep}?architecture=aarch64&image_type=jdk&os=windows`, i => i?.binary?.package?.link || i?.binaries?.[0]?.package?.link);
  }
  if (major === '25') {
    try { const html = await httpGetText('https://jdk.java.net/25/'); const m = html.match(/https:\/\/download\.java\.net\/[^"'\s<>]+windows-aarch64_bin\.zip/i); if (m?.[0]) out.push(m[0]); } catch (e) {}
  }
  if (MANAGED_JDKS[major]?.url) out.push(MANAGED_JDKS[major].url);
  return Array.from(new Set(out.filter(Boolean)));
}

function findJavaBinRoot(root, depth = 0) {
  if (fs.existsSync(path.join(root, 'bin', 'java.exe'))) return root;
  if (depth >= 3) return null;
  try {
    for (const e of fs.readdirSync(root, { withFileTypes: true })) {
      if (e.isDirectory()) { const hit = findJavaBinRoot(path.join(root, e.name), depth + 1); if (hit) return hit; }
    }
  } catch (e) {}
  return null;
}

function spawnArchiveTool(cmd, args, fallback) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, { windowsHide: true });
    let err = '';
    p.stderr.on('data', d => { err += d; });
    p.on('close', c => c === 0 ? resolve() : fallback ? fallback().then(resolve, reject) : reject(new Error(err.trim() || `${cmd} exited ${c}`)));
    p.on('error', () => fallback ? fallback().then(resolve, reject) : reject(new Error(`Failed to run ${cmd}`)));
  });
}

function extractZipAsync(zipPath, destDir) {
  const ps = () => spawnArchiveTool('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `Expand-Archive -LiteralPath '${zipPath.replace(/'/g, "''")}' -DestinationPath '${destDir.replace(/'/g, "''")}' -Force`]);
  return process.platform === 'win32' ? spawnArchiveTool('tar.exe', ['-xf', zipPath, '-C', destDir], ps) : ps();
}

async function moveDirSafe(src, dest) {
  if (fs.existsSync(dest)) fs.rmSync(dest, { recursive: true, force: true });
  safeMkdirSync(path.dirname(dest));
  for (let i = 0; i < 3; i++) {
    try { fs.renameSync(src, dest); return; }
    catch (e) {
      if (i < 2) await new Promise(r => setTimeout(r, 250));
      else { copyFolderRecursiveSync(src, dest); try { fs.rmSync(src, { recursive: true, force: true }); } catch (e2) {} }
    }
  }
}

async function installManagedJdkInternal(version, onReport = null) {
  const def = MANAGED_JDKS[String(version)];
  if (!def) throw new Error(`Unknown JDK version requested: ${version}`);
  const targetDir = path.join(RUNTIMES_DIR, def.dirName);
  const zipPath = path.join(RUNTIMES_DIR, `temp-${def.dirName}.zip`);
  const tempExtract = path.join(RUNTIMES_DIR, `temp-extract-${def.dirName}`);
  const report = (step, progress) => {
    onReport?.(step, progress);
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send('jdk-install-status', { version: String(version), step, progress });
  };

  try {
    report(`Resolving mirror for Java ${version}...`, 0.04);
    const urls = await resolveJdkDownloadCandidates(version);
    if (!urls.length) throw new Error(`No Windows package URL found for Java ${version}.`);
    let chosen = null;
    for (const u of urls) if (await checkUrlReachable(u)) { chosen = u; break; }
    const tryOrder = chosen ? [chosen, ...urls.filter(u => u !== chosen)] : urls;

    report(`Downloading ${def.name}...`, 0.08);
    let ok = false, lastErr = null;
    for (const u of tryOrder) {
      try {
        await downloadFile(u, zipPath, pct => report(`Downloading Java ${version} (${Math.round(pct * 100)}%)...`, 0.08 + pct * 0.70));
        if (fs.existsSync(zipPath) && fs.statSync(zipPath).size > 1048576) { ok = true; break; }
      } catch (e) { lastErr = e; try { fs.unlinkSync(zipPath); } catch (e2) {} }
    }
    if (!ok) throw lastErr || new Error(`Failed to download Java ${version} from any mirror.`);

    report('Extracting runtime binaries...', 0.84);
    if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true });
    safeMkdirSync(tempExtract);
    await extractZipAsync(zipPath, tempExtract);

    report('Finalizing Java runtime installation...', 0.94);
    const jdkRoot = findJavaBinRoot(tempExtract);
    if (!jdkRoot) throw new Error(`Extracted Java ${version} archive did not contain bin/java.exe`);
    await moveDirSafe(jdkRoot, targetDir);

    const javaBin = path.join(targetDir, 'bin', 'java.exe');
    if (!fs.existsSync(javaBin)) throw new Error(`Installation completed, but java.exe was not found at ${javaBin}`);
    setHighPerformanceGpu(javaBin);
    report(`Java ${version} ready!`, 1.0);
    return { success: true, path: javaBin, version: String(version) };
  } finally {
    try { if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath); } catch (e) {}
    try { if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true }); } catch (e) {}
  }
}

ipcMain.handle('install-managed-jdk', (e, { version }) => installManagedJdkInternal(version));

function getRequiredJavaMajor(targetVersionId, desc = null) {
  if (desc?.javaVersion?.majorVersion) return String(desc.javaVersion.majorVersion);
  const [maj = 1, min = 0, pat = 0] = extractMcVersion(targetVersionId || '1.20.4').split('.').map(n => parseInt(n, 10) || 0);
  if (maj >= 25) return '25';
  if (maj === 1 && min < 17) return '8';
  if (maj === 1 && (min < 20 || (min === 20 && pat <= 4))) return '17';
  return '21';
}

function resolveProfileJava(prof, globalCfg, targetVersionId, desc = null) {
  const mode = prof.javaRuntime || globalCfg.globalJavaRuntime || 'auto';
  if (['8', '17', '21', '25'].includes(mode)) { const p = getManagedJavaPathForKey(mode); if (p) return p; }
  if (mode === 'custom' || prof.customJavaPath) {
    if (prof.customJavaPath && fs.existsSync(prof.customJavaPath)) return prof.customJavaPath;
    if (globalCfg.customJavaPath && fs.existsSync(globalCfg.customJavaPath)) return globalCfg.customJavaPath;
  }
  const ideal = getRequiredJavaMajor(targetVersionId || prof.version, desc);
  return getManagedJavaPathForKey(ideal) || (ideal === '17' ? getManagedJavaPathForKey('21') : null);
}

// -------------------------------------------------------------
// PROFILES, MODPACKS & EXTERNAL SCANNERS
// -------------------------------------------------------------
ipcMain.handle('create-profile', async (e, { name, version, loader = 'vanilla', loaderVersion = '', javaRuntime = 'auto' }) => {
  const cfg = loadConfig(), id = 'prof-' + Date.now(), profPath = path.join(PROFILES_DIR, id);
  ensureProfileSubdirs(profPath);
  const newProf = { id, name: name || `Minecraft ${version}`, path: profPath, version: version || '1.20.4', loader, loaderVersion, javaRuntime, minRam: cfg.minRam || 2048, maxRam: cfg.maxRam || 4096, jvmArgs: DEFAULT_ORYON_JVM, customJavaPath: '', envVars: '' };
  cfg.profiles.push(newProf); cfg.activeProfileId = id; cfg.selectedVersion = newProf.version;
  saveConfig(cfg);
  return newProf;
});

ipcMain.handle('update-profile', async (e, data) => {
  const cfg = loadConfig(), idx = cfg.profiles.findIndex(p => p.id === data.id);
  if (idx === -1) return false;
  if (data.customJavaPath) setHighPerformanceGpu(data.customJavaPath);
  cfg.profiles[idx] = { ...cfg.profiles[idx], ...data };
  if (cfg.activeProfileId === data.id && data.version) cfg.selectedVersion = data.version;
  saveConfig(cfg);
  return true;
});

ipcMain.handle('duplicate-profile', async (e, profileId) => {
  const cfg = loadConfig(), src = cfg.profiles.find(p => p.id === profileId);
  if (!src) throw new Error('Source profile not found.');
  const newId = 'prof-' + Date.now(), newPath = path.join(PROFILES_DIR, newId);
  if (fs.existsSync(src.path)) copyFolderRecursiveSync(src.path, newPath);
  ensureProfileSubdirs(newPath);
  const cloned = { ...src, id: newId, name: `${src.name} (Copy)`, path: newPath };
  cfg.profiles.push(cloned); cfg.activeProfileId = newId;
  saveConfig(cfg);
  return cloned;
});

ipcMain.handle('delete-profile', async (e, profileId) => {
  if (profileId === 'default') return false;
  const cfg = loadConfig(), target = cfg.profiles.find(p => p.id === profileId);
  if (target?.path && fs.existsSync(target.path)) try { fs.rmSync(target.path, { recursive: true, force: true }); } catch (e) {}
  cfg.profiles = cfg.profiles.filter(p => p.id !== profileId);
  if (cfg.activeProfileId === profileId) cfg.activeProfileId = 'default';
  saveConfig(cfg);
  return true;
});

function writeZipOverrides(entries, prefix, destDir) {
  for (const entry of entries) {
    if (entry.isDir) continue;
    const norm = entry.fileName.replace(/\\/g, '/');
    if (!norm.startsWith(prefix)) continue;
    const rel = norm.slice(prefix.length);
    if (!rel || rel.includes('..')) continue;
    const out = path.join(destDir, rel);
    safeMkdirSync(path.dirname(out));
    const buf = entry.getBuffer();
    if (buf) try { fs.writeFileSync(out, buf); } catch (e) {}
  }
}

ipcMain.handle('import-modpack-dialog', async () => {
  const res = await openDialogFocused({ title: 'Import Modpack (.mrpack / CurseForge .zip)', filters: [{ name: 'Minecraft Modpacks', extensions: ['mrpack', 'zip'] }, { name: 'All Files', extensions: ['*'] }], properties: ['openFile'] });
  if (res.canceled || !res.filePaths.length) return null;

  const filePath = res.filePaths[0], entries = readZipEntries(filePath);
  if (!entries.length) throw new Error('Invalid or unreadable modpack archive.');
  const mrEntry = entries.find(e => e.fileName === 'modrinth.index.json');
  const cfEntry = entries.find(e => e.fileName === 'manifest.json');

  const cfg = loadConfig(), id = 'prof-pack-' + Date.now(), destDir = path.join(PROFILES_DIR, id);
  ensureProfileSubdirs(destDir);
  let packName = path.basename(filePath, path.extname(filePath)), mcVersion = '1.20.4', loader = 'vanilla', loaderVersion = '';

  if (mrEntry) {
    const mr = JSON.parse(mrEntry.getBuffer().toString('utf8')), deps = mr.dependencies || {};
    if (mr.name) packName = mr.name;
    if (deps.minecraft) mcVersion = deps.minecraft;
    for (const [k, l] of [['fabric-loader', 'fabric'], ['neoforge', 'neoforge'], ['forge', 'forge'], ['quilt-loader', 'quilt']]) {
      if (deps[k]) { loader = l; loaderVersion = deps[k]; break; }
    }
    const files = (mr.files || []).filter(f => f.env?.client !== 'unsupported' && f.downloads?.length);
    appendLog(`Importing Modrinth modpack "${packName}" (${files.length} files)...`);
    await runPool(files, 8, async (f) => {
      const rel = String(f.path || '').replace(/^\/+/, '');
      if (!rel || rel.includes('..')) return;
      const out = path.join(destDir, rel);
      safeMkdirSync(path.dirname(out));
      for (const dl of f.downloads) { try { await downloadFile(dl, out); break; } catch (e) {} }
    });
    writeZipOverrides(entries, 'overrides/', destDir);
    writeZipOverrides(entries, 'client-overrides/', destDir);
  } else if (cfEntry) {
    const cf = JSON.parse(cfEntry.getBuffer().toString('utf8'));
    if (cf.name) packName = cf.name;
    if (cf.minecraft?.version) mcVersion = cf.minecraft.version;
    const modLoaders = cf.minecraft?.modLoaders || [];
    const primary = (modLoaders.find(l => l.primary) || modLoaders[0] || {}).id || '';
    for (const l of ['fabric', 'neoforge', 'forge']) if (primary.startsWith(`${l}-`)) { loader = l; loaderVersion = primary.slice(l.length + 1); break; }
    const files = cf.files || [], modsDir = path.join(destDir, 'mods');
    appendLog(`Importing CurseForge modpack "${packName}" (${files.length} mods)...`);
    await runPool(files, 6, async (f) => {
      try {
        const meta = JSON.parse(await httpGetText(`https://api.curseforge.com/v1/mods/${f.projectID}/files/${f.fileID}`, { 'x-api-key': CF_API_KEY }));
        const fi = meta?.data;
        if (!fi) return;
        const dl = fi.downloadUrl || `https://edge.forgecdn.net/files/${Math.floor(fi.id / 1000)}/${fi.id % 1000}/${encodeURIComponent(fi.fileName)}`;
        await downloadFile(dl, path.join(modsDir, fi.fileName));
      } catch (e) {}
    });
    writeZipOverrides(entries, (cf.overrides || 'overrides').replace(/\/+$/, '') + '/', destDir);
  } else {
    throw new Error('Archive does not contain modrinth.index.json or CurseForge manifest.json.');
  }

  let effectiveVersion = mcVersion;
  if (loader === 'fabric' && loaderVersion) try { effectiveVersion = (await installFabricProfileInternal(mcVersion, loaderVersion)) || mcVersion; } catch (e) {}
  const newProf = { id, name: packName, path: destDir, version: effectiveVersion, loader, loaderVersion, javaRuntime: 'auto', minRam: cfg.minRam || 2048, maxRam: cfg.maxRam || 4096, jvmArgs: DEFAULT_ORYON_JVM, customJavaPath: '', envVars: '' };
  cfg.profiles.push(newProf); cfg.activeProfileId = id; cfg.selectedVersion = effectiveVersion;
  saveConfig(cfg);
  appendLog(`Modpack "${packName}" imported into profile ${id}.`);
  return newProf;
});

function parseMmcPack(packFile) {
  const pack = JSON.parse(fs.readFileSync(packFile, 'utf-8')), comps = pack.components || [];
  const mc = comps.find(c => c.uid === 'net.minecraft')?.version;
  const fab = comps.find(c => c.uid === 'net.fabricmc.fabric-loader');
  const neo = comps.find(c => c.uid === 'net.neoforged' || c.uid === 'net.neoforged.neoforge');
  const forge = comps.find(c => c.uid === 'net.minecraftforge');
  if (fab) return { mcVersion: mc, loader: 'fabric', loaderVersion: fab.version || '' };
  if (neo) return { mcVersion: mc, loader: 'neoforge', loaderVersion: neo.version || '' };
  if (forge) return { mcVersion: mc, loader: 'forge', loaderVersion: forge.version || '' };
  return { mcVersion: mc, loader: 'vanilla', loaderVersion: '' };
}

ipcMain.handle('scan-external-clients', async () => {
  const results = { prism: [], modrinth: [], lunar: [] };
  const appData = process.env.APPDATA || '', localApp = process.env.LOCALAPPDATA || '', home = process.env.USERPROFILE || '';

  for (const prismDir of [path.join(appData, 'PrismLauncher', 'instances'), path.join(localApp, 'PrismLauncher', 'instances'), path.join(home, '.local', 'share', 'PrismLauncher', 'instances')]) {
    if (!fs.existsSync(prismDir)) continue;
    try {
      for (const d of await fs.promises.readdir(prismDir, { withFileTypes: true })) {
        if (!d.isDirectory()) continue;
        const instDir = path.join(prismDir, d.name);
        if (results.prism.some(p => p.path === instDir)) continue;
        let name = d.name, mcVersion = '1.20.4', loader = 'vanilla', loaderVersion = '', minRam = 2048, maxRam = 4096;
        const cfgFile = path.join(instDir, 'instance.cfg'), packFile = path.join(instDir, 'mmc-pack.json');
        if (fs.existsSync(cfgFile)) {
          try {
            const ini = {};
            for (const ln of (await fs.promises.readFile(cfgFile, 'utf-8')).split(/\r?\n/)) {
              const i = ln.indexOf('='); if (i !== -1) ini[ln.slice(0, i).trim()] = ln.slice(i + 1).trim();
            }
            if (ini.name) name = ini.name;
            if (ini.MinMemAlloc) minRam = parseInt(ini.MinMemAlloc, 10) || 2048;
            if (ini.MaxMemAlloc) maxRam = parseInt(ini.MaxMemAlloc, 10) || 4096;
          } catch (e) {}
        }
        if (fs.existsSync(packFile)) try { const p = parseMmcPack(packFile); if (p.mcVersion) mcVersion = p.mcVersion; loader = p.loader; loaderVersion = p.loaderVersion; } catch (e) {}
        results.prism.push({ id: `ext-prism-${d.name}`, name, path: instDir, mcVersion, loader, loaderVersion, minRam, maxRam, source: 'Prism' });
      }
    } catch (e) {}
  }

  for (const mRoot of [path.join(appData, 'com.modrinth.theseus', 'profiles'), path.join(appData, 'ModrinthApp', 'profiles')]) {
    if (!fs.existsSync(mRoot)) continue;
    try {
      for (const d of await fs.promises.readdir(mRoot, { withFileTypes: true })) {
        if (!d.isDirectory()) continue;
        const profDir = path.join(mRoot, d.name), metaJson = path.join(profDir, 'profile.json');
        let name = d.name, mcVersion = '1.20.4', loader = 'vanilla', loaderVersion = '';
        if (fs.existsSync(metaJson)) try { const m = JSON.parse(await fs.promises.readFile(metaJson, 'utf-8')); name = m.name || name; mcVersion = m.game_version || mcVersion; loader = (m.loader || 'vanilla').toLowerCase(); loaderVersion = m.loader_version || ''; } catch (e) {}
        results.modrinth.push({ id: `ext-modrinth-${d.name}`, name, path: profDir, mcVersion, loader, loaderVersion, minRam: 2048, maxRam: 4096, source: 'Modrinth' });
      }
    } catch (e) {}
  }

  const lunarDir = path.join(home, '.lunarclient', 'offline', 'multiver');
  if (fs.existsSync(lunarDir)) {
    try {
      for (const d of await fs.promises.readdir(lunarDir, { withFileTypes: true })) {
        if (d.isDirectory()) results.lunar.push({ id: `ext-lunar-${d.name}`, name: `Lunar ${d.name}`, path: path.join(lunarDir, d.name), mcVersion: d.name, loader: 'lunar', loaderVersion: '', minRam: 2048, maxRam: 4096, source: 'Lunar' });
      }
    } catch (e) {}
  }
  return results;
});

ipcMain.handle('import-external-profile', async (e, { sourcePath, clientType = 'Prism', targetName = '', mcVersion = '1.20.4', loader = 'vanilla', loaderVersion = '', minRam = 2048, maxRam = 4096, javaRuntime = 'auto', jvmArgs = DEFAULT_ORYON_JVM }) => {
  if (!fs.existsSync(sourcePath)) throw new Error(`Source instance path not found: ${sourcePath}`);
  const cfg = loadConfig(), id = 'prof-imp-' + Date.now(), destDir = path.join(PROFILES_DIR, id);
  safeMkdirSync(destDir);

  let ver = mcVersion, lVer = loaderVersion, rawL = String(loader).toLowerCase();
  let normLoader = ['fabric', 'neoforge', 'forge', 'quilt'].find(l => rawL.includes(l)) || 'vanilla';
  if (clientType === 'Prism' && fs.existsSync(path.join(sourcePath, 'mmc-pack.json'))) {
    try { const p = parseMmcPack(path.join(sourcePath, 'mmc-pack.json')); if (p.mcVersion) ver = p.mcVersion; normLoader = p.loader; lVer = p.loaderVersion || lVer; } catch (err) {}
  }

  const cands = [path.join(sourcePath, 'minecraft'), path.join(sourcePath, '.minecraft'), sourcePath];
  const srcRoot = cands.find(c => fs.existsSync(c) && fs.statSync(c).isDirectory() && ['mods', 'config', 'saves', 'options.txt'].some(s => fs.existsSync(path.join(c, s)))) || cands.find(c => fs.existsSync(c)) || sourcePath;

  for (const item of ['mods', 'config', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots', 'options.txt', 'servers.dat']) {
    const s = path.join(srcRoot, item), d = path.join(destDir, item);
    if (fs.existsSync(s)) {
      try { if (fs.statSync(s).isDirectory()) copyFolderRecursiveSync(s, d); else fs.copyFileSync(s, d); } catch (err) {}
    } else if (item !== 'options.txt' && item !== 'servers.dat') safeMkdirSync(d);
  }

  let effectiveVersion = ver;
  if (normLoader === 'fabric' && lVer) try { effectiveVersion = (await installFabricProfileInternal(ver, lVer)) || ver; } catch (err) {}
  const newProf = { id, name: targetName || path.basename(sourcePath) || 'Imported Profile', path: destDir, version: effectiveVersion, loader: normLoader, loaderVersion: lVer, javaRuntime, minRam, maxRam, jvmArgs: jvmArgs || DEFAULT_ORYON_JVM, customJavaPath: '', envVars: '' };
  cfg.profiles.push(newProf); cfg.activeProfileId = id; cfg.selectedVersion = effectiveVersion;
  saveConfig(cfg);
  return newProf;
});

// -------------------------------------------------------------
// VERSIONS, LOADERS, CONTENT & MOD HUB
// -------------------------------------------------------------
function scanInstalledVersions() {
  const dir = path.join(MINECRAFT_DIR, 'versions');
  safeMkdirSync(dir);
  const list = [];
  try {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (!e.isDirectory()) continue;
      const jp = path.join(dir, e.name, `${e.name}.json`);
      if (!fs.existsSync(jp)) continue;
      try {
        const d = JSON.parse(fs.readFileSync(jp, 'utf-8')), low = e.name.toLowerCase();
        const isNeo = low.includes('neoforge');
        list.push({ id: e.name, inheritsFrom: d.inheritsFrom || null, isFabric: e.name.startsWith('fabric-loader') || !!d.inheritsFrom, isNeoForge: isNeo, isForge: !isNeo && low.includes('forge') });
      } catch (err) {}
    }
  } catch (e) {}
  return list;
}

ipcMain.handle('get-mc-versions', async () => {
  try {
    const parsed = JSON.parse(await httpGetText('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'));
    cachedManifest = parsed;
    return { latest: parsed.latest.release, versions: parsed.versions.filter(v => v.type === 'release').map(v => ({ id: v.id, url: v.url, releaseTime: v.releaseTime })), installed: scanInstalledVersions() };
  } catch (e) {
    return { latest: '1.20.4', versions: [{ id: '1.20.4', url: '' }], installed: scanInstalledVersions() };
  }
});

function getFabricMetaHost(cleanVer) {
  const [maj = 1, min = 0] = String(cleanVer).split('.').map(n => parseInt(n, 10) || 0);
  return (maj === 1 && min < 14) ? 'https://meta.legacyfabric.net' : 'https://meta.fabricmc.net';
}

async function getFabricLoadersForMc(gameVersion) {
  try {
    const clean = extractMcVersion(gameVersion);
    const list = JSON.parse(await httpGetText(`${getFabricMetaHost(clean)}/v2/versions/loader/${encodeURIComponent(clean)}`));
    return Array.isArray(list) ? list.map(i => ({ version: i.loader.version, stable: !!i.loader.stable })) : [];
  } catch (e) { return []; }
}

async function installFabricProfileInternal(gameVersion, loaderVersion) {
  const cleanVer = extractMcVersion(gameVersion);
  let chosen = loaderVersion;
  if (!chosen) {
    const list = await getFabricLoadersForMc(cleanVer);
    chosen = (list.find(l => l.stable) || list[0])?.version;
    if (!chosen) throw new Error(`No Fabric loader found for Minecraft ${cleanVer}`);
  }
  const profile = JSON.parse(await httpGetText(`${getFabricMetaHost(cleanVer)}/v2/versions/loader/${encodeURIComponent(cleanVer)}/${encodeURIComponent(chosen)}/profile/json`));
  const id = profile.id || `fabric-loader-${chosen}-${cleanVer}`, dir = path.join(MINECRAFT_DIR, 'versions', id);
  safeMkdirSync(dir);
  fs.writeFileSync(path.join(dir, `${id}.json`), JSON.stringify(profile, null, 2));
  return id;
}

ipcMain.handle('get-fabric-loaders', (e, v = '1.20.4') => getFabricLoadersForMc(v));
ipcMain.handle('install-fabric-profile', async (e, { gameVersion, loaderVersion }) => ({ success: true, profileId: await installFabricProfileInternal(gameVersion, loaderVersion) }));

ipcMain.handle('get-neoforge-versions', async (e, gameVersion = '1.20.4') => {
  const clean = extractMcVersion(gameVersion);
  try {
    const xml = await httpGetText('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml');
    const all = (xml.match(/<version>([^<]+)<\/version>/g) || []).map(v => v.replace(/<\/?version>/g, ''));
    const mm = clean.split('.').slice(1).join('.');
    return all.filter(v => v.startsWith(mm) || v.startsWith(`20.${clean.split('.')[2] || 0}`)).reverse();
  } catch (err) { return []; }
});

ipcMain.handle('install-neoforge-profile', async (e, { neoforgeVersion }) => {
  const cfg = loadConfig(), prof = getActiveProfile(cfg);
  const javaBin = resolveProfileJava(prof, cfg, neoforgeVersion) || (await findSystemArmJavaAsync()).path;
  if (!javaBin || !fs.existsSync(javaBin)) throw new Error('ARM64 JDK required for NeoForge installer. Please install Microsoft OpenJDK 21.');
  const temp = path.join(RUNTIMES_DIR, `neoforge-installer-${neoforgeVersion}.jar`);
  appendLog(`Downloading NeoForge installer (${neoforgeVersion})...`);
  await downloadFile(`https://maven.neoforged.net/releases/net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`, temp);
  appendLog('Executing headless NeoForge client installation...');
  return new Promise((resolve, reject) => {
    const p = spawn(javaBin, ['-jar', temp, '--installClient', MINECRAFT_DIR], { cwd: MINECRAFT_DIR });
    p.stdout.on('data', d => appendLog(d.toString(), 'stdout'));
    p.stderr.on('data', d => appendLog(d.toString(), 'stderr'));
    p.on('close', code => {
      try { fs.unlinkSync(temp); } catch (err) {}
      if (code === 0) { appendLog('NeoForge installed successfully.'); resolve({ success: true, profileId: `neoforge-${neoforgeVersion}` }); }
      else reject(new Error(`NeoForge installer exited with code ${code}`));
    });
  });
});

ipcMain.handle('install-shader-engine', async (e, gameVersion) => {
  const clean = extractMcVersion(gameVersion), modsDir = path.join(getActiveProfileDir(loadConfig()), 'mods');
  safeMkdirSync(modsDir);
  const results = [];
  for (const id of ['P7dR8mda', 'AANobbMI', 'YL57xq9U']) {
    let vers = [];
    try { vers = JSON.parse(await httpGetText(`https://api.modrinth.com/v2/project/${id}/version?game_versions=["${encodeURIComponent(clean)}"]&loaders=["fabric"]`)); } catch (err) {}
    if (!vers?.length) try { vers = JSON.parse(await httpGetText(`https://api.modrinth.com/v2/project/${id}/version?loaders=["fabric"]`)); } catch (err) {}
    if (vers?.length) {
      const f = vers[0].files.find(x => x.primary) || vers[0].files[0];
      await downloadFile(f.url, path.join(modsDir, f.filename));
      results.push(f.filename);
    }
  }
  return results;
});

function hashFileSha1(filePath) {
  return new Promise(resolve => {
    try {
      const h = crypto.createHash('sha1'), s = fs.createReadStream(filePath);
      s.on('data', d => h.update(d));
      s.on('end', () => resolve(h.digest('hex')));
      s.on('error', () => resolve(null));
    } catch (e) { resolve(null); }
  });
}

ipcMain.handle('check-mod-updates', async () => {
  const cfg = loadConfig(), modsDir = path.join(getActiveProfileDir(cfg), 'mods');
  safeMkdirSync(modsDir);
  const jars = fs.readdirSync(modsDir).filter(f => f.endsWith('.jar') && !f.endsWith('.disabled'));
  if (!jars.length) return [];

  const hashesMap = {};
  await runPool(jars, 8, async (file) => {
    const sha = await hashFileSha1(path.join(modsDir, file));
    if (sha) hashesMap[sha] = file;
  });
  const hashes = Object.keys(hashesMap);
  if (!hashes.length) return [];

  const hits = [];
  try {
    const verFiles = await httpPost('https://api.modrinth.com/v2/version_files', { hashes, algorithm: 'sha1' });
    const mcVer = extractMcVersion(getActiveProfile(cfg).version || cfg.selectedVersion || '1.20.4');
    await runPool(Object.entries(verFiles || {}), 8, async ([hash, info]) => {
      try {
        const vers = JSON.parse(await httpGetText(`https://api.modrinth.com/v2/project/${info.project_id}/version?game_versions=["${encodeURIComponent(mcVer)}"]`));
        if (vers?.length && vers[0].id !== info.id) {
          const f = vers[0].files.find(x => x.primary) || vers[0].files[0];
          hits.push({ currentFileName: hashesMap[hash], currentVersionNumber: info.version_number, newVersionNumber: vers[0].version_number, newFileName: f.filename, downloadUrl: f.url, projectId: info.project_id });
        }
      } catch (e) {}
    });
  } catch (err) { appendLog(`Update check fault: ${err.message}`, 'stderr'); }
  return hits;
});

ipcMain.handle('apply-mod-update', async (e, item) => {
  const dir = path.join(getActiveProfileDir(loadConfig()), 'mods');
  const oldP = path.join(dir, item.currentFileName), bakP = `${oldP}.bak`;
  if (fs.existsSync(oldP)) try { fs.renameSync(oldP, bakP); } catch (err) {}
  await downloadFile(item.downloadUrl, path.join(dir, item.newFileName));
  if (fs.existsSync(bakP)) try { fs.unlinkSync(bakP); } catch (err) {}
  return item.newFileName;
});

ipcMain.handle('get-console-logs', () => gameLogs);
ipcMain.handle('clear-console-logs', () => { gameLogs.length = 0; return true; });

function diagnoseCrash(logs) {
  const rules = [
    [l => l.includes('EXCEPTION_ACCESS_VIOLATION') || l.includes('0xc0000005'), 'ARM64 Native Execution Trap (Access Violation)', 'An unmapped instruction faulted inside Windows ARM64 translation layers. Ensure native ARM64 LWJGL binaries are unpacked and Qualcomm Adreno drivers are up to date.'],
    [l => l.includes('java.lang.UnsatisfiedLinkError'), 'Missing Native ARM64 DLL / Architecture Mismatch', 'Minecraft attempted to load an x86/x64 native binary. Verify ARM64 LWJGL extraction completed without disk permission errors.'],
    [l => l.includes('java.lang.OutOfMemoryError') || l.includes('Metaspace'), 'JVM Heap / Metaspace Exhaustion', 'The game exceeded allocated RAM. Increase Max RAM in Profile Settings to 4096MB or 6144MB.'],
    [l => l.includes('UnsupportedClassVersionError'), 'Java Runtime Version Mismatch', 'Minecraft version and Java runtime mismatch (< 1.17 requires Java 8; 1.17 – 1.20.4 requires Java 17; 1.20.5 – 1.21.x requires Java 21; 26.x+ requires Java 25). Set Java Runtime to Auto-Detect or select the matching runtime.']
  ];
  for (const [pred, title, recommendation] of rules) if (pred(logs)) return { faultIdentified: true, title, recommendation };
  return { faultIdentified: false, title: 'Unknown JVM Termination', recommendation: 'Verify that allocated memory is sufficient and incompatible mod combinations are disabled.' };
}

function findSystemArmJavaFast() {
  for (const k of ['21', '25', '17', '8']) { const p = getManagedJavaPathForKey(k); if (p) return { found: true, path: p, source: `Managed ${MANAGED_JDKS[k].name}` }; }
  for (const c of ['Microsoft\\jdk-25', 'Microsoft\\jdk-21', 'Microsoft\\jdk-17', 'Eclipse Adoptium\\jdk-21-arm64', 'Zulu\\zulu-21-arm64', 'Zulu\\zulu-17-arm64'].map(s => `C:\\Program Files\\${s}\\bin\\java.exe`)) {
    if (fs.existsSync(c)) return { found: true, path: c, source: 'Installed System ARM64 JDK' };
  }
  return { found: false, path: null, source: null };
}

async function findSystemArmJavaAsync() {
  const fast = findSystemArmJavaFast();
  if (fast.found) return fast;
  const out = await execAsync('java -XshowSettings:properties -version 2>&1', 2000);
  return out.includes('aarch64') ? { found: true, path: 'java', source: 'PATH (ARM64 Native)' } : { found: false, path: null, source: null };
}
ipcMain.handle('detect-arm-java', findSystemArmJavaAsync);
// -------------------------------------------------------------
// MICROSOFT OAUTH & SKIN STUDIO
// -------------------------------------------------------------
async function exchangeLiveTokenForMcAccount(liveRes) {
  if (!liveRes?.access_token) throw new Error(liveRes?.error_description || liveRes?.error || 'Missing Microsoft access token.');
  const xbl = await httpPost('https://user.auth.xboxlive.com/user/authenticate', { Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: liveRes.access_token }, RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT' });
  const uhs = xbl?.DisplayClaims?.xui?.[0]?.uhs;
  if (!uhs || !xbl.Token) throw new Error('Xbox Live authentication failed.');
  const xsts = await httpPost('https://xsts.auth.xboxlive.com/xsts/authorize', { Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] }, RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT' });
  if (!xsts.Token) throw new Error('XSTS authorization failed.');
  const mc = await httpPost('https://api.minecraftservices.com/authentication/login_with_xbox', { identityToken: `XBL3.0 x=${uhs};${xsts.Token}` });
  if (!mc.access_token) throw new Error('Minecraft Services login failed.');
  const prof = await httpGet('https://api.minecraftservices.com/minecraft/profile', mc.access_token);
  if (!prof?.id) throw new Error('Failed to fetch Minecraft profile ownership.');
  return { id: prof.id, name: prof.name, token: mc.access_token, refreshToken: liveRes.refresh_token || null, expiresAt: Date.now() + (parseInt(mc.expires_in || 86400, 10) * 1000) };
}

async function refreshMicrosoftAccountIfNeeded(account, cfg) {
  if (!account?.refreshToken || (account.expiresAt && Date.now() < account.expiresAt - 300000)) return account;
  const live = await httpPost('https://login.live.com/oauth20_token.srf', `client_id=${MS_CLIENT_ID}&refresh_token=${encodeURIComponent(account.refreshToken)}&grant_type=refresh_token&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}`);
  const refreshed = await exchangeLiveTokenForMcAccount(live);
  if (!refreshed.refreshToken) refreshed.refreshToken = account.refreshToken;
  cfg.accounts = (cfg.accounts || []).filter(a => a.id !== refreshed.id).concat(refreshed);
  if (cfg.activeAccount?.id === refreshed.id) cfg.activeAccount = refreshed;
  saveConfig(cfg);
  return refreshed;
}

ipcMain.handle('refresh-microsoft-account', async (e, id) => {
  const cfg = loadConfig(), target = (cfg.accounts || []).find(a => a.id === id) || cfg.activeAccount;
  if (!target) throw new Error('No account selected.');
  target.expiresAt = 0;
  return refreshMicrosoftAccountIfNeeded(target, cfg);
});

ipcMain.handle('start-microsoft-login', () => new Promise((resolve, reject) => {
  const authUrl = `https://login.live.com/oauth20_authorize.srf?client_id=${MS_CLIENT_ID}&response_type=code&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}&scope=XboxLive.signin%20offline_access&prompt=select_account`;
  const win = new BrowserWindow({ width: 520, height: 640, parent: mainWindow, modal: true, backgroundColor: '#09090b', title: 'Sign In — Microsoft' });
  win.setMenu(null);
  win.loadURL(authUrl);
  let done = false;
  const onUrl = async (newUrl) => {
    if (!newUrl?.startsWith(MS_REDIRECT_URI) || done) return;
    const p = new URL(newUrl), err = p.searchParams.get('error'), code = p.searchParams.get('code');
    if (err || code) { done = true; win.close(); if (mainWindow && !mainWindow.isDestroyed()) mainWindow.focus(); }
    if (err) return reject(new Error(p.searchParams.get('error_description') || err));
    if (code) {
      try {
        const live = await httpPost('https://login.live.com/oauth20_token.srf', `client_id=${MS_CLIENT_ID}&code=${encodeURIComponent(code)}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}`);
        const account = await exchangeLiveTokenForMcAccount(live), cfg = loadConfig();
        cfg.accounts = (cfg.accounts || []).filter(a => a.id !== account.id).concat(account);
        cfg.activeAccount = account;
        saveConfig(cfg);
        resolve(account);
      } catch (e) { reject(new Error('Auth failed: ' + (e.message || e))); }
    }
  };
  win.webContents.on('will-redirect', (e, u) => onUrl(u));
  win.webContents.on('did-navigate', (e, u) => onUrl(u));
  win.on('closed', () => { if (mainWindow && !mainWindow.isDestroyed()) mainWindow.focus(); if (!done) reject(new Error('Login window was closed.')); });
}));

const downloadSkinAsBase64 = async (url) => `data:image/png;base64,${(await httpReq(url, { raw: true })).toString('base64')}`;

ipcMain.handle('fetch-player-skin', async (e, username) => {
  const clean = username.trim();
  try {
    const prof = await httpGet(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(clean)}`);
    if (prof?.id) {
      const sess = await httpGet(`https://sessionserver.mojang.com/session/minecraft/profile/${prof.id}`);
      const prop = sess.properties?.find(p => p.name === 'textures');
      if (prop) {
        const parsed = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')), skin = parsed.textures?.SKIN;
        if (skin?.url) return { username: prof.name, uuid: prof.id, skinUrl: await downloadSkinAsBase64(skin.url), isSlim: skin.metadata?.model === 'slim' };
      }
    }
  } catch (err) {}
  try {
    const pdb = await httpGet(`https://playerdb.co/api/player/minecraft/${encodeURIComponent(clean)}`);
    if (pdb?.success && pdb.data?.player) return { username: pdb.data.player.username, uuid: pdb.data.player.id, skinUrl: await downloadSkinAsBase64(pdb.data.player.skin_texture), isSlim: false };
  } catch (err) {}
  try {
    return { username: clean, uuid: 'Unknown', skinUrl: await downloadSkinAsBase64(`https://minotar.net/skin/${encodeURIComponent(clean)}`), isSlim: false };
  } catch (err) { throw new Error(`Unable to fetch skin for ${clean}.`); }
});

// -------------------------------------------------------------
// LOCAL CONTENT, MODRINTH/CURSEFORGE HUB & VAULT
// -------------------------------------------------------------
const resolveFolderTarget = t => t === 'resourcepacks' ? 'resourcepacks' : t === 'shaderpacks' ? 'shaderpacks' : 'mods';

ipcMain.handle('get-installed-content', async (e, type = 'mods') => {
  const target = resolveFolderTarget(type), folder = path.join(getActiveProfileDir(loadConfig()), target);
  safeMkdirSync(folder);
  try {
    const files = await fs.promises.readdir(folder);
    return await Promise.all(files.map(async (file) => {
      let st = { size: 0, mtime: new Date(), isDirectory: () => false };
      try { st = await fs.promises.stat(path.join(folder, file)); } catch (err) {}
      return { fileName: file, name: file.replace(/\.disabled$/, ''), sizeMb: (st.size / 1048576).toFixed(2), modified: st.mtime.toLocaleDateString(), isEnabled: !file.endsWith('.disabled'), isDirectory: st.isDirectory(), folder: target };
    }));
  } catch (err) { return []; }
});

ipcMain.handle('toggle-content-item', async (e, { folder, fileName }) => {
  const dir = path.join(getActiveProfileDir(loadConfig()), folder), src = path.join(dir, fileName);
  const dest = path.join(dir, fileName.endsWith('.disabled') ? fileName.replace(/\.disabled$/, '') : `${fileName}.disabled`);
  if (fs.existsSync(src)) await fs.promises.rename(src, dest);
  return true;
});

ipcMain.handle('toggle-all-content', async (e, { folder, enable }) => {
  const dir = path.join(getActiveProfileDir(loadConfig()), folder);
  safeMkdirSync(dir);
  for (const f of await fs.promises.readdir(dir)) {
    if (enable && f.endsWith('.disabled')) await fs.promises.rename(path.join(dir, f), path.join(dir, f.replace(/\.disabled$/, '')));
    else if (!enable && !f.endsWith('.disabled')) await fs.promises.rename(path.join(dir, f), path.join(dir, `${f}.disabled`));
  }
  return true;
});

ipcMain.handle('delete-content-item', async (e, { folder, fileName }) => {
  const target = path.join(getActiveProfileDir(loadConfig()), folder, fileName);
  if (fs.existsSync(target)) { if (fs.statSync(target).isDirectory()) fs.rmSync(target, { recursive: true, force: true }); else fs.unlinkSync(target); }
  return true;
});

ipcMain.handle('import-content-dialog', async (e, folderType) => {
  const target = resolveFolderTarget(folderType);
  const res = await openDialogFocused({ title: `Import into ${target}`, filters: [{ name: 'Minecraft Packages', extensions: ['jar', 'zip'] }, { name: 'All Files', extensions: ['*'] }], properties: ['openFile', 'multiSelections'] });
  if (res.canceled || !res.filePaths.length) return false;
  const dest = path.join(getActiveProfileDir(loadConfig()), target);
  safeMkdirSync(dest);
  for (const src of res.filePaths) fs.copyFileSync(src, path.join(dest, path.basename(src)));
  return true;
});

ipcMain.handle('import-dropped-files', async (e, { folderType, filePaths }) => {
  const dest = path.join(getActiveProfileDir(loadConfig()), resolveFolderTarget(folderType));
  safeMkdirSync(dest);
  for (const src of filePaths) if (fs.existsSync(src)) fs.copyFileSync(src, path.join(dest, path.basename(src)));
  return true;
});

const contentFolderForType = t => t === 'resourcepack' ? 'resourcepacks' : t === 'shader' ? 'shaderpacks' : 'mods';

ipcMain.handle('search-modrinth', async (e, { query, type = 'mod', version = '1.20.4', loader = 'all' }) => {
  const facets = [[`project_type:${type}`], [`versions:${extractMcVersion(version)}`]];
  if (type === 'mod' && loader && loader !== 'all' && loader !== 'vanilla') facets.push([`categories:${loader.toLowerCase()}`]);
  try { return JSON.parse(await httpGetText(`https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20`)).hits || []; }
  catch (err) { return []; }
});

async function installModrinthWithDeps(projectId, type, cleanVer, loader, destDir, visited, depth, installed) {
  if (!projectId || visited.has(projectId) || depth > 2) return;
  visited.add(projectId);
  const lq = (type === 'mod' && loader && loader !== 'all' && loader !== 'vanilla') ? `&loaders=["${encodeURIComponent(loader.toLowerCase())}"]` : '';
  let vers = [];
  try { vers = JSON.parse(await httpGetText(`https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${encodeURIComponent(cleanVer)}"]${lq}`)); } catch (e) {}
  if (!vers?.length && type !== 'mod') try { vers = JSON.parse(await httpGetText(`https://api.modrinth.com/v2/project/${projectId}/version`)); } catch (e) {}
  if (!vers?.length) { if (depth === 0) throw new Error(`No compatible release found for MC ${cleanVer}${loader && loader !== 'all' ? ` (${loader})` : ''}.`); return; }
  const f = vers[0].files.find(x => x.primary) || vers[0].files[0];
  if (!f) return;
  const fp = path.join(destDir, f.filename);
  if (!fs.existsSync(fp)) await downloadFile(f.url, fp);
  installed.push(f.filename);
  if (type === 'mod' && Array.isArray(vers[0].dependencies)) {
    for (const d of vers[0].dependencies) if (d.dependency_type === 'required' && d.project_id && !visited.has(d.project_id)) {
      try { await installModrinthWithDeps(d.project_id, 'mod', cleanVer, loader, destDir, visited, depth + 1, installed); } catch (e) {}
    }
  }
}

ipcMain.handle('install-modrinth-item', async (e, { projectId, type, version = '1.20.4', loader = 'all' }) => {
  const destDir = path.join(getActiveProfileDir(loadConfig()), contentFolderForType(type));
  safeMkdirSync(destDir);
  const installed = [];
  await installModrinthWithDeps(projectId, type, extractMcVersion(version), loader, destDir, new Set(), 0, installed);
  return { primary: installed[0], all: installed, depsCount: Math.max(0, installed.length - 1) };
});

const cfClassId = t => t === 'resourcepack' ? 12 : t === 'shader' ? 6552 : 6;
const cfLoaderParam = (t, l) => (t === 'mod' && l && l !== 'all' && CF_LOADER_MAP[l.toLowerCase()]) ? `&modLoaderType=${CF_LOADER_MAP[l.toLowerCase()]}` : '';

ipcMain.handle('search-curseforge', async (e, { query, type = 'mod', version = '1.20.4', loader = 'all' }) => {
  try {
    const raw = await httpGetText(`https://api.curseforge.com/v1/mods/search?gameId=432&classId=${cfClassId(type)}&searchFilter=${encodeURIComponent(query || '')}&gameVersion=${encodeURIComponent(extractMcVersion(version))}${cfLoaderParam(type, loader)}&pageSize=20&sortField=2&sortOrder=desc`, { 'x-api-key': CF_API_KEY });
    return (JSON.parse(raw).data || []).map(m => ({ id: m.id, title: m.name, author: m.authors?.[0]?.name || 'Unknown', description: m.summary, icon_url: m.logo?.thumbnailUrl || null, provider: 'curseforge' }));
  } catch (err) { return []; }
});

async function installCurseforgeWithDeps(modId, type, cleanVer, loader, destDir, visited, depth, installed) {
  const key = String(modId);
  if (!modId || visited.has(key) || depth > 2) return;
  visited.add(key);
  let files = [];
  try { files = JSON.parse(await httpGetText(`https://api.curseforge.com/v1/mods/${modId}/files?gameVersion=${encodeURIComponent(cleanVer)}${cfLoaderParam(type, loader)}`, { 'x-api-key': CF_API_KEY })).data || []; } catch (e) {}
  if (!files.length && type !== 'mod') try { files = JSON.parse(await httpGetText(`https://api.curseforge.com/v1/mods/${modId}/files`, { 'x-api-key': CF_API_KEY })).data || []; } catch (e) {}
  if (!files.length) { if (depth === 0) throw new Error(`No compatible CurseForge build found for MC ${cleanVer}${loader && loader !== 'all' ? ` (${loader})` : ''}.`); return; }
  const f = files[0], dl = f.downloadUrl || `https://edge.forgecdn.net/files/${Math.floor(f.id / 1000)}/${f.id % 1000}/${encodeURIComponent(f.fileName)}`;
  const fp = path.join(destDir, f.fileName);
  if (!fs.existsSync(fp)) await downloadFile(dl, fp);
  installed.push(f.fileName);
  if (type === 'mod' && Array.isArray(f.dependencies)) {
    for (const d of f.dependencies) if (d.relationType === 3 && d.modId && !visited.has(String(d.modId))) {
      try { await installCurseforgeWithDeps(d.modId, 'mod', cleanVer, loader, destDir, visited, depth + 1, installed); } catch (e) {}
    }
  }
}

ipcMain.handle('install-curseforge-item', async (e, { modId, type, version = '1.20.4', loader = 'all' }) => {
  const destDir = path.join(getActiveProfileDir(loadConfig()), contentFolderForType(type));
  safeMkdirSync(destDir);
  const installed = [];
  await installCurseforgeWithDeps(modId, type, extractMcVersion(version), loader, destDir, new Set(), 0, installed);
  return { primary: installed[0], all: installed, depsCount: Math.max(0, installed.length - 1) };
});

ipcMain.handle('get-vault-data', async () => {
  const base = getActiveProfileDir(loadConfig()), savesDir = path.join(base, 'saves'), ssDir = path.join(base, 'screenshots');
  safeMkdirSync(savesDir); safeMkdirSync(ssDir);
  const worlds = [], screenshots = [];
  try {
    await Promise.all((await fs.promises.readdir(savesDir, { withFileTypes: true })).map(async d => {
      if (!d.isDirectory()) return;
      const p = path.join(savesDir, d.name);
      let bytes = 0;
      try { for (const f of await fs.promises.readdir(p)) { const st = await fs.promises.stat(path.join(p, f)).catch(() => null); if (st) bytes += st.size; } } catch (e) {}
      worlds.push({ name: d.name, folder: d.name, sizeMb: (bytes / 1048576).toFixed(1) });
    }));
  } catch (e) {}
  try {
    const files = (await fs.promises.readdir(ssDir)).filter(f => /\.(png|jpe?g|webp)$/i.test(f)).slice(-24).reverse();
    for (const f of files) { const full = path.join(ssDir, f); screenshots.push({ fileName: f, path: full, url: pathToFileURL(full).href }); }
  } catch (e) {}
  return { worlds, screenshots };
});

ipcMain.handle('backup-world', async (e, folderName) => {
  const base = getActiveProfileDir(loadConfig()), target = path.join(base, 'saves', folderName), backups = path.join(base, 'backups');
  safeMkdirSync(backups);
  const zipTarget = path.join(backups, `${folderName}-backup-${Date.now()}.zip`);
  const ps = () => spawnArchiveTool('powershell.exe', ['-NoProfile', '-Command', `Compress-Archive -Path '${target.replace(/'/g, "''")}' -DestinationPath '${zipTarget.replace(/'/g, "''")}' -Force`]);
  if (process.platform === 'win32') await spawnArchiveTool('tar.exe', ['-a', '-cf', zipTarget, '-C', path.join(base, 'saves'), folderName], ps);
  else await ps();
  return zipTarget;
});

// -------------------------------------------------------------
// GAME LAUNCH PIPELINE
// -------------------------------------------------------------
const LAUNCHER_FEATURES = { is_demo_user: false, has_custom_resolution: false, has_quick_plays_support: false, is_quick_play_singleplayer: false, is_quick_play_multiplayer: false, is_quick_play_realms: false, is_quick_play_chat: false };

async function resolveVanillaDescriptor(versionId, sendStatus) {
  const dir = path.join(MINECRAFT_DIR, 'versions', versionId), jp = path.join(dir, `${versionId}.json`);
  safeMkdirSync(dir);
  if (!fs.existsSync(jp) || fs.statSync(jp).size < 100) {
    sendStatus(`Fetching version descriptor for ${versionId}...`);
    if (!cachedManifest) cachedManifest = JSON.parse(await httpGetText('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'));
    const entry = cachedManifest.versions.find(v => v.id === versionId);
    if (!entry) throw new Error(`Version ${versionId} is not in Mojang's manifest.`);
    await downloadFile(entry.url, jp);
  }
  return JSON.parse(fs.readFileSync(jp, 'utf-8'));
}

function parseEnvString(str = '') {
  const env = {};
  for (const ln of str.split(/\r?\n/)) {
    const t = ln.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i > 0) env[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return env;
}

function evaluateRules(rules) {
  if (!rules?.length) return true;
  let allowed = false;
  for (const r of rules) {
    let cond = true;
    if (r.os) {
      if (r.os.name && r.os.name !== 'windows') cond = false;
      if (r.os.arch && r.os.arch !== 'arm64' && r.os.arch !== 'aarch64') cond = false;
    }
    if (r.features) for (const [k, v] of Object.entries(r.features)) if (!!LAUNCHER_FEATURES[k] !== !!v) { cond = false; break; }
    if (cond) allowed = (r.action === 'allow');
  }
  return allowed;
}

const resolveArgTokens = (arg, ctx) => arg.replace(/\$\{([^}]+)\}/g, (_, k) => (k in ctx ? ctx[k] : `\${${k}}`));

async function ensureAssetsDownloaded(assetIndexFile, sendStatus) {
  if (!fs.existsSync(assetIndexFile)) return;
  let objects;
  try { objects = JSON.parse(fs.readFileSync(assetIndexFile, 'utf-8'))?.objects; } catch (e) { return; }
  if (!objects) return;
  const objectsDir = path.join(MINECRAFT_DIR, 'assets', 'objects'), missing = [];
  safeMkdirSync(objectsDir);

  await runPool(Object.values(objects), 32, async (obj) => {
    if (!obj?.hash) return;
    const prefix = obj.hash.slice(0, 2), dest = path.join(objectsDir, prefix, obj.hash);
    const st = await fs.promises.stat(dest).catch(() => null);
    if (!st || (obj.size && st.size !== obj.size)) missing.push({ hash: obj.hash, prefix, dest });
  });
  if (!missing.length) return;
  sendStatus(`Downloading ${missing.length} missing game assets (textures & audio)...`);
  let done = 0;
  await runPool(missing, 16, async (item) => {
    try { await downloadFile(`https://resources.download.minecraft.net/${item.prefix}/${item.hash}`, item.dest); } catch (e) {}
    if (++done % 150 === 0 || done === missing.length) sendStatus(`Downloading game assets (${done}/${missing.length})...`);
  });
}

const getMavenGroupArtifact = coord => {
  if (!coord) return null;
  const p = coord.split('@')[0].split(':');
  return p.length >= 2 ? `${p[0]}:${p[1]}${p[3] ? ':' + p[3] : ''}` : null;
};

async function runGameExitHooks(payload) {
  for (const h of extensionRegistry.gameExitHooks) {
    try { await h.fn(payload); } catch (e) { appendLog(`[Ext:${h.extensionId}] onGameExit error: ${e.message}`, 'stderr'); }
  }
}

ipcMain.on('launch-game', async (event, reqCfg) => {
  if (isLaunchingGame || activeGameProcess) return;
  isLaunchingGame = true;
  const sendStatus = (message, isError = false) => { appendLog(message, isError ? 'stderr' : 'stdout'); event.reply('launch-status', { message, isError }); };

  try {
    const globalCfg = loadConfig(), prof = getActiveProfile(globalCfg), activeGameDir = getActiveProfileDir(globalCfg);
    let versionId = prof.version || reqCfg?.selectedVersion || '1.20.4';
    const profLoader = String(prof.loader || 'vanilla').toLowerCase();

    if (profLoader === 'fabric' && !versionId.startsWith('fabric-loader-')) {
      const clean = extractMcVersion(versionId);
      const match = scanInstalledVersions().find(v => v.isFabric && v.inheritsFrom === clean && (!prof.loaderVersion || v.id.includes(prof.loaderVersion)));
      if (match) versionId = match.id;
      else { sendStatus(`Setting up Fabric loader profile for MC ${clean}...`); versionId = await installFabricProfileInternal(clean, prof.loaderVersion || ''); }
      prof.version = versionId; saveConfig(globalCfg);
    } else if (profLoader === 'neoforge' && !versionId.toLowerCase().includes('neoforge')) {
      const clean = extractMcVersion(versionId);
      const match = scanInstalledVersions().find(v => v.isNeoForge && (v.inheritsFrom === clean || v.id.includes(clean)));
      if (match) { versionId = match.id; prof.version = versionId; saveConfig(globalCfg); }
    }

    const versionsDir = path.join(MINECRAFT_DIR, 'versions', versionId), vJsonPath = path.join(versionsDir, `${versionId}.json`);
    safeMkdirSync(versionsDir);
    const versionData = fs.existsSync(vJsonPath) ? JSON.parse(fs.readFileSync(vJsonPath, 'utf-8')) : await resolveVanillaDescriptor(versionId, sendStatus);
    const isInherited = !!versionData.inheritsFrom;
    const baseGameVersion = isInherited ? versionData.inheritsFrom : versionId;
    if (isInherited) sendStatus(`Resolving base client (${baseGameVersion})...`);
    const parentData = isInherited ? await resolveVanillaDescriptor(baseGameVersion, sendStatus) : null;
    const baseDesc = parentData || versionData;

    const reqMajor = getRequiredJavaMajor(baseGameVersion, baseDesc);
    let javaBin = resolveProfileJava(prof, globalCfg, baseGameVersion, baseDesc);
    if (!javaBin && (prof.javaRuntime === 'auto' || !prof.javaRuntime) && MANAGED_JDKS[reqMajor]) {
      sendStatus(`Minecraft ${baseGameVersion} requires Java ${reqMajor}. Installing managed runtime automatically...`);
      javaBin = (await installManagedJdkInternal(reqMajor, step => sendStatus(step))).path;
    }
    if (!javaBin || !fs.existsSync(javaBin)) {
      isLaunchingGame = false;
      return sendStatus(`Java ${reqMajor} runtime not found. Please install Java ${reqMajor} in Settings -> Runtimes.`, true);
    }

    let account = globalCfg.activeAccount;
    if (account?.refreshToken) {
      try { sendStatus('Verifying Microsoft session...'); account = await refreshMicrosoftAccountIfNeeded(account, globalCfg); }
      catch (authErr) { appendLog(`Session refresh warning: ${authErr.message}`, 'stderr'); }
    }
    if (!account) account = { name: globalCfg.offlineUsername || 'Player', id: '00000000000000000000000000000000', token: 'null' };

    setHighPerformanceGpu(javaBin);
    sendStatus(`Preparing Snapdragon X Oryon launch pipeline using ${path.basename(path.dirname(path.dirname(javaBin)))}...`);

    const baseDir = path.join(MINECRAFT_DIR, 'versions', baseGameVersion), clientJar = path.join(baseDir, `${baseGameVersion}.jar`), nativesDir = path.join(baseDir, 'natives-arm64');
    safeMkdirSync(nativesDir);
    if (!fs.existsSync(clientJar) || fs.statSync(clientJar).size < 1000) {
      const url = baseDesc.downloads?.client?.url;
      if (!url) throw new Error(`Missing client binary for ${baseGameVersion}`);
      sendStatus(`Downloading client binary (${baseGameVersion}.jar)...`);
      await downloadFile(url, clientJar);
    }

    sendStatus('Verifying libraries & native ARM64 LWJGL binaries...');
    const classpath = [], seenGA = new Set();
    const addCp = (p, coord) => {
      if (!p || !fs.existsSync(p)) return;
      const ga = getMavenGroupArtifact(coord);
      if (ga) { if (seenGA.has(ga)) return; seenGA.add(ga); }
      if (!classpath.includes(p)) classpath.push(p);
    };

    if (isInherited && Array.isArray(versionData.libraries)) {
      const activeModLibs = versionData.libraries.filter(l => l.name && evaluateRules(l.rules));
      await runPool(activeModLibs, 8, async (lib) => {
        const art = lib.downloads?.artifact;
        const dest = art ? path.join(MINECRAFT_DIR, 'libraries', art.path) : path.join(MINECRAFT_DIR, 'libraries', mavenToPath(lib.name));
        if (!fs.existsSync(dest) || fs.statSync(dest).size < 10) {
          const url = art?.url || `${(lib.url || 'https://maven.fabricmc.net/').replace(/\/?$/, '/')}${mavenToPath(lib.name)}`;
          if (url) try { await downloadFile(url, dest); } catch (e) {}
        }
      });
      for (const lib of activeModLibs) {
        const dest = lib.downloads?.artifact ? path.join(MINECRAFT_DIR, 'libraries', lib.downloads.artifact.path) : path.join(MINECRAFT_DIR, 'libraries', mavenToPath(lib.name));
        addCp(dest, lib.name);
      }
    }

    const activeVanillaLibs = (baseDesc.libraries || []).filter(l => evaluateRules(l.rules));
    await runPool(activeVanillaLibs, 8, async (lib) => {
      const art = lib.downloads?.artifact;
      if (art) {
        const dest = path.join(MINECRAFT_DIR, 'libraries', art.path);
        if (!fs.existsSync(dest) || fs.statSync(dest).size < 10) await downloadFile(art.url, dest);
        if (lib.name?.includes('natives-windows-arm64')) extractDllsFromZip(dest, nativesDir);
      }
      const cls = lib.downloads?.classifiers;
      if (cls) {
        const nat = cls['natives-windows-arm64'];
        if (nat) {
          const natJar = path.join(MINECRAFT_DIR, 'libraries', nat.path);
          if (!fs.existsSync(natJar) || fs.statSync(natJar).size < 10) await downloadFile(nat.url, natJar);
          extractDllsFromZip(natJar, nativesDir);
        } else if (lib.name?.startsWith('org.lwjgl')) {
          const [, artifact, ver] = lib.name.split(':');
          const rel = `org/lwjgl/${artifact}/${ver}/${artifact}-${ver}-natives-windows-arm64.jar`, dest = path.join(MINECRAFT_DIR, 'libraries', rel);
          if (!fs.existsSync(dest) || fs.statSync(dest).size < 10) try { await downloadFile(`https://repo1.maven.org/maven2/${rel}`, dest); } catch (e) {}
          if (fs.existsSync(dest) && fs.statSync(dest).size > 100) extractDllsFromZip(dest, nativesDir);
        }
      }
    });
    for (const lib of activeVanillaLibs) if (lib.downloads?.artifact) addCp(path.join(MINECRAFT_DIR, 'libraries', lib.downloads.artifact.path), lib.name);
    classpath.push(clientJar);

    const assetInfo = baseDesc.assetIndex || { id: 'legacy' };
    const assetIdxDir = path.join(MINECRAFT_DIR, 'assets', 'indexes'), assetIdxFile = path.join(assetIdxDir, `${assetInfo.id}.json`);
    safeMkdirSync(assetIdxDir);
    if (assetInfo.url && (!fs.existsSync(assetIdxFile) || fs.statSync(assetIdxFile).size < 50)) {
      sendStatus('Downloading game assets index...');
      await downloadFile(assetInfo.url, assetIdxFile);
    }
    await ensureAssetsDownloaded(assetIdxFile, sendStatus);

    const mainClass = versionData.mainClass || parentData?.mainClass || 'net.minecraft.client.main.Main';
    const userJvmFlags = (prof.jvmArgs || globalCfg.jvmArgs || DEFAULT_ORYON_JVM).split(' ').filter(Boolean);
    sendStatus('Booting instance on Snapdragon X Oryon ARM64...');

    const ctx = {
      auth_player_name: account.name, version_name: versionId, game_directory: activeGameDir,
      assets_root: path.join(MINECRAFT_DIR, 'assets'), assets_index_name: assetInfo.id || 'legacy',
      auth_uuid: account.id.replace(/-/g, ''), auth_access_token: account.token,
      user_type: account.token !== 'null' ? 'msa' : 'mojang', version_type: 'release',
      natives_directory: nativesDir, library_directory: path.join(MINECRAFT_DIR, 'libraries'),
      classpath_separator: ';', launcher_name: 'Aetheria', launcher_version: '1.0.0', classpath: classpath.join(';')
    };

    const jvmArgs = [
      `-Xms${prof.minRam || globalCfg.minRam || 2048}M`, `-Xmx${prof.maxRam || globalCfg.maxRam || 4096}M`,
      `-Djava.library.path=${nativesDir}`, `-Dorg.lwjgl.librarypath=${nativesDir}`,
      '-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=false', '-Dio.netty.tryReflectionSetAccessible=true', ...userJvmFlags
    ];

    let hasCp = false;
    for (const item of [...(parentData?.arguments?.jvm || []), ...(versionData?.arguments?.jvm || [])]) {
      const vals = typeof item === 'string' ? [item] : (typeof item === 'object' && evaluateRules(item.rules) ? (Array.isArray(item.value) ? item.value : [item.value]) : []);
      for (const v of vals) {
        if (typeof v !== 'string') continue;
        const r = resolveArgTokens(v, ctx);
        if (r.startsWith('-Djava.library.path=')) continue;
        if (r === '-cp' || r === '-classpath') hasCp = true;
        jvmArgs.push(r);
      }
    }
    if (!hasCp) jvmArgs.push('-cp', ctx.classpath);

    let gameArgs = [];
    const rawGameArgs = versionData.arguments?.game || parentData?.arguments?.game;
    if (Array.isArray(rawGameArgs)) {
      const combined = [...(parentData?.arguments?.game || []), ...(versionData !== parentData ? (versionData?.arguments?.game || []) : [])];
      for (const a of combined) {
        if (typeof a === 'string') gameArgs.push(resolveArgTokens(a, ctx));
        else if (typeof a === 'object' && evaluateRules(a.rules)) {
          for (const v of (Array.isArray(a.value) ? a.value : [a.value])) if (typeof v === 'string') gameArgs.push(resolveArgTokens(v, ctx));
        }
      }
    } else if (typeof (versionData.minecraftArguments || parentData?.minecraftArguments) === 'string') {
      gameArgs = (versionData.minecraftArguments || parentData.minecraftArguments).split(' ').map(t => resolveArgTokens(t, ctx));
    } else {
      gameArgs = ['--username', account.name, '--version', versionId, '--gameDir', activeGameDir, '--assetsDir', ctx.assets_root, '--assetIndex', ctx.assets_index_name, '--uuid', ctx.auth_uuid, '--accessToken', account.token, '--userType', ctx.user_type];
    }

    const launchContext = {
      prof, globalCfg, versionId, baseGameVersion, loader: profLoader, activeGameDir, javaBin, mainClass, jvmArgs, gameArgs, classpath,
      env: { ...process.env, ...parseEnvString(globalCfg.envVars || ''), ...parseEnvString(prof.envVars || '') },
      sendStatus, appendLog
    };
    for (const h of extensionRegistry.beforeLaunchHooks) {
      try { await h.fn(launchContext); } catch (extErr) { appendLog(`[Ext:${h.extensionId}] onBeforeLaunch warning: ${extErr.message}`, 'stderr'); }
    }

    activeGameProcess = spawn(launchContext.javaBin || javaBin, [...launchContext.jvmArgs, launchContext.mainClass || mainClass, ...launchContext.gameArgs], { cwd: activeGameDir, env: launchContext.env });
    let spawnedPid = null, totalOutput = '';

    activeGameProcess.stdout.on('data', d => {
      const line = d.toString();
      totalOutput = (totalOutput + line).slice(-131072);
      appendLog(line, 'stdout');
      if (line.includes('Setting user:') || line.includes('Backend library:')) sendStatus('Minecraft window active.');
    });
    activeGameProcess.stderr.on('data', d => {
      const line = d.toString();
      totalOutput = (totalOutput + line).slice(-131072);
      appendLog(line, 'stderr');
    });
    activeGameProcess.on('spawn', () => {
      isLaunchingGame = false;
      spawnedPid = activeGameProcess?.pid || null;
      event.reply('game-started', { pid: spawnedPid });
      sendStatus(`Minecraft running (PID: ${spawnedPid})`);
      if (globalCfg.closeOnLaunch) setTimeout(() => mainWindow?.hide(), 2500);
    });

    const onFinish = (code, crashed, diag) => {
      activeGameProcess = null; isLaunchingGame = false;
      if (mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible()) mainWindow.show();
      runGameExitHooks({ code, crashed, pid: spawnedPid, prof, versionId, baseGameVersion });
      if (crashed && diag) { event.reply('game-crashed', { code, diag }); sendStatus(`Game crashed (Exit Code ${code}): ${diag.title}`, true); }
      else { event.reply('game-exited', { code }); }
    };

    activeGameProcess.on('close', code => {
      const crashed = code !== 0 && code !== null;
      onFinish(crashed ? code : 0, crashed, crashed ? diagnoseCrash(totalOutput) : null);
      if (!crashed) sendStatus('Minecraft closed normally.');
    });
    activeGameProcess.on('error', err => {
      onFinish(-1, true, null);
      sendStatus(`Execution error: ${err.message}`, true);
    });
  } catch (err) {
    activeGameProcess = null; isLaunchingGame = false;
    event.reply('game-exited', { code: -1 });
    sendStatus(`Launch failed: ${err.message}`, true);
  }
});

app.whenReady().then(async () => {
  [APP_DIR, RUNTIMES_DIR, PROFILES_DIR, EXTENSIONS_USER_DIR].forEach(safeMkdirSync);
  await loadExtensions();
  createWindow();
});
app.on('before-quit', forceCleanExit);
app.on('window-all-closed', forceCleanExit);
