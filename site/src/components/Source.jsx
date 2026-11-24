import { useEffect } from 'react'
import { repoUrl as configRepoUrl } from '../config.js'
import { formatDateTime, plural } from '../forum.js'
import {
  blobHref,
  commitHref,
  findCommit,
  formatBytes,
  ghArchive,
  ghTree,
  listDir,
  profileUrl,
  readmeOf,
  searchHref,
  sourceHref,
  tagsHref,
  treeHref,
} from '../source.js'
import { loadText, useLazy, useSource } from '../useSource.js'
import Blob from './Blob.jsx'
import { CommitView, CommitsView } from './Commits.jsx'
import { ExternalIcon, Time } from './ForumParts.jsx'
import SourceMarkdown from './SourceMarkdown.jsx'
import SourceSearch from './SourceSearch.jsx'
import { AuthorName, CommitBar, CopyButton, Crumbs, FileIcon, FolderIcon, LanguageBar, SourceAvatar, SourceTabs, TagChips } from './SourceParts.jsx'

function useTitle(text) {
  useEffect(() => {
    document.title = text
  }, [text])
}

// ---------- a folder's rows ----------

function Listing({ source, path }) {
  const rows = listDir(source, path)
  const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
  if (!rows?.length) return <p className="empty">This folder is empty.</p>
  return (
    <ul className="src-list" aria-label={path ? `Files in ${path}` : 'Files'}>
      {path && (
        <li className="src-row">
          <FolderIcon />
          <a className="src-name" href={treeHref(parent)} aria-label="Up one folder">
            ..
          </a>
        </li>
      )}
      {rows.map((r) => {
        const c = r.commit != null ? source.commits[r.commit] : null
        return (
          <li className="src-row" key={`${r.type}:${r.path}`}>
            {r.type === 'dir' ? <FolderIcon /> : <FileIcon />}
            <a className="src-name" href={r.type === 'dir' ? treeHref(r.path) : blobHref(r.path)}>
              {r.name}
            </a>
            <span className="src-msg">
              {c && (
                <a href={commitHref(c.sha)} title={c.subject}>
                  {c.subject}
                </a>
              )}
            </span>
            <span className="src-time">{c && <Time iso={c.date} />}</span>
          </li>
        )
      })}
    </ul>
  )
}

/** The README of a folder, shown under its files. */
function Readme({ source, path }) {
  const file = readmeOf(source, path)
  const text = useLazy(file ? `text:${file.blob}.${file.ext}` : 'none', () => loadText(file), Boolean(file))
  if (!file) return null
  const markdown = file.lang === 'Markdown'
  return (
    <section className="src-readme" aria-label={file.name}>
      <h2>
        <FileIcon /> <a href={blobHref(file.path)}>{file.name}</a>
      </h2>
      {text.status === 'loading' && <p className="state" role="status">Loading…</p>}
      {text.status === 'ready' &&
        (markdown ? (
          <SourceMarkdown source={source} dir={path}>
            {text.data}
          </SourceMarkdown>
        ) : (
          <pre className="src-plain">{text.data}</pre>
        ))}
    </section>
  )
}

// ---------- the home ----------

