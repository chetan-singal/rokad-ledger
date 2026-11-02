# CLAUDE.md — ROKAD Daily Ledger

Claude: read this file first in every ROKAD chat. It holds the working agreement, the project facts, the rules that must not be broken, the current status and the open decisions. Last updated: 2026-10-30 (Phases 1–11 done, Phase 12 next).

---

## 1. How to work with Chetan

- **One step at a time.** Give one step, wait for him to paste the output (or a screenshot), verify it, then give the next step.
- **No "why" explanations.** Give the implementation: the exact code, the exact command, the expected result. Explain a reason only when he asks.
- **Complete file paths every time**, for example `C:\Users\Chetan\Desktop\rokad-ledger\frontend\src\overview\logic.js`. Windows 11, PowerShell. Use `\` in paths and PowerShell syntax in commands.
- **After each step, state the expected result** in one line so he can self-check.
- **Do not start the next step or phase until he says so** when he has asked for that.
- **Keep replies short.** At most one question per reply.
- **When he shares a zip:** unzip it, diff it against the previous version, compare with the plan document, run what can be run (section 9), and report briefly.
- **Be straight about mistakes.** If an earlier claim turns out wrong, say so (example: the date-range endpoint worry was wrong, see section 7).
- Copy code from verified files; never retype a long block from memory.

---

## 2. The project

ROKAD is a simple, **offline, single-user desktop ledger**. One active ledger at a time. Entries are Debit or Credit with a date, an amount and a reason. The user verifies and closes a ledger; closing saves it as a read-only record and automatically opens the next ledger, carrying the closing balance forward as its opening balance. No login, no cloud, no multi-user.

Documents (in project docs): `SRS.docx` (requirements, numbered FR/BR/NFR/TC), `roadmap.docx` (14 phases), per-phase progress reports and per-phase Execution Plan documents (Phase 9, 10, 11 exist).

---

## 3. Setup and commands

| Item | Value |
| --- | --- |
| Local folder | `C:\Users\Chetan\Desktop\rokad-ledger` (`backend`, `frontend`) |
| Repo | `https://github.com/chetan-singal/rokad-ledger`, branch `main` |
| Backend | Spring Boot 3.3, Java 17+, Maven 3.10, port 8080 |
| Frontend | Electron 31, React 18.3.1, Vite 5.4, react-router 7 (HashRouter), Vitest 2.1.9, port 5173 |
| Database | MySQL, `rokad_db` (app), `rokad_db_test` (tests), Flyway migrations V1, V2 |
| DB login | env vars `DB_USERNAME` (default `root`) and `DB_PASSWORD` (default empty) |

**Run the app** (two PowerShell windows, MySQL running):

```powershell
cd C:\Users\Chetan\Desktop\rokad-ledger\backend
mvn spring-boot:run
```

```powershell
cd C:\Users\Chetan\Desktop\rokad-ledger\frontend
npm run electron:dev
```

**Quality gates** (run after every step that changes code):

```powershell
cd C:\Users\Chetan\Desktop\rokad-ledger\backend
mvn spotless:apply
mvn test | Select-Object -Last 15

cd C:\Users\Chetan\Desktop\rokad-ledger\frontend
npm test
npm run lint
npm run build
```

Current expected results: backend **41 tests**, frontend **59 tests**, lint clean, build clean (CSS 7.27 kB, JS about 212 kB).

**Commit format:** `git add <paths>`, `git commit -m "Phase N (Step X): ..."`, `git push origin main`, then `git --no-pager log --oneline -3`. Last commit: `b5919cd` (Phase 11 Step E).

**Gotchas**
- Run Maven only in a terminal where `mvn -v` shows Java 17 or newer. One terminal showed Java 1.8 and Maven failed with a plugin error there.
- If MySQL 8 refuses the connection with a public-key or caching_sha2_password error, add `?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC` to the datasource URL.
- The Console error "A listener indicated an asynchronous response by returning true…" comes from a browser extension, not ROKAD. An Incognito window shows none.
- Seed data used for testing is deleted afterwards (`reason LIKE 'Seed entry %'`).

---

## 4. Architecture

### Backend (`backend/src/main/java/com/rokad`)

Packages: `config` (ClockConfig, CorsConfig), `controller`, `dto`, `entity`, `repository`, `service/{audit,calculation,closure,lifecycle,overview,transaction}`.

- **FinancialCalculationService** is the single place that does money arithmetic: `Balance = Opening + Credit − Debit`, running balances, totals. Every other service calls it.
- **Tables:** `ledger`, `ledger_transaction`, `ledger_audit`. Money is `DECIMAL(15,2)`. The database allows only one `ACTIVE` ledger (`uq_ledger_one_active`).
- **Closing** (`VerificationClosureService.closeLedger`) runs in one database transaction: recalculate from latest data, mark the ledger CLOSED, create the next ACTIVE ledger (opening = closing, start = end + 1 day). `verifyPeriod` writes one audit row per call.
- **Future-dated entries are rejected.** Entry dates must be on or after the ledger start date and not after today.
- Duplicate-save guard: an identical entry created within the last 2 seconds is rejected (`field: "duplicate"`).

