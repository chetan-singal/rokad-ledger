const { app, BrowserWindow, ipcMain } = require('electron')
const path = require('node:path')

const isDev = !app.isPackaged

let allowClose = false
let closeTimer = null

function createWindow() {
  const win = new BrowserWindow({
    width: 1100,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  })

  // The window's own close button and Alt+F4 land here. The page decides whether anything
  // is unsaved; this only closes once it confirms. (The Exit menu item starts in the page.)
  win.on('close', (event) => {
    if (allowClose) return
    event.preventDefault()
    win.webContents.send('app:close-requested')

    // If nothing in the page answers (load-error page, app not mounted yet),
    // don't trap the user in an unclosable window.
    clearTimeout(closeTimer)
    closeTimer = setTimeout(() => {
      if (win.isDestroyed()) return
      allowClose = true
      win.close()
    }, 1500)
  })

  if (isDev) {
    win.loadURL('http://localhost:5173')
  } else {
    win.loadFile(path.join(__dirname, '../dist/index.html'))
  }
}

// The page has received the close request and is handling it (possibly showing a prompt).
ipcMain.on('app:close-ack', () => clearTimeout(closeTimer))

// The page confirmed: nothing unsaved, or the user chose Save or Discard.
ipcMain.handle('app:confirm-close', (event) => {
  clearTimeout(closeTimer)
  allowClose = true
  BrowserWindow.fromWebContents(event.sender)?.close()
})

app.whenReady().then(createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})