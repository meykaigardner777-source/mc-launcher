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

// Ensure base directories exist
[APP_DIR, RUNTIMES_DIR, MINECRAFT_DIR, path.join(MINECRAFT_DIR, 'mods'), path.join(MINECRAFT_DIR, 'saves'), path.join(MINECRAFT_DIR, 'screenshots')].forEach(dir => {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
});

// ==========================================
// CONFIGURATION MANAGEMENT
// ==========================================
function loadConfig() {
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    } catch (e) {
      console.error('Failed to parse config:', e);
    }
  }
  return {
    accounts: [],
    activeAccount: null,
    offlineUsername: 'Player',
    minRam: 2048,
    maxRam: 4096,
    customJavaPath: '',
    closeOnLaunch: false,
    mcVersion: '1.20.4',
    jvmArgs: '-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50'
  };
}

function saveConfig(cfg) {
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
}

// ==========================================
// WINDOW INITIALIZATION
// ==========================================
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1080,
    height: 700,
    minWidth: 920,
    minHeight: 580,
    frame: false,
    backgroundColor: '#09090b',
    show: false,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  mainWindow.loadFile('index.html');

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });
}

// Window Controls
ipcMain.on('window-min', () => mainWindow.minimize());
ipcMain.on('window-max', () => {
  if (mainWindow.isMaximized()) mainWindow.unmaximize();
  else mainWindow.maximize();
});
ipcMain.on('window-close', () => mainWindow.close());
ipcMain.on('open-path', (e, targetPath) => {
  const full = targetPath === 'saves' 
    ? path.join(MINECRAFT_DIR, 'saves') 
    : targetPath === 'screenshots' 
    ? path.join(MINECRAFT_DIR, 'screenshots') 
    : path.join(MINECRAFT_DIR, 'mods');
  if (fs.existsSync(full)) shell.openPath(full);
});

// Configuration IPC
ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (event, newCfg) => {
  saveConfig(newCfg);
  return true;
});

// ==========================================
// ARM64 JDK DETECTION & AUTO-INSTALLER
// ==========================================
function findSystemArmJava() {
  // 1. Managed ARM64 JDK check
  const managedJavaw = path.join(JDK_DIR, 'bin', 'javaw.exe');
  const managedJava = path.join(JDK_DIR, 'bin', 'java.exe');
  if (fs.existsSync(managedJavaw)) return { found: true, path: managedJavaw, source: 'Managed ARM64 JDK (Local)' };
  if (fs.existsSync(managedJava)) return { found: true, path: managedJava, source: 'Managed ARM64 JDK (Local)' };

  // 2. Known Windows ARM64 JDK registry/file locations
  const candidates = [
    'C:\\Program Files\\Microsoft\\jdk-21\\bin\\javaw.exe',
    'C:\\Program Files\\Microsoft\\jdk-17\\bin\\javaw.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21-arm64\\bin\\javaw.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-17-arm64\\bin\\javaw.exe',
    'C:\\Program Files\\Zulu\\zulu-21-arm64\\bin\\javaw.exe'
  ];

  for (const c of candidates) {
    if (fs.existsSync(c)) {
      return { found: true, path: c, source: 'Installed System ARM64 JDK' };
    }
  }

  // 3. Fallback check PATH for aarch64
  try {
    const output = execSync('java -XshowSettings:properties -version 2>&1', { timeout: 3000 }).toString();
    if (output.includes('aarch64')) {
      return { found: true, path: 'javaw', source: 'System PATH (Native aarch64)' };
    }
  } catch (e) {}

  return { found: false, path: null, source: null };
}

ipcMain.handle('detect-arm-java', async () => {
  return findSystemArmJava();
});

