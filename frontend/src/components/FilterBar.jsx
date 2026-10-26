export default function FilterBar({ filters, onChange, onClear, active }) {
  const rangeBackwards = Boolean(filters.from && filters.to && filters.from > filters.to)
  const set = (field) => (event) => onChange({ ...filters, [field]: event.target.value })

  return (
    <form
      className="filter-bar"
      role="search"
      aria-label="Filter transactions"
      onSubmit={(event) => event.preventDefault()}
    >
      <div className="filter-field">
        <label htmlFor="filter-from">From</label>
        <input id="filter-from" type="date" value={filters.from} onChange={set('from')} />
      </div>
      <div className="filter-field">
        <label htmlFor="filter-to">To</label>
        <input id="filter-to" type="date" value={filters.to} onChange={set('to')} />
      </div>
      <div className="filter-field">
        <label htmlFor="filter-keyword">Reason contains</label>
        <input
          id="filter-keyword"
          type="search"
          value={filters.keyword}
          onChange={set('keyword')}
        />
      </div>
      <div className="filter-field">
        <label htmlFor="filter-type">Type</label>
        <select id="filter-type" value={filters.type} onChange={set('type')}>
          <option value="ALL">Debit and Credit</option>
          <option value="DEBIT">Debit only</option>
          <option value="CREDIT">Credit only</option>
        </select>
      </div>
      <button type="button" onClick={onClear} disabled={!active}>
        Clear filters
      </button>
      {rangeBackwards && (
        <p role="alert" className="field-error">
          The From date is after the To date, so nothing can match.
        </p>
      )}
    </form>
  )
}
