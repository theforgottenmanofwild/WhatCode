// Everything the Forums pages need that is not drawing: indexing the snapshot, sorting, filtering, searching,
// and the words for GitHub's enum values. No React in here, so the smoke test can check it directly.
import { BASE, to } from './base.js'

export const REACTIONS = {
  THUMBS_UP: { emoji: '👍', name: 'thumbs up' },
  THUMBS_DOWN: { emoji: '👎', name: 'thumbs down' },
  LAUGH: { emoji: '😄', name: 'laugh' },
  HOORAY: { emoji: '🎉', name: 'hooray' },
  CONFUSED: { emoji: '😕', name: 'confused' },
  HEART: { emoji: '❤️', name: 'heart' },
  ROCKET: { emoji: '🚀', name: 'rocket' },
  EYES: { emoji: '👀', name: 'eyes' },
}

/** How the author relates to the repository. NONE and MANNEQUIN get no badge. */
const ASSOCIATIONS = {
  OWNER: 'Owner',
  MEMBER: 'Member',
  COLLABORATOR: 'Collaborator',
  CONTRIBUTOR: 'Contributor',
  FIRST_TIME_CONTRIBUTOR: 'First-time contributor',
  FIRST_TIMER: 'First-timer',
}
export const associationLabel = (value) => ASSOCIATIONS[value] ?? null

const LOCK_REASONS = { OFF_TOPIC: 'off-topic', RESOLVED: 'resolved', SPAM: 'spam', TOO_HEATED: 'too heated' }
export const lockReasonLabel = (value) => LOCK_REASONS[value] ?? null

const STATE_REASONS = { RESOLVED: 'resolved', OUTDATED: 'outdated', DUPLICATE: 'a duplicate', REOPENED: 'reopened' }
export const stateReasonLabel = (value) => STATE_REASONS[value] ?? null

/** GitHub's own words for hiding a comment: spam, abuse, off-topic, outdated, duplicate, resolved. */
export const hiddenReasonLabel = (value) => String(value ?? 'hidden').replace(/[-_]/g, ' ')

export const SORTS = [
  { id: 'activity', label: 'Latest activity' },
  { id: 'new', label: 'Newest' },
  { id: 'old', label: 'Oldest' },
  { id: 'top', label: 'Most upvoted' },
  { id: 'busy', label: 'Most comments' },
]

export const STATUSES = [
  { id: 'all', label: 'All' },
  { id: 'open', label: 'Open' },
  { id: 'answered', label: 'Answered' },
  { id: 'unanswered', label: 'Unanswered' },
  { id: 'closed', label: 'Closed' },
]

export const COMMENT_SORTS = [
  { id: 'old', label: 'Oldest first' },
  { id: 'new', label: 'Newest first' },
  { id: 'top', label: 'Most upvoted' },
]