ipcMain.handle('install-arm-jdk', async (event) => {
  const JDK_URL = 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-aarch64.zip';
  const zipPath = path.join(RUNTIMES_DIR, 'ms-jdk-21-arm64.zip');
  const tempExtract = path.join(RUNTIMES_DIR, 'temp-extract');

  const sendProgress = (step, progress) => {
    event.sender.send('jdk-install-status', { step, progress });
  };

  return new Promise((resolve, reject) => {
    sendProgress('Connecting to Microsoft CDN...', 0.05);

    const download = (url) => {
      https.get(url, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          return download(res.headers.location);
        }
        if (res.statusCode !== 200) {
          return reject(new Error(`Server returned HTTP ${res.statusCode}`));
        }

        const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
        let receivedBytes = 0;
        const file = fs.createWriteStream(zipPath);

        res.on('data', (chunk) => {
          receivedBytes += chunk.length;
          if (totalBytes > 0) {
            const pct = receivedBytes / totalBytes;
            sendProgress(`Downloading Microsoft OpenJDK 21 ARM64 (${Math.round(pct * 100)}%)...`, pct * 0.75);
          }
        });

        res.pipe(file);

        file.on('finish', () => {
          file.close(async () => {
            sendProgress('Extracting JDK archive via Windows PowerShell...', 0.85);

            if (fs.existsSync(tempExtract)) fs.rmSync(tempExtract, { recursive: true, force: true });
            fs.mkdirSync(tempExtract, { recursive: true });

            // Extract using native Windows PowerShell command (no extra npm dependencies required)
            const psCmd = `powershell.exe -NoProfile -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${tempExtract}' -Force"`;
            exec(psCmd, (err) => {
              if (err) {
                return reject(new Error('Failed to extract JDK: ' + err.message));
              }

              try {
                // Find inner folder
                const entries = fs.readdirSync(tempExtract);
                const innerFolder = entries.find(e => fs.statSync(path.join(tempExtract, e)).isDirectory()) || '';
                const sourceRoot = path.join(tempExtract, innerFolder);

                if (fs.existsSync(JDK_DIR)) fs.rmSync(JDK_DIR, { recursive: true, force: true });
                fs.renameSync(sourceRoot, JDK_DIR);

                // Cleanup
                fs.rmSync(tempExtract, { recursive: true, force: true });
                if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);

                sendProgress('Microsoft OpenJDK 21 installed successfully.', 1.0);
                const finalExe = path.join(JDK_DIR, 'bin', 'javaw.exe');
                resolve({ success: true, path: finalExe });
              } catch (e) {
                reject(e);
              }
            });
          });
        });
      }).on('error', (err) => {
        if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);
        reject(err);
      });
    };

    download(JDK_URL);
  });
});

// ==========================================
// MICROSOFT / XBOX AUTHENTICATION (POPUP PIPELINE)
// ==========================================
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
      let result = '';
      res.on('data', chunk => result += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(result)); } catch (e) { resolve(result); }
      });
    });
    req.on('error', reject);
    req.write(bodyStr);
    req.end();
  });
}

function httpGet(url, token) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request({
      hostname: u.hostname,
      port: 443,
      path: u.pathname + u.search,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    }, (res) => {
      let result = '';
      res.on('data', chunk => result += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(result)); } catch (e) { resolve(result); }
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
      title: 'Sign in to Microsoft'
    });

    authWindow.setMenu(null);
    authWindow.loadURL(authUrl);

    let resolved = false;

    authWindow.webContents.on('will-redirect', async (event, newUrl) => {
      if (newUrl.startsWith(MS_REDIRECT_URI)) {
        const urlParams = new URL(newUrl).searchParams;
        const code = urlParams.get('code');

        if (code && !resolved) {
          resolved = true;
          authWindow.close();

          try {
            // 1. Exchange code for Live token
            const liveRes = await httpPost('https://login.live.com/oauth20_token.srf',
              `client_id=${MS_CLIENT_ID}&code=${code}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(MS_REDIRECT_URI)}`
            );

            // 2. Xbox Live authentication
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

            // 3. XSTS authorization
            const xstsRes = await httpPost('https://xsts.auth.xboxlive.com/xsts/authorize', {
              Properties: {
                SandboxId: 'RETAIL',
                UserTokens: [xblRes.Token]
              },
              RelyingParty: 'rp://api.minecraftservices.com/',
              TokenType: 'JWT'
            });

            // 4. Mojang login
            const mojangRes = await httpPost('https://api.minecraftservices.com/authentication/login_with_xbox', {
              identityToken: `XBL3.0 x=${uhs};${xstsRes.Token}`
            });

            // 5. Minecraft profile
            const profile = await httpGet('https://api.minecraftservices.com/minecraft/profile', mojangRes.access_token);

            const account = {
              id: profile.id,
              name: profile.name,
              token: mojangRes.access_token
            };

            const cfg = loadConfig();
            cfg.accounts = (cfg.accounts || []).filter(a => a.id !== account.id);
            cfg.accounts.push(account);
            cfg.activeAccount = account;
            saveConfig(cfg);

            resolve(account);
          } catch (err) {
            reject(new Error('Authentication failed: ' + (err.message || err)));
          }
        }
      }
    });

    authWindow.on('closed', () => {
      if (!resolved) reject(new Error('Authentication window closed.'));
    });
  });
});

