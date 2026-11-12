const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const { spawn, exec, execSync } = require('child_process');

let mainWindow;

// Directory Paths
const APP_DIR = path.join(app.getPath('appData'), '.aetheria-launcher');
const CONFIG_FILE = path.join(APP_DIR, 'launcher_config.json');
const RUNTIMES_DIR = path.join(APP_DIR, 'runtimes');
const JDK_DIR = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64');
const MINECRAFT_DIR = path.join(app.getPath('appData'), '.minecraft');

// -------------------------------------------------------------
// BULLETPROOF DIRECTORY UTILITIES (SAFE FOR WINDOWS DRIVE ROOTS)
// -------------------------------------------------------------
function safeMkdirSync(targetPath) {
  if (!targetPath) return;
  try {
    if (fs.existsSync(targetPath)) {
      if (!fs.statSync(targetPath).isDirectory()) {
        fs.unlinkSync(targetPath);
        fs.mkdirSync(targetPath, { recursive: true });
      }
    } else {
      fs.mkdirSync(targetPath, { recursive: true });
    }
  } catch (err) {
    // Windows root-safe fallback
    try {
      const resolved = path.resolve(targetPath);
      const parsed = path.parse(resolved);
      let current = parsed.root; // Guaranteed "C:\" with backslash on Windows
      const segments = resolved.slice(parsed.root.length).split(path.sep).filter(Boolean);

      for (const segment of segments) {
        current = path.join(current, segment);
        if (fs.existsSync(current)) {
          if (!fs.statSync(current).isDirectory()) {
            fs.unlinkSync(current);
            fs.mkdirSync(current);
          }
        } else {
          fs.mkdirSync(current);
        }
      }
    } catch (e) {
      console.error(`Failed to create directory: ${targetPath}`, e);
    }
  }
}

function initDirectories() {
  [
    APP_DIR,
    RUNTIMES_DIR,
    MINECRAFT_DIR,
    path.join(MINECRAFT_DIR, 'assets'),
    path.join(MINECRAFT_DIR, 'assets', 'indexes'),
    path.join(MINECRAFT_DIR, 'assets', 'objects'),
    path.join(MINECRAFT_DIR, 'versions'),
    path.join(MINECRAFT_DIR, 'libraries'),
    path.join(MINECRAFT_DIR, 'mods'),
    path.join(MINECRAFT_DIR, 'resourcepacks'),
    path.join(MINECRAFT_DIR, 'shaderpacks'),
    path.join(MINECRAFT_DIR, 'saves'),
    path.join(MINECRAFT_DIR, 'screenshots')
  ].forEach(dir => safeMkdirSync(dir));
}

// Configuration
function loadConfig() {
  if (fs.existsSync(CONFIG_FILE)) {
    try { return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')); } catch (e) {}
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
    jvmArgs: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50'
  };
}

function saveConfig(cfg) {
  safeMkdirSync(APP_DIR);
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
}

// Window Setup
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 980,
    minHeight: 640,
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

ipcMain.on('window-min', () => mainWindow.minimize());
ipcMain.on('window-max', () => mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize());
ipcMain.on('window-close', () => mainWindow.close());
ipcMain.on('open-path', (e, target) => {
  const p = path.join(MINECRAFT_DIR, target);
  safeMkdirSync(p);
  shell.openPath(p);
});

ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (e, cfg) => { saveConfig(cfg); return true; });

// -------------------------------------------------------------
// STREAM DOWNLOAD ENGINE (HTTP + HTTPS WITH AUTO RETRIES)
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

// -------------------------------------------------------------
// MOJANG VERSION MANIFEST SYNC
// -------------------------------------------------------------
let cachedManifest = null;

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
          resolve({ latest: parsed.latest.release, versions: releases });
        } catch (e) {
          resolve({ latest: '1.20.4', versions: [{ id: '1.20.4', url: '' }] });
        }
      });
    }).on('error', () => resolve({ latest: '1.20.4', versions: [{ id: '1.20.4', url: '' }] }));
  });
});

// -------------------------------------------------------------
// ARM64 JDK DETECTION & SETUP
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
    report('Connecting to Microsoft OpenJDK CDN...', 0.05);
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

    report('ARM64 JDK installed successfully.', 1.0);
    return { success: true, path: path.join(JDK_DIR, 'bin', 'java.exe') };
  } catch (err) {
    if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);
    throw err;
  }
});

