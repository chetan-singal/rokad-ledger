import GuardedLink from '../components/GuardedLink'
import OverviewFrame from '../components/OverviewFrame'
import { formatDate } from '../format'
import { formatCurrency } from '../money'
import { activityRow, hasNoTransactions, ledgerLabel, statusLabel } from '../overview/logic'

export default function Home() {
  return (
    <div className="home">
      <OverviewFrame>{(overview) => <HomeBody overview={overview} />}</OverviewFrame>
    </div>
  )
}

function HomeBody({ overview }) {
  const { summary, recentTransactions } = overview
  const facts = [
    ['Start date', formatDate(summary.startDate)],
    ['Total debit', formatCurrency(summary.totalDebit)],
    ['Total credit', formatCurrency(summary.totalCredit)],
    ['Transactions', String(summary.transactionCount)],
    ['Debit today', formatCurrency(overview.todayDebit)],
    ['Credit today', formatCurrency(overview.todayCredit)]
  ]

  return (
    <>
      <h2>ROKAD — Daily Ledger</h2>
      <p className="ledger-line">
        <strong>{ledgerLabel(summary)}</strong> · {statusLabel(summary.status)} ledger
      </p>

      <div className="balance-card">
        <p className="balance-label">Current balance</p>
        <p className="balance-value">{formatCurrency(summary.currentBalance)}</p>
        <p className="balance-sub">Opening balance {formatCurrency(summary.openingBalance)}</p>
      </div>

      <dl className="summary-strip">
        {facts.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>

      <nav className="toolbar" aria-label="Shortcuts">
        <GuardedLink to="/current-ledger" className="button-link">
          Current Ledger
        </GuardedLink>
        <GuardedLink to="/verify-close" className="button-link">
          Verify &amp; Close Ledger
        </GuardedLink>
        <GuardedLink to="/saved-ledgers" className="button-link">
          Saved Ledgers
        </GuardedLink>
      </nav>

      {hasNoTransactions(summary) ? (
        <div className="empty-state">
          <h3>No transactions yet</h3>
          <p>
            The balance is the opening balance of {formatCurrency(summary.openingBalance)}. Record
            your first debit or credit in the Current Ledger.
          </p>
          <GuardedLink to="/current-ledger" className="button-link">
            Record your first transaction
          </GuardedLink>
        </div>
      ) : (
        <section className="activity" aria-labelledby="activity-heading">
          <h3 id="activity-heading">Recent activity</h3>
          <ul className="activity-list">
            {recentTransactions.map((transaction) => {
              const row = activityRow(transaction)
              return (
                <li key={row.id}>
                  <span className="activity-date">{row.date}</span>
                  <span className="activity-side">{row.side}</span>
                  <span className="activity-reason">{row.reason}</span>
                  <span className="activity-amount">{row.amount}</span>
                </li>
              )
            })}
          </ul>
          <p className="activity-more">
            <GuardedLink to="/current-ledger">See every entry in the Current Ledger</GuardedLink>
          </p>
        </section>
      )}
    </>
  )
}