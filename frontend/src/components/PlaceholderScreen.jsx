import { useCurrentNavItem } from '../navigation'

export default function PlaceholderScreen() {
  const current = useCurrentNavItem()
  if (!current) return null

  return (
    <section>
      <p>{current.summary}</p>
      <p className="placeholder-note">Placeholder: this screen is built in Phase {current.phase}.</p>
    </section>
  )
}