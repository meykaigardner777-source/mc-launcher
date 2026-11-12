const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const zlib = require('zlib');
const { spawn, exec, execSync } = require('child_process');

// -------------------------------------------------------------
// PROCESS GUARDS & SINGLE INSTANCE LOCK
// -------------------------------------------------------------
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
  process.exit(0);
}

process.on('uncaughtException', (err) => {
  if (err && (err.code === 'EEXIST' || err.message.includes('EEXIST'))) {
    return;
  }
  console.error('[Uncaught Exception]', err);
});

let mainWindow;

// Directory Paths
const APP_DIR = path.join(app.getPath('appData'), '.aetheria-launcher');
const CONFIG_FILE = path.join(APP_DIR, 'launcher_config.json');
const RUNTIMES_DIR = path.join(APP_DIR, 'runtimes');
const JDK_DIR = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64');
const MINECRAFT_DIR = path.join(app.getPath('appData'), '.minecraft');
const INSTANCES_DIR = path.join(APP_DIR, 'instances');

const CF_API_KEY = '$2a$10$bL4bIL5pUWqfcO7KQtnMRehkWyMmRKon46u.85/QV/MQTgC70O80C';

// -------------------------------------------------------------
// ATOMIC DIRECTORY RESOLVER
// -------------------------------------------------------------
function safeMkdirSync(targetPath) {
  if (!targetPath) return;
  try {
    let lstat = null;
    try {
      lstat = fs.lstatSync(targetPath);
    } catch (e) {}

    if (lstat) {
      if (lstat.isDirectory()) return;
      if (lstat.isSymbolicLink()) {
        try {
          if (fs.statSync(targetPath).isDirectory()) return;
        } catch (symErr) {
          try { fs.unlinkSync(targetPath); } catch (e) {}
        }
      } else {
        try { fs.unlinkSync(targetPath); } catch (e) {}
      }
    }
    fs.mkdirSync(targetPath, { recursive: true });
  } catch (err) {
    if (err.code === 'EEXIST' || err.message.includes('EEXIST')) {
      try { if (fs.existsSync(targetPath)) return; } catch (e) {}
    }
    try {
      fs.mkdirSync(path.resolve(targetPath), { recursive: true });
    } catch (finalErr) {}
  }
}

function initDirectories() {
  safeMkdirSync(APP_DIR);
  safeMkdirSync(RUNTIMES_DIR);
  safeMkdirSync(INSTANCES_DIR);
}

// -------------------------------------------------------------
// PURE NODE.JS ZIP EXTRACTOR (ZERO POWERSHELL OVERHEAD)
// -------------------------------------------------------------
function extractDllsFromZip(zipFilePath, destDir) {
  safeMkdirSync(destDir);
  let buf;
  try {
    buf = fs.readFileSync(zipFilePath);
  } catch (e) {
    return;
  }

  let eocdOffset = -1;
  const maxSearch = Math.max(0, buf.length - 65557);
  for (let i = buf.length - 22; i >= maxSearch; i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) return;

  const totalEntries = buf.readUInt16LE(eocdOffset + 10);
  const cdOffset = buf.readUInt32LE(eocdOffset + 16);

  let cur = cdOffset;
  for (let i = 0; i < totalEntries && cur < eocdOffset; i++) {
    if (buf.readUInt32LE(cur) !== 0x02014b50) break;

    const method = buf.readUInt16LE(cur + 10);
    const compSize = buf.readUInt32LE(cur + 20);
    const nameLen = buf.readUInt16LE(cur + 28);
    const extraLen = buf.readUInt16LE(cur + 30);
    const commentLen = buf.readUInt16LE(cur + 32);
    const localHeaderOffset = buf.readUInt32LE(cur + 42);

    const fileName = buf.toString('utf8', cur + 46, cur + 46 + nameLen);
    cur += 46 + nameLen + extraLen + commentLen;

    const lowerName = fileName.toLowerCase();
    if (!lowerName.endsWith('.dll')) continue;
    if (lowerName.includes('/') && !lowerName.endsWith('.dll')) continue;

    if (localHeaderOffset + 30 > buf.length) continue;
    const localNameLen = buf.readUInt16LE(localHeaderOffset + 26);
    const localExtraLen = buf.readUInt16LE(localHeaderOffset + 28);
    const dataStart = localHeaderOffset + 30 + localNameLen + localExtraLen;
    const compressedData = buf.subarray(dataStart, dataStart + compSize);

    let uncompressedData;
    if (method === 0) {
      uncompressedData = compressedData;
    } else if (method === 8) {
      try {
        uncompressedData = zlib.inflateRawSync(compressedData);
      } catch (err) {
        continue;
      }
    } else {
      continue;
    }

    const outPath = path.join(destDir, path.basename(fileName));
    try {
      fs.writeFileSync(outPath, uncompressedData);
    } catch (e) {}
  }
}

// -------------------------------------------------------------
// SNAPDRAGON X HIGH PERFORMANCE GPU PROFILER
// -------------------------------------------------------------
function setHighPerformanceGpu(javaPath) {
  if (!javaPath || process.platform !== 'win32') return;
  try {
    const javaw = javaPath.replace(/java\.exe$/i, 'javaw.exe');
    exec(`reg add "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences" /v "${javaPath}" /t REG_SZ /d "GpuPreference=2;" /f`, () => {});
    if (fs.existsSync(javaw)) {
      exec(`reg add "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences" /v "${javaw}" /t REG_SZ /d "GpuPreference=2;" /f`, () => {});
    }
  } catch (e) {}
}

