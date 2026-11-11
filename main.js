const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const { spawn, exec } = require('child_process');

let mainWindow;

// Directory Paths
const APP_DIR = path.join(app.getPath('appData'), '.aetheria-launcher');
const CONFIG_FILE = path.join(APP_DIR, 'launcher_config.json');
const INSTANCES_DIR = path.join(APP_DIR, 'instances');
const DEFAULT_INSTANCE = path.join(INSTANCES_DIR, 'default');

// Ensure base directories exist
if (!fs.existsSync(APP_DIR)) fs.mkdirSync(APP_DIR, { recursive: true });
if (!fs.existsSync(DEFAULT_INSTANCE)) fs.mkdirSync(path.join(DEFAULT_INSTANCE, '.minecraft', 'mods'), { recursive: true });

function loadConfig() {
  if (fs.existsSync(CONFIG_FILE)) {
    try { return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')); } catch (e) {}
  }
  return {
    accounts: [],
    activeAccount: null,
    minRam: 2048,
    maxRam: 4096,
    customJavaPath: '',
    closeOnLaunch: false,
    resolution: '1920x1080',
    jvmArgs: '-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M'
  };
}

function saveConfig(cfg) {
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2));
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1220,
    height: 780,
    minWidth: 1020,
    minHeight: 660,
    frame: false,
    backgroundColor: '#07080c',
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  mainWindow.loadFile('index.html');
}

// Window Controls
ipcMain.on('window-min', () => mainWindow.minimize());
ipcMain.on('window-max', () => {
  if (mainWindow.isMaximized()) mainWindow.unmaximize();
  else mainWindow.maximize();
});
ipcMain.on('window-close', () => mainWindow.close());
ipcMain.on('open-external-url', (event, url) => shell.openExternal(url));

// Configuration IPC
ipcMain.handle('get-config', () => loadConfig());
ipcMain.handle('save-config', (event, newCfg) => {
  saveConfig(newCfg);
  return true;
});

// ==========================================
// SYSTEM ARM64 JDK DETECTION
// ==========================================
ipcMain.handle('detect-arm-java', async () => {
  const candidatePaths = [
    'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
    'C:\\Program Files\\Microsoft\\jdk-17\\bin\\java.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-21-arm64\\bin\\java.exe',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-17-arm64\\bin\\java.exe',
    'C:\\Program Files\\Zulu\\zulu-21-arm64\\bin\\java.exe'
  ];

  for (const p of candidatePaths) {
    if (fs.existsSync(p)) {
      return { found: true, path: p, source: 'Microsoft / Adoptium ARM64 Directory' };
    }
  }

  return new Promise((resolve) => {
    exec('java -XshowSettings:properties -version', (err, stdout, stderr) => {
      const output = stdout + stderr;
      if (!err && output.includes('aarch64')) {
        resolve({ found: true, path: 'java', source: 'System Environment PATH (ARM64 Verified)' });
      } else if (!err) {
        resolve({ found: true, path: 'java', source: 'System Environment PATH (Warning: Non-ARM64 Binary)' });
      } else {
        resolve({ found: false, path: null, error: 'No native Java JDK runtime found on machine.' });
      }
    });
  });
});

// ==========================================
// MICROSOFT / XBOX AUTHENTICATION PIPELINE
// ==========================================
const MS_CLIENT_ID = '00000000402b5328'; // Standard Public Client ID
const REDIRECT_PORT = 28543;
const REDIRECT_URI = `http://127.0.0.1:${REDIRECT_PORT}/callback`;

