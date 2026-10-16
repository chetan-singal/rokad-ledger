import { useEffect, useState } from 'react'

function App() {
  const [status, setStatus] = useState('checking...')

  useEffect(() => {
    fetch('http://localhost:8080/api/health')
      .then((res) => res.text())
      .then((text) => setStatus(text))
      .catch(() => setStatus('backend unreachable'))
  }, [])

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>ROKAD — Daily Ledger</h1>
      <p>Phase 1 skeleton. Backend health check: <strong>{status}</strong></p>
    </div>
  )
}

export default App