// -------------------------------------------------------------
// CONFIGURATION & INSTANCES
// -------------------------------------------------------------
function loadConfig() {
  safeMkdirSync(APP_DIR);
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      const cfg = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
      if (!cfg.instances || !cfg.instances.length) {
        cfg.instances = [{ id: 'default', name: 'Default Instance (.minecraft)', path: MINECRAFT_DIR, version: '1.20.4' }];
        cfg.activeInstanceId = 'default';
      }
      return cfg;
    } catch (e) {}
  }
  return {
    accounts: [],
    activeAccount: null,
    offlineUsername: 'Player',
    selectedVersion: '1.20.4',
    bgPreset: 'waves',
    minRam: 2048,
    maxRam: 4096,
    customJavaPath: '',
    closeOnLaunch: false,
    jvmArgs: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=20 -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:G1NewSizePercent=25',
    instances: [
      { id: 'default', name: 'Default Instance (.minecraft)', path: MINECRAFT_DIR, version: '1.20.4' }
    ],
    activeInstanceId: 'default'
  };
}

function saveConfig(cfg) {
  safeMkdirSync(APP_DIR);
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
  } catch (e) {}
}

function getActiveInstanceDir(cfg) {
  if (!cfg.activeInstanceId || cfg.activeInstanceId === 'default') {
    return MINECRAFT_DIR;
  }
  const inst = (cfg.instances || []).find(i => i.id === cfg.activeInstanceId);
  if (inst && inst.path) {
    safeMkdirSync(inst.path);
    return inst.path;
  }
  return MINECRAFT_DIR;
}

// -------------------------------------------------------------
// WINDOW SETUP
// -------------------------------------------------------------
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1220,
    height: 800,
    minWidth: 1020,
    minHeight: 680,
    frame: false,
    backgroundColor: '#09090b',
    show: false,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  mainWindow.loadFile('index.html');
  mainWindow.once('ready-to-show', () => mainWindow.show());
}

app.on('second-instance', () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  }
});

ipcMain.on('window-min', () => mainWindow.minimize());
ipcMain.on('window-max', () => mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize());
ipcMain.on('window-close', () => mainWindow.close());
ipcMain.on('open-path', (e, target) => {
  const cfg = loadConfig();
  const baseDir = getActiveInstanceDir(cfg);
  const p = path.join(baseDir, target);
  safeMkdirSync(p);
  shell.openPath(p);
});

ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (e, cfg) => { saveConfig(cfg); return true; });

// -------------------------------------------------------------
// INSTANCE CRUD
// -------------------------------------------------------------
ipcMain.handle('create-instance', async (e, { name, version }) => {
  const cfg = loadConfig();
  const id = 'inst-' + Date.now();
  const instPath = path.join(INSTANCES_DIR, id);

  ['mods', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots'].forEach(sub => {
    safeMkdirSync(path.join(instPath, sub));
  });

  const newInst = { id, name, path: instPath, version };
  cfg.instances.push(newInst);
  cfg.activeInstanceId = id;
  cfg.selectedVersion = version;
  saveConfig(cfg);
  return newInst;
});

ipcMain.handle('delete-instance', async (e, instanceId) => {
  if (instanceId === 'default') return false;
  const cfg = loadConfig();
  const target = cfg.instances.find(i => i.id === instanceId);
  if (target && target.path && fs.existsSync(target.path)) {
    try { fs.rmSync(target.path, { recursive: true, force: true }); } catch (e) {}
  }
  cfg.instances = cfg.instances.filter(i => i.id !== instanceId);
  cfg.activeInstanceId = 'default';
  saveConfig(cfg);
  return true;
});