### API

| Method and path | Purpose |
| --- | --- |
| `GET /api/health` | health check |
| `POST /api/ledger/setup` | create the first ledger (name optional, start date, opening balance ≥ 0) |
| `GET /api/ledger/active` | `LedgerSummaryDTO` of the active ledger |
| `GET /api/ledger/active/overview` | `LedgerOverviewDTO` for Home and Dashboard |
| `GET /api/transactions` | active ledger entries, oldest first; optional `startDate`, `endDate` (ISO dates, verified working) |
| `POST /api/transactions`, `PUT /api/transactions/{id}`, `DELETE /api/transactions/{id}` | add, edit, delete |
| `POST /api/ledger/verify` | body `{ledgerId, periodStart, periodEnd}` → `VerificationSummary` |
| `POST /api/ledger/close` | same body → summary of the new active ledger |
| `GET /api/ledgers` | closed ledgers, most recently closed first |
| `GET /api/ledgers/{id}` | `SavedLedgerDetailDTO {summary, transactions}` |

Shapes:
- `LedgerSummaryDTO {ledgerId, ledgerName, startDate, status, openingBalance, currentBalance, totalDebit, totalCredit, transactionCount}` (no `endDate` or `closedAt` yet)
- `LedgerOverviewDTO {summary, today, todayDebit, todayCredit, recentTransactions}` (5 newest entries, date descending then id descending)
- `TransactionDTO {transactionId, ledgerId, transactionDate, transactionType, amount, reason, createdAt, updatedAt}` (`createdAt` and `updatedAt` can be null on add and edit responses)
- `VerificationSummary {ledgerId, periodStart, periodEnd, openingBalance, totalDebit, totalCredit, closingBalance, debitCount, creditCount}`
- Errors: `{error, field, message}` with `VALIDATION_ERROR` (400), `NOT_FOUND` (404), `CONFLICT` (409), `INTERNAL_ERROR` (500, generic message). CORS allows `http://localhost:5173`, methods GET/POST/PUT/DELETE, header `Content-Type` only.

### Frontend (`frontend/src`)

- `api/client.js` — `request()`, `ApiError`, `getActiveLedger`, `setupLedger`, `listTransactions`, `addTransaction`, `editTransaction`, `deleteTransaction`, `getOverview`. (Verify, close and saved-ledger calls do not exist yet.)
- `money.js` — all money handling in **integer paise using BigInt**: `toPaise`, `sumPaise`, `formatPaise`, `formatCurrency` (₹, Indian digit grouping). The browser never adds or subtracts money as floats.
- `format.js` — `formatDate` ("22 Oct 2026"), `todayIso`.
- `transactions/logic.js` + `useTransactions.js` — validation (amount pattern `^\d{1,13}(\.\d{1,2})?$`, reason ≤ 500 chars), filters, paging (50 per side), `formatTotal`, `describeTransaction`.
- `overview/logic.js` + `useOverview.js` — Home and Dashboard figures; the hook reloads when the window regains focus.
- `state/` — `LedgerContext` (`useLedger`: ledger, refresh, status, error), `UnsavedChangesContext` (`setDirty(key, dirty, save)`, `saveAll`), `ExitContext` (exit flow and `guardNavigation`).
- `components/` — `AppShell`, `NavMenu`, `GuardedLink`, `LedgerGate`, `LedgerStatusIndicator`, `LedgerSummaryStrip`, `TransactionForm`, `TransactionTable`, `FilterBar`, `Field`, `ConfirmDialog`, `ExitDialog`, `OverviewFrame`, `PlaceholderScreen`.
- `screens/` — `Home`, `Dashboard`, `CurrentLedger`, `SetupScreen` are real; `VerifyCloseLedger`, `SavedLedgers`, `Settings` are placeholders.
- Navigation order (fixed): Home, Dashboard, Current Ledger, Verify & Close Ledger, Saved Ledgers, Settings, Exit. No Transactions or New Ledger item.
- In-app links that leave a screen with unsaved input must use `GuardedLink` or `useGuardedNavigation`.

---

## 5. Rules that must not be broken

1. Debit is always the **left** table and Credit the **right**, as two genuinely independent lists with explicit headings. Never one combined or colour-coded table. Debit stays first when the layout stacks (container query at 760 px).
2. Credit increases the balance, Debit decreases it. One calculation service; screens only display what the backend returns.
3. Only one active ledger. A closed ledger is read-only and cannot be reopened in v1.
4. Closing needs explicit confirmation and is atomic. A failed closure must never leave a half-closed ledger.
5. The new ledger starts the day after the previous end date, with the previous closing balance as its opening balance, and no entries copied over.
6. Filters never change stored balances. A filtered view must say it is filtered.
7. Delete needs confirmation. Edit needs confirmation when the amount, type or date changes.
8. Everything works fully offline on one machine.

