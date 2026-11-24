import { useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import {
  REACTIONS,
  associationLabel,
  avatarSrc,
  formatDateTime,
  isSiteHref,
  labelStyle,
  localHref,
  profileUrl,
  remarkLinkify,
  remarkSoftBreaks,
  stateReasonLabel,
} from '../forum.js'
import { relativeTime } from '../releases.js'

// ---------- people ----------

/** The picture, or the first letter when there is none (a deleted account, or a host we do not load pictures from). */
export function Avatar({ person, size = 32 }) {
  const src = person ? avatarSrc(person.avatar, size * 2) : null
  if (src) {
    return (
      <img
        className="avatar"
        src={src}
        width={size}
        height={size}
        alt=""
        loading="lazy"
        decoding="async"
        referrerPolicy="no-referrer"
      />
    )
  }
  return (
    <span className="avatar avatar-blank" style={{ width: size, height: size, fontSize: size * 0.45 }} aria-hidden="true">
      {(person?.login ?? '?').charAt(0).toUpperCase()}
    </span>
  )
}

/** A name, linked to the person's GitHub page, with how they relate to the project (Owner, Contributor...). */
export function Person({ person, association }) {
  const badge = associationLabel(association)
  const href = profileUrl(person)
  return (
    <span className="person">
      {person ? (
        href ? (
          <a className="person-name" href={href} target="_blank" rel="noopener noreferrer">
            {person.login}
          </a>
        ) : (
          <span className="person-name">{person.login}</span>
        )
      ) : (
        <span className="person-name person-ghost" title="This account no longer exists">ghost</span>
      )}
      {badge && <span className="badge badge-role">{badge}</span>}
    </span>
  )
}

// ---------- times ----------

export function Time({ iso }) {
  if (!iso) return null
  return (
    <time dateTime={iso} title={formatDateTime(iso)}>
      {relativeTime(iso)}
    </time>
  )
}

// ---------- labels, status, reactions ----------

export function Label({ label, as: Tag = 'span', ...rest }) {
  return (
    <Tag className="label" style={labelStyle(label.color)} title={label.description || undefined} {...rest}>
      {label.name}
    </Tag>
  )
}

export function Labels({ labels }) {
  if (!labels?.length) return null
  return (
    <ul className="labels" aria-label="Labels">
      {labels.map((l) => (
        <li key={l.name}>
          <Label label={l} />
        </li>
      ))}
    </ul>
  )
}

/** Open / Answered / Closed (with GitHub's reason) / Locked, as small pills. */
export function StatusPills({ d, answerable }) {
  const reason = stateReasonLabel(d.stateReason)
  return (
    <>
      {d.closed ? (
        <span className="pill pill-closed">{reason && d.stateReason !== 'REOPENED' ? `Closed as ${reason}` : 'Closed'}</span>
      ) : (
        <span className="pill pill-open">{d.stateReason === 'REOPENED' ? 'Reopened' : 'Open'}</span>
      )}
      {d.answered ? (
        <span className="pill pill-answered">Answered</span>
      ) : (
        answerable && !d.closed && <span className="pill pill-unanswered">Unanswered</span>
      )}
      {d.locked && <span className="pill pill-locked">Locked</span>}
    </>
  )
}

/** Upvotes first, then every emoji reaction somebody used. */
export function Reactions({ upvotes = 0, reactions = [] }) {
  if (!upvotes && !reactions.length) return null
  return (
    <ul className="reactions" aria-label="Reactions">
      {upvotes > 0 && (
        <li aria-label={`${upvotes} ${upvotes === 1 ? 'upvote' : 'upvotes'}`}>
          <span aria-hidden="true">▲</span> {upvotes}
        </li>
      )}
      {reactions.map((r) => {
        const info = REACTIONS[r.content]
        return (
          <li key={r.content} aria-label={`${r.count} ${info?.name ?? r.content.toLowerCase().replace(/_/g, ' ')}`}>
            <span aria-hidden="true">{info?.emoji ?? '❔'}</span> {r.count}
          </li>
        )
      })}
    </ul>
  )
}

// ---------- Markdown ----------

// Posts may contain HTML (<details>, <img>, <kbd>...). It is parsed and then cleaned with GitHub's own allow-list,
// so scripts, event handlers and javascript: links never reach the page.
const rehypePlugins = [rehypeRaw, rehypeSanitize]
const remarkRehypeOptions = { allowDangerousHtml: true }

// A link like #fn-1 would be read as a page route. Scroll to the element instead (GitHub's sanitiser prefixes the ids).
function jump(e, href) {
  e.preventDefault()
  let id = href.slice(1)
  try {
    id = decodeURIComponent(id)
  } catch {
    // keep it as written
  }
  const target = document.getElementById(id) ?? document.getElementById(`user-content-${id}`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function makeComponents(forum) {
  return {
    a({ node, href = '', children, ...rest }) {
      const inside = localHref(href, forum)
      if (inside) return <a {...rest} href={inside}>{children}</a>
      if (isSiteHref(href)) return <a {...rest} href={href}>{children}</a>
      if (href.startsWith('#')) {
        return (
          <a {...rest} href={href} onClick={(e) => jump(e, href)}>
            {children}
          </a>
        )
      }
      // "/owner/repo/pull/3" in a post means github.com/owner/repo/pull/3
      const absolute = href.startsWith('/') && !href.startsWith('//') ? `https://github.com${href}` : href
      if (/^(https?:|mailto:)/i.test(absolute)) {
        return (
          <a {...rest} href={absolute} target="_blank" rel="noopener noreferrer nofollow ugc">
            {children}
          </a>
        )
      }
      return <span>{children}</span>
    },
    img({ node, src, alt, ...rest }) {
      return <img {...rest} src={src} alt={alt ?? ''} loading="lazy" decoding="async" referrerPolicy="no-referrer" />
    },
    table({ node, children }) {
      return (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Table, scrolls sideways">
          <table>{children}</table>
        </div>
      )
    },
  }
}

export function Markdown({ children, forum }) {
  const remarkPlugins = useMemo(
    () => [remarkGfm, remarkSoftBreaks, [remarkLinkify, { known: forum.known, repo: forum.repo }]],
    [forum],
  )
  const components = useMemo(() => makeComponents(forum), [forum])
  return (
    <div className="fmd">
      <ReactMarkdown
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        remarkRehypeOptions={remarkRehypeOptions}
        components={components}
      >
        {String(children ?? '').replace(/\r\n?/g, '\n')}
      </ReactMarkdown>
    </div>
  )
}

// ---------- small shared bits ----------

export function Highlight({ text, terms }) {
  if (!terms?.length) return text
  const escaped = terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).sort((a, b) => b.length - a.length)
  const re = new RegExp(`(${escaped.join('|')})`, 'gi')
  return text.split(re).map((part, i) => (i % 2 ? <mark key={i}>{part}</mark> : part))
}

export function ExternalIcon() {
  return (
    <svg className="ext" viewBox="0 0 12 12" width="11" height="11" aria-hidden="true" focusable="false">
      <path d="M5 2H2.5A.5.5 0 0 0 2 2.5v7a.5.5 0 0 0 .5.5h7a.5.5 0 0 0 .5-.5V7M7 2h3v3M10 2 5.5 6.5" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function SnapshotNote({ forum }) {
  if (!forum.generatedAt) return null
  return (
    <p className="forum-note">
      These discussions are copied from {forum.repoUrl ? <a href={`${forum.repoUrl}/discussions`} target="_blank" rel="noopener noreferrer">GitHub Discussions</a> : 'GitHub Discussions'}{' '}
      whenever something changes and every few hours. Last copied{' '}
      <time dateTime={forum.generatedAt} title={formatDateTime(forum.generatedAt)}>{relativeTime(forum.generatedAt)}</time>.
      New posts and replies appear after the next copy.
    </p>
  )
}
