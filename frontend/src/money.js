// Money is handled as whole paise (1/100 of a rupee), never as floating-point rupees.
// The backend sends amounts as JSON numbers with at most two decimals; those survive the
// trip through a double exactly, and Math.round removes the binary noise in value * 100.
// Totals are summed as BigInt, so no sum can lose a paisa however long the ledger is.

export function toPaise(value) {
  const paise = Math.round(Number(value) * 100)
  if (!Number.isFinite(paise)) throw new TypeError(`Not a money amount: ${value}`)
  return paise
}

export function sumPaise(values) {
  let total = 0n
  for (const value of values) total += BigInt(toPaise(value))
  return total
}

// 5000000n -> '₹50,000.00' (Indian digit grouping, matching formatDate's en-IN default).
export function formatPaise(paise) {
  const total = BigInt(paise)
  const sign = total < 0n ? '-' : ''
  const abs = total < 0n ? -total : total
  const rupees = new Intl.NumberFormat('en-IN').format(abs / 100n)
  return `${sign}₹${rupees}.${String(abs % 100n).padStart(2, '0')}`
}

export function formatCurrency(amount) {
  return formatPaise(toPaise(amount))
}