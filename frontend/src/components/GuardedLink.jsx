import { Link } from 'react-router'
import { useGuardedNavigation } from '../navigation'

// A <Link> that asks first when a form has unsaved input.
export default function GuardedLink({ to, ...props }) {
  const guardClick = useGuardedNavigation()
  return <Link to={to} onClick={guardClick(to)} {...props} />
}