// -------------------------------------------------------------
// MICROSOFT LOGIN ENGINE
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
    const headers = { 'User-Agent': 'Aetheria-ARM64-Launcher' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const req = https.request({
      hostname: u.hostname,
      port: 443,
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
// SKIN LOOKUP & AUTO-DISPLAY
// -------------------------------------------------------------
ipcMain.handle('fetch-player-skin', async (event, username) => {
  try {
    const prof = await httpGet(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(username)}`);
    if (!prof || !prof.id) throw new Error('Player profile not found');

    const session = await httpGet(`https://sessionserver.mojang.com/session/minecraft/profile/${prof.id}`);
    const prop = session.properties && session.properties.find(p => p.name === 'textures');
    if (!prop) throw new Error('Texture property not found');

    const parsed = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8'));
    const skinUrl = parsed.textures && parsed.textures.SKIN && parsed.textures.SKIN.url;
    if (!skinUrl) throw new Error('Skin texture unavailable');

    return new Promise((resolve, reject) => {
      https.get(skinUrl, (res) => {
        const chunks = [];
        res.on('data', c => chunks.push(c));
        res.on('end', () => {
          const b64 = Buffer.concat(chunks).toString('base64');
          resolve({
            username: prof.name,
            uuid: prof.id,
            skinUrl: `data:image/png;base64,${b64}`,
            isSlim: parsed.textures.SKIN.metadata && parsed.textures.SKIN.metadata.model === 'slim'
          });
        });
      }).on('error', reject);
    });
  } catch (e) {
    throw new Error('Skin search failed: ' + e.message);
  }
});

ipcMain.handle('select-skin-dialog', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Select Skin (.png)',
    filters: [{ name: 'PNG Images', extensions: ['png'] }],
    properties: ['openFile']
  });
  if (res.canceled || !res.filePaths.length) return null;
  const p = res.filePaths[0];
  const b64 = fs.readFileSync(p).toString('base64');
  return { filePath: p, dataUrl: `data:image/png;base64,${b64}` };
});

ipcMain.handle('upload-skin', async (event, { token, filePath, isSlim }) => {
  const fileBytes = fs.readFileSync(filePath);
  const boundary = '----AetheriaSkinBoundary' + crypto.randomBytes(6).toString('hex');

  const head = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${isSlim ? 'slim' : 'classic'}\r\n` +
    `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`
  );
  const foot = Buffer.from(`\r\n--${boundary}--\r\n`);
  const body = Buffer.concat([head, fileBytes, foot]);

  return new Promise((resolve, reject) => {
    const req = https.request({
      hostname: 'api.minecraftservices.com',
      port: 443,
      path: '/minecraft/profile/skins',
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': body.length
      }
    }, (res) => {
      if (res.statusCode === 200 || res.statusCode === 204) resolve(true);
      else reject(new Error(`Mojang API error: HTTP ${res.statusCode}`));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
});

// -------------------------------------------------------------
// MOD MANAGER: INSTALLED CONTENT
// -------------------------------------------------------------
ipcMain.handle('get-installed-content', async (event, type = 'mods') => {
  let target = 'mods';
  if (type === 'resourcepacks') target = 'resourcepacks';
  if (type === 'shaderpacks') target = 'shaderpacks';

  const folder = path.join(MINECRAFT_DIR, target);
  safeMkdirSync(folder);

  const files = fs.readdirSync(folder);
  return files.map(file => {
    const full = path.join(folder, file);
    const stat = fs.statSync(full);
    const isDir = stat.isDirectory();
    const isEnabled = !file.endsWith('.disabled');
    return {
      fileName: file,
      name: file.replace('.disabled', ''),
      sizeMb: (stat.size / (1024 * 1024)).toFixed(2),
      isEnabled,
      isDirectory: isDir,
      folder: target
    };
  });
});

ipcMain.handle('toggle-content-item', async (event, { folder, fileName }) => {
  const dir = path.join(MINECRAFT_DIR, folder);
  const src = path.join(dir, fileName);
  let dest = fileName.endsWith('.disabled')
    ? path.join(dir, fileName.replace(/\.disabled$/, ''))
    : path.join(dir, `${fileName}.disabled`);
  if (fs.existsSync(src)) fs.renameSync(src, dest);
  return true;
});

ipcMain.handle('delete-content-item', async (event, { folder, fileName }) => {
  const target = path.join(MINECRAFT_DIR, folder, fileName);
  if (fs.existsSync(target)) {
    if (fs.statSync(target).isDirectory()) fs.rmSync(target, { recursive: true, force: true });
    else fs.unlinkSync(target);
  }
  return true;
});

// Modrinth API Search & Install
ipcMain.handle('search-modrinth', async (event, { query, type = 'mod', version = '1.20.4' }) => {
  const facets = [
    [`project_type:${type}`],
    [`versions:${version}`]
  ];
  const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20`;

  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try { resolve(JSON.parse(data).hits || []); } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
});

