const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { spawn } = require('child_process');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    minWidth: 900,
    minHeight: 600,
    frame: false, // Borderless modern aesthetic
    transparent: false,
    backgroundColor: '#090a0f',
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false
    }
  });

  mainWindow.loadFile('index.html');
}

// Window Controls (Minimize, Close)
ipcMain.on('window-min', () => mainWindow.minimize());
ipcMain.on('window-close', () => mainWindow.close());

// System Launcher Command
ipcMain.on('launch-game', (event, config) => {
  console.log("Preparing to launch Minecraft...");
  
  // Standard JVM launch arguments targeting ARM64 optimizations
  const javaArgs = [
    `-Xms${config.minMem || 2048}M`,
    `-Xmx${config.maxMem || 4096}M`,
    '-XX:+UseG1GC',
    '-version' // Replace with full launch classpath and client parameters
  ];

  const javaProcess = spawn('java', javaArgs);

  javaProcess.on('spawn', () => {
    event.reply('launch-status', 'Minecraft execution initialized!');
  });

  javaProcess.on('error', (err) => {
    event.reply('launch-status', `Failed to start Java: ${err.message}. Make sure ARM64 OpenJDK is installed.`);
  });
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