// -------------------------------------------------------------
// DOWNLOAD PIPELINE
// -------------------------------------------------------------
function downloadFile(url, dest, onProgress = null) {
  return new Promise((resolve, reject) => {
    safeMkdirSync(path.dirname(dest));

    const get = (targetUrl) => {
      const client = targetUrl.startsWith('https:') ? https : http;
      client.get(targetUrl, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          return get(res.headers.location);
        }
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode} from ${targetUrl}`));
        }

        const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
        let cur = 0;
        const fileStream = fs.createWriteStream(dest);

        res.on('data', chunk => {
          cur += chunk.length;
          if (onProgress && totalBytes) onProgress(cur / totalBytes);
        });

        res.pipe(fileStream);
        fileStream.on('finish', () => fileStream.close(() => resolve(dest)));
      }).on('error', (err) => {
        if (fs.existsSync(dest)) fs.unlinkSync(dest);
        reject(err);
      });
    };
    get(url);
  });
}

function mavenToPath(coord) {
  let ext = 'jar';
  if (coord.includes('@')) {
    const atParts = coord.split('@');
    coord = atParts[0];
    ext = atParts[1] || 'jar';
  }
  const parts = coord.split(':');
  const group = parts[0].replace(/\./g, '/');
  const artifact = parts[1];
  const version = parts[2];
  const classifier = parts[3] ? `-${parts[3]}` : '';
  return `${group}/${artifact}/${version}/${artifact}-${version}${classifier}.${ext}`;
}

function extractMcVersion(str) {
  if (!str) return '1.20.4';
  const match = str.match(/(?:fabric-loader-[^-]+-)?([0-9]+\.[0-9]+(?:\.[0-9]+)?)/);
  return match ? match[1] : str;
}

// -------------------------------------------------------------
// VERSION MANIFEST & SCANNER
// -------------------------------------------------------------
let cachedManifest = null;

function scanInstalledVersions() {
  const versionsDir = path.join(MINECRAFT_DIR, 'versions');
  safeMkdirSync(versionsDir);

  const list = [];
  try {
    for (const entry of fs.readdirSync(versionsDir, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        const jsonPath = path.join(versionsDir, entry.name, `${entry.name}.json`);
        if (fs.existsSync(jsonPath)) {
          try {
            const data = JSON.parse(fs.readFileSync(jsonPath, 'utf-8'));
            list.push({
              id: entry.name,
              inheritsFrom: data.inheritsFrom || null,
              isFabric: entry.name.startsWith('fabric-loader') || !!data.inheritsFrom
            });
          } catch (e) {}
        }
      }
    }
  } catch (e) {}
  return list;
}

ipcMain.handle('get-mc-versions', async () => {
  return new Promise((resolve) => {
    https.get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          cachedManifest = parsed;
          const releases = parsed.versions
            .filter(v => v.type === 'release')
            .map(v => ({ id: v.id, url: v.url, releaseTime: v.releaseTime }));
          resolve({
            latest: parsed.latest.release,
            versions: releases,
            installed: scanInstalledVersions()
          });
        } catch (e) {
          resolve({ latest: '1.20.4', versions: [{ id: '1.20.4', url: '' }], installed: scanInstalledVersions() });
        }
      });
    }).on('error', () => {
      resolve({ latest: '1.20.4', versions: [{ id: '1.20.4', url: '' }], installed: scanInstalledVersions() });
    });
  });
});

// -------------------------------------------------------------
// FABRIC & SODIUM/IRIS ENGINE
// -------------------------------------------------------------
ipcMain.handle('get-fabric-loaders', async (event, gameVersion = '1.20.4') => {
  const cleanVer = extractMcVersion(gameVersion);
  const url = `https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(cleanVer)}`;
  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          if (Array.isArray(parsed) && parsed.length) {
            resolve(parsed.map(i => ({ version: i.loader.version, stable: !!i.loader.stable })));
          } else {
            resolve([]);
          }
        } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
});

ipcMain.handle('install-fabric-profile', async (event, { gameVersion, loaderVersion }) => {
  const cleanVer = extractMcVersion(gameVersion);
  const profileUrl = `https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(cleanVer)}/${encodeURIComponent(loaderVersion)}/profile/json`;
  return new Promise((resolve, reject) => {
    https.get(profileUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', async () => {
        try {
          const profile = JSON.parse(data);
          const profileId = profile.id || `fabric-loader-${loaderVersion}-${cleanVer}`;
          const targetDir = path.join(MINECRAFT_DIR, 'versions', profileId);
          safeMkdirSync(targetDir);
          fs.writeFileSync(path.join(targetDir, `${profileId}.json`), JSON.stringify(profile, null, 2));
          resolve({ success: true, profileId });
        } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
});

ipcMain.handle('install-shader-engine', async (event, gameVersion) => {
  const cleanVer = extractMcVersion(gameVersion);
  const cfg = loadConfig();
  const modsFolder = path.join(getActiveInstanceDir(cfg), 'mods');
  safeMkdirSync(modsFolder);

  const targets = [
    { name: 'Fabric API', id: 'P7dR8mda' },
    { name: 'Sodium', id: 'AANobbMI' },
    { name: 'Iris Shaders', id: 'YL57xq9U' }
  ];

  const results = [];
  for (const t of targets) {
    let url = `https://api.modrinth.com/v2/project/${t.id}/version?game_versions=["${encodeURIComponent(cleanVer)}"]&loaders=["fabric"]`;
    let verData = await new Promise((res) => {
      https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (r) => {
        let b = ''; r.on('data', c => b += c); r.on('end', () => {
          try { res(JSON.parse(b)); } catch (e) { res([]); }
        });
      }).on('error', () => res([]));
    });

    if (!verData || !verData.length) {
      // Fallback query matching loader without strict minor version locks
      const fallbackUrl = `https://api.modrinth.com/v2/project/${t.id}/version?loaders=["fabric"]`;
      verData = await new Promise((res) => {
        https.get(fallbackUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (r) => {
          let b = ''; r.on('data', c => b += c); r.on('end', () => {
            try { res(JSON.parse(b)); } catch (e) { res([]); }
          });
        }).on('error', () => res([]));
      });
    }

    if (verData && verData.length) {
      const primary = verData[0].files.find(f => f.primary) || verData[0].files[0];
      const dest = path.join(modsFolder, primary.filename);
      await downloadFile(primary.url, dest);
      results.push(primary.filename);
    }
  }

  return results;
});

// -------------------------------------------------------------
// NATIVE ARM64 JDK RUNTIME
// -------------------------------------------------------------
function findSystemArmJava() {
  const localJava = path.join(JDK_DIR, 'bin', 'java.exe');
  const localJavaw = path.join(JDK_DIR, 'bin', 'javaw.exe');
  if (fs.existsSync(localJava)) return { found: true, path: localJava, source: 'Managed ARM64 JDK' };
  if (fs.existsSync(localJavaw)) return { found: true, path: localJavaw, source: 'Managed ARM64 JDK' };

  const candidates = [
    'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
    'C:\\Program Files\\Microsoft\\jdk-17\\bin\\java.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21-arm64\\bin\\java.exe',
    'C:\\Program Files\\Zulu\\zulu-21-arm64\\bin\\java.exe'
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return { found: true, path: c, source: 'Installed System ARM64 JDK' };
  }

  try {
    const out = execSync('java -XshowSettings:properties -version 2>&1', { timeout: 2500 }).toString();
    if (out.includes('aarch64')) return { found: true, path: 'java', source: 'PATH (ARM64 Native)' };
  } catch (e) {}

  return { found: false, path: null, source: null };
}

ipcMain.handle('detect-arm-java', async () => findSystemArmJava());

ipcMain.handle('install-arm-jdk', async (event) => {
  const JDK_URL = 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-aarch64.zip';
  const zipPath = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64.zip');
  const tempExtract = path.join(RUNTIMES_DIR, 'temp-jdk-extract');

  const report = (step, progress) => event.sender.send('jdk-install-status', { step, progress });

  try {
    report('Connecting to Microsoft CDN...', 0.05);
    await downloadFile(JDK_URL, zipPath, (pct) => {
      report(`Downloading ARM64 OpenJDK (${Math.round(pct * 100)}%)...`, pct * 0.75);
    });

    report('Extracting runtime binaries...', 0.85);
    if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true });
    safeMkdirSync(tempExtract);

    execSync(`powershell.exe -NoProfile -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${tempExtract}' -Force"`);

    const entries = fs.readdirSync(tempExtract);
    const innerDir = entries.find(e => fs.statSync(path.join(tempExtract, e)).isDirectory()) || '';
    const src = path.join(tempExtract, innerDir);

    if (fs.existsSync(JDK_DIR)) fs.rmSync(JDK_DIR, { recursive: true, force: true });
    fs.renameSync(src, JDK_DIR);

    fs.rmSync(tempExtract, { recursive: true, force: true });
    if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);

    const javaBin = path.join(JDK_DIR, 'bin', 'java.exe');
    setHighPerformanceGpu(javaBin);

    report('ARM64 JDK installed successfully.', 1.0);
    return { success: true, path: javaBin };
  } catch (err) {
    if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);
    throw err;
  }
});

