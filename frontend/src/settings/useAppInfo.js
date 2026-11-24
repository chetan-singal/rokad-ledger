import { getAppInfo } from '../api/client'
import { useLoaded } from '../useLoaded'

export function useAppInfo() {
  return useLoaded(getAppInfo)
}
