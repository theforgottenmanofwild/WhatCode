import { useEffect, useMemo, useRef, useState } from 'react'
import {
  BLAME_MAX_LINES,
  blobHref,
  commitHref,
  formatBytes,
  ghBlob,
  ghEdit,
  historyHref,
  lineAnchor,
  lineWord,
  parseLineRange,
  plainLines,
  rawUrl,
  sourceHref,
  treeHref,
} from '../source.js'
import { loadBlame, loadSyntax, loadText, useLazy } from '../useSource.js'
import { ExternalIcon, Time } from './ForumParts.jsx'
import SourceMarkdown from './SourceMarkdown.jsx'
import { CommitBar, CopyButton, Crumbs } from './SourceParts.jsx'

// Past this size a file is shown without colours: highlighting it would make the page stop for a while.
const HIGHLIGHT_MAX_BYTES = 300_000

// "Wrap long lines" survives going to another file.
let wrapPref = false

function NotFound({ source, path }) {
  const isDir = source.dirs.has(path)
  const gh = ghBlob(source, path)
  return (
    <section className="forum-missing">
      <h1>{isDir ? 'That is a folder' : 'File not found'}</h1>
      <p>
        {isDir ? (
          <>
            <code>{path}</code> is a folder. <a href={treeHref(path)}>Open the folder</a>.
          </>
        ) : (
          <>
            There is no file called <code>{path}</code> in this copy of the code. It may have been added after the copy
            was made. <a href={sourceHref()}>Back to the code</a>
            {gh && (
              <>
                , or{' '}
                <a href={gh} target="_blank" rel="noopener noreferrer">
                  look for it on GitHub <ExternalIcon />
                </a>
              </>
            )}
            .
          </>
        )}
      </p>
    </section>
  )
}

/** The blame column: one cell per run of lines that share a commit, spanning those lines. */
function useBlameLayout(runs) {
  return useMemo(() => {
    const starts = new Map()
    const inside = new Set()
    if (runs) {
      runs.forEach(([start, count, idx], i) => {
        starts.set(start, { idx, count, band: i % 2 })
        for (let n = start + 1; n < start + count; n++) inside.add(n)
      })
    }
    return { starts, inside }
  }, [runs])
}

function BlameCell({ source, run }) {
  const c = run.idx >= 0 ? source.commits[run.idx] : null
  return (
    <td className={`bl band-${run.band}`} rowSpan={run.count}>
      {c ? (
        <>
          <a className="bl-sha mono" href={commitHref(c.sha)} title={c.subject}>
            {c.short}
          </a>
          <span className="bl-who">{c.author.name}</span>
          <span className="bl-when">
            <Time iso={c.date} />
          </span>
        </>
      ) : (
        <span className="muted">Older commit</span>
      )}
    </td>
  )
}

function Notice({ children }) {
  return <p className="notice">{children}</p>
}

