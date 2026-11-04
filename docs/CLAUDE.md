# CLAUDE.md — ROKAD Daily Ledger

Claude: read this file first in every ROKAD chat. It holds the working agreement, the project facts, the rules that must not be broken, the current status and the open decisions. Last updated: 2026-11-04 (Phases 1–12 done, Phase 13 next).

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

Documents (in project docs): `SRS.docx` (requirements, numbered FR/BR/NFR/TC), `roadmap.docx` (14 phases), per-phase progress reports and per-phase Execution Plan documents (Phase 9, 10, 11 and 12 exist).

---

## 3. Setup and commands

| Item | Value |
| --- | --- |
| Local folder | `C:\Users\Chetan\Desktop\rokad-ledger` (`backend`, `frontend`, `docs`) |
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

Current expected results: backend **60 tests**, frontend **79 tests**, lint clean, build clean (CSS 8.85 kB, JS about 221 kB).

**Commit format:** `git add <paths>`, `git commit -m "Phase N (Step X): ..."`, `git push origin main`, then `git --no-pager log --oneline -3`. Last code commit: `a29b81a` (Phase 12 Step E). The Phase 12 Step F commit (checklist passed, this file updated) follows it.

**Gotchas**
- Run Maven only in a terminal where `mvn -v` shows Java 17 or newer. A terminal that showed Java 1.8 made Maven fail with a plugin error twice; on 2026-11-04 `mvn spotless:apply` failed with `UnsupportedClassVersionError ... class file version 61.0`, which means the same thing. Open a new terminal and run it again.
- If MySQL 8 refuses the connection with a public-key or caching_sha2_password error, add `?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC` to the datasource URL.
- The Console error "A listener indicated an asynchronous response by returning true…" comes from a browser extension, not ROKAD. An Incognito window shows none.
- Seed data used for testing is deleted afterwards (`reason LIKE 'Seed entry %'`).
- The development database holds the Phase 12 checklist closures and entries. Checklist rows 15, 16 and 18 used a development-only SQL statement (`UPDATE ledger SET start_date = CURDATE() WHERE status = 'ACTIVE'`) that moved some ledgers' start dates back by one day, so those ledgers start on the day the previous one ended. Restore `rokad_db_before_phase12.sql` (on the Desktop) when a clean ledger chain is needed, for example for the Phase 13 chain check.

---

## 4. Architecture

### Backend (`backend/src/main/java/com/rokad`)

Packages: `config` (ClockConfig, CorsConfig), `controller`, `dto`, `entity`, `repository`, `service/{audit,calculation,closure,lifecycle,overview,transaction}`.

- **FinancialCalculationService** is the single place that does money arithmetic: `Balance = Opening + Credit − Debit`, running balances, totals. Every other service calls it.
- **Tables:** `ledger`, `ledger_transaction`, `ledger_audit`. Money is `DECIMAL(15,2)`. The database allows only one `ACTIVE` ledger (`uq_ledger_one_active`) and `opening_balance >= 0` (`chk_ledger_opening_balance_nonneg`).
- **Closing** (`VerificationClosureService.closeLedger(ledgerId, periodStart, periodEnd, verified)`) runs in one database transaction: recalculate from latest data, check the closing rules, mark the ledger CLOSED, create the next ACTIVE ledger (opening = closing, start = end + 1 day). `verifyPeriod` stays flexible (any valid sub-range for review), writes one audit row per call and now returns `nextLedgerStartDate`.
- **Closing rules (Phase 12):** the period must start on the ledger's own start date; the end date must not be before the latest entry and not after today; the end date may be today, in which case the next ledger starts tomorrow; `verified` (the summary the user confirmed) must match the figures recomputed from the latest data, otherwise `StaleVerificationException` (409 `CONFLICT`); the closing balance must not be negative. `closeLedger` without `verified` is refused.
- **Balance rule (Phase 12):** an add, edit or delete that would take the ledger's balance below zero is refused with 400 `VALIDATION_ERROR`, `field: "balance"`, and nothing is saved. It is checked on the final balance, not day by day.
- **A ledger can start tomorrow.** After closing on today, no entry can be added until the new ledger's start date. Current Ledger shows a notice and disables Add; Verify & Close says "Nothing to close yet".
- **Future-dated entries are rejected.** Entry dates must be on or after the ledger start date and not after today.
- Duplicate-save guard: an identical entry created within the last 2 seconds is rejected (`field: "duplicate"`).
- Audit details abbreviate the reason (200 characters) so they fit the 1000-character column.

### API

