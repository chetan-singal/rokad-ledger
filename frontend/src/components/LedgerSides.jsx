import { useMemo, useState } from 'react'
import { paginate, sortChronologically, splitBySide } from '../transactions/logic'
import TransactionTable from './TransactionTable'

// The standard two-sided view with nothing editable: Debit on the left, Credit on the right,
// two independent lists with their own paging and total (SRS 5.1 to 5.4). `totals` are the
// backend's own figures, already formatted. Verify & Close uses it now; Saved Ledgers will in
// Phase 13.
export default function LedgerSides({ transactions, totals }) {
  const [pages, setPages] = useState({ debit: 1, credit: 1 })
  const sides = useMemo(() => splitBySide(sortChronologically(transactions)), [transactions])

  return (
    <div className="ledger-sides">
      <TransactionTable
        id="debit"
        title="Debit"
        readOnly
        page={paginate(sides.debit, pages.debit)}
        total={totals.debit}
        emptyMessage="No debit entries."
        onPageChange={(page) => setPages((prev) => ({ ...prev, debit: page }))}
      />
      <TransactionTable
        id="credit"
        title="Credit"
        readOnly
        page={paginate(sides.credit, pages.credit)}
        total={totals.credit}
        emptyMessage="No credit entries."
        onPageChange={(page) => setPages((prev) => ({ ...prev, credit: page }))}
      />
    </div>
  )
}