// -------------------------------------------------------------
// MICROSOFT OAUTH
// -------------------------------------------------------------
const MS_CLIENT_ID = '00000000402b5328';
const MS_REDIRECT_URI = 'https://login.live.com/oauth20_desktop.srf';

function httpPost(url, data, headers = {}) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const bodyStr = typeof data === 'string' ? data : JSON.stringify(data);
    const req = https.request({
      hostname: u.hostname,
      port: 443,
      path: u.pathname + u.search,
      method: 'POST',
      headers: {
        'Content-Type': typeof data === 'string' ? 'application/x-www-form-urlencoded' : 'application/json',
        'Content-Length': Buffer.byteLength(bodyStr),
        ...headers
      }
    }, (res) => {
      let b = '';
      res.on('data', c => b += c);
      res.on('end', () => {
        try { resolve(JSON.parse(b)); } catch (e) { resolve(b); }
      });
    });
    req.on('error', reject);
    req.write(bodyStr);
    req.end();
  });
}

function httpGet(url, token = null) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const client = u.protocol === 'http:' ? http : https;
    const headers = { 'User-Agent': 'Aetheria-ARM64-Launcher' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const req = client.request({
      hostname: u.hostname,
      port: u.port || (u.protocol === 'http:' ? 80 : 443),
      path: u.pathname + u.search,
      method: 'GET',
      headers
    }, (res) => {
      let b = '';
      res.on('data', c => b += c);
      res.on('end', () => {
        try { resolve(JSON.parse(b)); } catch (e) { resolve(b); }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

ipcMain.handle('start-microsoft-login', async () => {
  return new Promise((resolve, reject) => {
    const authUrl = `https://login.live.com/oauth20_authorize.srf?client_id=${MS_CLIENT_ID}&response_type=code&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}&scope=XboxLive.signin%20offline_access`;

    const authWindow = new BrowserWindow({
      width: 520,
      height: 640,
      parent: mainWindow,
      modal: true,
      backgroundColor: '#09090b',
      title: 'Sign In — Microsoft'
    });

    authWindow.setMenu(null);
    authWindow.loadURL(authUrl);

    let finished = false;

    authWindow.webContents.on('will-redirect', async (event, newUrl) => {
      if (newUrl.startsWith(MS_REDIRECT_URI)) {
        const code = new URL(newUrl).searchParams.get('code');
        if (code && !finished) {
          finished = true;
          authWindow.close();

          try {
            const liveRes = await httpPost('https://login.live.com/oauth20_token.srf',
              `client_id=${MS_CLIENT_ID}&code=${code}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}`
            );

            const xblRes = await httpPost('https://user.auth.xboxlive.com/user/authenticate', {
              Properties: {
                AuthMethod: 'RPS',
                SiteName: 'user.auth.xboxlive.com',
                RpsTicket: liveRes.access_token
              },
              RelyingParty: 'http://auth.xboxlive.com',
              TokenType: 'JWT'
            });

            const uhs = xblRes.DisplayClaims.xui[0].uhs;

            const xstsRes = await httpPost('https://xsts.auth.xboxlive.com/xsts/authorize', {
              Properties: {
                SandboxId: 'RETAIL',
                UserTokens: [xblRes.Token]
              },
              RelyingParty: 'rp://api.minecraftservices.com/',
              TokenType: 'JWT'
            });

            const mojangRes = await httpPost('https://api.minecraftservices.com/authentication/login_with_xbox', {
              identityToken: `XBL3.0 x=${uhs};${xstsRes.Token}`
            });

            const profile = await httpGet('https://api.minecraftservices.com/minecraft/profile', mojangRes.access_token);
            const account = { id: profile.id, name: profile.name, token: mojangRes.access_token };

            const cfg = loadConfig();
            cfg.accounts = (cfg.accounts || []).filter(a => a.id !== account.id);
            cfg.accounts.push(account);
            cfg.activeAccount = account;
            saveConfig(cfg);

            resolve(account);
          } catch (err) {
            reject(new Error('Auth failed: ' + (err.message || err)));
          }
        }
      }
    });

    authWindow.on('closed', () => {
      if (!finished) reject(new Error('Login window was closed.'));
    });
  });
});

// -------------------------------------------------------------
// SKIN STUDIO (MULTI-PROVIDER RESOLVER)
// -------------------------------------------------------------
async function downloadSkinAsBase64(skinUrl) {
  return new Promise((resolve, reject) => {
    const client = skinUrl.startsWith('https:') ? https : http;
    client.get(skinUrl, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return downloadSkinAsBase64(res.headers.location).then(resolve).catch(reject);
      }
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        const b64 = Buffer.concat(chunks).toString('base64');
        resolve(`data:image/png;base64,${b64}`);
      });
    }).on('error', reject);
  });
}

ipcMain.handle('fetch-player-skin', async (event, username) => {
  const cleanUser = username.trim();

  // Tier 1: Mojang Sessionserver
  try {
    const prof = await httpGet(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(cleanUser)}`);
    if (prof && prof.id) {
      const session = await httpGet(`https://sessionserver.mojang.com/session/minecraft/profile/${prof.id}`);
      const prop = session.properties && session.properties.find(p => p.name === 'textures');
      if (prop) {
        const parsed = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8'));
        const skinUrl = parsed.textures && parsed.textures.SKIN && parsed.textures.SKIN.url;
        if (skinUrl) {
          const skinB64 = await downloadSkinAsBase64(skinUrl);
          return {
            username: prof.name,
            uuid: prof.id,
            skinUrl: skinB64,
            isSlim: !!(parsed.textures.SKIN.metadata && parsed.textures.SKIN.metadata.model === 'slim')
          };
        }
      }
    }
  } catch (e) {}

  // Tier 2: PlayerDB Proxy Fallback
  try {
    const pdb = await httpGet(`https://playerdb.co/api/player/minecraft/${encodeURIComponent(cleanUser)}`);
    if (pdb && pdb.success && pdb.data && pdb.data.player) {
      const player = pdb.data.player;
      const skinB64 = await downloadSkinAsBase64(player.skin_texture);
      return {
        username: player.username,
        uuid: player.id,
        skinUrl: skinB64,
        isSlim: false
      };
    }
  } catch (e) {}

  // Tier 3: Minotar Raw Fallback
  try {
    const minotarUrl = `https://minotar.net/skin/${encodeURIComponent(cleanUser)}`;
    const skinB64 = await downloadSkinAsBase64(minotarUrl);
    return {
      username: cleanUser,
      uuid: 'Unknown',
      skinUrl: skinB64,
      isSlim: false
    };
  } catch (e) {
    throw new Error(`Unable to fetch skin for ${cleanUser}. Verify name and network connection.`);
  }
});

