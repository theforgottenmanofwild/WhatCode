import { useCallback, useEffect, useState } from 'react'
import { indexForum } from './forum.js'

// The snapshot is a file next to the page, written at build time (scripts/fetch-discussions.mjs).
const SNAPSHOT = `${import.meta.env.BASE_URL}discussions.json`

/**
 * Loads and indexes the snapshot.
 *   { status: 'ready', forum }                      the discussions, indexed (see forum.js)
 *   { status: 'unavailable', reason, generatedAt }  the build had nothing to show: no token, or Discussions is off
 * Throws when the file is missing or is not a snapshot this site understands.
 */
export async function loadForum(signal) {
  const res = await fetch(SNAPSHOT, { signal, cache: 'no-cache' })
  if (res.status === 404) throw new Error('missing')
  if (!res.ok) throw new Error(`http-${res.status}`)
  let data
  try {
    data = await res.json()
  } catch {
    // a dev server answers an unknown file with the home page
    throw new Error('missing')
  }
  if (data?.version !== 1) throw new Error('format')
  if (!data.available) return { status: 'unavailable', reason: data.reason ?? 'unknown', generatedAt: data.generatedAt ?? null }
  return { status: 'ready', forum: indexForum(data) }
}

// Moving between forum pages must not fetch again, so a loaded snapshot is kept for the life of the page.
let loaded = null

/** status: loading | ready | unavailable | error */
export function useForum() {
  const [state, setState] = useState(() => loaded ?? { status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (loaded && attempt === 0) return
    const controller = new AbortController()
    setState({ status: 'loading' })
    loadForum(controller.signal)
      .then((result) => {
        loaded = result
        setState(result)
      })
      .catch((err) => {
        if (err.name !== 'AbortError') setState({ status: 'error', reason: err.message })
      })
    return () => controller.abort()
  }, [attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  return { ...state, retry }
}
