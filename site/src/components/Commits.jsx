import { useEffect, useMemo, useState } from 'react'
import { plural } from '../forum.js'
import {
  STATUS_LABELS,
  authorKey,
  blobHref,
  commitHref,
  commitsHref,
  findCommit,
  formatDay,
  ghCommit,
  groupByDay,
  historyHref,
  parseDiff,
  queryCommits,
  sourceHref,
  treeHref,
} from '../source.js'
import { loadPatch, useLazy } from '../useSource.js'
import { ExternalIcon, Time } from './ForumParts.jsx'
import { AuthorName, CopyButton, Crumbs, SourceAvatar, TagChips } from './SourceParts.jsx'

const PAGE = 50

// ---------- the list ----------

function Stat({ c }) {
  if (c.additions == null) return null
  return (
    <span className="src-stat">
      <span className="add">+{c.additions.toLocaleString('en')}</span> <span className="del">−{c.deletions.toLocaleString('en')}</span>
    </span>
  )
}

function Row({ source, c }) {
  return (
    <li className="src-crow">
      <div className="src-crow-main">
        <h3>
          <a href={commitHref(c.sha)}>{c.subject}</a> <TagChips source={source} sha={c.sha} />
        </h3>
        {c.body && (
          <details className="src-crow-body">
            <summary>Show the rest of the message</summary>
            <pre>{c.body}</pre>
          </details>
        )}
        <p className="src-crow-meta">
          <SourceAvatar person={c.author} size={20} />
          <AuthorName person={c.author} />
          <span>
            committed <Time iso={c.date} />
          </span>
          <span>{plural(c.files.length + c.moreFiles, 'file')}</span>
          <Stat c={c} />
        </p>
      </div>
      <div className="src-crow-side">
        <a className="chip mono" href={commitHref(c.sha)}>
          {c.short}
        </a>
        <CopyButton text={c.sha} label="Copy id" />
      </div>
    </li>
  )
}

const memory = new Map()

