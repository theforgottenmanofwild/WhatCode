import { useDeferredValue, useEffect, useMemo, useState } from 'react'
import { repoUrl as configRepoUrl } from '../config.js'
import {
  SORTS,
  STATUSES,
  categoryHref,
  excerpt,
  formatDateTime,
  pinStripe,
  plural,
  queryDiscussions,
  searchTerms,
  threadHref,
} from '../forum.js'
import { useForum } from '../useForum.js'
import { Avatar, ExternalIcon, Highlight, Label, Person, SnapshotNote, StatusPills, Time } from './ForumParts.jsx'
import Thread from './Thread.jsx'

const PAGE = 25
const DEFAULT_FILTERS = { query: '', status: 'all', label: null, sort: 'activity' }

// Filters survive going into a discussion and pressing Back. One entry per list (all discussions, or one category).
const memory = new Map()

function useTitle(text) {
  useEffect(() => {
    document.title = text
  }, [text])
}

// ---------- one row of the list ----------

function Row({ d, forum, terms, showCategory, stripe }) {
  const category = forum.bySlug.get(d.category)
  const text = `${d.title}\n${d.body}`.toLowerCase()
  const onlyInComments = terms.length > 0 && !terms.every((t) => text.includes(t))
  const preview = excerpt(d.body)
  const faces = d.participants.slice(0, 4)
  const more = d.participants.length - faces.length

  return (
    <li className={d.pinned ? 'drow is-pinned' : 'drow'}>
      {stripe && <span className="pin-stripe" style={stripe} aria-hidden="true" />}
      <div className="drow-votes">
        <span aria-hidden="true">▲</span>
        <strong aria-hidden="true">{d.upvotes}</strong>
        <span className="sr-only">{plural(d.upvotes, 'upvote')}</span>
      </div>
      <div className="drow-main">
        <h3 className="drow-title">
          <a href={threadHref(d.number)}>
            <Highlight text={d.title} terms={terms} />
          </a>
        </h3>
        <div className="drow-tags">
          <StatusPills d={d} answerable={category?.answerable} />
          {d.pinned && <span className="pill pill-pin">Pinned</span>}
          {d.poll && <span className="pill">Poll</span>}
          {showCategory && category && (
            <span className="chip">
              <span aria-hidden="true">{category.emoji}</span> {category.name}
            </span>
          )}
          {d.labels.map((l) => (
            <Label key={l.name} label={l} />
          ))}
        </div>
        {preview && (
          <p className="drow-excerpt">
            <Highlight text={preview} terms={terms} />
          </p>
        )}
        {onlyInComments && <p className="drow-hint">Matches in the comments</p>}
        <p className="drow-meta">
          <Avatar person={d.author} size={20} />
          <Person person={d.author} association={d.association} />
          <span>
            started <Time iso={d.createdAt} />
          </span>
          {d.lastActivity !== d.createdAt && (
            <span>
              · last activity <Time iso={d.lastActivity} />
            </span>
          )}
        </p>
      </div>
      <div className="drow-stats">
        <span className="count">
          <span aria-hidden="true">💬 {d.commentCount}</span>
          <span className="sr-only">{plural(d.commentCount, 'comment')}</span>
        </span>
        <ul className="faces" aria-label={plural(d.participants.length, 'participant')}>
          {faces.map((p) => (
            <li key={p.login} title={p.login}>
              <Avatar person={p} size={22} />
            </li>
          ))}
          {more > 0 && <li className="faces-more">+{more}</li>}
        </ul>
      </div>
    </li>
  )
}

// ---------- the filters and the list ----------

