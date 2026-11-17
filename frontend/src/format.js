// How dates are written on screen. Settings lets the person choose; the choice only changes how a
// date is shown, never the date itself (SRS 11.2).
const DEFAULT_DATE_FORMAT = 'dMonY'
export const DATE_FORMATS = ['dMonY', 'dmy', 'ymd']
let dateFormat = DEFAULT_DATE_FORMAT

export function setDateFormat(format) {
  dateFormat = DATE_FORMATS.includes(format) ? format : DEFAULT_DATE_FORMAT
}

export function getDateFormat() {
  return dateFormat
}

// 'YYYY-MM-DD' -> '1 Apr 2026', '01/04/2026' or '2026-04-01'. Split by hand on purpose:
// new Date('2026-04-01') is UTC midnight, which renders as the previous day in time zones west
// of UTC.
export function formatDateWith(format, iso) {
  if (!iso) return ''
  const [year, month, day] = iso.split('-').map(Number)
  const pad = (n) => String(n).padStart(2, '0')
  if (format === 'dmy') return `${pad(day)}/${pad(month)}/${year}`
  if (format === 'ymd') return `${year}-${pad(month)}-${pad(day)}`
  return new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric'
  }).format(new Date(year, month - 1, day))
}

export function formatDate(iso) {
  return formatDateWith(dateFormat, iso)
}

// 'YYYY-MM-DDTHH:MM:SS' -> '1 Nov 2026, 09:00' (24-hour clock, no seconds)
export function formatDateTime(iso) {
  if (!iso) return ''
  return `${formatDate(iso.slice(0, 10))}, ${iso.slice(11, 16)}`
}

// Today's local date as 'YYYY-MM-DD' (toISOString() would give the UTC date instead).
export function todayIso() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