function BlobView({ source, file, anchor }) {
  const isText = file.kind === 'text'
  const hasPreview = isText && (file.lang === 'Markdown' || file.image)
  const startRange = parseLineRange(anchor)
  const [mode, setMode] = useState(hasPreview && !startRange ? 'preview' : 'code') // preview | code | blame
  const [wrap, setWrap] = useState(wrapPref)
  const [range, setRange] = useState(startRange)
  const lastClicked = useRef(startRange?.from ?? null)
  const scrolled = useRef(false)

  useEffect(() => {
    setRange(parseLineRange(anchor))
  }, [anchor])

  const raw = file.kind === 'large' ? null : rawUrl(file)
  const gh = ghBlob(source, file.path)
  const edit = ghEdit(source, file.path)
  const commit = file.commit != null ? source.commits[file.commit] : null
  const dir = file.path.includes('/') ? file.path.slice(0, file.path.lastIndexOf('/')) : ''

  const text = useLazy(`text:${file.blob}.${file.ext}`, () => loadText(file), isText)
  const showCode = isText && mode !== 'preview'
  const highlightable = showCode && text.status === 'ready' && file.size <= HIGHLIGHT_MAX_BYTES && file.hl && file.hl !== 'plaintext'
  const highlighted = useLazy(
    `hl:${file.blob}:${file.hl}`,
    async () => (await loadSyntax()).highlightLines(text.data, file.hl),
    highlightable,
  )
  const canBlame = isText && file.lines > 0 && file.lines <= BLAME_MAX_LINES && !file.symlink
  const blame = useLazy(`blame:${file.bid}`, () => loadBlame(file.bid), mode === 'blame' && canBlame)
  const layout = useBlameLayout(blame.status === 'ready' ? blame.data : null)

  const lines = useMemo(() => {
    if (text.status !== 'ready') return []
    if (highlighted.status === 'ready') return highlighted.data.map((html) => ({ html }))
    return plainLines(text.data).map((t) => ({ t }))
  }, [text, highlighted])

  // A link to some lines scrolls to them once they exist.
  useEffect(() => {
    if (scrolled.current || !range || !showCode || !lines.length) return
    scrolled.current = true
    document.getElementById(`L${range.from}`)?.scrollIntoView({ block: 'center' })
  }, [lines.length, range, showCode])

  function pick(n) {
    return (e) => {
      e.preventDefault()
      const next =
        e.shiftKey && lastClicked.current
          ? { from: Math.min(lastClicked.current, n), to: Math.max(lastClicked.current, n) }
          : { from: n, to: n }
      if (!e.shiftKey) lastClicked.current = n
      setRange(next)
      // Change the address without going to the page again, so the page does not jump.
      window.history.replaceState(null, '', blobHref(file.path, lineAnchor(next.from, next.to)))
    }
  }

  function clearRange() {
    setRange(null)
    window.history.replaceState(null, '', blobHref(file.path))
  }

  const selectedText = () => (range && text.status === 'ready' ? plainLines(text.data).slice(range.from - 1, range.to).join('\n') : '')
  const blameOn = mode === 'blame' && blame.status === 'ready'

  const meta = [
    isText ? lineWord(file.lines) : null,
    formatBytes(file.size),
    isText ? file.lang : file.kind === 'binary' ? 'Binary file' : null,
  ].filter(Boolean)

  return (
    <div className="src-blob">
      <Crumbs source={source} path={file.path} />
      <header className="src-filehead">
        <h1>{file.name}</h1>
        <p className="muted">
          {meta.join(' · ')}
          {file.exec && <span className="badge">executable</span>}
          {file.symlink && <span className="badge">symbolic link</span>}
          {file.generated && <span className="badge">generated</span>}
        </p>
      </header>

      <CommitBar source={source} index={file.commit} />

      <div className="src-toolbar">
        {isText && (
          <div className="seg" role="group" aria-label="View">
            {hasPreview && (
              <button type="button" aria-pressed={mode === 'preview'} onClick={() => setMode('preview')}>
                Preview
              </button>
            )}
            <button type="button" aria-pressed={mode === 'code'} onClick={() => setMode('code')}>
              Code
            </button>
            {canBlame && (
              <button type="button" aria-pressed={mode === 'blame'} onClick={() => setMode('blame')}>
                Blame
              </button>
            )}
          </div>
        )}
        <div className="src-actions">
          {showCode && (
            <label className="check">
              <input
                type="checkbox"
                checked={wrap}
                onChange={(e) => {
                  wrapPref = e.target.checked
                  setWrap(e.target.checked)
                }}
              />{' '}
              Wrap lines
            </label>
          )}
          {isText && text.status === 'ready' && <CopyButton text={() => text.data} label="Copy file" />}
          {raw && (
            <a href={raw} target="_blank" rel="noopener noreferrer">
              Raw
            </a>
          )}
          {raw && (
            <a href={raw} download={file.name}>
              Download
            </a>
          )}
          <a href={historyHref(file.path)}>History</a>
          {gh && (
            <a href={gh} target="_blank" rel="noopener noreferrer">
              GitHub <ExternalIcon />
            </a>
          )}
          {edit && (
            <a href={edit} target="_blank" rel="noopener noreferrer">
              Edit <ExternalIcon />
            </a>
          )}
        </div>
      </div>

      {showCode && range && (
        <p className="src-selbar" role="status">
          <strong>{range.from === range.to ? `Line ${range.from}` : `Lines ${range.from}–${range.to}`}</strong>
          <CopyButton text={selectedText} label="Copy lines" />
          <CopyButton text={() => window.location.href} label="Copy link" />
          <button type="button" className="textbtn" onClick={clearRange}>
            Clear
          </button>
        </p>
      )}

      <div className="src-view">
        {file.kind === 'large' && (
          <Notice>
            This file is {formatBytes(file.size)}, too large to include in this copy of the code.{' '}
            {gh && (
              <a href={gh} target="_blank" rel="noopener noreferrer">
                View it on GitHub <ExternalIcon />
              </a>
            )}
          </Notice>
        )}

        {file.kind === 'binary' &&
          (file.image ? (
            <div className="src-image">
              <img src={raw} alt={file.name} />
            </div>
          ) : (
            <Notice>
              This is a binary file ({formatBytes(file.size)}), so there is no text to show.{' '}
              <a href={raw} download={file.name}>
                Download it
              </a>
              .
            </Notice>
          ))}

        {isText && text.status === 'loading' && <p className="state" role="status">Loading…</p>}
        {isText && text.status === 'error' && (
          <Notice>
            The file could not be loaded.{' '}
            {gh && (
              <a href={gh} target="_blank" rel="noopener noreferrer">
                View it on GitHub <ExternalIcon />
              </a>
            )}
          </Notice>
        )}

        {isText && text.status === 'ready' && mode === 'preview' && (
          file.lang === 'Markdown' ? (
            <article className="src-doc">
              <SourceMarkdown source={source} dir={dir}>
                {text.data}
              </SourceMarkdown>
            </article>
          ) : (
            <div className="src-image">
              <img src={raw} alt={file.name} />
            </div>
          )
        )}

        {isText && text.status === 'ready' && showCode && (
          <>
            {mode === 'blame' && blame.status === 'loading' && <p className="state" role="status">Loading blame…</p>}
            {mode === 'blame' && blame.status === 'error' && <Notice>Blame is not available for this file.</Notice>}
            {file.size > HIGHLIGHT_MAX_BYTES && <p className="muted src-note">This file is large, so it is shown without colours.</p>}
            {lines.length === 0 ? (
              <p className="empty">This file is empty.</p>
            ) : (
              <div className={wrap ? 'src-code is-wrap' : 'src-code'} tabIndex={0} role="region" aria-label={`Code of ${file.path}`}>
                <table>
                  <tbody>
                    {lines.map((l, i) => {
                      const n = i + 1
                      const selected = range && n >= range.from && n <= range.to
                      const run = blameOn ? layout.starts.get(n) : null
                      return (
                        <tr key={n} id={`L${n}`} className={selected ? 'is-sel' : undefined}>
                          {blameOn && (run ? <BlameCell source={source} run={run} /> : !layout.inside.has(n) ? <td className="bl" /> : null)}
                          <td className="ln">
                            <a href={blobHref(file.path, `L${n}`)} onClick={pick(n)} aria-label={`Line ${n}`}>
                              {n}
                            </a>
                          </td>
                          <td className="lc">
                            {l.html != null ? <code dangerouslySetInnerHTML={{ __html: l.html }} /> : <code>{l.t}</code>}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>

      {commit && (
        <p className="muted src-note">
          Last changed in <a href={commitHref(commit.sha)}>{commit.short}</a> <Time iso={commit.date} />.
        </p>
      )}
    </div>
  )
}

export default function Blob({ source, route }) {
  const file = source.byPath.get(route.path)
  if (!file) return <NotFound source={source} path={route.path} />
  return <BlobView key={file.path} source={source} file={file} anchor={route.anchor} />
}
