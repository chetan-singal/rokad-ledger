// 'YYYY-MM-DD' -> '1 Apr 2026'. Split by hand on purpose: new Date('2026-04-01') is
// UTC midnight, which renders as the previous day in time zones west of UTC.
export function formatDate(iso) {
  if (!iso) return ''
  const [year, month, day] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric'
  }).format(new Date(year, month - 1, day))
}

// Today's local date as 'YYYY-MM-DD' (toISOString() would give the UTC date instead).
export function todayIso() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}