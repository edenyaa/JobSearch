import { useCallback, useEffect, useState } from 'react'
import { messageOf } from '../api/client'

interface LoadState<T> {
  data: T | null
  error: string | null
  loading: boolean
}

export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<LoadState<T>>({ data: null, error: null, loading: true })
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let active = true
    setState((previous) => ({ ...previous, loading: true, error: null }))
    load()
      .then((data) => {
        if (active) setState({ data, error: null, loading: false })
      })
      .catch((error) => {
        if (active) setState({ data: null, error: messageOf(error), loading: false })
      })
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])
  const setData = useCallback((data: T) => setState((previous) => ({ ...previous, data })), [])

  return { ...state, reload, setData }
}
