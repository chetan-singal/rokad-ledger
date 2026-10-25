const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? 'Something went wrong. Please try again.')
    this.status = status
    this.code = body?.error ?? 'UNKNOWN'
    this.field = body?.field ?? null
  }
}

export async function request(path, { method = 'GET', body } = {}) {
  let response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    throw new ApiError(0, {
      error: 'NETWORK',
      message: 'Cannot reach the ROKAD backend. Make sure it is running, then try again.'
    })
  }

  if (response.status === 204) return null
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