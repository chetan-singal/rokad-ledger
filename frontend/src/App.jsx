import { HashRouter, Navigate, Route, Routes } from 'react-router'
import AppShell from './components/AppShell'
import LedgerGate from './components/LedgerGate'
import CurrentLedger from './screens/CurrentLedger'
import Dashboard from './screens/Dashboard'
import Home from './screens/Home'
import SavedLedgerDetail from './screens/SavedLedgerDetail'
import SavedLedgers from './screens/SavedLedgers'
import Settings from './screens/Settings'
import VerifyCloseLedger from './screens/VerifyCloseLedger'
import { ExitProvider } from './state/ExitContext'
import { LedgerProvider } from './state/LedgerContext'
import { SettingsProvider, SettingsRedraw } from './state/SettingsContext'
import { UnsavedChangesProvider } from './state/UnsavedChangesContext'

// HashRouter, not BrowserRouter: the packaged app loads index.html from a file:// URL,
// where path-based routes can't resolve. '#/current-ledger' works in both modes.
function App() {
  return (
    <HashRouter>
      <SettingsProvider>
        <UnsavedChangesProvider>
          <ExitProvider>
            <LedgerProvider>
              <LedgerGate>
                <SettingsRedraw>
                  <Routes>
                    <Route element={<AppShell />}>
                      <Route index element={<Home />} />
                      <Route path="dashboard" element={<Dashboard />} />
                      <Route path="current-ledger" element={<CurrentLedger />} />
                      <Route path="verify-close" element={<VerifyCloseLedger />} />
                      <Route path="saved-ledgers" element={<SavedLedgers />} />
                      <Route path="saved-ledgers/:ledgerId" element={<SavedLedgerDetail />} />
                      <Route path="settings" element={<Settings />} />
                      <Route path="*" element={<Navigate to="/" replace />} />
                    </Route>
                  </Routes>
                </SettingsRedraw>
              </LedgerGate>
            </LedgerProvider>
          </ExitProvider>
        </UnsavedChangesProvider>
      </SettingsProvider>
    </HashRouter>
  )
}

export default App