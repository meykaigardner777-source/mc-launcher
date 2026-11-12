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
  if (err && (err.code === 'EEXIST' || err.message.includes('EEXIST'))) return;
  console.error('[Uncaught Exception]', err);
});

let mainWindow;
let activeGameProcess = null;

// Directory Paths
const APP_DIR = path.join(app.getPath('appData'), '.aetheria-launcher');
const CONFIG_FILE = path.join(APP_DIR, 'launcher_config.json');
const RUNTIMES_DIR = path.join(APP_DIR, 'runtimes');
const JDK_DIR = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64');
const MINECRAFT_DIR = path.join(app.getPath('appData'), '.minecraft');
const PROFILES_DIR = path.join(APP_DIR, 'profiles');

const CF_API_KEY = '$2a$10$bL4bIL5pUWqfcO7KQtnMRehkWyMmRKon46u.85/QV/MQTgC70O80C';
const DEFAULT_ORYON_JVM = '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=20 -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:G1NewSizePercent=25';
const DEFAULT_ORYON = DEFAULT_ORYON_JVM;

// CurseForge modLoaderType mappings
const CF_LOADER_MAP = {
  'any': 0,
  'forge': 1,
  'cauldron': 2,
  'liteloader': 3,
  'fabric': 4,
  'quilt': 5,
  'neoforge': 6
};

// Real-time console log ring buffer
const LOG_BUFFER_MAX = 5000;
const gameLogs = [];

function appendLog(line, stream = 'stdout') {
  const entry = {
    time: new Date().toLocaleTimeString(),
    stream,
    text: line.trimEnd()
  };
  gameLogs.push(entry);
  if (gameLogs.length > LOG_BUFFER_MAX) gameLogs.shift();
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send('game-log-stream', entry);
  }
}

// -------------------------------------------------------------
// MANAGED ARM64 RUNTIME CATALOG
// -------------------------------------------------------------
const MANAGED_JDKS = {
  '8': {
    name: 'Azul Zulu OpenJDK 8 (ARM64)',
    url: 'https://cdn.azul.com/zulu/bin/zulu8.80.0.17-ca-jdk8.0.422-win_aarch64.zip',
    dirName: 'java-8-arm64',
    version: '8',
    major: 8,
    desc: 'Legacy Minecraft (< 1.17)'
  },
  '17': {
    name: 'Microsoft OpenJDK 17 (ARM64)',
    url: 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-aarch64.zip',
    dirName: 'java-17-arm64',
    version: '17',
    major: 17,
    desc: 'Modern Minecraft (1.17 – 1.20.4)'
  },
  '21': {
    name: 'Microsoft OpenJDK 21 (ARM64)',
    url: 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-aarch64.zip',
    dirName: 'java-21-arm64',
    version: '21',
    major: 21,
    desc: 'Current Minecraft (1.20.5+)'
  },
  '25': {
    name: 'OpenJDK 25 Early Access (ARM64)',
    url: 'https://download.java.net/java/early_access/jdk25/11/GPL/openjdk-25-ea+11_windows-aarch64_bin.zip',
    dirName: 'java-25-arm64',
    version: '25',
    major: 25,
    desc: 'Experimental / Next-Gen JVM'
  }
};

// -------------------------------------------------------------
// JUNCTION-SAFE DIRECTORY RESOLVER & COPIER
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

function copyFolderRecursiveSync(src, dest) {
  try {
    let lstat = null;
    try { lstat = fs.lstatSync(src); } catch (e) { return; }
    if (!lstat) return;

    safeMkdirSync(dest);

    let entries = [];
    try { entries = fs.readdirSync(src); } catch (e) { return; }

    for (const item of entries) {
      const srcItem = path.join(src, item);
      const destItem = path.join(dest, item);

      try {
        const itemLstat = fs.lstatSync(srcItem);
        if (itemLstat.isSymbolicLink()) {
          try {
            const targetStat = fs.statSync(srcItem);
            if (targetStat.isDirectory()) {
              copyFolderRecursiveSync(srcItem, destItem);
            } else {
              fs.copyFileSync(srcItem, destItem);
            }
          } catch (symErr) {
            continue;
          }
        } else if (itemLstat.isDirectory()) {
          copyFolderRecursiveSync(srcItem, destItem);
        } else {
          try { fs.copyFileSync(srcItem, destItem); } catch (e) {}
        }
      } catch (itemErr) {}
    }
  } catch (err) {}
}

function initDirectories() {
  safeMkdirSync(APP_DIR);
  safeMkdirSync(RUNTIMES_DIR);
  safeMkdirSync(PROFILES_DIR);
}

// -------------------------------------------------------------
// PURE NODE.JS ZIP EXTRACTOR (ZERO POWERSHELL FOR DLLs)
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
// UNIFIED PROFILES & CONFIGURATION
// -------------------------------------------------------------
function loadConfig() {
  safeMkdirSync(APP_DIR);
  let cfg = null;
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      cfg = JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    } catch (e) {}
  }

  if (!cfg) {
    cfg = {
      accounts: [],
      activeAccount: null,
      offlineUsername: 'Player',
      selectedVersion: '1.20.4',
      bgPreset: 'waves',
      minRam: 2048,
      maxRam: 4096,
      customJavaPath: '',
      globalJavaRuntime: 'auto',
      envVars: '',
      closeOnLaunch: false,
      jvmArgs: DEFAULT_ORYON_JVM,
      profiles: [],
      activeProfileId: 'default'
    };
  }

  if (Array.isArray(cfg.instances) && !Array.isArray(cfg.profiles)) {
    cfg.profiles = cfg.instances.map(inst => ({
      ...inst,
      path: inst.id === 'default' ? MINECRAFT_DIR : path.join(PROFILES_DIR, inst.id)
    }));
    cfg.activeProfileId = cfg.activeInstanceId || 'default';
    delete cfg.instances;
    delete cfg.activeInstanceId;
  }

  if (!Array.isArray(cfg.profiles) || !cfg.profiles.length) {
    cfg.profiles = [{
      id: 'default',
      name: 'Default Profile (.minecraft)',
      path: MINECRAFT_DIR,
      version: '1.20.4',
      loader: 'vanilla',
      loaderVersion: '',
      javaRuntime: 'auto',
      minRam: 2048,
      maxRam: 4096,
      jvmArgs: DEFAULT_ORYON_JVM,
      customJavaPath: '',
      envVars: ''
    }];
    cfg.activeProfileId = 'default';
  }

  for (const prof of cfg.profiles) {
    if (prof.path && prof.id !== 'default') {
      safeMkdirSync(prof.path);
      ['mods', 'resourcepacks', 'shaderpacks', 'saves', 'config'].forEach(sub => safeMkdirSync(path.join(prof.path, sub)));
    }
  }

  return cfg;
}

