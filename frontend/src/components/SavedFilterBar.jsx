import { isBackwardsRange } from '../saved/logic'

// Search by name or number, and by date range (SRS 10.4). The Saved Ledgers screen does the
// filtering; this only holds the three inputs.
export default function SavedFilterBar({ filters, onChange, onClear, active }) {
  const set = (field) => (event) => onChange({ ...filters, [field]: event.target.value })

  return (
    <form
      className="filter-bar"
      role="search"
      aria-label="Find a saved ledger"
      onSubmit={(event) => event.preventDefault()}
    >
      <div className="filter-field">
        <label htmlFor="saved-filter-text">Name or number</label>
        <input
          id="saved-filter-text"
          type="search"
          value={filters.text}
          onChange={set('text')}
        />
      </div>
      <div className="filter-field">
        <label htmlFor="saved-filter-from">From</label>
        <input id="saved-filter-from" type="date" value={filters.from} onChange={set('from')} />
      </div>
      <div className="filter-field">
        <label htmlFor="saved-filter-to">To</label>
        <input id="saved-filter-to" type="date" value={filters.to} onChange={set('to')} />
      </div>
      <button type="button" onClick={onClear} disabled={!active}>
        Clear filters
      </button>
      {isBackwardsRange(filters) && (
        <p role="alert" className="field-error">
          The From date is after the To date, so nothing can match.
        </p>
      )}
    </form>
  )
}
