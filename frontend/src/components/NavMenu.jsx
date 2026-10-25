import { NavLink } from 'react-router'
import { NAV_ITEMS, useGuardedNavigation } from '../navigation'
import { useExit } from '../state/ExitContext'

export default function NavMenu() {
  const { requestExit } = useExit()
  const guardClick = useGuardedNavigation()

  return (
    <nav aria-label="Main">
      <ul className="nav-menu">
        {NAV_ITEMS.map((item) => (
          <li key={item.key}>
            {item.path ? (
              <NavLink to={item.path} end={item.path === '/'} onClick={guardClick(item.path)}>
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