// ==========================================
// MODRINTH API & DIRECT DOWNLOAD ENGINE
// ==========================================
ipcMain.handle('search-modrinth', async (event, { query }) => {
  const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify([["project_type:mod"], ["versions:1.20.4"]]))}&limit=20`;
  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data).hits || []); } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
});

ipcMain.handle('install-mod', async (event, { projectId }) => {
  const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version?game_versions=["1.20.4"]`;

  return new Promise((resolve, reject) => {
    https.get(versionsUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const versions = JSON.parse(data);
          if (!versions || !versions.length) return reject(new Error('No compatible 1.20.4 releases found.'));

          const primaryFile = versions[0].files.find(f => f.primary) || versions[0].files[0];
          const modsDir = path.join(MINECRAFT_DIR, 'mods');
          if (!fs.existsSync(modsDir)) fs.mkdirSync(modsDir, { recursive: true });

          const outPath = path.join(modsDir, primaryFile.filename);
          const fileStream = fs.createWriteStream(outPath);

          const download = (fileUrl) => {
            https.get(fileUrl, (dlRes) => {
              if (dlRes.statusCode >= 300 && dlRes.statusCode < 400 && dlRes.headers.location) {
                return download(dlRes.headers.location);
              }
              dlRes.pipe(fileStream);
              fileStream.on('finish', () => {
                fileStream.close();
                resolve(primaryFile.filename);
              });
            }).on('error', reject);
          };

          download(primaryFile.url);
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
});

// ==========================================
// VAULT (SAVES & SCREENSHOTS)
// ==========================================
ipcMain.handle('get-vault-data', async () => {
  const savesDir = path.join(MINECRAFT_DIR, 'saves');
  const screenshotsDir = path.join(MINECRAFT_DIR, 'screenshots');

  const worlds = [];
  if (fs.existsSync(savesDir)) {
    const entries = fs.readdirSync(savesDir, { withFileTypes: true });
    for (const d of entries) {
      if (d.isDirectory()) {
        const p = path.join(savesDir, d.name);
        let totalSize = 0;
        try {
          const files = fs.readdirSync(p);
          for (const f of files) {
            totalSize += fs.statSync(path.join(p, f)).size;
          }
        } catch (e) {}
        worlds.push({
          name: d.name,
          folder: d.name,
          sizeMb: (totalSize / (1024 * 1024)).toFixed(1)
        });
      }
    }
  }

  const screenshots = [];
  if (fs.existsSync(screenshotsDir)) {
    const files = fs.readdirSync(screenshotsDir);
    for (const f of files) {
      if (f.toLowerCase().endsWith('.png')) {
        const fullPath = path.join(screenshotsDir, f);
        try {
          const base64 = fs.readFileSync(fullPath).toString('base64');
          screenshots.push({
            fileName: f,
            url: `data:image/png;base64,${base64}`
          });
        } catch (e) {}
      }
    }
  }

  return { worlds, screenshots };
});

ipcMain.handle('backup-world', async (event, folderName) => {
  const targetDir = path.join(MINECRAFT_DIR, 'saves', folderName);
  const backupsDir = path.join(MINECRAFT_DIR, 'backups');
  if (!fs.existsSync(backupsDir)) fs.mkdirSync(backupsDir, { recursive: true });

  const zipTarget = path.join(backupsDir, `${folderName}-backup-${Date.now()}.zip`);
  return new Promise((resolve, reject) => {
    const cmd = `powershell.exe -NoProfile -Command "Compress-Archive -Path '${targetDir}' -DestinationPath '${zipTarget}' -Force"`;
    exec(cmd, (err) => {
      if (err) reject(err);
      else resolve(zipTarget);
    });
  });
});

// ==========================================
// SKIN SELECTION & MOJANG UPLOADER
// ==========================================
ipcMain.handle('select-skin-dialog', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: 'Select Minecraft Skin (.png)',
    filters: [{ name: 'PNG Images', extensions: ['png'] }],
    properties: ['openFile']
  });

  if (res.canceled || !res.filePaths.length) return null;
  const filePath = res.filePaths[0];
  const base64 = fs.readFileSync(filePath).toString('base64');
  return { filePath, dataUrl: `data:image/png;base64,${base64}` };
});