export const threadHref = (number, anchor) => to(`/forums/${number}${anchor ? `#${anchor}` : ''}`)
export const categoryHref = (slug) => to(`/forums/c/${encodeURIComponent(slug)}`)

/** True for the links this file makes to the forums themselves (not for "/owner/repo/pull/3" written in a post, which is GitHub's). */
export const isSiteHref = (href) =>
  typeof href === 'string' && href.startsWith(`${BASE}/forums`) && /^\/forums(\/c\/[^/#?]+|\/\d+)?(#.*)?$/.test(href.slice(BASE.length))
export const commentAnchor = (id) => `comment-${id}`

export const plural = (n, one, many = `${one}s`) => `${n.toLocaleString('en')} ${n === 1 ? one : many}`

/** "Nov 24, 2026, 11:13 AM", for the tooltip on a relative time. */
export function formatDateTime(iso) {
  try {
    return iso ? new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso)) : ''
  } catch {
    return String(iso ?? '')
  }
}

/** A person's GitHub page, only when it really is one. */
export const profileUrl = (person) =>
  person && typeof person.url === 'string' && person.url.startsWith('https://github.com/') ? person.url : null

/** A few plain words from the start of some Markdown, for the discussion list. */
export function excerpt(markdown, max = 180) {
  const text = String(markdown ?? '')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/(```|~~~)[\s\S]*?(\1|$)/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, ' ')
    .replace(/^\s{0,3}(#{1,6}\s+|>\s?|[-*+]\s+(\[[ xX]\]\s+)?|\d+\.\s+)/gm, '')
    .replace(/[*`~]+|(?<!\w)_+|_+(?!\w)/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (text.length <= max) return text
  const cut = text.slice(0, max)
  return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), Math.floor(max * 0.6))).trimEnd()}…`
}

const stamp = (iso) => (iso ? new Date(iso).getTime() || 0 : 0)

/** A comment and its replies, in one flat list. */
export function flattenComments(comments) {
  const out = []
  for (const c of comments) {
    out.push(c)
    for (const r of c.replies ?? []) out.push(r)
  }
  return out
}

function decorate(d, pinned) {
  const all = flattenComments(d.comments)
  let last = Math.max(stamp(d.createdAt), stamp(d.updatedAt), stamp(d.editedAt))
  for (const c of all) last = Math.max(last, stamp(c.createdAt), stamp(c.editedAt))

  // Who took part, in order of first appearance (the author first).
  const seen = new Map()
  const add = (a) => {
    if (a && !seen.has(a.login)) seen.set(a.login, a)
  }
  add(d.author)
  for (const c of all) add(c.author)

  const visible = all.filter((c) => !c.deleted)
  return {
    ...d,
    pinned: pinned.has(d.number),
    commentCount: visible.length,
    topLevelCount: d.comments.filter((c) => !c.deleted).length,
    lastActivity: new Date(last || stamp(d.createdAt)).toISOString(),
    participants: [...seen.values()],
    answer: all.find((c) => c.isAnswer) ?? null,
    // Lower-cased text of the whole thread, so searching is one `includes` per word.
    haystack: [
      d.title,
      d.body,
      d.author?.login,
      ...d.labels.map((l) => l.name),
      ...all.map((c) => `${c.body} ${c.author?.login ?? ''}`),
    ]
      .join('\n')
      .toLowerCase(),
  }
}

/** Adds what the pages need to the raw snapshot: counts, last activity, participants, lookups. */
export function indexForum(data) {
  const pinned = new Set((data.pinned ?? []).map((p) => p.number))
  const discussions = (data.discussions ?? []).map((d) => decorate(d, pinned))
  const byNumber = new Map(discussions.map((d) => [d.number, d]))

  const categories = (data.categories ?? []).map((c) => {
    const items = discussions.filter((d) => d.category === c.slug)
    const latest = items.reduce((best, d) => (!best || stamp(d.lastActivity) > stamp(best.lastActivity) ? d : best), null)
    return {
      ...c,
      count: items.length,
      comments: items.reduce((n, d) => n + d.commentCount, 0),
      lastActivity: latest ? latest.lastActivity : null,
      latest: latest ? latest.number : null,
    }
  })
  const bySlug = new Map(categories.map((c) => [c.slug, c]))

  const labels = new Map()
  for (const d of discussions) {
    for (const l of d.labels) {
      const entry = labels.get(l.name) ?? { ...l, count: 0 }
      entry.count += 1
      labels.set(l.name, entry)
    }
  }

  const people = new Set()
  let comments = 0
  for (const d of discussions) {
    comments += d.commentCount
    for (const p of d.participants) people.add(p.login)
  }
  const last = discussions.reduce((m, d) => Math.max(m, stamp(d.lastActivity)), 0)

  return {
    generatedAt: data.generatedAt,
    repo: data.repo,
    repoUrl: data.repo ? `https://github.com/${data.repo}` : null,
    categories,
    bySlug,
    discussions,
    byNumber,
    known: new Set(byNumber.keys()),
    pinned: (data.pinned ?? [])
      .map((p) => (byNumber.has(p.number) ? { discussion: byNumber.get(p.number), colors: p.colors ?? [] } : null))
      .filter(Boolean),
    labels: [...labels.values()].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name)),
    stats: {
      discussions: discussions.length,
      comments,
      people: people.size,
      answered: discussions.filter((d) => d.answered).length,
      lastActivity: last ? new Date(last).toISOString() : null,
    },
  }
}