/** Every commit, or the ones that touched a file or folder, with a filter. */
export function CommitsView({ source, route }) {
  const path = route.view === 'history' ? route.path : ''
  const [filters, setFilters] = useState(() => memory.get(path) ?? { query: '', author: '' })
  const [limit, setLimit] = useState(PAGE)
  const change = (patch) => {
    setFilters((prev) => {
      const next = { ...prev, ...patch }
      memory.set(path, next)
      return next
    })
    setLimit(PAGE)
  }

  const everything = useMemo(() => queryCommits(source, { path }), [source, path])
  const list = useMemo(() => queryCommits(source, { path, ...filters }), [source, path, filters])
  const groups = useMemo(() => groupByDay(list.slice(0, limit)), [list, limit])
  const narrowed = Boolean(filters.query.trim() || filters.author)
  const file = path ? source.byPath.get(path) : null
  const isDir = path ? source.dirs.has(path) : false
  const authors = source.contributors

  return (
    <div className="src-commits">
      {path && <Crumbs source={source} path={path} />}
      <header className="src-pagehead">
        <h1>{path ? 'History' : 'Commits'}</h1>
        <p className="muted">
          {path ? (
            <>
              Commits that changed{' '}
              {file ? <a href={blobHref(path)}>{path}</a> : isDir ? <a href={treeHref(path)}>{path}</a> : <code>{path}</code>}
              {file && ', following renames'}.{' '}
            </>
          ) : null}
          {plural(everything.length, 'commit')}
          {!path && source.commitCount > source.commits.length && ` (the newest ${source.commits.length.toLocaleString('en')} of ${source.commitCount.toLocaleString('en')} are listed)`}
        </p>
      </header>

      <form className="ftools" role="search" onSubmit={(e) => e.preventDefault()}>
        <div className="search-field">
          <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
            <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2" />
            <path d="m13 13 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
          <input
            type="search"
            value={filters.query}
            onChange={(e) => change({ query: e.target.value })}
            placeholder="Search messages, authors and commit ids"
            aria-label="Search commits"
            autoComplete="off"
            spellCheck="false"
          />
        </div>
        {authors.length > 1 && (
          <div className="ftools-row">
            <label className="select">
              <span>Author</span>
              <select value={filters.author} onChange={(e) => change({ author: e.target.value })}>
                <option value="">Everyone</option>
                {authors.map((a) => (
                  <option key={authorKey(a)} value={authorKey(a)}>
                    {a.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
      </form>

      <p className="result-count" role="status">
        {narrowed ? `${plural(list.length, 'commit')} found` : plural(list.length, 'commit')}
        {narrowed && (
          <>
            {' '}
            <button type="button" className="textbtn" onClick={() => change({ query: '', author: '' })}>
              Clear filters
            </button>
          </>
        )}
      </p>

      {list.length === 0 ? (
        <p className="empty">{everything.length === 0 ? 'No commits in this copy touched that path.' : 'No commit matches. Try other words, or clear the filters.'}</p>
      ) : (
        groups.map((g) => (
          <section key={g.day} className="src-day" aria-label={formatDay(g.day)}>
            <h2>Commits on {formatDay(g.day)}</h2>
            <ul className="src-clist">
              {g.items.map((c) => (
                <Row key={c.sha} source={source} c={c} />
              ))}
            </ul>
          </section>
        ))
      )}

      {list.length > limit && (
        <p className="more">
          <button type="button" className="btn btn-quiet" onClick={() => setLimit((n) => n + PAGE)}>
            Show {Math.min(PAGE, list.length - limit)} more
          </button>
          <span>{plural(list.length - limit, 'commit')} not shown</span>
        </p>
      )}
    </div>
  )
}

// ---------- one commit ----------

function jumpTo(e, id) {
  e.preventDefault()
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function DiffBar({ additions, deletions }) {
  const total = additions + deletions
  if (!total) return null
  const green = Math.round((additions / total) * 5)
  return (
    <span className="diffbar" aria-hidden="true">
      {Array.from({ length: 5 }, (_, i) => (
        <i key={i} className={i < green ? 'add' : 'del'} />
      ))}
    </span>
  )
}

const KIND_WORDS = { add: 'Added: ', del: 'Removed: ' }

function FileDiff({ source, f, index, force }) {
  const big = (f.additions ?? 0) + (f.deletions ?? 0) > 800
  const [open, setOpen] = useState(!big)
  useEffect(() => {
    if (force.value != null) setOpen(force.value)
  }, [force])
  const rows = useMemo(() => (open && f.patch ? parseDiff(f.patch) : []), [open, f.patch])
  const exists = f.status !== 'D' && source.byPath.has(f.path)
  const id = `diff-${index}`

  return (
    <section className="src-filediff" id={id} aria-label={`Changes to ${f.path}`}>
      <header>
        <button type="button" className="src-chev" aria-expanded={open} aria-controls={`${id}-body`} onClick={() => setOpen(!open)}>
          <span aria-hidden="true">{open ? '▾' : '▸'}</span>
          <span className="sr-only">{open ? 'Hide' : 'Show'} changes to {f.path}</span>
        </button>
        <span className={`status status-${f.status}`}>{STATUS_LABELS[f.status] ?? f.status}</span>
        <span className="src-filediff-path">
          {f.oldPath && (
            <>
              <span className="muted">{f.oldPath}</span> <span aria-hidden="true">→</span>{' '}
            </>
          )}
          {exists ? <a href={blobHref(f.path)}>{f.path}</a> : <span>{f.path}</span>}
        </span>
        {f.additions != null && (
          <span className="src-stat">
            <span className="add">+{f.additions.toLocaleString('en')}</span> <span className="del">−{f.deletions.toLocaleString('en')}</span>{' '}
            <DiffBar additions={f.additions} deletions={f.deletions} />
          </span>
        )}
      </header>
      <div id={`${id}-body`} hidden={!open}>
        {open && f.binary && <p className="src-note muted">Binary file not shown.</p>}
        {open && f.combined && <p className="src-note muted">A merge commit; its combined diff is not shown here.</p>}
        {open && f.tooLarge && <p className="src-note muted">This commit changes too much to include every diff in this copy.</p>}
        {open && !f.binary && !f.combined && !f.tooLarge && !f.patch && <p className="src-note muted">No lines changed (the file was only renamed, copied or its mode changed).</p>}
        {open && rows.length > 0 && (
          <div className="src-diff" tabIndex={0} role="region" aria-label={`Diff of ${f.path}`}>
            <table>
              <tbody>
                {rows.map((r, i) =>
                  r.kind === 'hunk' ? (
                    <tr key={i} className="hunk">
                      <td colSpan={3}>{r.text}</td>
                    </tr>
                  ) : r.kind === 'meta' ? (
                    <tr key={i} className="meta">
                      <td colSpan={3}>{r.text}</td>
                    </tr>
                  ) : (
                    <tr key={i} className={r.kind}>
                      <td className="ln">{r.old ?? ''}</td>
                      <td className="ln">{r.new ?? ''}</td>
                      <td className="lc">
                        <code>
                          {KIND_WORDS[r.kind] && <span className="sr-only">{KIND_WORDS[r.kind]}</span>}
                          <span className="sign" aria-hidden="true">
                            {r.kind === 'add' ? '+' : r.kind === 'del' ? '-' : ' '}
                          </span>
                          {r.text}
                        </code>
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
        {open && f.truncated > 0 && <p className="src-note muted">{plural(f.truncated, 'more line')} of this diff are not included.</p>}
      </div>
    </section>
  )
}

function NotFound({ source, sha }) {
  const gh = source.repoUrl ? `${source.repoUrl}/commit/${sha}` : null
  return (
    <section className="forum-missing">
      <h1>Commit not found</h1>
      <p>
        There is no commit <code>{sha}</code> in this copy of the history. It may be newer than the last copy, or older than the commits it lists.{' '}
        <a href={commitsHref()}>See all commits</a>
        {gh && (
          <>
            , or{' '}
            <a href={gh} target="_blank" rel="noopener noreferrer">
              look for it on GitHub <ExternalIcon />
            </a>
          </>
        )}
        .
      </p>
    </section>
  )
}

export function CommitView({ source, route }) {
  const c = findCommit(source, route.sha)
  const patch = useLazy(c ? `patch:${c.sha}` : 'none', () => loadPatch(c.sha), Boolean(c?.patch))
  const [force, setForce] = useState({ value: null })
  if (!c) return <NotFound source={source} sha={route.sha} />

  const older = source.commits[c.idx + 1]
  const newer = source.commits[c.idx - 1]
  const gh = ghCommit(source, c.sha)
  const files =
    patch.status === 'ready'
      ? patch.data.files
      : c.files.map(([status, path, oldPath]) => ({ status, path, oldPath: oldPath ?? null }))

  return (
    <div className="src-commit">
      <nav className="crumbs src-crumbs" aria-label="Breadcrumb">
        <a href={sourceHref()}>{source.repoName}</a>
        <span aria-hidden="true"> / </span>
        <a href={commitsHref()}>Commits</a>
        <span aria-hidden="true"> / </span>
        <span aria-current="page" className="mono">{c.short}</span>
      </nav>

      <header className="src-commithead">
        <h1>{c.subject}</h1>
        <TagChips source={source} sha={c.sha} />
        {c.body && <pre className="src-cbody">{c.body}</pre>}
        <p className="src-crow-meta">
          <SourceAvatar person={c.author} size={24} />
          <AuthorName person={c.author} />
          <span>
            committed <Time iso={c.date} />
          </span>
        </p>
        <dl className="src-facts">
          <div>
            <dt>Commit</dt>
            <dd>
              <code className="mono">{c.sha}</code> <CopyButton text={c.sha} label="Copy" />
            </dd>
          </div>
          {c.parents.length > 0 && (
            <div>
              <dt>{c.parents.length === 1 ? 'Parent' : 'Parents'}</dt>
              <dd>
                {c.parents.map((p, i) => {
                  const known = source.bySha.get(p)
                  return (
                    <span key={p}>
                      {i > 0 && ', '}
                      {known ? (
                        <a className="mono" href={commitHref(p)}>
                          {known.short}
                        </a>
                      ) : (
                        <span className="mono">{p.slice(0, 7)}</span>
                      )}
                    </span>
                  )
                })}
              </dd>
            </div>
          )}
          <div>
            <dt>Changes</dt>
            <dd>
              {plural(c.files.length + c.moreFiles, 'file')}{' '}
              <span className="src-stat">
                <span className="add">+{c.additions.toLocaleString('en')}</span> <span className="del">−{c.deletions.toLocaleString('en')}</span>
              </span>
            </dd>
          </div>
        </dl>
        <p className="src-commitnav">
          {older ? <a href={commitHref(older.sha)}>← Older: {older.short}</a> : <span className="muted">Oldest commit listed</span>}
          {newer ? <a href={commitHref(newer.sha)}>Newer: {newer.short} →</a> : <span className="muted">Newest commit</span>}
          {gh && (
            <a href={gh} target="_blank" rel="noopener noreferrer">
              View on GitHub <ExternalIcon />
            </a>
          )}
        </p>
      </header>

      {files.length > 0 && (
        <section aria-labelledby="changed-title" className="src-changed">
          <h2 id="changed-title">{plural(files.length, 'changed file')}{c.moreFiles > 0 && ` (and ${c.moreFiles.toLocaleString('en')} more not listed)`}</h2>
          <ul>
            {files.map((f, i) => (
              <li key={`${f.path}:${i}`}>
                <span className={`status status-${f.status}`}>{STATUS_LABELS[f.status] ?? f.status}</span>
                {patch.status === 'ready' ? (
                  <a href={`#diff-${i}`} onClick={(e) => jumpTo(e, `diff-${i}`)}>
                    {f.path}
                  </a>
                ) : (
                  <span>{f.path}</span>
                )}
                {f.additions != null && (
                  <span className="src-stat">
                    <span className="add">+{f.additions}</span> <span className="del">−{f.deletions}</span>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {c.patch ? (
        <section aria-labelledby="diff-title">
          <div className="src-diffhead">
            <h2 id="diff-title">Changes</h2>
            {patch.status === 'ready' && patch.data.files.length > 1 && (
              <span className="src-actions">
                <button type="button" className="textbtn" onClick={() => setForce({ value: true })}>
                  Expand all
                </button>
                <button type="button" className="textbtn" onClick={() => setForce({ value: false })}>
                  Collapse all
                </button>
              </span>
            )}
          </div>
          {patch.status === 'loading' && <p className="state" role="status">Loading the changes…</p>}
          {patch.status === 'error' && (
            <p className="notice">
              The changes could not be loaded.{' '}
              {gh && (
                <a href={gh} target="_blank" rel="noopener noreferrer">
                  See them on GitHub <ExternalIcon />
                </a>
              )}
            </p>
          )}
          {patch.status === 'ready' &&
            patch.data.files.map((f, i) => <FileDiff key={`${f.path}:${i}`} source={source} f={f} index={i} force={force} />)}
        </section>
      ) : (
        <p className="notice">
          The changes of this commit are not included in this copy, which keeps only the newest commits' diffs.{' '}
          {gh && (
            <a href={gh} target="_blank" rel="noopener noreferrer">
              See them on GitHub <ExternalIcon />
            </a>
          )}
        </p>
      )}
    </div>
  )
}