ipcMain.handle('upload-skin', async (event, { token, filePath, isSlim }) => {
  const fileBytes = fs.readFileSync(filePath);
  const boundary = '----AetheriaBoundary' + crypto.randomBytes(8).toString('hex');

  const payloadHeader = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="variant"\r\n\r\n${isSlim ? 'slim' : 'classic'}\r\n` +
    `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="skin.png"\r\nContent-Type: image/png\r\n\r\n`
  );
  const payloadFooter = Buffer.from(`\r\n--${boundary}--\r\n`);
  const body = Buffer.concat([payloadHeader, fileBytes, payloadFooter]);

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
      else reject(new Error(`Mojang API responded with HTTP status ${res.statusCode}`));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
});

// ==========================================
// MINECRAFT 1.20.4 DOWNLOADER & LAUNCHER
// ==========================================
function downloadAsync(url, dest) {
  return new Promise((resolve, reject) => {
    if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return resolve();
    const folder = path.dirname(dest);
    if (!fs.existsSync(folder)) fs.mkdirSync(folder, { recursive: true });

    const get = (targetUrl) => {
      https.get(targetUrl, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          return get(res.headers.location);
        }
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode} on ${targetUrl}`));
        const fileStream = fs.createWriteStream(dest);
        res.pipe(fileStream);
        fileStream.on('finish', () => fileStream.close(() => resolve()));
      }).on('error', reject);
    };
    get(url);
  });
}

