// Hands a text file to the person as a download. In the desktop app the system's Save As window
// opens, so they choose the folder; in a browser it goes to the downloads folder.
export function saveTextFile(text, fileName) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
