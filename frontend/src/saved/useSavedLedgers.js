import { listSavedLedgers } from '../api/client'
import { useLoaded } from '../useLoaded'

export function useSavedLedgers() {
  return useLoaded(listSavedLedgers)
}
