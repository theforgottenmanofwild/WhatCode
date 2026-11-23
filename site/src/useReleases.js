import { useCallback, useEffect, useState } from 'react'
import { links } from './config.js'
import { fetchReleases } from './releases.js'

/** status: loading | ready | error | unconfigured */
export function useReleases() {
  const [state, setState] = useState(() =>
    links ? { status: 'loading' } : { status: 'unconfigured' },
  )
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!links) return
    const controller = new AbortController()
    setState({ status: 'loading' })
    fetchReleases(links.api, controller.signal)
      .then((data) => setState({ status: 'ready', ...data }))
      .catch((err) => {
        if (err.name !== 'AbortError') setState({ status: 'error', reason: err.message })
      })
    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  return { ...state, retry }
}