function SearchIcon() {
  return (
    <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
      <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="m13 13 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function DiscussionBrowser({ forum, category = null }) {
  const key = category ?? ''
  const [filters, setFilters] = useState(() => memory.get(key) ?? DEFAULT_FILTERS)
  const [limit, setLimit] = useState(PAGE)
  const query = useDeferredValue(filters.query)
  const terms = useMemo(() => searchTerms(query), [query])
  const searching = terms.length > 0

  const change = (patch) => {
    setFilters((prev) => {
      const next = { ...prev, ...patch }
      memory.set(key, next)
      return next
    })
    setLimit(PAGE)
  }

  const list = useMemo(
    () => queryDiscussions(forum, { category, status: filters.status, label: filters.label, query, sort: filters.sort }),
    [forum, category, filters.status, filters.label, filters.sort, query],
  )
  const pins = useMemo(() => new Map(forum.pinned.map((p) => [p.discussion.number, pinStripe(p.colors)])), [forum])
  const narrowed = Boolean(filters.query.trim() || filters.status !== 'all' || filters.label)
  const inCategory = forum.discussions.filter((d) => !category || d.category === category)
  const hasAnswerable = forum.categories.some((c) => c.answerable && (!category || c.slug === category))
  const statuses = STATUSES.filter((s) => hasAnswerable || (s.id !== 'answered' && s.id !== 'unanswered'))
  const labels = forum.labels.filter((l) => inCategory.some((d) => d.labels.some((x) => x.name === l.name)))
  const shown = list.slice(0, limit)

  return (
    <div className="browser">
      <form className="ftools" role="search" onSubmit={(e) => e.preventDefault()}>
        <div className="search-field">
          <SearchIcon />
          <input
            type="search"
            value={filters.query}
            onChange={(e) => change({ query: e.target.value })}
            placeholder="Search titles, text, comments, labels and people"
            aria-label="Search discussions"
            autoComplete="off"
            spellCheck="false"
          />
        </div>

        <div className="ftools-row">
          <div className="seg" role="group" aria-label="Show">
            {statuses.map((s) => (
              <button key={s.id} type="button" aria-pressed={filters.status === s.id} onClick={() => change({ status: s.id })}>
                {s.label}
              </button>
            ))}
          </div>
          <label className="select">
            <span>Sort</span>
            <select value={searching ? 'match' : filters.sort} disabled={searching} onChange={(e) => change({ sort: e.target.value })}>
              {searching && <option value="match">Best match</option>}
              {SORTS.map((s) => (
                <option key={s.id} value={s.id}>{s.label}</option>
              ))}
            </select>
          </label>
        </div>

        {labels.length > 0 && (
          <div className="label-filter" role="group" aria-label="Filter by label">
            {labels.map((l) => (
              <Label
                key={l.name}
                label={l}
                as="button"
                type="button"
                aria-pressed={filters.label === l.name}
                onClick={() => change({ label: filters.label === l.name ? null : l.name })}
              />
            ))}
          </div>
        )}
      </form>

      <p className="result-count" role="status">
        {narrowed ? `${plural(list.length, 'discussion')} found` : plural(list.length, 'discussion')}
        {narrowed && (
          <>
            {' '}
            <button type="button" className="textbtn" onClick={() => change(DEFAULT_FILTERS)}>
              Clear filters
            </button>
          </>
        )}
      </p>

      {list.length > 0 ? (
        <ul className="dlist">
          {shown.map((d) => (
            <Row key={d.number} d={d} forum={forum} terms={terms} showCategory={!category} stripe={d.pinned && !narrowed && !category ? pins.get(d.number) : null} />
          ))}
        </ul>
      ) : (
        <p className="empty">
          {inCategory.length === 0
            ? 'There are no discussions here yet.'
            : 'No discussion matches. Try other words, or clear the filters.'}
        </p>
      )}

      {list.length > shown.length && (
        <p className="more">
          <button type="button" className="btn btn-quiet" onClick={() => setLimit((n) => n + PAGE)}>
            Show {Math.min(PAGE, list.length - shown.length)} more
          </button>
          <span>{plural(list.length - shown.length, 'discussion')} not shown</span>
        </p>
      )}
    </div>
  )
}

// ---------- the forum home ----------

function Stats({ forum }) {
  const s = forum.stats
  const items = [
    ['Discussions', s.discussions.toLocaleString('en')],
    ['Comments', s.comments.toLocaleString('en')],
    ['People', s.people.toLocaleString('en')],
    ['Answered', s.answered.toLocaleString('en')],
  ]
  return (
    <dl className="fstats">
      {items.map(([name, value]) => (
        <div key={name}>
          <dt>{name}</dt>
          <dd>{value}</dd>
        </div>
      ))}
      {s.lastActivity && (
        <div>
          <dt>Last activity</dt>
          <dd><Time iso={s.lastActivity} /></dd>
        </div>
      )}
    </dl>
  )
}

function CategoryRow({ c, forum }) {
  const latest = c.latest ? forum.byNumber.get(c.latest) : null
  return (
    <li className="cat">
      <span className="cat-emoji" aria-hidden="true">{c.emoji}</span>
      <div className="cat-main">
        <h3>
          <a href={categoryHref(c.slug)}>{c.name}</a>
          {c.answerable && <span className="badge">Marked answers</span>}
        </h3>
        {c.description && <p className="cat-desc">{c.description}</p>}
        <p className="cat-last">
          {latest ? (
            <>
              Latest: <a href={threadHref(latest.number)}>{latest.title}</a> <Time iso={c.lastActivity} />
            </>
          ) : (
            'No discussions yet'
          )}
        </p>
      </div>
      <dl className="cat-stats">
        <div>
          <dt>Discussions</dt>
          <dd>{c.count}</dd>
        </div>
        <div>
          <dt>Comments</dt>
          <dd>{c.comments}</dd>
        </div>
      </dl>
    </li>
  )
}

function StartButton({ forum, slug }) {
  if (!forum.repoUrl) return null
  const href = slug ? `${forum.repoUrl}/discussions/new?category=${encodeURIComponent(slug)}` : `${forum.repoUrl}/discussions/new/choose`
  return (
    <div className="forums-cta">
      <a className="btn" href={href} target="_blank" rel="noopener noreferrer">
        Start a discussion <ExternalIcon />
      </a>
      <small>Posting needs a free GitHub account.</small>
    </div>
  )
}

function ForumHome({ forum }) {
  return (
    <div className="forums">
      <header className="forums-hero">
        <div>
          <h1>Forums</h1>
          <p className="lede">Questions, ideas and news from the people who use WhatCodeEditor.</p>
        </div>
        <StartButton forum={forum} />
      </header>

      <Stats forum={forum} />

      {forum.categories.length > 0 && (
        <section aria-labelledby="cats-title">
          <h2 id="cats-title">Categories</h2>
          <ul className="cats">
            {forum.categories.map((c) => (
              <CategoryRow key={c.slug} c={c} forum={forum} />
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="all-title">
        <h2 id="all-title">All discussions</h2>
        <DiscussionBrowser forum={forum} />
      </section>

      <SnapshotNote forum={forum} />
    </div>
  )
}

// ---------- one category ----------

function CategoryPage({ forum, slug }) {
  const c = forum.bySlug.get(slug)
  if (!c) {
    return (
      <section className="forum-missing">
        <h1>Category not found</h1>
        <p>
          There is no category called <code>{slug}</code>. <a href="#/forums">Back to the forums</a>.
        </p>
      </section>
    )
  }
  return (
    <div className="forums">
      <nav className="crumbs" aria-label="Breadcrumb">
        <a href="#/forums">Forums</a>
        <span aria-hidden="true"> › </span>
        <span aria-current="page">{c.name}</span>
      </nav>

      <header className="forums-hero">
        <div>
          <h1>
            <span aria-hidden="true">{c.emoji}</span> {c.name}
          </h1>
          {c.description && <p className="lede">{c.description}</p>}
          <p className="muted">
            {plural(c.count, 'discussion')} · {plural(c.comments, 'comment')}
            {c.answerable && ' · answers can be marked'}
          </p>
        </div>
        <StartButton forum={forum} slug={c.slug} />
      </header>

      <nav className="catnav" aria-label="Categories">
        <a href="#/forums">All</a>
        {forum.categories.map((x) => (
          <a key={x.slug} href={categoryHref(x.slug)} aria-current={x.slug === c.slug ? 'page' : undefined}>
            <span aria-hidden="true">{x.emoji}</span> {x.name}
          </a>
        ))}
      </nav>

      <DiscussionBrowser forum={forum} category={c.slug} />

      <SnapshotNote forum={forum} />
    </div>
  )
}

// ---------- when there is nothing to show ----------

const UNAVAILABLE = {
  'no-token':
    'This copy of the site was built without access to GitHub, so the discussions were not copied in. The website workflow does this each time it publishes the site.',
  'no-repository': 'This copy of the site was built without knowing which repository to read, so the discussions were not copied in.',
  disabled: 'Discussions is turned off for this project on GitHub, so there is nothing to show yet.',
}

function Unavailable({ state }) {
  return (
    <section className="forum-missing">
      <h1>Forums</h1>
      <p className="state">{UNAVAILABLE[state.reason] ?? 'The discussions are not available in this copy of the site.'}</p>
      {configRepoUrl && (
        <p>
          <a href={`${configRepoUrl}/discussions`} target="_blank" rel="noopener noreferrer">
            Open the discussions on GitHub <ExternalIcon />
          </a>
        </p>
      )}
      {state.generatedAt && (
        <p className="muted">
          Checked <time dateTime={state.generatedAt} title={formatDateTime(state.generatedAt)}>{formatDateTime(state.generatedAt)}</time>.
        </p>
      )}
    </section>
  )
}

function Problem({ state, onRetry }) {
  return (
    <section className="forum-missing">
      <h1>Forums</h1>
      <p className="state" role="alert">
        {state.reason === 'missing'
          ? 'This build of the site has no copy of the discussions. Run npm run discussions in the site folder, or publish the site again.'
          : 'The forums could not be loaded.'}{' '}
        <button type="button" className="textbtn" onClick={onRetry}>Try again</button>
      </p>
    </section>
  )
}

// ---------- the forums area ----------

/** The page body for a given load state. `route` says which page: the home, a category or one discussion. */
export function ForumsView({ state, route, onRetry }) {
  const forum = state.status === 'ready' ? state.forum : null
  const thread = forum && route.thread ? forum.byNumber.get(route.thread) : null
  const category = forum && route.category ? forum.bySlug.get(route.category) : null
  useTitle(
    thread
      ? `${thread.title} - Forums - WhatCodeEditor`
      : category
        ? `${category.name} - Forums - WhatCodeEditor`
        : 'Forums - WhatCodeEditor',
  )

  if (state.status === 'loading') {
    return (
      <section className="forum-missing">
        <h1>Forums</h1>
        <p className="state" role="status">Loading…</p>
      </section>
    )
  }
  if (state.status === 'unavailable') return <Unavailable state={state} />
  if (state.status === 'error') return <Problem state={state} onRetry={onRetry} />
  if (route.thread) return <Thread forum={forum} number={route.thread} anchor={route.anchor} />
  if (route.category) return <CategoryPage forum={forum} slug={route.category} />
  return <ForumHome forum={forum} />
}

export default function Forums({ route }) {
  const state = useForum()
  return <ForumsView state={state} route={route} onRetry={state.retry} />
}