// -------------------------------------------------------------
// CONTENT MANAGER
// -------------------------------------------------------------
function resolveFolderTarget(type) {
  if (type === 'resourcepacks') return 'resourcepacks';
  if (type === 'shaderpacks') return 'shaderpacks';
  return 'mods';
}

ipcMain.handle('get-installed-content', async (event, type = 'mods') => {
  const cfg = loadConfig();
  const baseDir = getActiveInstanceDir(cfg);
  const target = resolveFolderTarget(type);
  const folder = path.join(baseDir, target);
  safeMkdirSync(folder);

  return fs.readdirSync(folder).map(file => {
    const full = path.join(folder, file);
    let stat = { size: 0, mtime: new Date(), isDirectory: () => false };
    try { stat = fs.statSync(full); } catch (e) {}

    return {
      fileName: file,
      name: file.replace(/\.disabled$/, ''),
      sizeMb: (stat.size / (1024 * 1024)).toFixed(2),
      modified: stat.mtime.toLocaleDateString(),
      isEnabled: !file.endsWith('.disabled'),
      isDirectory: stat.isDirectory(),
      folder: target
    };
  });
});

ipcMain.handle('toggle-content-item', async (event, { folder, fileName }) => {
  const cfg = loadConfig();
  const dir = path.join(getActiveInstanceDir(cfg), folder);
  const src = path.join(dir, fileName);
  const dest = fileName.endsWith('.disabled')
    ? path.join(dir, fileName.replace(/\.disabled$/, ''))
    : path.join(dir, `${fileName}.disabled`);
  if (fs.existsSync(src)) fs.renameSync(src, dest);
  return true;
});

ipcMain.handle('toggle-all-content', async (event, { folder, enable }) => {
  const cfg = loadConfig();
  const dir = path.join(getActiveInstanceDir(cfg), folder);
  safeMkdirSync(dir);
  for (const f of fs.readdirSync(dir)) {
    const full = path.join(dir, f);
    if (enable && f.endsWith('.disabled')) {
      fs.renameSync(full, path.join(dir, f.replace(/\.disabled$/, '')));
    } else if (!enable && !f.endsWith('.disabled')) {
      fs.renameSync(full, path.join(dir, `${f}.disabled`));
    }
  }
  return true;
});

ipcMain.handle('delete-content-item', async (event, { folder, fileName }) => {
  const cfg = loadConfig();
  const target = path.join(getActiveInstanceDir(cfg), folder, fileName);
  if (fs.existsSync(target)) {
    if (fs.statSync(target).isDirectory()) fs.rmSync(target, { recursive: true, force: true });
    else fs.unlinkSync(target);
  }
  return true;
});

ipcMain.handle('import-content-dialog', async (event, folderType) => {
  const target = resolveFolderTarget(folderType);
  const res = await dialog.showOpenDialog(mainWindow, {
    title: `Import into ${target}`,
    filters: [
      { name: 'Minecraft Packages', extensions: ['jar', 'zip'] },
      { name: 'All Files', extensions: ['*'] }
    ],
    properties: ['openFile', 'multiSelections']
  });

  if (res.canceled || !res.filePaths.length) return false;

  const cfg = loadConfig();
  const destFolder = path.join(getActiveInstanceDir(cfg), target);
  safeMkdirSync(destFolder);

  for (const src of res.filePaths) {
    fs.copyFileSync(src, path.join(destFolder, path.basename(src)));
  }
  return true;
});

ipcMain.handle('import-dropped-files', async (event, { folderType, filePaths }) => {
  const cfg = loadConfig();
  const target = resolveFolderTarget(folderType);
  const destFolder = path.join(getActiveInstanceDir(cfg), target);
  safeMkdirSync(destFolder);

  for (const src of filePaths) {
    if (fs.existsSync(src)) {
      fs.copyFileSync(src, path.join(destFolder, path.basename(src)));
    }
  }
  return true;
});

// -------------------------------------------------------------
// DUAL PROVIDER (MODRINH + CURSEFORGE)
// -------------------------------------------------------------
ipcMain.handle('search-modrinth', async (event, { query, type = 'mod', version = '1.20.4' }) => {
  const cleanVer = extractMcVersion(version);
  const facets = [
    [`project_type:${type}`],
    [`versions:${cleanVer}`]
  ];
  let url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20`;

  let hits = await new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data).hits || []); } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });

  if (!hits.length && query) {
    const relaxedFacets = [[`project_type:${type}`]];
    const fallbackUrl = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(JSON.stringify(relaxedFacets))}&limit=20`;
    hits = await new Promise((resolve) => {
      https.get(fallbackUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
        let data = '';
        res.on('data', c => data += c);
        res.on('end', () => {
          try { resolve(JSON.parse(data).hits || []); } catch (e) { resolve([]); }
        });
      }).on('error', () => resolve([]));
    });
  }

  return hits;
});

