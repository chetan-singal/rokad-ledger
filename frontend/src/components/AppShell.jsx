import { Outlet } from 'react-router'
import { useCurrentNavItem } from '../navigation'
import LedgerStatusIndicator from './LedgerStatusIndicator'
import NavMenu from './NavMenu'

export default function AppShell() {
  const current = useCurrentNavItem()

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-brand">
          ROKAD <span>Daily Ledger</span>
        </div>
        <NavMenu />
      </aside>
      <header className="app-header">
        <h1>{current?.label ?? 'ROKAD'}</h1>
        <LedgerStatusIndicator />
      </header>
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  )
}