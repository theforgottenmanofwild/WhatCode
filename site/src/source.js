// Everything the Source pages need that is not drawing: links, indexing the snapshot, listing folders, following a
// file's history, searching, splitting highlighted code into lines, reading diffs. No React in here, so the smoke
// test can check it directly.
import { shortSha } from './releases.js'

/** Where the snapshot's files are served from (next to the page, written at build time by scripts/fetch-source.mjs). */
export const SOURCE_BASE = `${import.meta.env.BASE_URL}source/`

// ---------------------------------------------------------------- links

const seg = (p) => String(p).split('/').filter(Boolean).map(encodeURIComponent).join('/')

export const sourceHref = () => '#/source'
export const treeHref = (path) => (path ? `#/source/tree/${seg(path)}` : '#/source')
export const blobHref = (path, anchor) => `#/source/blob/${seg(path)}${anchor ? `#${anchor}` : ''}`
export const historyHref = (path) => (path ? `#/source/history/${seg(path)}` : '#/source/commits')
export const commitsHref = () => '#/source/commits'
export const commitHref = (sha, anchor) => `#/source/commit/${sha}${anchor ? `#${anchor}` : ''}`
export const tagsHref = () => '#/source/tags'
export const searchHref = (mode = 'files', query = '') =>
  query ? `#/source/search/${mode}/${encodeURIComponent(query)}` : mode === 'code' ? '#/source/search/code' : '#/source/search'

/** The copy of a file the page can fetch. Named by content, so identical files share one copy. */
export const rawUrl = (file) => `${SOURCE_BASE}blob/${file.blob}${file.ext ? `.${file.ext}` : ''}`

// The same thing on GitHub, pinned to the commit this copy was made from.
export const ghBlob = (src, path) => (src.repoUrl ? `${src.repoUrl}/blob/${src.head}/${seg(path)}` : null)
export const ghTree = (src, path) => (src.repoUrl ? `${src.repoUrl}/tree/${src.head}${path ? `/${seg(path)}` : ''}` : null)
export const ghCommit = (src, sha) => (src.repoUrl ? `${src.repoUrl}/commit/${sha}` : null)
export const ghEdit = (src, path) => (src.repoUrl ? `${src.repoUrl}/edit/${src.branch}/${seg(path)}` : null)
export const ghArchive = (src) => (src.repoUrl ? `${src.repoUrl}/archive/${src.head}.zip` : null)

export const avatarUrl = (person, size = 40) =>
  person?.login && !person.login.includes('[') ? `https://github.com/${encodeURIComponent(person.login)}.png?size=${size * 2}` : null
export const profileUrl = (person) => (person?.login && !person.login.includes('[') ? `https://github.com/${encodeURIComponent(person.login)}` : null)

// ---------------------------------------------------------------- words