function saveConfig(cfg) {
  safeMkdirSync(APP_DIR);
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
  } catch (e) {}
}

function getActiveProfile(cfg) {
  const id = cfg.activeProfileId || 'default';
  let prof = (cfg.profiles || []).find(p => p.id === id);
  if (!prof) {
    prof = (cfg.profiles && cfg.profiles[0]) || {
      id: 'default',
      name: 'Default Profile (.minecraft)',
      path: MINECRAFT_DIR,
      version: cfg.selectedVersion || '1.20.4',
      loader: 'vanilla',
      loaderVersion: '',
      javaRuntime: 'auto',
      minRam: 2048,
      maxRam: 4096,
      jvmArgs: DEFAULT_ORYON_JVM,
      customJavaPath: '',
      envVars: ''
    };
  }
  return prof;
}

function getActiveProfileDir(cfg) {
  const prof = getActiveProfile(cfg);
  if (prof.path && prof.id !== 'default') {
    safeMkdirSync(prof.path);
    return prof.path;
  }
  return MINECRAFT_DIR;
}

// -------------------------------------------------------------
// WINDOW SETUP
// -------------------------------------------------------------
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1260,
    height: 860,
    minWidth: 1040,
    minHeight: 700,
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
  const baseDir = getActiveProfileDir(cfg);
  const p = path.join(baseDir, target);
  safeMkdirSync(p);
  shell.openPath(p);
});

ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (e, cfg) => { saveConfig(cfg); return true; });

// -------------------------------------------------------------
// MULTI-VERSION JAVA RUNTIMES & SCANNER
// -------------------------------------------------------------
function getManagedJdkStatus() {
  return Object.keys(MANAGED_JDKS).map(key => {
    const def = MANAGED_JDKS[key];
    const binPath = path.join(RUNTIMES_DIR, def.dirName, 'bin', 'java.exe');
    let installed = fs.existsSync(binPath);
    let effectivePath = installed ? binPath : null;

    if (!installed && key === '21') {
      const legacy21 = path.join(JDK_DIR, 'bin', 'java.exe');
      if (fs.existsSync(legacy21)) {
        installed = true;
        effectivePath = legacy21;
      }
    }

    return {
      key,
      name: def.name,
      version: def.version,
      desc: def.desc,
      installed,
      path: effectivePath
    };
  });
}

function scanSystemJavaRuntimes() {
  const results = [];
  const managed = getManagedJdkStatus();
  for (const m of managed) {
    if (m.installed && m.path) {
      results.push({ name: `Managed ${m.name}`, path: m.path, arch: 'ARM64', version: m.key });
    }
  }

  const standardDirs = [
    'C:\\Program Files\\Microsoft',
    'C:\\Program Files\\Eclipse Adoptium',
    'C:\\Program Files\\Zulu',
    'C:\\Program Files\\BellSoft',
    'C:\\Program Files\\Java'
  ];

  for (const root of standardDirs) {
    if (!fs.existsSync(root)) continue;
    try {
      for (const entry of fs.readdirSync(root)) {
        const binJava = path.join(root, entry, 'bin', 'java.exe');
        if (fs.existsSync(binJava)) {
          let arch = 'Unknown';
          try {
            const out = execSync(`"${binJava}" -XshowSettings:properties -version 2>&1`, { timeout: 1500 }).toString();
            arch = out.includes('aarch64') ? 'ARM64' : (out.includes('x86_64') || out.includes('amd64') ? 'x64' : 'x86');
          } catch (e) {}
          results.push({ name: `${entry} (${arch})`, path: binJava, arch });
        }
      }
    } catch (e) {}
  }

  return results;
}

ipcMain.handle('get-managed-jdks', () => getManagedJdkStatus());
ipcMain.handle('get-system-javas', () => scanSystemJavaRuntimes());

ipcMain.handle('browse-java-path', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Select Java Executable (java.exe / javaw.exe)',
    filters: [
      { name: 'Java Executable', extensions: ['exe'] },
      { name: 'All Files', extensions: ['*'] }
    ],
    properties: ['openFile']
  });

  if (res.canceled || !res.filePaths.length) return null;
  const chosenPath = res.filePaths[0];
  setHighPerformanceGpu(chosenPath);
  return chosenPath;
});

ipcMain.handle('install-managed-jdk', async (event, { version }) => {
  const def = MANAGED_JDKS[version];
  if (!def) throw new Error(`Unknown JDK version requested: ${version}`);

  const targetDir = path.join(RUNTIMES_DIR, def.dirName);
  const zipPath = path.join(RUNTIMES_DIR, `temp-${def.dirName}.zip`);
  const tempExtract = path.join(RUNTIMES_DIR, `temp-extract-${def.dirName}`);

  const report = (step, progress) => {
    event.sender.send('jdk-install-status', { version, step, progress });
  };

  try {
    report(`Connecting to ${def.name} distribution CDN...`, 0.05);
    await downloadFile(def.url, zipPath, (pct) => {
      report(`Downloading ${def.name} (${Math.round(pct * 100)}%)...`, pct * 0.75);
    });

    report('Extracting runtime binaries...', 0.85);
    if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true });
    safeMkdirSync(tempExtract);

    execSync(`powershell.exe -NoProfile -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${tempExtract}' -Force"`);

    const entries = fs.readdirSync(tempExtract);
    const innerDir = entries.find(e => fs.statSync(path.join(tempExtract, e)).isDirectory()) || '';
    const src = innerDir ? path.join(tempExtract, innerDir) : tempExtract;

    if (fs.existsSync(targetDir)) fs.rmSync(targetDir, { recursive: true, force: true });
    safeMkdirSync(path.dirname(targetDir));
    fs.renameSync(src, targetDir);

    try { fs.rmSync(tempExtract, { recursive: true, force: true }); } catch (e) {}
    try { if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath); } catch (e) {}

    const javaBin = path.join(targetDir, 'bin', 'java.exe');
    setHighPerformanceGpu(javaBin);

    report(`${def.name} ready for Snapdragon X!`, 1.0);
    return { success: true, path: javaBin, version };
  } catch (err) {
    try { if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath); } catch (e) {}
    try { if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true }); } catch (e) {}
    throw err;
  }
});

