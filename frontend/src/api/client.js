const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? 'Something went wrong. Please try again.')
    this.status = status
    this.code = body?.error ?? 'UNKNOWN'
    this.field = body?.field ?? null
  }
}

// rawBody sends text exactly as given (a backup file must not be re-written on the way out), and
// asText returns the answer as text for the same reason. Everything else is plain JSON.
export async function request(path, { method = 'GET', body, rawBody, asText = false } = {}) {
  const payload = rawBody !== undefined ? rawBody : body === undefined ? undefined : JSON.stringify(body)
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: payload === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: payload
    })
  } catch {
    throw new ApiError(0, {
      error: 'NETWORK',
      message: 'Cannot reach the ROKAD backend. Make sure it is running, then try again.'
    })
  }

  if (response.status === 204) return null
  if (asText && response.ok) return response.text()
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(response.status, data)
  return data
}

// 404 here is the normal first-launch answer ("no active ledger yet"), not a failure.
export async function getActiveLedger() {
  try {
    return await request('/api/ledger/active')
  } catch (error) {
    if (error.status === 404) return null
    throw error
  }
}

export function setupLedger(setup) {
  return request('/api/ledger/setup', { method: 'POST', body: setup })
}

export function listTransactions() {
  return request('/api/transactions')
}

export function addTransaction(transaction) {
  return request('/api/transactions', { method: 'POST', body: transaction })
}

export function editTransaction(id, transaction) {
  return request(`/api/transactions/${id}`, { method: 'PUT', body: transaction })
}

export function deleteTransaction(id) {
  return request(`/api/transactions/${id}`, { method: 'DELETE' })
}

export function getOverview() {
  return request('/api/ledger/active/overview')
}

export function getClosingWindow() {
  return request('/api/ledger/active/closing-window')
}

export function verifyPeriod(period) {
  return request('/api/ledger/verify', { method: 'POST', body: period })
}

export function closeLedger(closing) {
  return request('/api/ledger/close', { method: 'POST', body: closing })
}

export function listSavedLedgers() {
  return request('/api/ledgers')
}

export function getSavedLedger(ledgerId) {
  return request(`/api/ledgers/${ledgerId}`)
}

export function getAppInfo() {
  return request('/api/app/info')
}

// The three backup calls pass the file around as text, never as a parsed object.
export function exportBackup() {
  return request('/api/backup/export', { asText: true })
}

export function inspectBackup(text) {
  return request('/api/backup/inspect', { method: 'POST', rawBody: text })
}

export function restoreBackup(text) {
  return request('/api/backup/restore', { method: 'POST', rawBody: text })
}
