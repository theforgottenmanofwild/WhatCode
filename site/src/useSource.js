import { useCallback, useEffect, useState } from 'react'
import { SOURCE_BASE, indexSource, rawUrl } from './source.js'

/** What is fetched once and kept for the life of the page: moving between source pages must not fetch the same thing again. */
const cache = new Map()

function once(key, load) {
  if (!cache.has(key)) {
    cache.set(
      key,
      load().catch((err) => {
        cache.delete(key) // a failure may be a moment's trouble; the next try asks again
        throw err
      }),
    )
  }
  return cache.get(key)
}

async function getJson(name, signal) {
  const res = await fetch(`${SOURCE_BASE}${name}`, { signal, cache: 'no-cache' })
  if (res.status === 404) throw new Error('missing')
  if (!res.ok) throw new Error(`http-${res.status}`)
  try {
    return await res.json()
  } catch {
    // a dev server answers an unknown file with the home page
    throw new Error('missing')
  }
}

/**
 * Loads and indexes the snapshot.
 *   { status: 'ready', source }                     the repository, indexed (see source.js)
 *   { status: 'unavailable', reason, generatedAt }  the build had no git history to copy
 * Throws when the file is missing or is not a snapshot this site understands.
 */
export async function loadSource(signal) {
  const data = await getJson('index.json', signal)
  if (data?.version !== 1) throw new Error('format')
  if (!data.available) return { status: 'unavailable', reason: data.reason ?? 'unknown', generatedAt: data.generatedAt ?? null }
  return { status: 'ready', source: indexSource(data) }
}

let loaded = null

/** status: loading | ready | unavailable | error */
export function useSource() {
  const [state, setState] = useState(() => loaded ?? { status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (loaded && attempt === 0) return
    const controller = new AbortController()
    setState({ status: 'loading' })
    loadSource(controller.signal)
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

// ---------- the pieces fetched when a page needs them ----------

/** The text of a file. */
export const loadText = (file) =>
  once(`text:${file.blob}.${file.ext}`, async () => {
    const res = await fetch(rawUrl(file))
    if (!res.ok) throw new Error(`http-${res.status}`)
    return res.text()
  })

/** The diff of one commit: { files: [...] }. */
export const loadPatch = (sha) => once(`patch:${sha}`, () => getJson(`commits/${sha}.json`))

/** Who last changed each line of a file: [[firstLine, lineCount, commitIndex], ...]. */
export const loadBlame = (id) => once(`blame:${id}`, () => getJson(`blame/${id}.json`))

/** The text of every source file, for searching the code. */
export const loadSearchBundle = () => once('search', () => getJson('search.json'))

/** A syntax highlighter, brought in only when someone opens a file. */
export const loadSyntax = () => once('syntax', () => import('./syntax.js'))

/**
 * Runs `load()` when `enabled` and gives { status: 'idle' | 'loading' | 'ready' | 'error', data, error }.
 * `key` says when to ask again.
 */
export function useLazy(key, load, enabled = true) {
  const [state, setState] = useState({ status: enabled ? 'loading' : 'idle', data: null, error: null })

  useEffect(() => {
    if (!enabled) {
      setState({ status: 'idle', data: null, error: null })
      return
    }
    let live = true
    setState((s) => (s.status === 'ready' && s.key === key ? s : { status: 'loading', data: null, error: null, key }))
    Promise.resolve()
      .then(load)
      .then((data) => live && setState({ status: 'ready', data, error: null, key }))
      .catch((error) => live && setState({ status: 'error', data: null, error: error.message, key }))
    return () => {
      live = false
    }
    // `load` is written fresh each render; `key` is what says the thing to load changed
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, enabled])

  return state
}