| Method and path | Purpose |
| --- | --- |
| `GET /api/health` | health check |
| `POST /api/ledger/setup` | create the first ledger (name optional, start date, opening balance ≥ 0) |
| `GET /api/ledger/active` | `LedgerSummaryDTO` of the active ledger |
| `GET /api/ledger/active/overview` | `LedgerOverviewDTO` for Home and Dashboard |
| `GET /api/ledger/active/closing-window` | the end dates the active ledger can be closed on (`ClosingWindowDTO`) |
| `GET /api/transactions` | active ledger entries, oldest first; optional `startDate`, `endDate` (ISO dates, verified working) |
| `POST /api/transactions`, `PUT /api/transactions/{id}`, `DELETE /api/transactions/{id}` | add, edit, delete |
| `POST /api/ledger/verify` | body `{ledgerId, periodStart, periodEnd}` → `VerificationSummary` |
| `POST /api/ledger/close` | body `CloseRequest {ledgerId, periodStart, periodEnd, verified}` → summary of the new active ledger |
| `GET /api/ledgers` | closed ledgers, most recently closed first |
| `GET /api/ledgers/{id}` | `SavedLedgerDetailDTO {summary, transactions}` |

Shapes:
- `LedgerSummaryDTO {ledgerId, ledgerName, startDate, status, openingBalance, currentBalance, totalDebit, totalCredit, transactionCount}` (no `endDate` or `closedAt` yet)
- `LedgerOverviewDTO {summary, today, todayDebit, todayCredit, recentTransactions}` (5 newest entries, date descending then id descending)
- `TransactionDTO {transactionId, ledgerId, transactionDate, transactionType, amount, reason, createdAt, updatedAt}` (`createdAt` and `updatedAt` can be null on add and edit responses)
- `VerificationSummary {ledgerId, periodStart, periodEnd, openingBalance, totalDebit, totalCredit, closingBalance, debitCount, creditCount, nextLedgerStartDate}`
- `ClosingWindowDTO {ledgerId, startDate, lastEntryDate, earliestEndDate, latestEndDate, closable}` (`lastEntryDate` is null while the ledger has no entries; `earliestEndDate` is the latest entry's date, or the start date when empty; `latestEndDate` is today; `closable` is false only when the ledger starts after today)
- `CloseRequest {ledgerId, periodStart, periodEnd, verified}` (`verified` is the `VerificationSummary` the user confirmed)
- Errors: `{error, field, message}` with `VALIDATION_ERROR` (400), `NOT_FOUND` (404), `CONFLICT` (409, also for a stale verification), `INTERNAL_ERROR` (500, generic message). CORS allows `http://localhost:5173`, methods GET/POST/PUT/DELETE, header `Content-Type` only.

### Frontend (`frontend/src`)

- `api/client.js` — `request()`, `ApiError`, `getActiveLedger`, `setupLedger`, `listTransactions`, `addTransaction`, `editTransaction`, `deleteTransaction`, `getOverview`, `getClosingWindow`, `verifyPeriod`, `closeLedger` (10 of the 12 endpoints; the two saved-ledger calls do not exist yet).
- `money.js` — all money handling in **integer paise using BigInt**: `toPaise`, `sumPaise`, `formatPaise`, `formatCurrency` (₹, Indian digit grouping). The browser never adds or subtracts money as floats.
- `format.js` — `formatDate` ("22 Oct 2026"), `todayIso`. `useToday.js` — today's local date as an ISO string; it updates when the window regains focus.
- `transactions/logic.js` + `useTransactions.js` — validation (amount pattern `^\d{1,13}(\.\d{1,2})?$`, reason ≤ 500 chars), filters, paging (50 per side), `formatTotal`, `describeTransaction`.
- `overview/logic.js` + `useOverview.js` — Home and Dashboard figures; the hook reloads when the window regains focus.
- `verify/logic.js` + `verify/useClosingWindow.js` — end-date checks, period request, wording for the next-ledger and not-started sentences, close-error messages; the hook loads the closing window and reloads on focus.
- `state/` — `LedgerContext` (`useLedger`: ledger, refresh, status, error; it loads at start and after actions, not on window focus), `UnsavedChangesContext` (`setDirty(key, dirty, save)`, `isDirty`, `saveAll`), `ExitContext` (exit flow and `guardNavigation`).
- `components/` — `AppShell`, `NavMenu`, `GuardedLink`, `LedgerGate`, `LedgerStatusIndicator`, `LedgerSummaryStrip`, `TransactionForm`, `TransactionTable` (takes `readOnly`), `LedgerSides` (the two independent side-by-side lists for a period, read only; reuse it for Saved Ledgers), `FilterBar`, `Field`, `ConfirmDialog`, `ExitDialog`, `OverviewFrame`, `PlaceholderScreen`.
- `screens/` — `Home`, `Dashboard`, `CurrentLedger`, `SetupScreen` and `VerifyCloseLedger` are real; `SavedLedgers` and `Settings` are placeholders.
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
9. A ledger is closed as a whole: the start date is the ledger's own, only the end date is chosen, not before the latest entry and not after today.
10. Closing needs the confirmed figures to match: the server recomputes them and refuses if the ledger changed after verification.
11. The balance never goes below zero: an add, edit or delete that would do so is refused and nothing is saved.

---

## 6. Status (2026-11-04)

| Phase | Name | Status |
| --- | --- | --- |
| 1–8 | Setup, database, domain, calculation, lifecycle, transactions, closure service, API | Done |
| 9 | UI Shell & Navigation | Done |
| 10 | Current Ledger Screen | Done |
| 11 | Home & Dashboard | Done |
| 12 | Verify & Close Ledger Screen (closing rules, closing window, confirmed figures, balance rule, the screen, layout fixes; 30-row manual checklist passed) | Done |
| 13 | Saved Ledgers & Settings (backup and restore) | **Next** |
| 14 | Testing, hardening, packaging, user docs | Not started |

Test counts: backend 60 (calculation 5, lifecycle 5, transactions 9, balance guard 5, closure 8, closure rules 13, closure atomicity 1, repositories 5, overview 6, API smoke 1, CORS 2); frontend 79 (money 10, transaction logic 39, overview logic 10, verify logic 20).

---

## 7. Open items

**Phase 13**
- `LedgerSummaryDTO` needs `endDate` and `closedAt` (SRS 10.1). The Saved Ledgers list must show entry counts without loading every closed ledger's entries.
- Backup and restore design (local folder, manual export), first-launch restore after a reinstall, and a chain check after restore: each opening equals the previous closing, dates are contiguous, exactly one ledger is active.
- Reuse `LedgerSides` for the saved ledger detail.

**Phase 14**
- Packaging is the largest schedule risk: Electron, the Java backend and MySQL must start in order, on a free port, and stop together; how MySQL reaches the user's machine; single-instance lock; CORS origin of the packaged app; `electron-builder` configuration. Start a spike before Phase 13.
- The backend listens on all network interfaces and has no API secret: bind to 127.0.0.1 and require a per-launch secret from Electron.
- Malformed requests (bad JSON, date or enum) answer 500 through the catch-all handler. The server does not check amount decimals, reason length or ledger-name length. The setup screen has no limit on the ledger name either, so a name over 255 characters would fail at the database as a 500.
- The closing check compares only totals, closing balance and the two counts. It does not compare the period dates, opening balance or next start date; the screen always sends the verified summary's own dates, so only a direct API call could differ.
- `spring.jpa.show-sql=true`, the README still describes the Phase 1 skeleton, the 10,000-entry performance check (NFR-002), automated browser tests of the layout rules, CI, a seed-data generator, a logging plan.

**Documents (no code)**
- SRS and roadmap wording: numbering of the two joined documents, traceability rows for Home, Dashboard and filters, "Phase 0" for the planning stage, contradictions (§4.3 "right table", future dates "default", currency in setup). Section 10 of the Phase 12 plan lists the Phase 12 amendments.

**Settled, do not raise again:** `GET /api/transactions?startDate=…&endDate=…` accepts ISO dates (checked with `curl.exe`); no fix is needed.

---

## 8. Phase workflow convention

Each phase has an Execution Plan document with lettered steps (A, B, C…), a gate after each step (tests, lint, build), commit points, and a numbered manual checklist run in the real app. Chetan works through the steps with Claude one at a time. A step is done when its gate output is pasted and checked. Keep the same pattern for Phase 13 and later.

---

## 9. How Claude verifies uploaded code

The sandbox has no network, so Maven and `npm install` cannot run there. What works:
- `unzip`, `diff -rq` against the previous version, and reading the code.
- Node 22 with `tsx` and a small stand-in for Vitest's `describe/it/expect`, to run the pure-logic test files.
- `esbuild` to bundle screens, and Playwright with headless Chromium to drive the real screens against a mock backend that copies the API's rules (the Phase 10 and 11 checks were done this way). The sandbox has React 19, the app uses 18.3.1.
- State plainly what was only read and not run (the backend is never run in the sandbox).