function postJson(url, data, headers = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const postData = typeof data === 'string' ? data : JSON.stringify(data);
    const req = https.request({
      hostname: parsed.hostname,
      port: 443,
      path: parsed.pathname + parsed.search,
      method: 'POST',
      headers: {
        'Content-Type': typeof data === 'string' ? 'application/x-www-form-urlencoded' : 'application/json',
        'Content-Length': Buffer.byteLength(postData),
        ...headers
      }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(body)); } catch (e) { resolve(body); }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function getJson(url, token) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const req = https.request({
      hostname: parsed.hostname,
      port: 443,
      path: parsed.pathname + parsed.search,
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(body)); } catch (e) { resolve(body); }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

ipcMain.handle('start-microsoft-login', async () => {
  return new Promise((resolve, reject) => {
    const authUrl = `https://login.live.com/oauth20_authorize.srf?client_id=${MS_CLIENT_ID}&response_type=code&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=XboxLive.signin%20offline_access`;
    
    const server = http.createServer(async (req, res) => {
      if (req.url.startsWith('/callback')) {
        const u = new URL(req.url, `http://127.0.0.1:${REDIRECT_PORT}`);
        const code = u.searchParams.get('code');
        
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end('<html><body style="background:#07080c;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;"><h2>Authentication complete! Return to Aetheria.</h2></body></html>');
        server.close();

        if (!code) {
          return reject('Authentication cancelled or failed to retrieve OAuth code.');
        }

        try {
          // 1. Get Live Token
          const msToken = await postJson('https://login.live.com/oauth20_token.srf', 
            `client_id=${MS_CLIENT_ID}&code=${code}&grant_type=authorization_code&redirect_uri=${encodeURIComponent(REDIRECT_URI)}`
          );

          // 2. Xbox Live Authentication
          const xbl = await postJson('https://user.auth.xboxlive.com/user/authenticate', {
            Properties: {
              AuthMethod: 'RPS',
              SiteName: 'user.auth.xboxlive.com',
              RpsTicket: msToken.access_token
            },
            RelyingParty: 'http://auth.xboxlive.com',
            TokenType: 'JWT'
          });

          const uhs = xbl.DisplayClaims.xui[0].uhs;

          // 3. XSTS Token Exchange
          const xsts = await postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
            Properties: {
              SandboxId: 'RETAIL',
              UserTokens: [xbl.Token]
            },
            RelyingParty: 'rp://api.minecraftservices.com/',
            TokenType: 'JWT'
          });

          // 4. Mojang API Login
          const mojang = await postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
            identityToken: `XBL3.0 x=${uhs};${xsts.Token}`
          });

          // 5. Fetch Player Profile
          const profile = await getJson('https://api.minecraftservices.com/minecraft/profile', mojang.access_token);

          const account = {
            id: profile.id,
            name: profile.name,
            token: mojang.access_token,
            skins: profile.skins
          };

          const cfg = loadConfig();
          cfg.accounts = cfg.accounts.filter(a => a.id !== account.id);
          cfg.accounts.push(account);
          cfg.activeAccount = account;
          saveConfig(cfg);

          resolve(account);
        } catch (err) {
          reject('OAuth pipeline failure: ' + (err.message || err));
        }
      }
    });

    server.listen(REDIRECT_PORT, () => {
      shell.openExternal(authUrl);
    });
  });
});

// ==========================================
// MODRINTH API & DIRECT DOWNLOAD ENGINE
// ==========================================
ipcMain.handle('search-modrinth', async (event, { query, loader }) => {
  const facets = [['project_type:mod']];
  if (loader && loader !== 'all') {
    facets.push([`categories:${loader}`]);
  }
  const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(query || '')}&facets=${encodeURIComponent(JSON.stringify(facets))}&limit=20`;
  
  return new Promise((resolve) => {
    https.get(url, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data).hits); } catch (e) { resolve([]); }
      });
    }).on('error', () => resolve([]));
  });
});

ipcMain.handle('install-mod', async (event, { projectId, version = '1.20.4' }) => {
  const versionsUrl = `https://api.modrinth.com/v2/project/${projectId}/version`;

  return new Promise((resolve, reject) => {
    https.get(versionsUrl, { headers: { 'User-Agent': 'Aetheria-ARM64-Launcher' } }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const versions = JSON.parse(data);
          if (!versions.length) return reject('No downloadable release versions found.');
          
          const primaryFile = versions[0].files.find(f => f.primary) || versions[0].files[0];
          const outDir = path.join(DEFAULT_INSTANCE, '.minecraft', 'mods');
          if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

          const outPath = path.join(outDir, primaryFile.filename);
          const fileStream = fs.createWriteStream(outPath);

          https.get(primaryFile.url, (dlRes) => {
            dlRes.pipe(fileStream);
            fileStream.on('finish', () => {
              fileStream.close();
              resolve(primaryFile.filename);
            });
          }).on('error', reject);
        } catch (e) {
          reject('Version resolution failed: ' + e.message);
        }
      });
    }).on('error', reject);
  });
});