// -------------------------------------------------------------
// SMART JAVA RUNTIME RESOLUTION PER PROFILE
// -------------------------------------------------------------
function resolveProfileJava(prof, globalCfg, targetVersionId) {
  const chosenMode = prof.javaRuntime || globalCfg.globalJavaRuntime || 'auto';

  if (['8', '17', '21', '25'].includes(chosenMode)) {
    const def = MANAGED_JDKS[chosenMode];
    const candidate = path.join(RUNTIMES_DIR, def.dirName, 'bin', 'java.exe');
    if (fs.existsSync(candidate)) return candidate;
    if (chosenMode === '21') {
      const leg21 = path.join(JDK_DIR, 'bin', 'java.exe');
      if (fs.existsSync(leg21)) return leg21;
    }
  }

  if (chosenMode === 'custom' || prof.customJavaPath) {
    if (prof.customJavaPath && fs.existsSync(prof.customJavaPath)) return prof.customJavaPath;
    if (globalCfg.customJavaPath && fs.existsSync(globalCfg.customJavaPath)) return globalCfg.customJavaPath;
  }

  const cleanVer = extractMcVersion(targetVersionId || prof.version || '1.20.4');
  const vParts = cleanVer.split('.').map(n => parseInt(n, 10) || 0);
  const minor = vParts[1] || 0;
  const patch = vParts[2] || 0;

  let idealKey = '21';
  if (minor < 17) {
    idealKey = '8';
  } else if (minor < 20 || (minor === 20 && patch <= 4)) {
    idealKey = '17';
  } else {
    idealKey = '21';
  }

  const idealPath = path.join(RUNTIMES_DIR, MANAGED_JDKS[idealKey].dirName, 'bin', 'java.exe');
  if (fs.existsSync(idealPath)) return idealPath;
  if (idealKey === '21') {
    const leg21 = path.join(JDK_DIR, 'bin', 'java.exe');
    if (fs.existsSync(leg21)) return leg21;
  }

  if (idealKey === '17') {
    const j21Path = path.join(RUNTIMES_DIR, MANAGED_JDKS['21'].dirName, 'bin', 'java.exe');
    if (fs.existsSync(j21Path)) return j21Path;
  }

  const detected = findSystemArmJava();
  if (detected.found) return detected.path;

  for (const k of ['21', '17', '8', '25']) {
    const p = path.join(RUNTIMES_DIR, MANAGED_JDKS[k].dirName, 'bin', 'java.exe');
    if (fs.existsSync(p)) return p;
  }

  return null;
}

// -------------------------------------------------------------
// PROFILE CRUD, DUPLICATION & ISOLATION
// -------------------------------------------------------------
ipcMain.handle('create-profile', async (e, { name, version, loader = 'vanilla', loaderVersion = '', javaRuntime = 'auto' }) => {
  const cfg = loadConfig();
  const id = 'prof-' + Date.now();
  const profPath = path.join(PROFILES_DIR, id);

  ['mods', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots', 'config'].forEach(sub => {
    safeMkdirSync(path.join(profPath, sub));
  });

  const newProf = {
    id,
    name: name || `Minecraft ${version}`,
    path: profPath,
    version: version || '1.20.4',
    loader: loader || 'vanilla',
    loaderVersion: loaderVersion || '',
    javaRuntime: javaRuntime || 'auto',
    minRam: cfg.minRam || 2048,
    maxRam: cfg.maxRam || 4096,
    jvmArgs: DEFAULT_ORYON_JVM,
    customJavaPath: '',
    envVars: ''
  };

  cfg.profiles.push(newProf);
  cfg.activeProfileId = id;
  cfg.selectedVersion = newProf.version;
  saveConfig(cfg);
  return newProf;
});

ipcMain.handle('update-profile', async (e, updatedData) => {
  const cfg = loadConfig();
  const idx = cfg.profiles.findIndex(p => p.id === updatedData.id);
  if (idx !== -1) {
    if (updatedData.customJavaPath) setHighPerformanceGpu(updatedData.customJavaPath);
    cfg.profiles[idx] = { ...cfg.profiles[idx], ...updatedData };
    if (cfg.activeProfileId === updatedData.id && updatedData.version) {
      cfg.selectedVersion = updatedData.version;
    }
    saveConfig(cfg);
    return true;
  }
  return false;
});

ipcMain.handle('duplicate-profile', async (e, profileId) => {
  const cfg = loadConfig();
  const srcProf = cfg.profiles.find(p => p.id === profileId);
  if (!srcProf) throw new Error('Source profile not found.');

  const newId = 'prof-' + Date.now();
  const newProfPath = path.join(PROFILES_DIR, newId);
  safeMkdirSync(newProfPath);

  if (fs.existsSync(srcProf.path)) {
    copyFolderRecursiveSync(srcProf.path, newProfPath);
  }

  const clonedProf = {
    ...srcProf,
    id: newId,
    name: `${srcProf.name} (Copy)`,
    path: newProfPath
  };

  cfg.profiles.push(clonedProf);
  cfg.activeProfileId = newId;
  saveConfig(cfg);
  return clonedProf;
});

