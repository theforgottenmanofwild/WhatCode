// Documentation lives in ../docs (repo root) so it reads well on GitHub too.
// A folder is a section, a file is a page; numeric prefixes only set the order
// (see docs/README.md). Adding a page or a section needs no code change.
const files = import.meta.glob('../../docs/*/*.md', { query: '?raw', import: 'default', eager: true })

const byName = (a, b) => a.localeCompare(b, 'en', { numeric: true })
const stripNumber = (name) => name.replace(/^\d+-/, '')
const sentenceCase = (text) => text.charAt(0).toUpperCase() + text.slice(1)

// ---------- text helpers (shared with the renderer so heading ids always agree) ----------

// Markdown inline syntax -> the text a reader sees.
export function plain(md) {
  return md
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, '')
    .replace(/`+/g, '')
    .replace(/\*+|~~/g, '')
    .replace(/(^|[^\w])_([^_\n]+)_(?=[^\w]|$)/g, '$1$2')
    .replace(/\s+/g, ' ')
    .trim()
}

// GitHub-style heading ids: "Changing the keys" -> "changing-the-keys", repeats get -1, -2.
export function makeSlugger() {
  const seen = new Map()
  return (text) => {
    const base =
      text
        .toLowerCase()
        .trim()
        .replace(/[^\p{L}\p{N}\s_-]/gu, '')
        .replace(/\s+/g, '-') || 'section'
    const n = seen.get(base) ?? 0
    seen.set(base, n + 1)
    return n === 0 ? base : `${base}-${n}`
  }
}

// A block of markdown lines -> one line of searchable text.
function searchText(lines) {
  return plain(
    lines
      .filter((l) => !/^\s*(```|~~~)/.test(l) && !/^\s*\|?[\s:|-]+\|?\s*$/.test(l))
      .map((l) => l.replace(/^\s*(>|[-*+]\s|\d+\.\s)/, ' ').replace(/\|/g, ' '))
      .join(' '),
  )
}

// ---------- parsing one page ----------

function parsePage(raw) {
  const lines = raw.replace(/\r\n/g, '\n').split('\n')
  const slugger = makeSlugger()
  const headings = [] // ## and ### only: what "On this page" lists
  const chunks = [] // the text under each heading, so search can link to the exact spot
  let title = null
  let summary = ''
  let current = { heading: null, id: null, lines: [] }
  let inFence = false
  let intro = true // between the title and the first ## heading
  let para = []

  const endPara = () => {
    if (intro && !summary && para.length) summary = plain(para.join(' '))
    para = []
  }

  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      if (intro && !summary) endPara()
      current.lines.push(line)
      continue
    }
    if (!inFence) {
      const m = line.match(/^(#{1,3})\s+(.+?)\s*#*\s*$/)
      if (m) {
        const level = m[1].length
        const text = plain(m[2])
        if (level === 1 && title === null) {
          title = text
          continue
        }
        if (level >= 2) {
          endPara()
          intro = false
          chunks.push(current)
          const id = slugger(text)
          headings.push({ level, text, id })
          current = { heading: text, id, lines: [] }
          continue
        }
      }
      if (intro && !summary) {
        const t = line.trim()
        if (t === '') endPara()
        else if (/^(\||[-*+]\s|\d+\.\s|>)/.test(t) && !para.length) intro = false // a list or table, not a paragraph
        else para.push(t)
      }
    }
    current.lines.push(line)
  }
  endPara()
  chunks.push(current)

  return {
    title,
    summary,
    headings,
    chunks: chunks
      .map((c) => ({ heading: c.heading, id: c.id, text: searchText(c.lines) }))
      .filter((c) => c.heading || c.text),
  }
}

// ---------- the page and section lists ----------

const pages = Object.entries(files).map(([path, raw]) => {
  const parts = path.split('/')
  const file = parts.pop()
  const folder = parts.pop()
  const slug = stripNumber(file).replace(/\.md$/, '')
  const parsed = parsePage(raw)
  return {
    file, // as it is in the repo, relative to docs/, for the "suggest a change" link
    path: `${folder}/${file}`,
    folder,
    slug,
    raw,
    ...parsed,
    title: parsed.title ?? slug,
  }
})

pages.sort((a, b) => byName(a.folder, b.folder) || byName(a.file, b.file))

export const sections = []
for (const page of pages) {
  let section = sections.at(-1)
  if (!section || section.folder !== page.folder) {
    section = {
      folder: page.folder,
      key: stripNumber(page.folder),
      title: sentenceCase(stripNumber(page.folder).replace(/-/g, ' ')),
      docs: [],
    }
    sections.push(section)
  }
  page.section = section
  section.docs.push(page)
}

