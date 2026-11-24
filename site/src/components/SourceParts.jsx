import { useEffect, useRef, useState } from 'react'
import { plural } from '../forum.js'
import {
  avatarUrl,
  breadcrumbs,
  commitHref,
  commitsHref,
  historyHref,
  profileUrl,
  searchHref,
  sourceHref,
  tagsHref,
  treeHref,
} from '../source.js'
import { Time } from './ForumParts.jsx'

// ---------- icons ----------

export function FolderIcon() {
  return (
    <svg className="src-icon is-dir" viewBox="0 0 16 16" width="18" height="18" aria-hidden="true" focusable="false">
      <path d="M1.5 3.5A1 1 0 0 1 2.5 2.5h3.2l1.4 1.5h6.4a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" fill="currentColor" />
    </svg>
  )
}

export function FileIcon() {
  return (
    <svg className="src-icon" viewBox="0 0 16 16" width="18" height="18" aria-hidden="true" focusable="false">
      <path d="M4 1.5h5L12.5 5v9a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V2a.5.5 0 0 1 .5-.5z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="M9 1.5V5h3.5" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
    </svg>
  )
}

// ---------- people ----------

export function SourceAvatar({ person, size = 20 }) {
  const src = avatarUrl(person, size)
  if (src) {
    return (
      <img className="avatar" src={src} width={size} height={size} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" />
    )
  }
  return (
    <span className="avatar avatar-blank" style={{ width: size, height: size, fontSize: size * 0.45 }} aria-hidden="true">
      {(person?.name ?? '?').charAt(0).toUpperCase()}
    </span>
  )
}

export function AuthorName({ person }) {
  const href = profileUrl(person)
  return href ? (
    <a className="person-name" href={href} target="_blank" rel="noopener noreferrer">
      {person.name}
    </a>
  ) : (
    <span className="person-name">{person?.name ?? 'unknown'}</span>
  )
}

// ---------- copy ----------

/** A button that puts `text` (or what `text()` returns) on the clipboard and says so. */
export function CopyButton({ text, label = 'Copy', className = 'textbtn', children }) {
  const [done, setDone] = useState(false)
  const timer = useRef(0)
  useEffect(() => () => clearTimeout(timer.current), [])

  async function copy() {
    const value = typeof text === 'function' ? text() : text
    try {
      await navigator.clipboard.writeText(value)
    } catch {
      const box = document.createElement('textarea')
      box.value = value
      box.setAttribute('readonly', '')
      box.style.cssText = 'position:fixed;opacity:0'
      document.body.appendChild(box)
      box.select()
      try {
        document.execCommand('copy')
      } catch {
        // nothing more to try
      }
      box.remove()
    }
    setDone(true)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => setDone(false), 1500)
  }

  return (
    <button type="button" className={className} onClick={copy}>
      <span aria-live="polite">{done ? 'Copied' : (children ?? label)}</span>
    </button>
  )
}

// ---------- navigation ----------

/** Code | Commits | Tags | Search, shown above every source page. */
export function SourceTabs({ source, view }) {
  const code = ['home', 'tree', 'blob'].includes(view)
  const commits = ['commits', 'commit', 'history'].includes(view)
  return (
    <nav className="src-tabs" aria-label="Source">
      <a href={sourceHref()} aria-current={code ? 'page' : undefined}>
        Code
      </a>
      <a href={commitsHref()} aria-current={commits ? 'page' : undefined}>
        Commits <span className="count-chip">{source.commitCount.toLocaleString('en')}</span>
      </a>
      <a href={tagsHref()} aria-current={view === 'tags' ? 'page' : undefined}>
        Tags <span className="count-chip">{source.tags.length}</span>
      </a>
      <a href={searchHref('files')} aria-current={view === 'search' ? 'page' : undefined}>
        Search
      </a>
    </nav>
  )
}

/** repo / folder / folder / file, every part but the last a link. */
export function Crumbs({ source, path, last = null }) {
  const parts = breadcrumbs(path)
  return (
    <nav className="crumbs src-crumbs" aria-label="Breadcrumb">
      {parts.length || last ? <a href={sourceHref()}>{source.repoName}</a> : <span aria-current="page">{source.repoName}</span>}
      {parts.map((p, i) => (
        <span key={p.path}>
          <span aria-hidden="true"> / </span>
          {i < parts.length - 1 || last ? (
            <a href={treeHref(p.path)}>{p.name}</a>
          ) : (
            <span aria-current="page">{p.name}</span>
          )}
        </span>
      ))}
      {last && (
        <span>
          <span aria-hidden="true"> / </span>
          <span aria-current="page">{last}</span>
        </span>
      )}
    </nav>
  )
}

// ---------- commits ----------

export function TagChips({ source, sha }) {
  const names = source.tagsBySha.get(sha)
  if (!names?.length) return null
  return (
    <>
      {names.map((n) => (
        <a key={n} className="chip chip-tag" href={tagsHref()}>
          {n}
        </a>
      ))}
    </>
  )
}

/** The newest commit of a file or folder, as one line: who, what, when. */
export function CommitBar({ source, index, historyPath = null }) {
  const c = index == null ? null : source.commits[index]
  if (!c) return null
  return (
    <div className="src-commitbar">
      <SourceAvatar person={c.author} size={22} />
      <AuthorName person={c.author} />
      <a className="src-commitbar-msg" href={commitHref(c.sha)} title={c.subject}>
        {c.subject}
      </a>
      <span className="src-commitbar-meta">
        <TagChips source={source} sha={c.sha} />
        <a className="mono" href={commitHref(c.sha)}>
          {c.short}
        </a>
        <Time iso={c.date} />
        {historyPath != null && (
          <a href={historyHref(historyPath)}>History</a>
        )}
      </span>
    </div>
  )
}

// ---------- languages ----------

const percent = (part, whole) => {
  const p = (part / whole) * 100
  return p < 0.1 ? '<0.1' : p.toFixed(1)
}

/** A bar split by language and a legend under it. */
export function LanguageBar({ languages }) {
  const total = languages.reduce((n, l) => n + l.lines, 0)
  if (!total) return null
  return (
    <section className="src-langs" aria-label="Languages">
      <div className="src-langbar" role="img" aria-label={languages.map((l) => `${l.name} ${percent(l.lines, total)}%`).join(', ')}>
        {languages.map((l) => (
          <span key={l.name} style={{ width: `${(l.lines / total) * 100}%`, background: l.color ?? 'var(--muted)' }} title={`${l.name} ${percent(l.lines, total)}%`} />
        ))}
      </div>
      <ul className="src-legend">
        {languages.map((l) => (
          <li key={l.name}>
            <span className="src-dot" style={{ background: l.color ?? 'var(--muted)' }} aria-hidden="true" />
            <strong>{l.name}</strong> <span className="muted">{percent(l.lines, total)}%</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

export const filesWord = (n) => plural(n, 'file')