---

## 6. Status (2026-10-30)

| Phase | Name | Status |
| --- | --- | --- |
| 1–8 | Setup, database, domain, calculation, lifecycle, transactions, closure service, API | Done |
| 9 | UI Shell & Navigation | Done |
| 10 | Current Ledger Screen | Done |
| 11 | Home & Dashboard (overview endpoint, hook, logic, screens, styles; 20-row manual checklist passed) | Done |
| 12 | Verify & Close Ledger Screen | **Next**, design first |
| 13 | Saved Ledgers & Settings (backup and restore) | Not started |
| 14 | Testing, hardening, packaging, user docs | Not started |

Test counts: backend 41 (calculation 5, lifecycle 5, transactions 9, closure 8, repositories 5, overview 6, API smoke 1, CORS 2); frontend 59 (money 10, transaction logic 39, overview logic 10).

---

## 7. Phase 12 starting notes, decisions and open items

**Decision (2026-10-30):** design the Verify & Close Ledger page first, then change the backend closing rules to match the design. Do not change `closeLedger` before the design is agreed.

**Phase 12 requirements to cover** (SRS Section 8 and 9, roadmap Phase 12, TC-016, TC-027–TC-031):
- Period selection (start and end date); end ≥ start; end not in the future; period inclusive of both ends.
- Show ledger name, selected period, applicable opening balance, total debit, total credit, calculated closing balance, debit and credit counts, and the two-sided transaction display for the period.
- "Verify & Close Ledger" primary action and a clearly different "return without closing" action.
- Confirmation dialog listing period, opening balance, total credit, total debit, closing balance, that the ledger becomes read-only, and that a new ledger starts automatically.
- Closure blocked while a form is unsaved, when the period is invalid, or when anything fails; failures show a clear message and leave the active ledger untouched.
- Call `/api/ledger/verify` from a button, not on every date change (each call writes an audit row).

**Open item A — closing rule (proposed, not applied).** Today `closeLedger` accepts any valid period. Entries outside the period stay attached to the closed ledger but are left out of its stored totals and out of the balance carried forward, so money would vanish and Saved Ledgers would show rows that do not add up. Example with the current dev data: closing 27 Oct to 29 Oct would carry forward ₹70.00 instead of ₹9,470.00. Proposed rule in `closeLedger`: the period must start on the ledger's own start date and end on or after the date of the last entry; `verifyPeriod` stays flexible for reviewing sub-ranges. The page then locks the start date and sets the end date minimum (the newest entry date is `recentTransactions[0].transactionDate` in the overview). A ready-made version of `VerificationClosureService.java` with this rule was prepared and deliberately not applied.

**Open item B — ledger that starts tomorrow.** Closing with end date = today starts the next ledger tomorrow. Until then no entry can be added (the date picker's minimum is later than its maximum) and Current Ledger does not explain why. Decide the wording or behaviour in the Phase 12 design.

**Small Current Ledger items (not blocking):**
- Clicking "Add transaction" again while the Add form holds typed text asks to discard, but "Discard changes" leaves the typed values in place.
- At the default 1100 px window each side is about 400 px wide and the Reason column can break mid-word; at 1280 px it reads fine.

**Later phases:**
- Phase 13: `LedgerSummaryDTO` needs `endDate` and `closedAt` for Saved Ledgers; backup and restore design (local folder, manual export) is undecided.
- Phase 14: malformed requests (bad JSON, date or enum) return 500 through the catch-all handler; the server does not validate amount decimals or reason length (the UI does); the README still describes the Phase 1 skeleton; packaging needs a CORS origin for the packaged app, a way to start the backend, and `electron-builder` configuration.

**Settled, do not raise again:** `GET /api/transactions?startDate=…&endDate=…` accepts ISO dates (checked with `curl.exe`); no fix is needed.

---

## 8. Phase workflow convention

Each phase has an Execution Plan document with lettered steps (A, B, C…), a gate after each step (tests, lint, build), commit points, and a numbered manual checklist run in the real app. Chetan works through the steps with Claude one at a time. A step is done when its gate output is pasted and checked. Keep the same pattern for Phase 12 and later.

---

## 9. How Claude verifies uploaded code

The sandbox has no network, so Maven and `npm install` cannot run there. What works:
- `unzip`, `diff -rq` against the previous version, and reading the code.
- Node 22 with `tsx` and a small stand-in for Vitest's `describe/it/expect`, to run the pure-logic test files.
- `esbuild` to bundle screens, and Playwright with headless Chromium to drive the real screens against a mock backend that copies the API's rules (the Phase 10 and 11 checks were done this way). The sandbox has React 19, the app uses 18.3.1.
- State plainly what was only read and not run (the backend is never run in the sandbox).