ipcMain.handle('install-modrinth-item', async (event, { projectId, type, version = '1.20.4' }) => {
  const cleanVer = extractMcVersion(version);
  let versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${encodeURIComponent(cleanVer)}"]`;

  let versions = await new Promise((resolve, reject) => {
    https.get(versionsUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (e) { resolve([]); }
      });
    }).on('error', reject);
  });

  if (!versions || !versions.length) {
    versions = await new Promise((resolve, reject) => {
      https.get(`https://api.modrinth.com/v2/project/${projectId}/version`, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
        let data = '';
        res.on('data', c => data += c);
        res.on('end', () => {
          try { resolve(JSON.parse(data)); } catch (e) { resolve([]); }
        });
      }).on('error', reject);
    });
  }

  if (!versions || !versions.length) throw new Error(`No compatible release found.`);

  const primary = versions[0].files.find(f => f.primary) || versions[0].files[0];
  let targetFolder = 'mods';
  if (type === 'resourcepack') targetFolder = 'resourcepacks';
  if (type === 'shader') targetFolder = 'shaderpacks';

  const cfg = loadConfig();
  const destDir = path.join(getActiveInstanceDir(cfg), targetFolder);
  safeMkdirSync(destDir);

  const filePath = path.join(destDir, primary.filename);
  await downloadFile(primary.url, filePath);
  return primary.filename;
});

