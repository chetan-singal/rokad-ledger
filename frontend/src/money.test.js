import { describe, expect, it } from 'vitest'
import { formatCurrency, formatPaise, sumPaise, toPaise } from './money'

describe('toPaise', () => {
  it('converts amounts with two decimals exactly, despite binary floating point', () => {
    expect(toPaise(19.99)).toBe(1999)
    expect(toPaise(0.29)).toBe(29) // 0.29 * 100 is 28.999999999999996 in a double
    expect(toPaise(1.15)).toBe(115)
    expect(toPaise(600)).toBe(60000)
  })

  it('accepts the string the user typed', () => {
    expect(toPaise('600.5')).toBe(60050)
    expect(toPaise('0.05')).toBe(5)
  })

  it('refuses something that is not a number', () => {
    expect(() => toPaise('abc')).toThrow(TypeError)
  })
})

describe('sumPaise', () => {
  it('does not drift the way floating-point addition does', () => {
    expect(0.1 + 0.2).not.toBe(0.3)
    expect(sumPaise([0.1, 0.2])).toBe(30n)
  })

  it('rounds each amount to the nearest paisa, not down, before adding', () => {
    // 0.29 * 100 and 1.15 * 100 both land just below the true value in a double
    expect(sumPaise([0.29, 1.15])).toBe(144n)
  })

  it('stays exact over a 10,000-transaction ledger', () => {
    expect(sumPaise(new Array(10000).fill(1234.56))).toBe(1234560000n)
  })

  it('sums nothing to zero', () => {
    expect(sumPaise([])).toBe(0n)
  })
})

describe('formatPaise / formatCurrency', () => {
  it('formats rupees with two decimals and Indian grouping', () => {
    expect(formatPaise(5000000n)).toBe('₹50,000.00')
    expect(formatPaise(123456789n)).toBe('₹12,34,567.89')
    expect(formatPaise(0n)).toBe('₹0.00')
  })

  it('pads paise and handles negatives and plain numbers', () => {
    expect(formatPaise(5)).toBe('₹0.05')
    expect(formatPaise(-5n)).toBe('-₹0.05')
    expect(formatPaise(-123450n)).toBe('-₹1,234.50')
  })

  it('formats a backend amount directly', () => {
    expect(formatCurrency(1500.5)).toBe('₹1,500.50')
    expect(formatCurrency('600')).toBe('₹600.00')
  })
})