ipcMain.handle('delete-profile', async (e, profileId) => {
  if (profileId === 'default') return false;
  const cfg = loadConfig();
  const target = cfg.profiles.find(p => p.id === profileId);
  if (target && target.path && fs.existsSync(target.path)) {
    try { fs.rmSync(target.path, { recursive: true, force: true }); } catch (e) {}
  }
  cfg.profiles = cfg.profiles.filter(p => p.id !== profileId);
  cfg.activeProfileId = 'default';
  saveConfig(cfg);
  return true;
});

// -------------------------------------------------------------
// EXTERNAL CLIENT SCANNERS & BULLETPROOF PRISM RESOLVER
// -------------------------------------------------------------
function parseIni(content) {
  const map = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx !== -1) {
      map[trimmed.slice(0, eqIdx).trim()] = trimmed.slice(eqIdx + 1).trim();
    }
  }
  return map;
}

function resolveInstanceGameDir(sourcePath) {
  const candidates = [
    path.join(sourcePath, 'minecraft'),
    path.join(sourcePath, '.minecraft'),
    sourcePath
  ];
  for (const cand of candidates) {
    if (fs.existsSync(cand)) {
      try {
        if (fs.statSync(cand).isDirectory()) {
          if (
            fs.existsSync(path.join(cand, 'mods')) ||
            fs.existsSync(path.join(cand, 'config')) ||
            fs.existsSync(path.join(cand, 'saves')) ||
            fs.existsSync(path.join(cand, 'options.txt'))
          ) {
            return cand;
          }
        }
      } catch (e) {}
    }
  }

  if (fs.existsSync(path.join(sourcePath, 'minecraft'))) return path.join(sourcePath, 'minecraft');
  if (fs.existsSync(path.join(sourcePath, '.minecraft'))) return path.join(sourcePath, '.minecraft');
  return sourcePath;
}

ipcMain.handle('scan-external-clients', async () => {
  const results = {
    prism: [],
    modrinth: [],
    lunar: []
  };

  const appData = process.env.APPDATA || '';
  const localAppData = process.env.LOCALAPPDATA || '';
  const userProfile = process.env.USERPROFILE || '';

  // 1. Scan Prism Launcher
  const prismDirs = [
    path.join(appData, 'PrismLauncher', 'instances'),
    path.join(localAppData, 'PrismLauncher', 'instances'),
    path.join(userProfile, '.local', 'share', 'PrismLauncher', 'instances')
  ];

  for (const prismDir of prismDirs) {
    if (!fs.existsSync(prismDir)) continue;
    try {
      for (const d of fs.readdirSync(prismDir, { withFileTypes: true })) {
        if (!d.isDirectory()) continue;
        const instDir = path.join(prismDir, d.name);
        const cfgFile = path.join(instDir, 'instance.cfg');
        const packFile = path.join(instDir, 'mmc-pack.json');
        let name = d.name;
        let mcVersion = '1.20.4';
        let loader = 'Vanilla';
        let minRam = 2048;
        let maxRam = 4096;

        if (fs.existsSync(cfgFile)) {
          const ini = parseIni(fs.readFileSync(cfgFile, 'utf-8'));
          if (ini.name) name = ini.name;
          if (ini.MinMemAlloc) minRam = parseInt(ini.MinMemAlloc, 10) || 2048;
          if (ini.MaxMemAlloc) maxRam = parseInt(ini.MaxMemAlloc, 10) || 4096;
        }

        if (fs.existsSync(packFile)) {
          try {
            const pack = JSON.parse(fs.readFileSync(packFile, 'utf-8'));
            const mcComp = pack.components && pack.components.find(c => c.uid === 'net.minecraft');
            if (mcComp) mcVersion = mcComp.version;
            if (pack.components?.some(c => c.uid === 'net.fabricmc.fabric-loader')) loader = 'Fabric';
            else if (pack.components?.some(c => c.uid === 'net.neoforged' || c.uid === 'net.neoforged.neoforge')) loader = 'NeoForge';
            else if (pack.components?.some(c => c.uid === 'net.minecraftforge')) loader = 'Forge';
          } catch (e) {}
        }

        if (!results.prism.some(p => p.path === instDir)) {
          results.prism.push({ name, path: instDir, mcVersion, loader, minRam, maxRam, source: 'Prism' });
        }
      }
    } catch (e) {}
  }

  // 2. Scan Modrinth App
  const modrinthCandidates = [
    path.join(appData, 'com.modrinth.theseus', 'profiles'),
    path.join(appData, 'ModrinthApp', 'profiles')
  ];
  for (const mRoot of modrinthCandidates) {
    if (!fs.existsSync(mRoot)) continue;
    try {
      for (const d of fs.readdirSync(mRoot, { withFileTypes: true })) {
        if (!d.isDirectory()) continue;
        const profDir = path.join(mRoot, d.name);
        const metaJson = path.join(profDir, 'profile.json');
        let name = d.name;
        let mcVersion = '1.20.4';
        let loader = 'Vanilla';

        if (fs.existsSync(metaJson)) {
          try {
            const meta = JSON.parse(fs.readFileSync(metaJson, 'utf-8'));
            if (meta.name) name = meta.name;
            if (meta.game_version) mcVersion = meta.game_version;
            if (meta.loader) loader = meta.loader;
          } catch (e) {}
        }
        results.modrinth.push({ name, path: profDir, mcVersion, loader, minRam: 2048, maxRam: 4096, source: 'Modrinth' });
      }
    } catch (e) {}
  }

  // 3. Scan Lunar Client
  const lunarOffline = path.join(userProfile, '.lunarclient', 'offline', 'multiver');
  if (fs.existsSync(lunarOffline)) {
    try {
      for (const d of fs.readdirSync(lunarOffline, { withFileTypes: true })) {
        if (!d.isDirectory()) continue;
        results.lunar.push({
          name: `Lunar ${d.name}`,
          path: path.join(lunarOffline, d.name),
          mcVersion: d.name,
          loader: 'Lunar Client',
          minRam: 2048,
          maxRam: 4096,
          source: 'Lunar'
        });
      }
    } catch (e) {}
  }

  return results;
});

