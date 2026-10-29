import OverviewFrame from '../components/OverviewFrame'
import { formatDate } from '../format'
import { dashboardCards, ledgerLabel, statusLabel } from '../overview/logic'

export default function Dashboard() {
  return (
    <div className="dashboard">
      <OverviewFrame>{(overview) => <DashboardBody overview={overview} />}</OverviewFrame>
    </div>
  )
}

function DashboardBody({ overview }) {
  const { summary } = overview

  return (
    <>
      <p className="ledger-line">
        <strong>{ledgerLabel(summary)}</strong> · {statusLabel(summary.status)} · started{' '}
        {formatDate(summary.startDate)}
      </p>

      <dl className="stat-cards">
        {dashboardCards(overview).map((card) => (
          <div key={card.key} className="stat-card">
            <dt>{card.label}</dt>
            <dd>{card.value}</dd>
          </div>
        ))}
      </dl>

      <p className="dashboard-note">
        Today is {formatDate(overview.today)}. Today&apos;s Debit and Credit count only entries
        dated today.
      </p>
    </>
  )
}