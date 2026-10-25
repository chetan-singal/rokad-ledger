import { useCallback } from 'react'
import { matchPath, useLocation, useNavigate } from 'react-router'
import { useExit } from './state/ExitContext'
// SRS 3.1: this exact order. No Transactions or New Ledger entry exists, on purpose.
// Exit has no path -- it is an action, not a screen.
export const NAV_ITEMS = [
  {
    key: 'home',
    label: 'Home',
    path: '/',
    phase: 11,
    summary: 'Starting screen: current ledger status, current balance, recent activity, shortcuts.'
  },
  {
    key: 'dashboard',
    label: 'Dashboard',
    path: '/dashboard',
    phase: 11,
    summary: "Summary of the active ledger's financial position and activity."
  },
  {
    key: 'current-ledger',
    label: 'Current Ledger',
    path: '/current-ledger',
    phase: 10,
    summary: 'Main transaction interface: Debit table left, Credit table right, add entries.'
  },
  {
    key: 'verify-close',
    label: 'Verify & Close Ledger',
    path: '/verify-close',
    phase: 12,
    summary: 'Review a period, verify entries, confirm totals, close the ledger.'
  },
  {
    key: 'saved-ledgers',
    label: 'Saved Ledgers',
    path: '/saved-ledgers',
    phase: 13,
    summary: 'Historical, already-closed ledgers, read-only.'
  },
  {
    key: 'settings',
    label: 'Settings',
    path: '/settings',
    phase: 13,
    summary: 'Currency display, date format, backup.'
  },
  { key: 'exit', label: 'Exit' }
]

export function useCurrentNavItem() {
  const { pathname } = useLocation()
  return (
    NAV_ITEMS.find(
      (item) => item.path && matchPath({ path: item.path, end: item.path === '/' }, pathname)
    ) ?? null
  )
}

// Click-handler factory for in-app links: with unsaved form input it asks before leaving.
// (HashRouter has no useBlocker, so the guard sits on the links themselves.)
export function useGuardedNavigation() {
  const { guardNavigation } = useExit()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return useCallback(
    (to) => (event) => {
      if (pathname === to) return
      event.preventDefault()
      guardNavigation(() => navigate(to))
    },
    [guardNavigation, navigate, pathname]
  )
}