ipcMain.handle('import-external-profile', async (event, {
  sourcePath,
  clientType,
  targetName,
  mcVersion = '1.20.4',
  loader = 'vanilla',
  minRam = 2048,
  maxRam = 4096,
  javaRuntime = 'auto',
  jvmArgs = DEFAULT_ORYON_JVM
}) => {
  if (!fs.existsSync(sourcePath)) throw new Error(`Source instance path not found: ${sourcePath}`);

  const cfg = loadConfig();
  const id = 'prof-imp-' + Date.now();
  const destDir = path.join(PROFILES_DIR, id);
  safeMkdirSync(destDir);

  let detectedVersion = mcVersion;
  let rawLoader = (loader || 'vanilla').toLowerCase();
  let normalizedLoader = rawLoader.includes('fabric') ? 'fabric' : (rawLoader.includes('neoforge') ? 'neoforge' : 'vanilla');

  if (clientType === 'Prism') {
    const packFile = path.join(sourcePath, 'mmc-pack.json');
    if (fs.existsSync(packFile)) {
      try {
        const pack = JSON.parse(fs.readFileSync(packFile, 'utf-8'));
        const mcComp = pack.components?.find(c => c.uid === 'net.minecraft');
        if (mcComp && mcComp.version) detectedVersion = mcComp.version;
        if (pack.components?.some(c => c.uid === 'net.fabricmc.fabric-loader')) normalizedLoader = 'fabric';
        else if (pack.components?.some(c => c.uid === 'net.neoforged' || c.uid === 'net.neoforged.neoforge')) normalizedLoader = 'neoforge';
      } catch (e) {}
    }
  }

  const effectiveSourceRoot = resolveInstanceGameDir(sourcePath);

  const copyPayload = ['mods', 'config', 'resourcepacks', 'shaderpacks', 'saves', 'screenshots', 'options.txt', 'servers.dat'];
  for (const item of copyPayload) {
    const src = path.join(effectiveSourceRoot, item);
    const dest = path.join(destDir, item);
    if (fs.existsSync(src)) {
      try {
        const stat = fs.statSync(src);
        if (stat.isDirectory()) {
          copyFolderRecursiveSync(src, dest);
        } else {
          fs.copyFileSync(src, dest);
        }
      } catch (e) {}
    } else {
      if (item !== 'options.txt' && item !== 'servers.dat') {
        safeMkdirSync(dest);
      }
    }
  }

  const newProfile = {
    id,
    name: targetName || path.basename(sourcePath),
    path: destDir,
    version: detectedVersion,
    loader: normalizedLoader,
    loaderVersion: '',
    javaRuntime: javaRuntime || 'auto',
    minRam: minRam || 2048,
    maxRam: maxRam || 4096,
    jvmArgs: jvmArgs || DEFAULT_ORYON_JVM,
    customJavaPath: '',
    envVars: ''
  };

  cfg.profiles.push(newProfile);
  cfg.activeProfileId = id;
  cfg.selectedVersion = detectedVersion;
  saveConfig(cfg);
  return newProfile;
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
  const match = str.match(/(?:fabric-loader-[^-]+-|neoforge-|forge-)?([0-9]+\.[0-9]+(?:\.[0-9]+)?)/);
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
            const isNeo = entry.name.toLowerCase().includes('neoforge');
            const isForge = !isNeo && entry.name.toLowerCase().includes('forge');
            const isFabric = entry.name.startsWith('fabric-loader') || !!data.inheritsFrom;
            list.push({
              id: entry.name,
              inheritsFrom: data.inheritsFrom || null,
              isFabric,
              isNeoForge: isNeo,
              isForge: isForge
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
// FABRIC & NEOFORGE SETUP
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

ipcMain.handle('get-neoforge-versions', async (event, gameVersion = '1.20.4') => {
  const cleanVer = extractMcVersion(gameVersion);
  const url = 'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml';
  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let xml = '';
      res.on('data', c => xml += c);
      res.on('end', () => {
        try {
          const versionMatches = xml.match(/<version>([^<]+)<\/version>/g) || [];
          const allVersions = versionMatches.map(v => v.replace(/<\/?version>/g, ''));
          const majorMinor = cleanVer.split('.').slice(1).join('.');
          const matched = allVersions.filter(v => v.startsWith(majorMinor) || v.startsWith(`20.${cleanVer.split('.')[2] || 0}`));
          resolve(matched.reverse());
        } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
});

ipcMain.handle('install-neoforge-profile', async (event, { neoforgeVersion }) => {
  const detected = findSystemArmJava();
  const cfg = loadConfig();
  const prof = getActiveProfile(cfg);
  const javaBin = resolveProfileJava(prof, cfg, neoforgeVersion) || detected.path;

  if (!javaBin || !fs.existsSync(javaBin)) {
    throw new Error('ARM64 JDK required for NeoForge installer. Please install Microsoft OpenJDK 21.');
  }

  const installerUrl = `https://maven.neoforged.net/releases/net/neoforged/neoforge/${neoforgeVersion}/neoforge-${neoforgeVersion}-installer.jar`;
  const tempInstaller = path.join(RUNTIMES_DIR, `neoforge-installer-${neoforgeVersion}.jar`);

  appendLog(`Downloading NeoForge installer (${neoforgeVersion})...`);
  await downloadFile(installerUrl, tempInstaller);

  appendLog(`Executing headless NeoForge client installation...`);
  return new Promise((resolve, reject) => {
    const proc = spawn(javaBin, ['-jar', tempInstaller, '--installClient', MINECRAFT_DIR], {
      cwd: MINECRAFT_DIR
    });

    proc.stdout.on('data', d => appendLog(d.toString(), 'stdout'));
    proc.stderr.on('data', d => appendLog(d.toString(), 'stderr'));

    proc.on('close', (code) => {
      try { fs.unlinkSync(tempInstaller); } catch (e) {}
      if (code === 0) {
        appendLog(`NeoForge installed successfully.`);
        resolve({ success: true, profileId: `neoforge-${neoforgeVersion}` });
      } else {
        reject(new Error(`NeoForge installer exited with code ${code}`));
      }
    });
  });
});

ipcMain.handle('install-shader-engine', async (event, gameVersion) => {
  const cleanVer = extractMcVersion(gameVersion);
  const cfg = loadConfig();
  const modsFolder = path.join(getActiveProfileDir(cfg), 'mods');
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
// AUTOMATIC MOD UPDATE CHECKER (MODRINH SHA1 BATCH API)
// -------------------------------------------------------------
function hashFileSha1(filePath) {
  return new Promise((resolve) => {
    try {
      const hash = crypto.createHash('sha1');
      const stream = fs.createReadStream(filePath);
      stream.on('data', data => hash.update(data));
      stream.on('end', () => resolve(hash.digest('hex')));
      stream.on('error', () => resolve(null));
    } catch (e) { resolve(null); }
  });
}

ipcMain.handle('check-mod-updates', async () => {
  const cfg = loadConfig();
  const modsFolder = path.join(getActiveProfileDir(cfg), 'mods');
  safeMkdirSync(modsFolder);

  const jarFiles = fs.readdirSync(modsFolder).filter(f => f.endsWith('.jar') && !f.endsWith('.disabled'));
  if (!jarFiles.length) return [];

  const hashesMap = {};
  for (const file of jarFiles) {
    const full = path.join(modsFolder, file);
    const sha = await hashFileSha1(full);
    if (sha) hashesMap[sha] = file;
  }

  const hashes = Object.keys(hashesMap);
  if (!hashes.length) return [];

  const updateHits = [];
  try {
    const bodyStr = JSON.stringify({ hashes, algorithm: 'sha1' });
    const versionFilesRes = await new Promise((resolve, reject) => {
      const req = https.request('https://api.modrinth.com/v2/version_files', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'User-Agent': 'Aetheria-ARM64-Launcher'
        }
      }, (res) => {
        let b = '';
        res.on('data', c => b += c);
        res.on('end', () => {
          try { resolve(JSON.parse(b)); } catch (e) { resolve({}); }
        });
      });
      req.on('error', reject);
      req.write(bodyStr);
      req.end();
    });

    const activeMcVer = extractMcVersion(cfg.selectedVersion || '1.20.4');

    for (const [currHash, verInfo] of Object.entries(versionFilesRes)) {
      const projectId = verInfo.project_id;
      const fileName = hashesMap[currHash];

      const projVersions = await new Promise((resolve) => {
        https.get(`https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${encodeURIComponent(activeMcVer)}"]`, {
          headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' }
        }, (res) => {
          let b = '';
          res.on('data', c => b += c);
          res.on('end', () => {
            try { resolve(JSON.parse(b)); } catch (e) { resolve([]); }
          });
        }).on('error', () => resolve([]));
      });

      if (projVersions && projVersions.length) {
        const latestVersion = projVersions[0];
        if (latestVersion.id !== verInfo.id) {
          const primaryFile = latestVersion.files.find(f => f.primary) || latestVersion.files[0];
          updateHits.push({
            currentFileName: fileName,
            currentVersionNumber: verInfo.version_number,
            newVersionNumber: latestVersion.version_number,
            newFileName: primaryFile.filename,
            downloadUrl: primaryFile.url,
            projectId: projectId
          });
        }
      }
    }
  } catch (err) {
    appendLog(`Update check fault: ${err.message}`, 'stderr');
  }

  return updateHits;
});

ipcMain.handle('apply-mod-update', async (event, updateItem) => {
  const cfg = loadConfig();
  const modsFolder = path.join(getActiveProfileDir(cfg), 'mods');
  const oldPath = path.join(modsFolder, updateItem.currentFileName);
  const bakPath = path.join(modsFolder, `${updateItem.currentFileName}.bak`);
  const newPath = path.join(modsFolder, updateItem.newFileName);

  if (fs.existsSync(oldPath)) {
    try { fs.renameSync(oldPath, bakPath); } catch (e) {}
  }

  await downloadFile(updateItem.downloadUrl, newPath);
  if (fs.existsSync(bakPath)) {
    try { fs.unlinkSync(bakPath); } catch (e) {}
  }

  return updateItem.newFileName;
});

// -------------------------------------------------------------
// LIVE CONSOLE & ARM64 CRASH HEURISTICS
// -------------------------------------------------------------
ipcMain.handle('get-console-logs', () => gameLogs);
ipcMain.handle('clear-console-logs', () => { gameLogs.length = 0; return true; });

function diagnoseCrash(fullLogs) {
  const summary = {
    faultIdentified: false,
    title: 'Unknown JVM Termination',
    recommendation: 'Verify that allocated memory is sufficient and incompatible mod combinations are disabled.'
  };

  if (fullLogs.includes('EXCEPTION_ACCESS_VIOLATION') || fullLogs.includes('0xc0000005')) {
    summary.faultIdentified = true;
    summary.title = 'ARM64 Native Execution Trap (Access Violation)';
    summary.recommendation = 'An unmapped instruction faulted inside Windows ARM64 translation layers. Ensure native ARM64 LWJGL binaries are unpacked and Qualcomm Adreno drivers are up to date.';
  } else if (fullLogs.includes('java.lang.UnsatisfiedLinkError')) {
    summary.faultIdentified = true;
    summary.title = 'Missing Native ARM64 DLL / Architecture Mismatch';
    summary.recommendation = 'Minecraft attempted to load an x86/x64 native binary. Verify ARM64 LWJGL extraction completed without disk permission errors.';
  } else if (fullLogs.includes('java.lang.OutOfMemoryError') || fullLogs.includes('Metaspace')) {
    summary.faultIdentified = true;
    summary.title = 'JVM Heap / Metaspace Exhaustion';
    summary.recommendation = 'The game exceeded allocated RAM. Increase Max RAM in Profile Settings to 4096MB or 6144MB.';
  } else if (fullLogs.includes('UnsupportedClassVersionError')) {
    summary.faultIdentified = true;
    summary.title = 'Java Runtime Version Mismatch';
    summary.recommendation = 'Minecraft version and Java runtime mismatch (< 1.17 requires Java 8; 1.17 – 1.20.4 requires Java 17; 1.20.5+ requires Java 21). Select the appropriate runtime in Profile Settings.';
  }

  return summary;
}

// -------------------------------------------------------------
// NATIVE ARM64 JDK RUNTIME DETECTOR
// -------------------------------------------------------------
function findSystemArmJava() {
  for (const k of ['21', '17', '8', '25']) {
    const local = path.join(RUNTIMES_DIR, MANAGED_JDKS[k].dirName, 'bin', 'java.exe');
    if (fs.existsSync(local)) return { found: true, path: local, source: `Managed ${MANAGED_JDKS[k].name}` };
  }

  const legacyJava = path.join(JDK_DIR, 'bin', 'java.exe');
  if (fs.existsSync(legacyJava)) return { found: true, path: legacyJava, source: 'Managed ARM64 JDK (21)' };

  const candidates = [
    'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
    'C:\\Program Files\\Microsoft\\jdk-17\\bin\\java.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21-arm64\\bin\\java.exe',
    'C:\\Program Files\\Zulu\\zulu-21-arm64\\bin\\java.exe',
    'C:\\Program Files\\Zulu\\zulu-17-arm64\\bin\\java.exe',
    'C:\\Program Files\\Zulu\\zulu-8-arm64\\bin\\java.exe'
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
// SKIN STUDIO
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
    throw new Error(`Unable to fetch skin for ${cleanUser}.`);
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
  const baseDir = getActiveProfileDir(cfg);
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
  const dir = path.join(getActiveProfileDir(cfg), folder);
  const src = path.join(dir, fileName);
  const dest = fileName.endsWith('.disabled')
    ? path.join(dir, fileName.replace(/\.disabled$/, ''))
    : path.join(dir, `${fileName}.disabled`);
  if (fs.existsSync(src)) fs.renameSync(src, dest);
  return true;
});

ipcMain.handle('toggle-all-content', async (event, { folder, enable }) => {
  const cfg = loadConfig();
  const dir = path.join(getActiveProfileDir(cfg), folder);
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
  const target = path.join(getActiveProfileDir(cfg), folder, fileName);
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
  const destFolder = path.join(getActiveProfileDir(cfg), target);
  safeMkdirSync(destFolder);

  for (const src of res.filePaths) {
    fs.copyFileSync(src, path.join(destFolder, path.basename(src)));
  }
  return true;
});

ipcMain.handle('import-dropped-files', async (event, { folderType, filePaths }) => {
  const cfg = loadConfig();
  const target = resolveFolderTarget(folderType);
  const destFolder = path.join(getActiveProfileDir(cfg), target);
  safeMkdirSync(destFolder);

  for (const src of filePaths) {
    if (fs.existsSync(src)) {
      fs.copyFileSync(src, path.join(destFolder, path.basename(src)));
    }
  }
  return true;
});

// -------------------------------------------------------------
// DUAL PROVIDER (MODRINH + CURSEFORGE) WITH LOADER FILTERING
// -------------------------------------------------------------
ipcMain.handle('search-modrinth', async (event, { query, type = 'mod', version = '1.20.4', loader = 'all' }) => {
  const cleanVer = extractMcVersion(version);
  const facets = [
    [`project_type:${type}`],
    [`versions:${cleanVer}`]
  ];

  if (type === 'mod' && loader && loader !== 'all' && loader !== 'vanilla') {
    facets.push([`categories:${loader.toLowerCase()}`]);
  }

  const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20`;

  let hits = await new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data).hits || []); } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });

  return hits;
});

ipcMain.handle('install-modrinth-item', async (event, { projectId, type, version = '1.20.4', loader = 'all' }) => {
  const cleanVer = extractMcVersion(version);
  let loaderQuery = '';
  if (type === 'mod' && loader && loader !== 'all' && loader !== 'vanilla') {
    loaderQuery = `&loaders=["${encodeURIComponent(loader.toLowerCase())}"]`;
  }

  let versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${encodeURIComponent(cleanVer)}"]${loaderQuery}`;

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
  const destDir = path.join(getActiveProfileDir(cfg), targetFolder);
  safeMkdirSync(destDir);

  const filePath = path.join(destDir, primary.filename);
  await downloadFile(primary.url, filePath);
  return primary.filename;
});

ipcMain.handle('search-curseforge', async (event, { query, type = 'mod', version = '1.20.4', loader = 'all' }) => {
  const cleanVer = extractMcVersion(version);
  let classId = 6;
  if (type === 'resourcepack') classId = 12;
  if (type === 'shader') classId = 6552;

  let loaderParam = '';
  if (type === 'mod' && loader && loader !== 'all' && CF_LOADER_MAP[loader.toLowerCase()]) {
    loaderParam = `&modLoaderType=${CF_LOADER_MAP[loader.toLowerCase()]}`;
  }

  const url = `https://api.curseforge.com/v1/mods/search?gameId=432&classId=${classId}&searchFilter=${encodeURIComponent(query || '')}&gameVersion=${encodeURIComponent(cleanVer)}${loaderParam}&pageSize=20&sortField=2&sortOrder=desc`;

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

  return results;
});

ipcMain.handle('install-curseforge-item', async (event, { modId, type, version = '1.20.4', loader = 'all' }) => {
  const cleanVer = extractMcVersion(version);
  let loaderParam = '';
  if (type === 'mod' && loader && loader !== 'all' && CF_LOADER_MAP[loader.toLowerCase()]) {
    loaderParam = `&modLoaderType=${CF_LOADER_MAP[loader.toLowerCase()]}`;
  }

  const filesUrl = `https://api.curseforge.com/v1/mods/${modId}/files?gameVersion=${encodeURIComponent(cleanVer)}${loaderParam}`;

  return new Promise((resolve, reject) => {
    https.get(filesUrl, { headers: { 'Accept': 'application/json', 'x-api-key': CF_API_KEY } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', async () => {
        try {
          const parsed = JSON.parse(data);
          let files = parsed.data || [];
          if (!files.length) {
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
          const destDir = path.join(getActiveProfileDir(cfg), targetFolder);
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
  const baseDir = getActiveProfileDir(cfg);
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
  const baseDir = getActiveProfileDir(cfg);
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
// GAME LAUNCH PIPELINE WITH MULTI-JAVA & ENV INJECTION
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
    if (!entry) throw new Error(`Version ${versionId} is not in Mojang's manifest.`);
    await downloadFile(entry.url, versionJsonPath);
  }

  return JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));
}

function parseEnvString(str = '') {
  const envObj = {};
  for (const line of str.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx !== -1) {
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      if (key) envObj[key] = val;
    }
  }
  return envObj;
}

ipcMain.on('launch-game', async (event, reqCfg) => {
  const sendStatus = (message, isError = false) => {
    appendLog(message, isError ? 'stderr' : 'stdout');
    event.reply('launch-status', { message, isError });
  };

  try {
    const globalCfg = loadConfig();
    const prof = getActiveProfile(globalCfg);
    const versionId = prof.version || reqCfg.selectedVersion || '1.20.4';
    const activeGameDir = getActiveProfileDir(globalCfg);

    let javaBin = resolveProfileJava(prof, globalCfg, versionId);
    if (!javaBin || !fs.existsSync(javaBin)) {
      return sendStatus('Compatible ARM64 Java runtime not found. Please install Java 8, 17, or 21 in Settings.', true);
    }

    setHighPerformanceGpu(javaBin);
    sendStatus(`Preparing Snapdragon X Oryon launch pipeline using ${path.basename(path.dirname(path.dirname(javaBin)))}...`);

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
      sendStatus(`Resolving base client (${baseGameVersion})...`);
      parentData = await resolveVanillaDescriptor(baseGameVersion, sendStatus);
    }

    const effectiveBaseDir = path.join(MINECRAFT_DIR, 'versions', baseGameVersion);
    const clientJarPath = path.join(effectiveBaseDir, `${baseGameVersion}.jar`);
    const nativesDir = path.join(effectiveBaseDir, 'natives-arm64');
    safeMkdirSync(nativesDir);

    const clientDownloadUrl = (parentData || versionData).downloads?.client?.url;
    if (!fs.existsSync(clientJarPath) || fs.statSync(clientJarPath).size < 1000) {
      if (!clientDownloadUrl) throw new Error(`Missing client binary for ${baseGameVersion}`);
      sendStatus(`Downloading client binary (${baseGameVersion}.jar)...`);
      await downloadFile(clientDownloadUrl, clientJarPath);
    }

    sendStatus('Verifying native ARM64 LWJGL libraries...');
    const classpath = [];

    if (isInherited && Array.isArray(versionData.libraries)) {
      for (const lib of versionData.libraries) {
        if (!lib.name) continue;
        const libRelPath = mavenToPath(lib.name);
        const libDest = path.join(MINECRAFT_DIR, 'libraries', libRelPath);

        if (!fs.existsSync(libDest) || fs.statSync(libDest).size < 10) {
          const baseUrl = lib.url || 'https://maven.fabricmc.net/';
          const fullUrl = baseUrl.endsWith('/') ? `${baseUrl}${libRelPath}` : `${baseUrl}/${libRelPath}`;
          try { await downloadFile(fullUrl, libDest); } catch (e) {}
        }
        if (fs.existsSync(libDest)) classpath.push(libDest);
      }
    }

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

        if (lib.name && lib.name.includes('natives-windows-arm64')) {
          extractDllsFromZip(libDest, nativesDir);
        }
      }

      if (lib.downloads && lib.downloads.classifiers) {
        const cls = lib.downloads.classifiers;
        let nativeKey = cls['natives-windows-arm64'] ? 'natives-windows-arm64' : null;

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
    const account = globalCfg.activeAccount || { name: globalCfg.offlineUsername || 'Player', id: '00000000000000000000000000000000', token: 'null' };
    const userJvmFlags = (prof.jvmArgs || globalCfg.jvmArgs || DEFAULT_ORYON_JVM).split(' ').filter(Boolean);

    sendStatus(`Booting instance on Snapdragon X Oryon ARM64...`);

    const minRam = prof.minRam || globalCfg.minRam || 2048;
    const maxRam = prof.maxRam || globalCfg.maxRam || 4096;

    const args = [
      `-Xms${minRam}M`,
      `-Xmx${maxRam}M`,
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

    const customEnv = {
      ...parseEnvString(globalCfg.envVars || ''),
      ...parseEnvString(prof.envVars || '')
    };

    const launchEnv = {
      ...process.env,
      ...customEnv
    };

    activeGameProcess = spawn(javaBin, args, { cwd: activeGameDir, env: launchEnv });
    let totalOutput = '';

    activeGameProcess.stdout.on('data', (d) => {
      const line = d.toString();
      totalOutput += line;
      appendLog(line, 'stdout');
      if (line.includes('Setting user:') || line.includes('Backend library:')) {
        sendStatus('Minecraft window active.');
      }
    });

    activeGameProcess.stderr.on('data', (d) => {
      const line = d.toString();
      totalOutput += line;
      appendLog(line, 'stderr');
    });

    activeGameProcess.on('spawn', () => {
      event.reply('game-started', { pid: activeGameProcess.pid });
      sendStatus(`Minecraft running (PID: ${activeGameProcess.pid})`);
      if (globalCfg.closeOnLaunch) setTimeout(() => mainWindow.hide(), 2500);
    });

    activeGameProcess.on('close', (code) => {
      activeGameProcess = null;
      if (code !== 0 && code !== null) {
        const diag = diagnoseCrash(totalOutput);
        event.reply('game-crashed', { code, diag });
        sendStatus(`Game crashed (Exit Code ${code}): ${diag.title}`, true);
      } else {
        event.reply('game-exited', { code: 0 });
        sendStatus('Minecraft closed normally.');
      }
    });

    activeGameProcess.on('error', (err) => {
      activeGameProcess = null;
      event.reply('game-exited', { code: -1 });
      sendStatus(`Execution error: ${err.message}`, true);
    });
  } catch (err) {
    activeGameProcess = null;
    event.reply('game-exited', { code: -1 });
    sendStatus(`Launch failed: ${err.message}`, true);
  }
});

// -------------------------------------------------------------
// APP LIFECYCLE
// -------------------------------------------------------------
app.whenReady().then(() => {
  initDirectories();
  createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