export const docs = pages
export const docBySlug = (slug) => docs.find((d) => d.slug === slug)

export function neighbours(slug) {
  const i = docs.findIndex((d) => d.slug === slug)
  return i < 0 ? {} : { prev: docs[i - 1], next: docs[i + 1] }
}

// ---------- links inside the markdown ----------

// Pages link to each other by file path (../02-editing/01-key-rows.md#changing-the-keys) so the
// links work on GitHub too. On the site a page is #/docs/<slug>, and a heading #/docs/<slug>#<id>.
export function resolveHref(href = '', currentSlug = '') {
  const md = href.match(/^(?:\.{1,2}\/|[\w-]+\/)*(?:\d+-)?([\w-]+)\.md(#.*)?$/)
  if (md) return { href: `#/docs/${md[1]}${md[2] ?? ''}`, external: false }
  if (href.startsWith('#/')) return { href, external: false }
  if (href.startsWith('#')) return { href: currentSlug ? `#/docs/${currentSlug}${href}` : href, external: false }
  return { href, external: /^https?:\/\//.test(href) }
}

// ---------- search ----------

const words = (s) => s.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(Boolean)

const index = docs.flatMap((doc) =>
  doc.chunks.map((chunk) => ({
    doc,
    heading: chunk.heading,
    id: chunk.id,
    text: chunk.text,
    lcText: chunk.text.toLowerCase(),
    lcHeading: (chunk.heading ?? '').toLowerCase(),
    lcTitle: doc.title.toLowerCase(),
    lcSection: doc.section.title.toLowerCase(),
  })),
)

export function queryTerms(query) {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const terms = words(q)
  // Symbols such as // or { } have no letters: search them as typed.
  return terms.length ? terms : [q]
}

const wordStart = (hay, term) => hay.startsWith(term) || hay.includes(` ${term}`) || hay.includes(`-${term}`)

/**
 * Finds pages and the headings inside them.
 * Every word must appear somewhere in the page title, section, heading or text. Matches in a title
 * count most, then headings, then body text. Returns at most `limit` hits, up to two per page.
 */
export function search(query, limit = 10) {
  const terms = queryTerms(query)
  if (!terms.length || (terms.length === 1 && terms[0].length < 2 && /[\p{L}\p{N}]/u.test(terms[0]))) return []
  const phrase = terms.join(' ')

  const scored = []
  for (const e of index) {
    let score = 0
    let ok = true
    for (const t of terms) {
      let s = 0
      if (e.lcTitle.includes(t)) s += wordStart(e.lcTitle, t) ? 8 : 5
      if (e.lcHeading.includes(t)) s += wordStart(e.lcHeading, t) ? 6 : 4
      if (e.lcSection.includes(t)) s += 1
      if (e.lcText.includes(t)) s += wordStart(e.lcText, t) ? 2 : 1
      if (!s) {
        ok = false
        break
      }
      score += s
    }
    if (!ok) continue
    if (terms.length > 1 && (e.lcText.includes(phrase) || e.lcHeading.includes(phrase))) score += 4
    // The top of a page (no heading) is the page itself: prefer it on a tie.
    scored.push({ e, score: score + (e.heading ? 0 : 0.5) })
  }
  scored.sort((a, b) => b.score - a.score)

  const perDoc = new Map()
  const hits = []
  for (const { e, score } of scored) {
    const n = perDoc.get(e.doc.slug) ?? 0
    if (n >= 2) continue
    perDoc.set(e.doc.slug, n + 1)
    hits.push({
      doc: e.doc,
      heading: e.heading,
      href: `#/docs/${e.doc.slug}${e.id ? `#${e.id}` : ''}`,
      snippet: snippet(e.text || e.doc.summary, terms),
      score,
    })
    if (hits.length >= limit) break
  }
  return hits
}

// A short piece of the text around the first matching word.
export function snippet(text, terms, size = 150) {
  const lc = text.toLowerCase()
  let at = -1
  for (const t of terms) {
    const i = lc.indexOf(t)
    if (i >= 0 && (at < 0 || i < at)) at = i
  }
  if (text.length <= size) return text
  const start = Math.max(0, Math.min(at < 0 ? 0 : at - 40, text.length - size))
  let out = text.slice(start, start + size).trim()
  if (start > 0) out = `…${out}`
  if (start + size < text.length) out = `${out}…`
  return out
}
