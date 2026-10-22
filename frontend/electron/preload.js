const { contextBridge, ipcRenderer } = require('electron')

const closeListeners = new Set()

ipcRenderer.on('app:close-requested', () => {
  // No listener means the React app isn't mounted to answer; main falls back to closing.
  if (closeListeners.size === 0) return
  ipcRenderer.send('app:close-ack')
  closeListeners.forEach((listener) => listener())
})

contextBridge.exposeInMainWorld('rokad', {
  onCloseRequested(listener) {
    closeListeners.add(listener)
    return () => closeListeners.delete(listener)
  },
  confirmClose() {
    return ipcRenderer.invoke('app:confirm-close')
  }
})