const SORTERS = {
  activity: (a, b) => stamp(b.lastActivity) - stamp(a.lastActivity),
  new: (a, b) => stamp(b.createdAt) - stamp(a.createdAt),
  old: (a, b) => stamp(a.createdAt) - stamp(b.createdAt),
  top: (a, b) => b.upvotes - a.upvotes || stamp(b.lastActivity) - stamp(a.lastActivity),
  busy: (a, b) => b.commentCount - a.commentCount || stamp(b.lastActivity) - stamp(a.lastActivity),
}

export const searchTerms = (query) => String(query ?? '').toLowerCase().split(/\s+/).filter(Boolean)

/** Title hits count most, then labels and author, then the text. Returns 0 when a word is missing. */
function relevance(d, terms) {
  let score = 0
  const title = d.title.toLowerCase()
  for (const t of terms) {
    if (!d.haystack.includes(t)) return 0
    score += 1
    if (title.includes(t)) score += 5
    if (d.labels.some((l) => l.name.toLowerCase().includes(t))) score += 2
  }
  return score
}

/**
 * The discussions to list. With a search the best match comes first; otherwise the chosen sort applies,
 * with pinned discussions on top when nothing narrows the list.
 */
export function queryDiscussions(forum, { category = null, status = 'all', label = null, query = '', sort = 'activity' } = {}) {
  const terms = searchTerms(query)
  let list = forum.discussions.filter((d) => {
    if (category && d.category !== category) return false
    if (label && !d.labels.some((l) => l.name === label)) return false
    const answerable = forum.bySlug.get(d.category)?.answerable
    if (status === 'open' && d.closed) return false
    if (status === 'closed' && !d.closed) return false
    if (status === 'answered' && !d.answered) return false
    if (status === 'unanswered' && !(answerable && !d.answered)) return false
    return true
  })
  if (terms.length) {
    return list
      .map((d) => ({ d, score: relevance(d, terms) }))
      .filter((x) => x.score > 0)
      .sort((a, b) => b.score - a.score || SORTERS.activity(a.d, b.d))
      .map((x) => x.d)
  }
  list = [...list].sort(SORTERS[sort] ?? SORTERS.activity)
  const narrowed = category || status !== 'all' || label
  return narrowed ? list : [...list.filter((d) => d.pinned), ...list.filter((d) => !d.pinned)]
}

const COMMENT_SORTERS = {
  old: (a, b) => stamp(a.createdAt) - stamp(b.createdAt),
  new: (a, b) => stamp(b.createdAt) - stamp(a.createdAt),
  top: (a, b) => b.upvotes - a.upvotes || stamp(a.createdAt) - stamp(b.createdAt),
}

/** Top-level comments in the chosen order. Replies always stay oldest first, under their comment. */
export function sortComments(comments, sort = 'old') {
  const sorted = [...comments].sort(COMMENT_SORTERS[sort] ?? COMMENT_SORTERS.old)
  return sorted.map((c) => ({ ...c, replies: [...(c.replies ?? [])].sort(COMMENT_SORTERS.old) }))
}

/** Share of the vote for each poll option, in whole percent (0 when nobody has voted). */
export function pollShares(poll) {
  const total = poll.options.reduce((n, o) => n + o.votes, 0) || poll.total || 0
  const top = Math.max(0, ...poll.options.map((o) => o.votes))
  return poll.options.map((o) => ({
    ...o,
    percent: total ? Math.round((o.votes / total) * 100) : 0,
    leading: top > 0 && o.votes === top,
  }))
}

/** Avatars come from GitHub's own host only, at the size asked for. Anything else gets no picture. */
export function avatarSrc(url, size = 64) {
  try {
    const u = new URL(url)
    if (u.protocol !== 'https:' || u.hostname !== 'avatars.githubusercontent.com') return null
    u.searchParams.set('s', String(size))
    return u.toString()
  } catch {
    return null
  }
}