export function formatBytes(n) {
  if (!Number.isFinite(n)) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(n < 10240 ? 1 : 0)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

export const formatDay = (day) => {
  try {
    return new Intl.DateTimeFormat('en', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${day}T00:00:00Z`))
  } catch {
    return day
  }
}

export const STATUS_LABELS = { A: 'Added', M: 'Modified', D: 'Deleted', R: 'Renamed', C: 'Copied' }

/** Files with more lines than this have no blame view (scripts/source.mjs skips them). */
export const BLAME_MAX_LINES = 20_000

export const lineWord = (n) => `${n.toLocaleString('en')} ${n === 1 ? 'line' : 'lines'}`

// ---------------------------------------------------------------- indexing

const makeDir = (path) => ({
  path,
  name: path.slice(path.lastIndexOf('/') + 1),
  dirs: [],
  files: [],
  fileCount: 0,
  size: 0,
  commit: null,
})

const byName = (a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' })

const ancestors = (path) => {
  const parts = path.split('/')
  const out = ['']
  for (let i = 0; i < parts.length - 1; i++) out.push(parts.slice(0, i + 1).join('/'))
  return out
}

/** Adds what the pages need to the raw snapshot: lookups, the folder tree, the last commit of every file and folder. */
export function indexSource(data) {
  const files = data.files.map((f) => ({ ...f, name: f.path.slice(f.path.lastIndexOf('/') + 1), commit: null }))
  const byPath = new Map(files.map((f) => [f.path, f]))

  const commits = data.commits.map((c, idx) => ({
    ...c,
    idx,
    short: shortSha(c.sha),
    time: Date.parse(c.date) || 0,
    hay: [c.subject, c.body, c.author.name, c.author.login ?? '', c.sha].join('\n').toLowerCase(),
  }))
  const bySha = new Map(commits.map((c) => [c.sha, c]))

  const dirs = new Map([['', makeDir('')]])
  for (const f of files) {
    const chain = ancestors(f.path)
    for (let i = 1; i < chain.length; i++) {
      if (!dirs.has(chain[i])) {
        dirs.set(chain[i], makeDir(chain[i]))
        dirs.get(chain[i - 1]).dirs.push(chain[i])
      }
    }
    for (const a of chain) {
      const d = dirs.get(a)
      d.fileCount++
      d.size += f.size
    }
    dirs.get(chain[chain.length - 1]).files.push(f.path)
  }

  // The newest commit that touched each file, then each folder (the newest of its files).
  const seen = new Set()
  for (const c of commits) {
    for (const [, p] of c.files) {
      const f = byPath.get(p)
      if (f && !seen.has(p)) {
        seen.add(p)
        f.commit = c.idx
      }
    }
  }
  for (const f of files) {
    if (f.commit === null) continue
    for (const a of ancestors(f.path)) {
      const d = dirs.get(a)
      d.commit = d.commit === null ? f.commit : Math.min(d.commit, f.commit)
    }
  }

  const tagsBySha = new Map()
  for (const t of data.tags ?? []) tagsBySha.set(t.sha, [...(tagsBySha.get(t.sha) ?? []), t.name])

  return {
    ...data,
    files,
    commits,
    tags: data.tags ?? [],
    contributors: data.contributors ?? [],
    languages: data.languages ?? [],
    byPath,
    bySha,
    dirs,
    tagsBySha,
    repoUrl: data.repo ? `https://github.com/${data.repo}` : null,
    repoName: data.repo ? data.repo.split('/')[1] : 'repository',
  }
}

/** A commit by its full id, or by the start of it when only one commit begins that way. */
export function findCommit(src, sha) {
  if (!sha) return null
  const exact = src.bySha.get(sha)
  if (exact) return exact
  const hits = src.commits.filter((c) => c.sha.startsWith(sha))
  return hits.length === 1 ? hits[0] : null
}

export function breadcrumbs(path) {
  const parts = String(path).split('/').filter(Boolean)
  return parts.map((name, i) => ({ name, path: parts.slice(0, i + 1).join('/') }))
}

/** The rows of a folder: folders first. A chain of folders that hold nothing else is shown as one row, like GitHub does. */
export function listDir(src, path = '') {
  const dir = src.dirs.get(path)
  if (!dir) return null
  const rows = dir.dirs.map((dp) => {
    const whole = src.dirs.get(dp)
    let cur = whole
    const names = [cur.name]
    while (cur.files.length === 0 && cur.dirs.length === 1) {
      cur = src.dirs.get(cur.dirs[0])
      names.push(cur.name)
    }
    return { type: 'dir', name: names.join('/'), path: cur.path, commit: whole.commit, fileCount: whole.fileCount, size: whole.size }
  })
  rows.sort(byName)
  const files = dir.files
    .map((p) => src.byPath.get(p))
    .sort(byName)
    .map((f) => ({ type: 'file', name: f.name, path: f.path, commit: f.commit, size: f.size, file: f }))
  return [...rows, ...files]
}

/** The README that sits in a folder, if there is one. Markdown wins over plain text. */
export function readmeOf(src, path = '') {
  const dir = src.dirs.get(path)
  if (!dir) return null
  const found = dir.files
    .map((p) => src.byPath.get(p))
    .filter((f) => f.kind === 'text' && /^readme(\.[a-z]+)?$/i.test(f.name))
    .sort((a, b) => Number(/\.md$/i.test(b.name)) - Number(/\.md$/i.test(a.name)))
  return found[0] ?? null
}

// ---------------------------------------------------------------- history

/** Indexes of the commits that touched a file (following renames) or a folder, newest first. No path means all. */
export function historyOf(src, path) {
  if (!path) return src.commits.map((c) => c.idx)
  const out = []
  if (src.dirs.has(path)) {
    const prefix = `${path}/`
    for (const c of src.commits) {
      if (c.files.some(([, p, o]) => p.startsWith(prefix) || (o && o.startsWith(prefix)))) out.push(c.idx)
    }
    return out
  }
  const names = new Set([path])
  for (const c of src.commits) {
    let hit = false
    for (const [status, p, old] of c.files) {
      if (names.has(p)) {
        hit = true
        if (status === 'R' && old) names.add(old)
      }
    }
    if (hit) out.push(c.idx)
  }
  return out
}

export const authorKey = (author) => author.login ?? `name:${author.name}`

/** The commits of a path (or all), narrowed to those that contain every word typed and, optionally, one author. */
export function queryCommits(src, { path = '', query = '', author = '' } = {}) {
  const terms = String(query).toLowerCase().split(/\s+/).filter(Boolean)
  return historyOf(src, path)
    .map((i) => src.commits[i])
    .filter((c) => (!author || authorKey(c.author) === author) && terms.every((t) => c.hay.includes(t)))
}

/** [{ day: '2026-11-24', items }] in the order given. The day is the author's own date, so it does not depend on the reader's zone. */
export function groupByDay(commits) {
  const groups = []
  for (const c of commits) {
    const day = c.date.slice(0, 10)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(c)
    else groups.push({ day, items: [c] })
  }
  return groups
}

// ---------------------------------------------------------------- links inside Markdown

const SCHEME = /^([a-z][a-z0-9+.-]*:|\/\/)/i

function joinPath(base, rel) {
  const out = base ? base.split('/') : []
  for (const part of rel.split('/')) {
    if (part === '' || part === '.') continue
    if (part === '..') out.pop()
    else out.push(part)
  }
  return out.join('/')
}

/**
 * Where a link in a Markdown file goes. `fromPath` is the folder the file is in.
 *   { kind: 'external' | 'file' | 'tree' | 'anchor' | 'missing', href }
 * A file that is in this copy stays in the site; anything else relative goes to GitHub.
 */
export function resolveRepoLink(src, href, fromDir = '') {
  const raw = String(href ?? '')
  if (raw.startsWith('#')) return { kind: 'anchor', href: raw }
  if (SCHEME.test(raw)) return { kind: 'external', href: raw }
  const [pathPart, fragment = ''] = raw.split('#')
  const target = pathPart.split('?')[0]
  const path = target.startsWith('/') ? joinPath('', target) : joinPath(fromDir, target)
  const anchor = /^L\d/.test(fragment) ? fragment : ''
  let decoded = path
  try {
    decoded = decodeURIComponent(path)
  } catch {
    // keep as written
  }
  if (src.byPath.has(decoded)) return { kind: 'file', href: blobHref(decoded, anchor), path: decoded }
  if (src.dirs.has(decoded)) return { kind: 'tree', href: treeHref(decoded), path: decoded }
  const gh = ghBlob(src, decoded)
  return gh ? { kind: 'external', href: gh } : { kind: 'missing', href: raw }
}

/** Where an image in a Markdown file comes from: this copy when the file is in it, else GitHub. */
export function resolveRepoImage(src, href, fromDir = '') {
  const raw = String(href ?? '')
  if (SCHEME.test(raw) || raw.startsWith('data:')) return raw
  const target = raw.split('#')[0].split('?')[0]
  let path = target.startsWith('/') ? joinPath('', target) : joinPath(fromDir, target)
  try {
    path = decodeURIComponent(path)
  } catch {
    // keep as written
  }
  const file = src.byPath.get(path)
  if (file && file.kind !== 'large') return rawUrl(file)
  return src.repo ? `https://raw.githubusercontent.com/${src.repo}/${src.head}/${seg(path)}` : raw
}

// ---------------------------------------------------------------- lines

/** "L12" or "L12-L20" -> { from, to }, or null. */
export function parseLineRange(anchor) {
  const m = /^L(\d+)(?:-L?(\d+))?$/.exec(String(anchor ?? ''))
  if (!m) return null
  const a = Number(m[1])
  const b = m[2] ? Number(m[2]) : a
  if (!a || !b) return null
  return { from: Math.min(a, b), to: Math.max(a, b) }
}

export const lineAnchor = (from, to) => (to && to !== from ? `L${from}-L${to}` : `L${from}`)

/**
 * highlight.js gives one string of HTML where a comment or string may run over several lines. Tables need one
 * piece per line, so every span still open at a line break is closed there and opened again on the next line.
 * A final line break does not make an extra empty line.
 */
export function splitHighlighted(html) {
  const lines = []
  const open = []
  let cur = ''
  for (const part of String(html).split(/(<span[^>]*>|<\/span>|\n)/)) {
    if (part === '') continue
    if (part === '\n') {
      lines.push(cur + '</span>'.repeat(open.length))
      cur = open.join('')
    } else if (part === '</span>') {
      open.pop()
      cur += part
    } else if (part.startsWith('<span')) {
      open.push(part)
      cur += part
    } else cur += part
  }
  if (cur !== open.join('')) lines.push(cur + '</span>'.repeat(open.length))
  return lines
}

/** The lines of some text, without an empty last line for a final line break. */
export function plainLines(text) {
  const lines = String(text).split('\n')
  if (lines[lines.length - 1] === '') lines.pop()
  return lines
}

// ---------------------------------------------------------------- diffs

/** A file's diff (starting at its first @@) as rows: hunk headers, context, added, removed, notes. */
export function parseDiff(patch) {
  const rows = []
  let o = 0
  let n = 0
  for (const line of String(patch ?? '').split('\n')) {
    if (line.startsWith('@@')) {
      const m = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@ ?(.*)$/.exec(line)
      if (m) {
        o = Number(m[1])
        n = Number(m[2])
      }
      rows.push({ kind: 'hunk', text: line, section: m?.[3] ?? '' })
    } else if (line.startsWith('+')) rows.push({ kind: 'add', old: null, new: n++, text: line.slice(1) })
    else if (line.startsWith('-')) rows.push({ kind: 'del', old: o++, new: null, text: line.slice(1) })
    else if (line.startsWith('\\')) rows.push({ kind: 'meta', text: line })
    else if (line === '') continue
    else rows.push({ kind: 'ctx', old: o++, new: n++, text: line.slice(1) })
  }
  return rows
}

// ---------------------------------------------------------------- finding files

/**
 * Matches typed letters against paths. A run inside the file name beats a run elsewhere in the path, which beats
 * letters spread out in order. Returns [{ file, score, marks }] best first; `marks` are the character positions to highlight.
 */
export function findFiles(src, query, limit = 100) {
  const q = String(query).trim().toLowerCase().replace(/\s+/g, '')
  if (!q) return []
  const out = []
  for (const file of src.files) {
    const p = file.path.toLowerCase()
    const nameStart = p.lastIndexOf('/') + 1
    let score = 0
    let marks = null
    const inName = p.indexOf(q, nameStart)
    if (inName >= 0) {
      score = 1000 - (inName - nameStart) * 4 - (p.length - nameStart) + (inName === nameStart ? 200 : 0)
      marks = Array.from({ length: q.length }, (_, i) => inName + i)
    } else {
      const inPath = p.indexOf(q)
      if (inPath >= 0) {
        score = 600 - inPath - p.length
        marks = Array.from({ length: q.length }, (_, i) => inPath + i)
      } else {
        let at = 0
        const found = []
        for (const ch of q) {
          at = p.indexOf(ch, at)
          if (at < 0) break
          found.push(at++)
        }
        if (found.length === q.length) {
          let runs = 0
          for (let i = 1; i < found.length; i++) if (found[i] === found[i - 1] + 1) runs++
          const inFile = found.filter((i) => i >= nameStart).length
          score = 100 + runs * 6 + inFile * 3 - p.length * 0.1
          marks = found
        }
      }
    }
    if (marks) out.push({ file, score, marks })
  }
  return out.sort((a, b) => b.score - a.score || a.file.path.localeCompare(b.file.path)).slice(0, limit)
}

// ---------------------------------------------------------------- searching the code

const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** A long line is cut to a window around its first match, with the match positions moved to fit. */
function windowed(line, ranges, width = 300) {
  if (line.length <= width) return { text: line, ranges }
  const start = Math.max(0, ranges[0][0] - 80)
  const slice = line.slice(start, start + width)
  const lead = start > 0 ? '…' : ''
  const shift = lead.length - start
  return {
    text: `${lead}${slice}${start + width < line.length ? '…' : ''}`,
    ranges: ranges.filter(([s]) => s < start + width).map(([s, e]) => [s + shift, Math.min(e, start + width) + shift]),
  }
}

/**
 * Looks for text in { path: text }. Returns { results: [{ path, count, hits: [{ line, text, ranges }] }], files, total, shown, error }.
 * `count` is the number of matching lines in the file; only the first few of them are listed.
 */
export function searchCode(bundle, query, { matchCase = false, regex = false, limit = 500, perFile = 50 } = {}) {
  const empty = { results: [], files: 0, total: 0, shown: 0, error: null }
  if (!query) return empty
  let re
  try {
    re = new RegExp(regex ? query : escapeRegExp(query), matchCase ? 'g' : 'gi')
  } catch {
    return { ...empty, error: 'That is not a valid regular expression.' }
  }
  if (new RegExp(re.source, re.flags).exec('')) return { ...empty, error: 'That pattern matches empty text, so it would match everything.' }

  const results = []
  let total = 0
  let shown = 0
  for (const path of Object.keys(bundle).sort()) {
    const hits = []
    let count = 0
    const lines = bundle[path].split('\n')
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i]
      re.lastIndex = 0
      const ranges = []
      let m
      while ((m = re.exec(line)) && ranges.length < 20) {
        ranges.push([m.index, m.index + m[0].length])
        if (m[0].length === 0) re.lastIndex++
      }
      if (!ranges.length) continue
      count++
      if (hits.length < perFile && shown < limit) {
        hits.push({ line: i + 1, ...windowed(line, ranges) })
        shown++
      }
    }
    if (count) {
      total += count
      results.push({ path, count, hits })
    }
  }
  return { results, files: results.length, total, shown, error: null }
}
