import { NavLink } from 'react-router'
import { NAV_ITEMS } from '../navigation'
import { useExit } from '../state/ExitContext'

export default function NavMenu() {
  const { requestExit } = useExit()

  return (
    <nav aria-label="Main">
      <ul className="nav-menu">
        {NAV_ITEMS.map((item) => (
          <li key={item.key}>
            {item.path ? (
              <NavLink to={item.path} end={item.path === '/'}>
                {item.label}
              </NavLink>
            ) : (
              <button type="button" onClick={requestExit}>
                {item.label}
              </button>
            )}
          </li>
        ))}
      </ul>
    </nav>
  )
}