/** A label's colour is a bare hex value; the text on it is dark or light, whichever reads better. */
export function labelStyle(color) {
  if (!/^[0-9a-f]{6}$/i.test(color ?? '')) return {}
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(color.slice(i, i + 2), 16) / 255)
  const lin = (v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
  const luminance = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
  return { backgroundColor: `#${color}`, color: luminance > 0.4 ? '#1b2430' : '#ffffff' }
}

/** A pinned card's accent: a stripe between the two colours GitHub stores for the pin, when both are valid. */
export function pinStripe(colors = []) {
  const ok = colors.filter((c) => /^[0-9a-f]{6}$/i.test(c))
  return ok.length >= 2 ? { backgroundImage: `linear-gradient(90deg, #${ok[0]}, #${ok[1]})` } : {}
}

// ---------------------------------------------------------------- markdown: @mentions and #numbers

const REF = /(?<![\w`/.&@#-])(?:@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))|#(\d{1,6}))(?![\w-])/g
const SKIP = new Set(['link', 'linkReference', 'inlineCode', 'code', 'html'])
const link = (url, text) => ({ type: 'link', url, title: null, children: [{ type: 'text', value: text }] })

export function linkifyText(value, { known = new Set(), repo = null } = {}) {
  const out = []
  let last = 0
  for (const m of value.matchAll(REF)) {
    if (m.index > last) out.push({ type: 'text', value: value.slice(last, m.index) })
    if (m[1]) {
      out.push(link(`https://github.com/${m[1]}`, `@${m[1]}`))
    } else {
      const n = Number(m[2])
      // A number that is one of these discussions opens here; any other is an issue or pull request on GitHub.
      const href = known.has(n) ? threadHref(n) : repo ? `https://github.com/${repo}/issues/${n}` : null
      out.push(href ? link(href, `#${n}`) : { type: 'text', value: `#${n}` })
    }
    last = m.index + m[0].length
  }
  if (last < value.length) out.push({ type: 'text', value: value.slice(last) })
  return out.length ? out : [{ type: 'text', value }]
}

/** A remark plugin: turns @name and #123 in running text into links, leaving code and existing links alone. */
export function remarkLinkify(options = {}) {
  return (tree) => {
    const walk = (node) => {
      if (!node.children) return
      const next = []
      for (const child of node.children) {
        if (child.type === 'text') {
          next.push(...linkifyText(child.value, options))
        } else {
          if (!SKIP.has(child.type)) walk(child)
          next.push(child)
        }
      }
      node.children = next
    }
    walk(tree)
  }
}

/** A remark plugin: GitHub keeps a single line break inside a post as a line break; plain Markdown folds it into a space. */
export function remarkSoftBreaks() {
  return (tree) => {
    const walk = (node) => {
      if (!node.children) return
      const next = []
      for (const child of node.children) {
        if (child.type === 'text' && child.value.includes('\n')) {
          child.value.split('\n').forEach((part, i) => {
            if (i) next.push({ type: 'break' })
            if (part) next.push({ type: 'text', value: part })
          })
        } else {
          walk(child)
          next.push(child)
        }
      }
      node.children = next
    }
    walk(tree)
  }
}

/**
 * A link to this repository's own discussions, categories or discussion list becomes a link inside the site,
 * so reading a thread never sends anyone to GitHub by accident. Anything else returns null.
 */
export function localHref(href, forum) {
  if (!forum?.repo || typeof href !== 'string') return null
  const m = href.match(/^https:\/\/github\.com\/([\w.-]+)\/([\w.-]+)\/discussions(?:\/(\d+)|\/categories\/([\w-]+))?\/?(?:[?#].*)?$/i)
  if (!m || `${m[1]}/${m[2]}`.toLowerCase() !== forum.repo.toLowerCase()) return null
  if (m[3]) return forum.known.has(Number(m[3])) ? threadHref(Number(m[3])) : null
  if (m[4]) return forum.bySlug.has(m[4]) ? categoryHref(m[4]) : null
  return to('/forums')
}