ipcMain.handle('search-curseforge', async (event, { query, type = 'mod', version = '1.20.4' }) => {
  const cleanVer = extractMcVersion(version);
  let classId = 6;
  if (type === 'resourcepack') classId = 12;
  if (type === 'shader') classId = 6552;

  let url = `https://api.curseforge.com/v1/mods/search?gameId=432&classId=${classId}&searchFilter=${encodeURIComponent(query || '')}&gameVersion=${encodeURIComponent(cleanVer)}&pageSize=20&sortField=2&sortOrder=desc`;

  let results = await new Promise((resolve) => {
    https.get(url, { headers: { 'Accept': 'application/json', 'x-api-key': CF_API_KEY } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve((parsed.data || []).map(m => ({
            id: m.id,
            title: m.name,
            author: m.authors && m.authors[0] ? m.authors[0].name : 'Unknown',
            description: m.summary,
            icon_url: m.logo ? m.logo.thumbnailUrl : null,
            provider: 'curseforge'
          })));
        } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });

  if (!results.length && query) {
    const fallbackUrl = `https://api.curseforge.com/v1/mods/search?gameId=432&classId=${classId}&searchFilter=${encodeURIComponent(query)}&pageSize=20&sortField=2&sortOrder=desc`;
    results = await new Promise((resolve) => {
      https.get(fallbackUrl, { headers: { 'Accept': 'application/json', 'x-api-key': CF_API_KEY } }, (res) => {
        let data = '';
        res.on('data', c => data += c);
        res.on('end', () => {
          try {
            const parsed = JSON.parse(data);
            resolve((parsed.data || []).map(m => ({
              id: m.id,
              title: m.name,
              author: m.authors && m.authors[0] ? m.authors[0].name : 'Unknown',
              description: m.summary,
              icon_url: m.logo ? m.logo.thumbnailUrl : null,
              provider: 'curseforge'
            })));
          } catch (e) { resolve([]); }
        });
      }).on('error', () => resolve([]));
    });
  }

  return results;
});

ipcMain.handle('install-curseforge-item', async (event, { modId, type, version = '1.20.4' }) => {
  const cleanVer = extractMcVersion(version);
  const filesUrl = `https://api.curseforge.com/v1/mods/${modId}/files?gameVersion=${encodeURIComponent(cleanVer)}`;

  return new Promise((resolve, reject) => {
    https.get(filesUrl, { headers: { 'Accept': 'application/json', 'x-api-key': CF_API_KEY } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', async () => {
        try {
          const parsed = JSON.parse(data);
          let files = parsed.data || [];
          if (!files.length) {
            // Attempt query without version filter
            const allFiles = await new Promise((rRes) => {
              https.get(`https://api.curseforge.com/v1/mods/${modId}/files`, { headers: { 'Accept': 'application/json', 'x-api-key': CF_API_KEY } }, (r) => {
                let d = ''; r.on('data', chunk => d += chunk); r.on('end', () => {
                  try { rRes(JSON.parse(d).data || []); } catch (e) { rRes([]); }
                });
              }).on('error', () => rRes([]));
            });
            files = allFiles;
          }

          if (!files.length) throw new Error(`No compatible CurseForge build found.`);

          const file = files[0];
          let downloadUrl = file.downloadUrl;
          if (!downloadUrl) {
            downloadUrl = `https://edge.forgecdn.net/files/${Math.floor(file.id / 1000)}/${file.id % 1000}/${encodeURIComponent(file.fileName)}`;
          }

          let targetFolder = 'mods';
          if (type === 'resourcepack') targetFolder = 'resourcepacks';
          if (type === 'shader') targetFolder = 'shaderpacks';

          const cfg = loadConfig();
          const destDir = path.join(getActiveInstanceDir(cfg), targetFolder);
          safeMkdirSync(destDir);

          const filePath = path.join(destDir, file.fileName);
          await downloadFile(downloadUrl, filePath);
          resolve(file.fileName);
        } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
});

// -------------------------------------------------------------
// VAULT
// -------------------------------------------------------------
ipcMain.handle('get-vault-data', async () => {
  const cfg = loadConfig();
  const baseDir = getActiveInstanceDir(cfg);
  const savesDir = path.join(baseDir, 'saves');
  const screenshotsDir = path.join(baseDir, 'screenshots');
  safeMkdirSync(savesDir);
  safeMkdirSync(screenshotsDir);

  const worlds = [];
  try {
    for (const d of fs.readdirSync(savesDir, { withFileTypes: true })) {
      if (d.isDirectory()) {
        const p = path.join(savesDir, d.name);
        let bytes = 0;
        try { for (const f of fs.readdirSync(p)) bytes += fs.statSync(path.join(p, f)).size; } catch (e) {}
        worlds.push({ name: d.name, folder: d.name, sizeMb: (bytes / (1024 * 1024)).toFixed(1) });
      }
    }
  } catch (e) {}

  const screenshots = [];
  try {
    for (const f of fs.readdirSync(screenshotsDir)) {
      if (f.toLowerCase().endsWith('.png')) {
        try {
          const b64 = fs.readFileSync(path.join(screenshotsDir, f)).toString('base64');
          screenshots.push({ fileName: f, url: `data:image/png;base64,${b64}` });
        } catch (e) {}
      }
    }
  } catch (e) {}

  return { worlds, screenshots };
});

ipcMain.handle('backup-world', async (event, folderName) => {
  const cfg = loadConfig();
  const baseDir = getActiveInstanceDir(cfg);
  const targetDir = path.join(baseDir, 'saves', folderName);
  const backupsDir = path.join(baseDir, 'backups');
  safeMkdirSync(backupsDir);

  const zipTarget = path.join(backupsDir, `${folderName}-backup-${Date.now()}.zip`);
  return new Promise((resolve, reject) => {
    exec(`powershell.exe -NoProfile -Command "Compress-Archive -Path '${targetDir}' -DestinationPath '${zipTarget}' -Force"`, (err) => {
      if (err) reject(err);
      else resolve(zipTarget);
    });
  });
});

// -------------------------------------------------------------
// GAME EXECUTION ENGINE
// -------------------------------------------------------------
async function resolveVanillaDescriptor(versionId, sendStatus) {
  const versionsDir = path.join(MINECRAFT_DIR, 'versions', versionId);
  const versionJsonPath = path.join(versionsDir, `${versionId}.json`);
  safeMkdirSync(versionsDir);

  if (!fs.existsSync(versionJsonPath) || fs.statSync(versionJsonPath).size < 100) {
    sendStatus(`Fetching vanilla manifest for ${versionId}...`);
    if (!cachedManifest) {
      cachedManifest = await new Promise((res, rej) => {
        https.get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', (r) => {
          let b = ''; r.on('data', c => b += c); r.on('end', () => res(JSON.parse(b)));
        }).on('error', rej);
      });
    }

    const entry = cachedManifest.versions.find(v => v.id === versionId);
    if (!entry) throw new Error(`Version ${versionId} is not in Mojang's official manifest.`);
    await downloadFile(entry.url, versionJsonPath);
  }

  return JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));
}

ipcMain.on('launch-game', async (event, cfg) => {
  const sendStatus = (message, isError = false) => event.reply('launch-status', { message, isError });

  try {
    const versionId = cfg.selectedVersion || '1.20.4';
    const activeGameDir = getActiveInstanceDir(cfg);

    let javaBin = cfg.customJavaPath;
    if (!javaBin || !fs.existsSync(javaBin)) {
      const detected = findSystemArmJava();
      if (!detected.found) return sendStatus('Native ARM64 JDK runtime required. Install Microsoft OpenJDK 21 below.', true);
      javaBin = detected.path;
    }

    setHighPerformanceGpu(javaBin);
    sendStatus(`Preparing native ARM64 launch pipeline for ${versionId}...`);

    const versionsDir = path.join(MINECRAFT_DIR, 'versions', versionId);
    const versionJsonPath = path.join(versionsDir, `${versionId}.json`);
    safeMkdirSync(versionsDir);

    let versionData;
    let baseGameVersion = versionId;
    let isInherited = false;

    if (fs.existsSync(versionJsonPath)) {
      versionData = JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));
    } else {
      versionData = await resolveVanillaDescriptor(versionId, sendStatus);
    }

    let parentData = null;
    if (versionData.inheritsFrom) {
      isInherited = true;
      baseGameVersion = versionData.inheritsFrom;
      sendStatus(`Resolving base Minecraft client (${baseGameVersion}) for Fabric Knot engine...`);
      parentData = await resolveVanillaDescriptor(baseGameVersion, sendStatus);
    }

    const effectiveBaseDir = path.join(MINECRAFT_DIR, 'versions', baseGameVersion);
    const clientJarPath = path.join(effectiveBaseDir, `${baseGameVersion}.jar`);
    const nativesDir = path.join(effectiveBaseDir, 'natives-arm64');
    safeMkdirSync(nativesDir);

    const clientDownloadUrl = (parentData || versionData).downloads?.client?.url;
    if (!fs.existsSync(clientJarPath) || fs.statSync(clientJarPath).size < 1000) {
      if (!clientDownloadUrl) throw new Error(`Missing client binary specification for ${baseGameVersion}`);
      sendStatus(`Downloading client binary (${baseGameVersion}.jar)...`);
      await downloadFile(clientDownloadUrl, clientJarPath);
    }

    sendStatus('Verifying native ARM64 dependencies and libraries...');
    const classpath = [];

    // Fabric Libraries
    if (isInherited && Array.isArray(versionData.libraries)) {
      for (const lib of versionData.libraries) {
        if (!lib.name) continue;
        const libRelPath = mavenToPath(lib.name);
        const libDest = path.join(MINECRAFT_DIR, 'libraries', libRelPath);

        if (!fs.existsSync(libDest) || fs.statSync(libDest).size < 10) {
          const baseUrl = lib.url || 'https://maven.fabricmc.net/';
          const fullUrl = baseUrl.endsWith('/') ? `${baseUrl}${libRelPath}` : `${baseUrl}/${libRelPath}`;
          await downloadFile(fullUrl, libDest);
        }
        classpath.push(libDest);
      }
    }

    // Vanilla Libraries & Natives
    const vanillaLibs = (parentData || versionData).libraries || [];
    for (const lib of vanillaLibs) {
      let allowed = true;
      if (lib.rules) {
        allowed = false;
        for (const rule of lib.rules) {
          let matches = true;
          if (rule.os) {
            if (rule.os.name && rule.os.name !== 'windows') matches = false;
            if (rule.os.arch && rule.os.arch !== 'arm64' && rule.os.arch !== 'aarch64') matches = false;
          }
          if (matches) allowed = (rule.action === 'allow');
        }
      }
      if (!allowed) continue;

      if (lib.downloads && lib.downloads.artifact) {
        const libDest = path.join(MINECRAFT_DIR, 'libraries', lib.downloads.artifact.path);
        if (!fs.existsSync(libDest) || fs.statSync(libDest).size < 10) {
          await downloadFile(lib.downloads.artifact.url, libDest);
        }
        classpath.push(libDest);

        // Modern 1.20+ ARM64 native artifact
        if (lib.name && lib.name.includes('natives-windows-arm64')) {
          extractDllsFromZip(libDest, nativesDir);
        }
      }

      // Windows ARM64 Classified Natives
      if (lib.downloads && lib.downloads.classifiers) {
        const cls = lib.downloads.classifiers;
        let nativeKey = cls['natives-windows-arm64'] ? 'natives-windows-arm64' : null;

        // Auto-resolve ARM64 LWJGL natives from Maven Central if absent
        if (!nativeKey && lib.name && lib.name.startsWith('org.lwjgl')) {
          const parts = lib.name.split(':');
          const artifact = parts[1];
          const ver = parts[2];
          const arm64RelPath = `org/lwjgl/${artifact}/${ver}/${artifact}-${ver}-natives-windows-arm64.jar`;
          const arm64Dest = path.join(MINECRAFT_DIR, 'libraries', arm64RelPath);
          if (!fs.existsSync(arm64Dest) || fs.statSync(arm64Dest).size < 10) {
            try {
              const centralUrl = `https://repo1.maven.org/maven2/${arm64RelPath}`;
              await downloadFile(centralUrl, arm64Dest);
            } catch (e) {}
          }
          if (fs.existsSync(arm64Dest) && fs.statSync(arm64Dest).size > 100) {
            extractDllsFromZip(arm64Dest, nativesDir);
          }
        } else if (nativeKey && cls[nativeKey]) {
          const nat = cls[nativeKey];
          const natJar = path.join(MINECRAFT_DIR, 'libraries', nat.path);
          if (!fs.existsSync(natJar) || fs.statSync(natJar).size < 10) {
            await downloadFile(nat.url, natJar);
          }
          extractDllsFromZip(natJar, nativesDir);
        }
      }
    }

    classpath.push(clientJarPath);

    const assetInfo = (parentData || versionData).assetIndex;
    const assetIndexDir = path.join(MINECRAFT_DIR, 'assets', 'indexes');
    safeMkdirSync(assetIndexDir);
    const assetIndexFile = path.join(assetIndexDir, `${assetInfo.id}.json`);

    if (!fs.existsSync(assetIndexFile) || fs.statSync(assetIndexFile).size < 50) {
      sendStatus('Downloading game assets index...');
      await downloadFile(assetInfo.url, assetIndexFile);
    }

    const mainClass = versionData.mainClass || (parentData && parentData.mainClass) || 'net.minecraft.client.main.Main';
    const account = cfg.account || { name: cfg.offlineUsername || 'Player', id: '00000000000000000000000000000000', token: 'null' };
    const userJvmFlags = (cfg.jvmArgs || '').split(' ').filter(Boolean);

    sendStatus(`Booting ${isInherited ? 'Fabric' : 'Minecraft'} on Snapdragon X ARM64...`);

    const args = [
      `-Xms${cfg.minRam || 2048}M`,
      `-Xmx${cfg.maxRam || 4096}M`,
      `-Djava.library.path=${nativesDir}`,
      `-Dorg.lwjgl.librarypath=${nativesDir}`,
      `-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=false`,
      `-Dio.netty.tryReflectionSetAccessible=true`,
      ...userJvmFlags,
      '-cp',
      classpath.join(';'),
      mainClass,
      '--username', account.name,
      '--version', versionId,
      '--gameDir', activeGameDir,
      '--assetsDir', path.join(MINECRAFT_DIR, 'assets'),
      '--assetIndex', assetInfo.id,
      '--uuid', account.id.replace(/-/g, ''),
      '--accessToken', account.token,
      '--userType', account.token !== 'null' ? 'msa' : 'mojang'
    ];

    const game = spawn(javaBin, args, { cwd: activeGameDir });
    let stderrOutput = '';

    // Crucial: Stream and drain stdout to prevent pipe buffer deadlocks that freeze the title screen
    game.stdout.on('data', (d) => {
      const line = d.toString();
      if (line.includes('Setting user:') || line.includes('Backend library:')) {
        sendStatus('Minecraft graphics initialized natively.');
      }
    });

    game.stderr.on('data', (d) => {
      stderrOutput += d.toString();
    });

    game.on('spawn', () => {
      sendStatus(`Minecraft running (PID: ${game.pid})`);
      if (cfg.closeOnLaunch) setTimeout(() => mainWindow.hide(), 2500);
    });

    game.on('close', (code) => {
      if (code !== 0 && code !== null) {
        sendStatus(`Process exited (Code ${code}): ${stderrOutput.slice(-300) || 'Check JVM parameters'}`, true);
      }
    });

    game.on('error', (err) => sendStatus(`Execution failed: ${err.message}`, true));
  } catch (err) {
    sendStatus(`Launch failed: ${err.message}`, true);
  }
});

// App Lifecycle
app.whenReady().then(() => {
  initDirectories();
  createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