// ==========================================
// VAULT (SAVES & SCREENSHOT SCANNER)
// ==========================================
ipcMain.handle('get-vault-data', async () => {
  const savesDir = path.join(DEFAULT_INSTANCE, '.minecraft', 'saves');
  const screenshotsDir = path.join(DEFAULT_INSTANCE, '.minecraft', 'screenshots');

  const worlds = [];
  if (fs.existsSync(savesDir)) {
    const dirs = fs.readdirSync(savesDir, { withFileTypes: true });
    for (const d of dirs) {
      if (d.isDirectory()) {
        const p = path.join(savesDir, d.name);
        let totalSize = 0;
        try {
          const files = fs.readdirSync(p);
          for (const f of files) {
            const stat = fs.statSync(path.join(p, f));
            totalSize += stat.size;
          }
        } catch (e) {}
        worlds.push({
          name: d.name,
          folder: d.name,
          sizeMb: (totalSize / (1024 * 1024)).toFixed(2)
        });
      }
    }
  }

  const screenshots = [];
  if (fs.existsSync(screenshotsDir)) {
    const files = fs.readdirSync(screenshotsDir);
    for (const f of files) {
      if (f.endsWith('.png')) {
        const fullPath = path.join(screenshotsDir, f);
        screenshots.push({
          fileName: f,
          url: `file://${fullPath.replace(/\\/g, '/')}`
        });
      }
    }
  }

  return { worlds, screenshots };
});

ipcMain.handle('backup-world', async (event, folderName) => {
  const targetDir = path.join(DEFAULT_INSTANCE, '.minecraft', 'saves', folderName);
  const backupsDir = path.join(DEFAULT_INSTANCE, 'backups');
  if (!fs.existsSync(backupsDir)) fs.mkdirSync(backupsDir, { recursive: true });

  const zipTarget = path.join(backupsDir, `${folderName}-backup-${Date.now()}.zip`);
  return new Promise((resolve, reject) => {
    // Windows Native Shell Compression
    const cmd = `powershell.exe -command "Compress-Archive -Path '${targetDir}' -DestinationPath '${zipTarget}' -Force"`;
    exec(cmd, (err) => {
      if (err) reject(err.message);
      else resolve(zipTarget);
    });
  });
});

// ==========================================
// SKIN UPLOAD ENGINE
// ==========================================
ipcMain.handle('upload-skin', async (event, { token, filePath, isSlim }) => {
  const fileBytes = fs.readFileSync(filePath);
  const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);

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
      else reject(`Mojang API responded with HTTP status ${res.statusCode}`);
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
});

// ==========================================
// GAME EXECUTION HANDLER
// ==========================================
ipcMain.on('launch-game', (event, cfg) => {
  const javaExe = cfg.customJavaPath || 'java';
  const instanceDir = DEFAULT_INSTANCE;
  const gameDir = path.join(instanceDir, '.minecraft');
  const binDir = path.join(instanceDir, 'bin');
  const libsDir = path.join(instanceDir, 'libraries');
  const nativesDir = path.join(binDir, 'natives');

  if (!fs.existsSync(nativesDir)) fs.mkdirSync(nativesDir, { recursive: true });

  // Classpath construction
  const cpEntries = [];
  if (fs.existsSync(libsDir)) {
    const scanJars = (dir) => {
      const files = fs.readdirSync(dir, { withFileTypes: true });
      for (const f of files) {
        const full = path.join(dir, f.name);
        if (f.isDirectory()) scanJars(full);
        else if (f.name.endsWith('.jar')) cpEntries.push(full);
      }
    };
    scanJars(libsDir);
  }

  const clientJar = path.join(binDir, 'client.jar');
  if (fs.existsSync(clientJar)) cpEntries.push(clientJar);

  // Full Launch Argument Matrix
  const jvmFlags = (cfg.jvmArgs || '').split(' ').filter(Boolean);
  const args = [
    `-Xms${cfg.minRam || 2048}M`,
    `-Xmx${cfg.maxRam || 4096}M`,
    ...jvmFlags,
    `-Djava.library.path=${nativesDir}`,
    '-cp',
    cpEntries.length ? cpEntries.join(';') : 'client.jar',
    'net.minecraft.client.main.Main',
    '--username', cfg.account ? cfg.account.name : 'Player',
    '--version', '1.20.4',
    '--gameDir', gameDir,
    '--assetsDir', path.join(instanceDir, 'assets'),
    '--assetIndex', '5',
    '--uuid', cfg.account ? cfg.account.id : '00000000-0000-0000-0000-000000000000',
    '--accessToken', cfg.account ? cfg.account.token : 'null',
    '--userType', 'msa'
  ];

  const gameProc = spawn(javaExe, args, { cwd: gameDir, detached: true });

  gameProc.on('spawn', () => {
    event.reply('launch-status', { success: true, message: `Active PID: ${gameProc.pid} running natively!` });
    if (cfg.closeOnLaunch) {
      setTimeout(() => mainWindow.hide(), 2000);
    }
  });

  gameProc.on('error', (err) => {
    event.reply('launch-status', { 
      success: false, 
      message: `Execution halt: ${err.message}. Verify that Microsoft OpenJDK 21 ARM64 is installed.` 
    });
  });
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