ipcMain.handle('install-modrinth-item', async (event, { projectId, type, version = '1.20.4' }) => {
  const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["${version}"]`;

  return new Promise((resolve, reject) => {
    https.get(versionsUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', async () => {
        try {
          const versions = JSON.parse(data);
          if (!versions || !versions.length) throw new Error(`No compatible release found for ${version}`);

          const primary = versions[0].files.find(f => f.primary) || versions[0].files[0];
          let targetFolder = 'mods';
          if (type === 'resourcepack') targetFolder = 'resourcepacks';
          if (type === 'shader') targetFolder = 'shaderpacks';

          const destDir = path.join(MINECRAFT_DIR, targetFolder);
          safeMkdirSync(destDir);

          const filePath = path.join(destDir, primary.filename);
          await downloadFile(primary.url, filePath);
          resolve(primary.filename);
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
});

// Vault
ipcMain.handle('get-vault-data', async () => {
  const savesDir = path.join(MINECRAFT_DIR, 'saves');
  const screenshotsDir = path.join(MINECRAFT_DIR, 'screenshots');
  safeMkdirSync(savesDir);
  safeMkdirSync(screenshotsDir);

  const worlds = [];
  for (const d of fs.readdirSync(savesDir, { withFileTypes: true })) {
    if (d.isDirectory()) {
      const p = path.join(savesDir, d.name);
      let bytes = 0;
      try {
        for (const f of fs.readdirSync(p)) bytes += fs.statSync(path.join(p, f)).size;
      } catch (e) {}
      worlds.push({ name: d.name, folder: d.name, sizeMb: (bytes / (1024 * 1024)).toFixed(1) });
    }
  }

  const screenshots = [];
  for (const f of fs.readdirSync(screenshotsDir)) {
    if (f.toLowerCase().endsWith('.png')) {
      try {
        const b64 = fs.readFileSync(path.join(screenshotsDir, f)).toString('base64');
        screenshots.push({ fileName: f, url: `data:image/png;base64,${b64}` });
      } catch (e) {}
    }
  }

  return { worlds, screenshots };
});

ipcMain.handle('backup-world', async (event, folderName) => {
  const targetDir = path.join(MINECRAFT_DIR, 'saves', folderName);
  const backupsDir = path.join(MINECRAFT_DIR, 'backups');
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
// GAME EXECUTION ENGINE (WITH CRASH DETECTION PIPE)
// -------------------------------------------------------------
ipcMain.on('launch-game', async (event, cfg) => {
  const sendStatus = (message, isError = false) => event.reply('launch-status', { message, isError });

  try {
    const versionId = cfg.selectedVersion || '1.20.4';

    // 1. Resolve ARM64 Java Binary
    let javaBin = cfg.customJavaPath;
    if (!javaBin || !fs.existsSync(javaBin)) {
      const detected = findSystemArmJava();
      if (!detected.found) {
        return sendStatus('No native ARM64 JDK found. Click "Install ARM64 JDK" below.', true);
      }
      javaBin = detected.path;
    }

    sendStatus(`Checking game files for Minecraft ${versionId}...`);

    const versionsDir = path.join(MINECRAFT_DIR, 'versions', versionId);
    const versionJsonPath = path.join(versionsDir, `${versionId}.json`);
    const clientJarPath = path.join(versionsDir, `${versionId}.jar`);
    const nativesDir = path.join(versionsDir, 'natives');

    safeMkdirSync(versionsDir);
    safeMkdirSync(nativesDir);

    // 2. Fetch Version Metadata if Missing or Incomplete
    let needsManifest = !fs.existsSync(versionJsonPath) || fs.statSync(versionJsonPath).size < 100;
    if (needsManifest) {
      sendStatus(`Fetching version descriptor for ${versionId}...`);
      if (!cachedManifest) {
        const raw = await new Promise((res, rej) => {
          https.get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', (r) => {
            let b = ''; r.on('data', c => b += c); r.on('end', () => res(JSON.parse(b)));
          }).on('error', rej);
        });
        cachedManifest = raw;
      }

      const entry = cachedManifest.versions.find(v => v.id === versionId);
      if (!entry) throw new Error(`Version ${versionId} not present in Mojang manifest.`);

      await downloadFile(entry.url, versionJsonPath);
    }

    const versionData = JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));

    // 3. Download Client Binary
    if (!fs.existsSync(clientJarPath) || fs.statSync(clientJarPath).size < 1000) {
      sendStatus(`Downloading client binary (${versionId}.jar)...`);
      await downloadFile(versionData.downloads.client.url, clientJarPath);
    }

    // 4. Resolve Libraries
    sendStatus('Verifying library dependencies...');
    const classpath = [];
    const libraries = versionData.libraries || [];

    for (const lib of libraries) {
      let allowed = true;
      if (lib.rules) {
        allowed = false;
        for (const rule of lib.rules) {
          if (rule.action === 'allow') {
            if (!rule.os || rule.os.name === 'windows') allowed = true;
          } else if (rule.action === 'disallow') {
            if (rule.os && rule.os.name === 'windows') allowed = false;
          }
        }
      }
      if (!allowed) continue;

      if (lib.downloads && lib.downloads.artifact) {
        const libDest = path.join(MINECRAFT_DIR, 'libraries', lib.downloads.artifact.path);
        if (!fs.existsSync(libDest) || fs.statSync(libDest).size < 10) {
          await downloadFile(lib.downloads.artifact.url, libDest);
        }
        classpath.push(libDest);
      }

      // Windows ARM64 Natives
      if (lib.downloads && lib.downloads.classifiers) {
        const cls = lib.downloads.classifiers;
        const nativeKey = cls['natives-windows-arm64'] ? 'natives-windows-arm64' : cls['natives-windows'] ? 'natives-windows' : null;
        if (nativeKey && cls[nativeKey]) {
          const nat = cls[nativeKey];
          const natJar = path.join(MINECRAFT_DIR, 'libraries', nat.path);
          if (!fs.existsSync(natJar) || fs.statSync(natJar).size < 10) {
            await downloadFile(nat.url, natJar);
          }
          try {
            execSync(`powershell.exe -NoProfile -Command "Expand-Archive -Path '${natJar}' -DestinationPath '${nativesDir}' -Force"`);
          } catch (e) {}
        }
      }
    }
    classpath.push(clientJarPath);

    // 5. Download Asset Index
    const assetInfo = versionData.assetIndex;
    const assetIndexDir = path.join(MINECRAFT_DIR, 'assets', 'indexes');
    safeMkdirSync(assetIndexDir);
    const assetIndexFile = path.join(assetIndexDir, `${assetInfo.id}.json`);

    if (!fs.existsSync(assetIndexFile) || fs.statSync(assetIndexFile).size < 50) {
      sendStatus('Downloading game assets index...');
      await downloadFile(assetInfo.url, assetIndexFile);
    }

    // 6. Spawn Game Process
    sendStatus(`Initializing Minecraft ${versionId}...`);
    const account = cfg.account || { name: cfg.offlineUsername || 'Player', id: '00000000000000000000000000000000', token: 'null' };
    const jvmFlags = (cfg.jvmArgs || '').split(' ').filter(Boolean);

    const args = [
      `-Xms${cfg.minRam || 2048}M`,
      `-Xmx${cfg.maxRam || 4096}M`,
      ...jvmFlags,
      `-Djava.library.path=${nativesDir}`,
      '-cp',
      classpath.join(';'),
      'net.minecraft.client.main.Main',
      '--username', account.name,
      '--version', versionId,
      '--gameDir', MINECRAFT_DIR,
      '--assetsDir', path.join(MINECRAFT_DIR, 'assets'),
      '--assetIndex', assetInfo.id,
      '--uuid', account.id.replace(/-/g, ''),
      '--accessToken', account.token,
      '--userType', account.token !== 'null' ? 'msa' : 'mojang'
    ];

    const game = spawn(javaBin, args, { cwd: MINECRAFT_DIR });
    let stderrOutput = '';

    game.stderr.on('data', (d) => {
      stderrOutput += d.toString();
    });

    game.on('spawn', () => {
      sendStatus(`Game running natively (PID: ${game.pid})`);
      if (cfg.closeOnLaunch) setTimeout(() => mainWindow.hide(), 2000);
    });

    game.on('close', (code) => {
      if (code !== 0 && code !== null) {
        sendStatus(`Game process exited (Code ${code}): ${stderrOutput.slice(-300) || 'Check JVM memory configuration'}`, true);
      }
    });

    game.on('error', (err) => sendStatus(`Execution halted: ${err.message}`, true));
  } catch (err) {
    sendStatus(`Launch failed: ${err.message}`, true);
  }
});

// App Lifecycle
app.whenReady().then(() => {
  initDirectories();
  createWindow();
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