function Stats({ source }) {
  const t = source.totals
  const items = [
    ['Files', t.files.toLocaleString('en')],
    ['Lines', t.lines.toLocaleString('en')],
    ['Commits', source.commitCount.toLocaleString('en')],
    ['Contributors', source.contributors.length.toLocaleString('en')],
    ['Tags', source.tags.length.toLocaleString('en')],
    ['Size', formatBytes(t.bytes)],
  ]
  return (
    <dl className="fstats">
      {items.map(([name, value]) => (
        <div key={name}>
          <dt>{name}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

function About({ source }) {
  const clone = source.repoUrl ? `git clone ${source.repoUrl}.git` : null
  const zip = ghArchive(source)
  return (
    <aside className="src-side" aria-label="About the code">
      {clone && (
        <section>
          <h2>Get the code</h2>
          <div className="src-clone">
            <code>{clone}</code>
            <CopyButton text={clone} label="Copy" />
          </div>
          <p className="src-links">
            {zip && (
              <a href={zip} target="_blank" rel="noopener noreferrer">
                Download as ZIP <ExternalIcon />
              </a>
            )}
            <a href={source.repoUrl} target="_blank" rel="noopener noreferrer">
              View on GitHub <ExternalIcon />
            </a>
          </p>
        </section>
      )}

      <section>
        <h2>Languages</h2>
        <LanguageBar languages={source.languages} />
      </section>

      {source.tags.length > 0 && (
        <section>
          <h2>Tags</h2>
          <ul className="src-mini">
            {source.tags.slice(0, 5).map((t) => (
              <li key={t.name}>
                <a href={source.bySha.has(t.sha) ? commitHref(t.sha) : tagsHref()}>{t.name}</a> <span className="muted"><Time iso={t.date} /></span>
              </li>
            ))}
          </ul>
          {source.tags.length > 5 && <p><a href={tagsHref()}>All {source.tags.length} tags</a></p>}
        </section>
      )}

      {source.contributors.length > 0 && (
        <section>
          <h2>Contributors</h2>
          <ul className="src-mini src-people">
            {source.contributors.slice(0, 10).map((p) => (
              <li key={p.login ?? p.name}>
                <SourceAvatar person={p} size={24} />
                {profileUrl(p) ? (
                  <a href={profileUrl(p)} target="_blank" rel="noopener noreferrer">
                    {p.name}
                  </a>
                ) : (
                  <span>{p.name}</span>
                )}
                <span className="muted">{plural(p.commits, 'commit')}</span>
              </li>
            ))}
          </ul>
          {source.contributors.length > 10 && <p className="muted">and {source.contributors.length - 10} more</p>}
        </section>
      )}
    </aside>
  )
}

function Home({ source }) {
  const gh = ghTree(source, '')
  return (
    <div className="src-home">
      <header className="src-hero">
        <div>
          <h1>Source</h1>
          <p className="lede">
            The code of {source.repoName}, to read here. This is a read-only copy of the <code>{source.branch}</code> branch.
          </p>
        </div>
        <div className="src-hero-actions">
          <a className="btn" href={searchHref('files')}>
            Find a file
          </a>
          {gh && (
            <a className="btn btn-quiet" href={gh} target="_blank" rel="noopener noreferrer">
              View on GitHub <ExternalIcon />
            </a>
          )}
        </div>
      </header>

      <Stats source={source} />
      <CommitBar source={source} index={source.commits.length ? 0 : null} historyPath="" />

      <div className="src-cols">
        <div className="src-main">
          <Listing source={source} path="" />
          <Readme source={source} path="" />
        </div>
        <About source={source} />
      </div>
    </div>
  )
}

// ---------- a folder ----------

function Tree({ source, route }) {
  const dir = source.dirs.get(route.path)
  if (!dir) {
    const isFile = source.byPath.has(route.path)
    return (
      <section className="forum-missing">
        <h1>{isFile ? 'That is a file' : 'Folder not found'}</h1>
        <p>
          {isFile ? (
            <>
              <code>{route.path}</code> is a file. <a href={blobHref(route.path)}>Open the file</a>.
            </>
          ) : (
            <>
              There is no folder called <code>{route.path}</code> in this copy of the code. <a href={sourceHref()}>Back to the code</a>.
            </>
          )}
        </p>
      </section>
    )
  }
  return (
    <div className="src-tree">
      <Crumbs source={source} path={route.path} />
      <header className="src-pagehead">
        <h1>{dir.name}</h1>
        <p className="muted">
          {plural(dir.fileCount, 'file')} · {formatBytes(dir.size)}
        </p>
      </header>
      <CommitBar source={source} index={dir.commit} historyPath={route.path} />
      <Listing source={source} path={route.path} />
      <Readme source={source} path={route.path} />
    </div>
  )
}

// ---------- tags ----------

function Tags({ source }) {
  return (
    <div className="src-tags">
      <header className="src-pagehead">
        <h1>Tags</h1>
        <p className="muted">
          {plural(source.tags.length, 'tag')}. Releases are built from these.
        </p>
      </header>
      {source.tags.length === 0 ? (
        <p className="empty">This project has no tags yet.</p>
      ) : (
        <ul className="src-taglist">
          {source.tags.map((t) => {
            const c = source.bySha.get(t.sha)
            return (
              <li key={t.name}>
                <h2>
                  <span className="chip chip-tag">{t.name}</span> <Time iso={t.date} />
                </h2>
                <p className="src-tagmsg">{t.subject}</p>
                <p className="src-links">
                  {c ? (
                    <a href={commitHref(c.sha)}>
                      Commit <span className="mono">{c.short}</span>
                    </a>
                  ) : (
                    <span className="muted mono">{t.sha.slice(0, 7)}</span>
                  )}
                  {source.repoUrl && (
                    <>
                      <a href={`${source.repoUrl}/releases/tag/${encodeURIComponent(t.name)}`} target="_blank" rel="noopener noreferrer">
                        Release <ExternalIcon />
                      </a>
                      <a href={`${source.repoUrl}/archive/refs/tags/${encodeURIComponent(t.name)}.zip`} target="_blank" rel="noopener noreferrer">
                        ZIP <ExternalIcon />
                      </a>
                    </>
                  )}
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

// ---------- the note under every page ----------

function SnapshotNote({ source }) {
  const c = source.commits[0]
  return (
    <p className="forum-note">
      This is a read-only copy of the <code>{source.branch}</code> branch
      {c && (
        <>
          {' '}
          at <a className="mono" href={commitHref(c.sha)}>{c.short}</a>
        </>
      )}
      , made when the site was last published{' '}
      <time dateTime={source.generatedAt} title={formatDateTime(source.generatedAt)}>
        <Time iso={source.generatedAt} />
      </time>
      . Changes made since then show up after the next publish.
      {source.repoUrl && (
        <>
          {' '}
          <a href={source.repoUrl} target="_blank" rel="noopener noreferrer">
            Open the repository on GitHub <ExternalIcon />
          </a>
        </>
      )}
    </p>
  )
}

// ---------- when there is nothing to show ----------

const UNAVAILABLE = {
  'no-git':
    "This copy of the site was built without the project's git history, so the code was not copied in. The website workflow does this each time it publishes the site.",
}

function Unavailable({ state }) {
  return (
    <section className="forum-missing">
      <h1>Source</h1>
      <p className="state">{UNAVAILABLE[state.reason] ?? 'The code is not available in this copy of the site.'}</p>
      {configRepoUrl && (
        <p>
          <a href={configRepoUrl} target="_blank" rel="noopener noreferrer">
            Open the code on GitHub <ExternalIcon />
          </a>
        </p>
      )}
    </section>
  )
}

function Problem({ state, onRetry }) {
  return (
    <section className="forum-missing">
      <h1>Source</h1>
      <p className="state" role="alert">
        {state.reason === 'missing'
          ? 'This build of the site has no copy of the code. Run npm run source in the site folder, or publish the site again.'
          : 'The code could not be loaded.'}{' '}
        <button type="button" className="textbtn" onClick={onRetry}>
          Try again
        </button>
      </p>
    </section>
  )
}

// ---------- the source area ----------

function titleOf(source, route) {
  const tail = 'Source - WhatCodeEditor'
  if (!source) return tail
  switch (route.view) {
    case 'tree':
      return `${route.path} - ${tail}`
    case 'blob':
      return `${route.path} - ${tail}`
    case 'commits':
      return `Commits - ${tail}`
    case 'history':
      return `History of ${route.path} - ${tail}`
    case 'commit': {
      const c = findCommit(source, route.sha)
      return c ? `${c.subject} (${c.short}) - ${tail}` : `Commit - ${tail}`
    }
    case 'tags':
      return `Tags - ${tail}`
    case 'search':
      return `Search - ${tail}`
    default:
      return tail
  }
}

/** The page body for a given load state. `route` says which page. */
export function SourceView({ state, route, onRetry }) {
  const source = state.status === 'ready' ? state.source : null
  useTitle(titleOf(source, route))

  if (state.status === 'loading') {
    return (
      <section className="forum-missing">
        <h1>Source</h1>
        <p className="state" role="status">Loading…</p>
      </section>
    )
  }
  if (state.status === 'unavailable') return <Unavailable state={state} />
  if (state.status === 'error') return <Problem state={state} onRetry={onRetry} />

  let body
  switch (route.view) {
    case 'tree':
      body = <Tree source={source} route={route} />
      break
    case 'blob':
      body = <Blob source={source} route={route} />
      break
    case 'commits':
    case 'history':
      body = <CommitsView source={source} route={route} />
      break
    case 'commit':
      body = <CommitView source={source} route={route} />
      break
    case 'tags':
      body = <Tags source={source} />
      break
    case 'search':
      body = <SourceSearch source={source} route={route} />
      break
    default:
      body = <Home source={source} />
  }

  return (
    <div className="src">
      <SourceTabs source={source} view={route.view} />
      {body}
      <SnapshotNote source={source} />
    </div>
  )
}

export default function Source({ route }) {
  const state = useSource()
  return <SourceView state={state} route={route} onRetry={state.retry} />
}

export { AuthorName, TagChips }
