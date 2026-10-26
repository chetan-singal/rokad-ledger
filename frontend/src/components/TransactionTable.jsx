import { formatDate } from '../format'
import { formatCurrency } from '../money'
import { describeTransaction } from '../transactions/logic'

// One side of the ledger. The Debit and Credit tables are two separate instances of this:
// they never share rows, and neither knows the other exists (SRS 5.3).
export default function TransactionTable({
  id,
  title,
  page,
  total,
  emptyMessage,
  onPageChange,
  onEdit,
  onDelete,
  headingRef
}) {
  const headingId = `${id}-heading`

  return (
    <section className="ledger-side" aria-labelledby={headingId}>
      <h2 id={headingId} ref={headingRef} tabIndex={-1}>
        {title}
      </h2>
      <div className="table-scroll">
        <table>
          <caption className="visually-hidden">{title} entries, oldest first</caption>
          <thead>
            <tr>
              <th scope="col">Date</th>
              <th scope="col">Reason</th>
              <th scope="col" className="amount">
                Amount
              </th>
              <th scope="col">
                <span className="visually-hidden">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {page.items.length === 0 ? (
              <tr>
                <td colSpan={4} className="empty-row">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              page.items.map((t) => (
                <tr key={t.transactionId}>
                  <td>{formatDate(t.transactionDate)}</td>
                  <td className="reason">{t.reason}</td>
                  <td className="amount">{formatCurrency(t.amount)}</td>
                  <td className="row-actions">
                    <button
                      type="button"
                      onClick={() => onEdit(t)}
                      aria-label={`Edit ${describeTransaction(t)}`}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      onClick={() => onDelete(t)}
                      aria-label={`Delete ${describeTransaction(t)}`}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="side-footer">
        {page.total > 0 && (
          <nav className="pager" aria-label={`${title} pages`}>
            {page.pageCount > 1 && (
              <button
                type="button"
                disabled={page.page <= 1}
                onClick={() => onPageChange(page.page - 1)}
              >
                Previous
              </button>
            )}
            <span>
              Showing {page.first}–{page.last} of {page.total}
            </span>
            {page.pageCount > 1 && (
              <button
                type="button"
                disabled={page.page >= page.pageCount}
                onClick={() => onPageChange(page.page + 1)}
              >
                Next
              </button>
            )}
          </nav>
        )}
        <p className="side-total">
          <strong>Total {title}</strong> <span>{total}</span>
        </p>
      </div>
    </section>
  )
}
