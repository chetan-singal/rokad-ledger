import { useEffect, useState } from 'react'
import { todayIso } from './format'

// Today's local date, read again whenever the window regains focus so it rolls over at midnight.
export function useToday() {
  const [today, setToday] = useState(todayIso)

  useEffect(() => {
    const update = () => setToday(todayIso())
    window.addEventListener('focus', update)
    return () => window.removeEventListener('focus', update)
  }, [])

  return today
}