ipcMain.on('launch-game', async (event, cfg) => {
  const sendStatus = (message, isError = false) => {
    event.reply('launch-status', { message, isError });
  };

  try {
    // 1. Resolve ARM64 Java Binary
    let javaBin = cfg.customJavaPath;
    if (!javaBin || !fs.existsSync(javaBin)) {
      const armJava = findSystemArmJava();
      if (!armJava.found) {
        return sendStatus('No ARM64 JDK located. Please click "Install ARM64 JDK" first.', true);
      }
      javaBin = armJava.path;
    }

    sendStatus('Verifying Minecraft 1.20.4 game files...');

    const versionsDir = path.join(MINECRAFT_DIR, 'versions', '1.20.4');
    const versionJsonPath = path.join(versionsDir, '1.20.4.json');
    const clientJarPath = path.join(versionsDir, '1.20.4.jar');
    const nativesDir = path.join(versionsDir, 'natives');

    if (!fs.existsSync(versionsDir)) fs.mkdirSync(versionsDir, { recursive: true });
    if (!fs.existsSync(nativesDir)) fs.mkdirSync(nativesDir, { recursive: true });

    // 2. Obtain Version Metadata
    let versionData;
    if (!fs.existsSync(versionJsonPath)) {
      sendStatus('Fetching Mojang version manifest...');
      const manifest = await new Promise((res, rej) => {
        https.get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', (r) => {
          let b = ''; r.on('data', c => b += c); r.on('end', () => res(JSON.parse(b)));
        }).on('error', rej);
      });

      const entry = manifest.versions.find(v => v.id === '1.20.4');
      if (!entry) throw new Error('Version 1.20.4 not found in official manifest.');

      await downloadAsync(entry.url, versionJsonPath);
    }
    versionData = JSON.parse(fs.readFileSync(versionJsonPath, 'utf-8'));

    // 3. Download Client Jar
    if (!fs.existsSync(clientJarPath)) {
      sendStatus('Downloading Minecraft 1.20.4 client binary...');
      await downloadAsync(versionData.downloads.client.url, clientJarPath);
    }

    // 4. Download Libraries (With Windows ARM64 native resolution)
    sendStatus('Resolving library matrix...');
    const classpathEntries = [];
    const libraries = versionData.libraries || [];

    for (const lib of libraries) {
      // Check OS rules
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

      // Handle standard artifact
      if (lib.downloads && lib.downloads.artifact) {
        const libPath = path.join(MINECRAFT_DIR, 'libraries', lib.downloads.artifact.path);
        await downloadAsync(lib.downloads.artifact.url, libPath);
        classpathEntries.push(libPath);
      }

      // Handle native binaries
      if (lib.downloads && lib.downloads.classifiers) {
        const classifiers = lib.downloads.classifiers;
        const nativeKey = classifiers['natives-windows-arm64'] ? 'natives-windows-arm64' : classifiers['natives-windows'] ? 'natives-windows' : null;
        if (nativeKey && classifiers[nativeKey]) {
          const nat = classifiers[nativeKey];
          const natJarPath = path.join(MINECRAFT_DIR, 'libraries', nat.path);
          await downloadAsync(nat.url, natJarPath);

          // Extract DLLs to natives folder using PowerShell
          const cmd = `powershell.exe -NoProfile -Command "Expand-Archive -Path '${natJarPath}' -DestinationPath '${nativesDir}' -Force"`;
          try { execSync(cmd); } catch (e) {}
        }
      }
    }
    classpathEntries.push(clientJarPath);

    // 5. Download Assets Index
    const assetIndexInfo = versionData.assetIndex;
    const assetIndexDir = path.join(MINECRAFT_DIR, 'assets', 'indexes');
    const assetIndexPath = path.join(assetIndexDir, `${assetIndexInfo.id}.json`);
    await downloadAsync(assetIndexInfo.url, assetIndexPath);

    // 6. Build Launch Parameters
    sendStatus('Launching Minecraft on ARM64 runtime...');
    const account = cfg.account || { name: cfg.offlineUsername || 'Player', id: '00000000000000000000000000000000', token: 'null' };
    const jvmFlags = (cfg.jvmArgs || '').split(' ').filter(Boolean);

    const args = [
      `-Xms${cfg.minRam || 2048}M`,
      `-Xmx${cfg.maxRam || 4096}M`,
      ...jvmFlags,
      `-Djava.library.path=${nativesDir}`,
      '-cp',
      classpathEntries.join(';'),
      'net.minecraft.client.main.Main',
      '--username', account.name,
      '--version', '1.20.4',
      '--gameDir', MINECRAFT_DIR,
      '--assetsDir', path.join(MINECRAFT_DIR, 'assets'),
      '--assetIndex', assetIndexInfo.id,
      '--uuid', account.id.replace(/-/g, ''),
      '--accessToken', account.token,
      '--userType', account.token !== 'null' ? 'msa' : 'mojang'
    ];

    const gameProcess = spawn(javaBin, args, { cwd: MINECRAFT_DIR, detached: true });

    gameProcess.on('spawn', () => {
      sendStatus(`Game process active (PID: ${gameProcess.pid})`);
      if (cfg.closeOnLaunch) {
        setTimeout(() => mainWindow.hide(), 1500);
      }
    });

    gameProcess.stderr.on('data', (data) => {
      console.error(`[Minecraft]: ${data}`);
    });

    gameProcess.on('error', (err) => {
      sendStatus(`Launch failed: ${err.message}`, true);
    });

  } catch (err) {
    sendStatus(`Initialization error: ${err.message}`, true);
  }
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
