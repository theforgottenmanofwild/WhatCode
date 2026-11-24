import { useDeferredValue, useEffect, useMemo, useState } from 'react'
import { plural } from '../forum.js'
import { blobHref, findFiles, formatBytes, searchCode, searchHref } from '../source.js'
import { loadSearchBundle, useLazy } from '../useSource.js'
import { FileIcon } from './SourceParts.jsx'

/** `text` with the characters at the given positions marked. */
function Marked({ text, marks }) {
  const on = new Set(marks)
  const out = []
  for (let i = 0; i < text.length; ) {
    const hit = on.has(i)
    let j = i
    while (j < text.length && on.has(j) === hit) j++
    out.push(hit ? <mark key={i}>{text.slice(i, j)}</mark> : text.slice(i, j))
    i = j
  }
  return out
}

/** `text` with the [start, end) ranges marked. */
function Ranged({ text, ranges }) {
  const out = []
  let at = 0
  for (const [s, e] of [...ranges].sort((a, b) => a[0] - b[0])) {
    if (s < at) continue
    if (s > at) out.push(text.slice(at, s))
    out.push(<mark key={s}>{text.slice(s, e)}</mark>)
    at = e
  }
  if (at < text.length) out.push(text.slice(at))
  return out
}

function FileResults({ source, query }) {
  const found = useMemo(() => findFiles(source, query, 100), [source, query])
  const recent = useMemo(
    () =>
      source.files
        .filter((f) => f.commit !== null)
        .sort((a, b) => a.commit - b.commit)
        .slice(0, 15),
    [source],
  )

  if (!query.trim()) {
    return (
      <>
        <p className="result-count">Recently changed files</p>
        <ul className="src-results">
          {recent.map((f) => (
            <li key={f.path}>
              <FileIcon />
              <a href={blobHref(f.path)}>{f.path}</a>
              <span className="muted">{formatBytes(f.size)}</span>
            </li>
          ))}
        </ul>
      </>
    )
  }
  return (
    <>
      <p className="result-count" role="status">
        {plural(found.length, 'file')} found{found.length === 100 && ' (the best 100 are shown)'}
      </p>
      {found.length === 0 ? (
        <p className="empty">No file name matches. The letters only need to appear in order, so try fewer of them.</p>
      ) : (
        <ul className="src-results">
          {found.map(({ file, marks }) => (
            <li key={file.path}>
              <FileIcon />
              <a href={blobHref(file.path)}>
                <Marked text={file.path} marks={marks} />
              </a>
              <span className="muted">
                {file.lang ? `${file.lang} · ` : ''}
                {formatBytes(file.size)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

function CodeResults({ query, matchCase, regex }) {
  const bundle = useLazy('search', loadSearchBundle)
  const text = regex ? query : query.trim()
  const res = useMemo(
    () => (bundle.status === 'ready' && text.length >= 2 ? searchCode(bundle.data, text, { matchCase, regex }) : null),
    [bundle, text, matchCase, regex],
  )

  if (bundle.status === 'loading') return <p className="state" role="status">Loading the code…</p>
  if (bundle.status === 'error') return <p className="notice">The code could not be loaded for searching. Try again in a moment.</p>
  if (!res) return <p className="muted src-note">Type at least two characters. Searches the text of the source files in this copy, not lock files or files over 250 KB.</p>
  if (res.error) return <p className="notice" role="alert">{res.error}</p>

  return (
    <>
      <p className="result-count" role="status">
        {plural(res.total, 'matching line')} in {plural(res.files, 'file')}
        {res.shown < res.total && ` (the first ${res.shown.toLocaleString('en')} are shown)`}
      </p>
      {res.results.length === 0 ? (
        <p className="empty">Nothing in the code matches.</p>
      ) : (
        <ul className="src-hits">
          {res.results.map((r) => (
            <li key={r.path}>
              <h3>
                <FileIcon /> <a href={blobHref(r.path)}>{r.path}</a> <span className="count-chip">{r.count}</span>
              </h3>
              <ol>
                {r.hits.map((h) => (
                  <li key={h.line}>
                    <a className="ln" href={blobHref(r.path, `L${h.line}`)} aria-label={`Line ${h.line}`}>
                      {h.line}
                    </a>
                    <code>
                      <Ranged text={h.text} ranges={h.ranges} />
                    </code>
                  </li>
                ))}
                {r.count > r.hits.length && (
                  <li className="muted src-more">
                    {plural(r.count - r.hits.length, 'more line')} in this file. <a href={blobHref(r.path)}>Open the file</a>
                  </li>
                )}
              </ol>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/** Find a file by name, or find text in the code. */
export default function SourceSearch({ source, route }) {
  const [mode, setMode] = useState(route.mode ?? 'files')
  const [q, setQ] = useState(route.query)
  const [matchCase, setMatchCase] = useState(false)
  const [regex, setRegex] = useState(false)
  const query = useDeferredValue(q)

  // Following a link to another search (or the Search tab) replaces what was typed.
  useEffect(() => {
    setMode(route.mode ?? 'files')
    setQ(route.query)
  }, [route.mode, route.query])

  // The address follows what is typed, so a search can be shared, without making the browser go to the page again.
  useEffect(() => {
    const href = searchHref(mode, q.trim())
    if (window.location.hash !== href) window.history.replaceState(null, '', href)
  }, [mode, q])

  return (
    <div className="src-search">
      <header className="src-pagehead">
        <h1>Search</h1>
        <p className="muted">Find a file by name, or find text anywhere in the code.</p>
      </header>

      <form className="ftools" role="search" onSubmit={(e) => e.preventDefault()}>
        <div className="search-field">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
            <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2" />
            <path d="m13 13 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={mode === 'files' ? 'Type part of a file name or path' : 'Type the text to find'}
            aria-label={mode === 'files' ? 'Find a file' : 'Search the code'}
            autoComplete="off"
            autoCapitalize="off"
            spellCheck="false"
          />
        </div>
        <div className="ftools-row">
          <div className="seg" role="group" aria-label="Search in">
            <button type="button" aria-pressed={mode === 'files'} onClick={() => setMode('files')}>
              File names
            </button>
            <button type="button" aria-pressed={mode === 'code'} onClick={() => setMode('code')}>
              Code
            </button>
          </div>
          {mode === 'code' && (
            <div className="src-actions">
              <label className="check">
                <input type="checkbox" checked={matchCase} onChange={(e) => setMatchCase(e.target.checked)} /> Match case
              </label>
              <label className="check">
                <input type="checkbox" checked={regex} onChange={(e) => setRegex(e.target.checked)} /> Regular expression
              </label>
            </div>
          )}
        </div>
      </form>

      {mode === 'files' ? <FileResults source={source} query={query} /> : <CodeResults query={query} matchCase={matchCase} regex={regex} />}
    </div>
  )
}
