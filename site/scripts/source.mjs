// Turns the repository's own files and history into the snapshot the Source pages show.
//
// It reads git directly (the website workflow has the whole repository checked out), so unlike the Forums it needs
// no token and no network. It is given `git(args, { input, buffer })`, which makes it easy to test with a small
// repository made on the fly (see smoke-source.mjs).
//
// What it produces (fetch-source.mjs writes these under public/source/):
//   index          every file, commit, tag, contributor and language count, in one JSON file
//   blobs          a copy of each file, named by its content, so the pages can fetch exactly one file
//   patches        the diff of each recent commit, one file per commit
//   blame          who last changed each line of each text file, one file per file
//   search         the text of every source file in one file, loaded only when someone searches the code
//
// Email addresses are never written out: only names, and the GitHub login when the address is a noreply one.
import { createHash } from 'node:crypto'

export const LIMITS = {
  commits: 1000, // commits listed
  patchCommits: 300, // newest commits that also get their diffs
  commitFiles: 300, // files listed per commit
  patchFileLines: 1500, // lines of one file's diff
  patchBytes: 400_000, // diff text of one commit
  rawBytes: 3_000_000, // larger files are listed but not copied
  searchFileBytes: 250_000,
  searchBytes: 6_000_000,
  blameLines: 20_000,
}

// ---------------------------------------------------------------- languages

// extension -> [name, colour, highlight.js language]
const BY_EXT = {
  kt: ['Kotlin', '#A97BFF', 'kotlin'],
  kts: ['Kotlin', '#A97BFF', 'kotlin'],
  java: ['Java', '#b07219', 'java'],
  js: ['JavaScript', '#f1e05a', 'javascript'],
  mjs: ['JavaScript', '#f1e05a', 'javascript'],
  cjs: ['JavaScript', '#f1e05a', 'javascript'],
  jsx: ['JavaScript', '#f1e05a', 'javascript'],
  ts: ['TypeScript', '#3178c6', 'typescript'],
  tsx: ['TypeScript', '#3178c6', 'typescript'],
  json: ['JSON', '#9aa4b2', 'json'],
  css: ['CSS', '#7b5ea7', 'css'],
  html: ['HTML', '#e34c26', 'xml'],
  htm: ['HTML', '#e34c26', 'xml'],
  xml: ['XML', '#2f8fbf', 'xml'],
  svg: ['SVG', '#ff9900', 'xml'],
  md: ['Markdown', '#4a7dd1', 'markdown'],
  markdown: ['Markdown', '#4a7dd1', 'markdown'],
  yml: ['YAML', '#cb171e', 'yaml'],
  yaml: ['YAML', '#cb171e', 'yaml'],
  sh: ['Shell', '#89e051', 'bash'],
  bash: ['Shell', '#89e051', 'bash'],
  bat: ['Batchfile', '#C1F12E', 'dos'],
  cmd: ['Batchfile', '#C1F12E', 'dos'],
  py: ['Python', '#3572A5', 'python'],
  gradle: ['Gradle', '#02a4c9', 'gradle'],
  properties: ['Properties', '#9aa4b2', 'properties'],
  toml: ['TOML', '#9c4221', 'ini'],
  ini: ['INI', '#9aa4b2', 'ini'],
  sql: ['SQL', '#e38c00', 'sql'],
  txt: ['Text', null, 'plaintext'],
}

const BY_NAME = {
  gradlew: ['Shell', '#89e051', 'bash'],
  'gradlew.bat': ['Batchfile', '#C1F12E', 'dos'],
  Dockerfile: ['Dockerfile', '#384d54', 'dockerfile'],
  Makefile: ['Makefile', '#427819', 'makefile'],
}

const TEXT = ['Text', null, 'plaintext']

export const extensionOf = (path) => {
  const name = path.slice(path.lastIndexOf('/') + 1)
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.slice(dot + 1).toLowerCase() : ''
}

/** { name, color, hl } for a file name; plain text when the name says nothing. */
export function languageOf(path) {
  const name = path.slice(path.lastIndexOf('/') + 1)
  const hit = BY_NAME[name] ?? BY_EXT[extensionOf(path)] ?? TEXT
  return { name: hit[0], color: hit[1], hl: hit[2] }
}

/** Lock files and the like: shown, but not counted as the project's code. */
export const isGenerated = (path) => /(^|\/)(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|[^/]+\.lock|gradle-wrapper\.jar)$/.test(path)

const IMAGE = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'ico', 'avif', 'svg'])
export const isImagePath = (path) => IMAGE.has(extensionOf(path))

export const looksBinary = (buf) => buf.subarray(0, 8000).includes(0)

export function countLines(text) {
  if (text === '') return 0
  return text.split('\n').length - (text.endsWith('\n') ? 1 : 0)
}

const sha1 = (s) => createHash('sha1').update(s).digest('hex')

/** A stable file name for a path (blame files are named after the path, which may hold characters a URL dislikes). */
export const pathId = (path) => sha1(path).slice(0, 16)

// ---------------------------------------------------------------- people

/** "12345+name@users.noreply.github.com" -> "name". Any other address says nothing about the login. */
export function loginFromEmail(email) {
  const m = String(email ?? '').match(/^(?:\d+\+)?([^@\s]+)@users\.noreply\.github\.com$/i)
  return m ? m[1] : null
}

const person = (name, email) => ({ name: name || 'unknown', login: loginFromEmail(email) })

// ---------------------------------------------------------------- git output

/** `git ls-tree -r -l -z`: mode, type, blob id, size and path of every file. */
export function parseLsTree(output) {
  const out = []
  for (const rec of String(output).split('\0')) {
    if (!rec) continue
    const m = rec.match(/^(\d+) (\w+) ([0-9a-f]+)\s+(-|\d+)\t([\s\S]*)$/)
    if (!m || m[2] !== 'blob') continue // a submodule has nothing to show
    out.push({ mode: m[1], blob: m[3], size: m[4] === '-' ? 0 : Number(m[4]), path: m[5] })
  }
  return out
}

const STATUS = { A: 'A', M: 'M', D: 'D', R: 'R', C: 'C', T: 'M' }

/** The lines of `--name-status`: M\tpath, R087\told\tnew. */
export function parseNameStatus(text) {
  const files = []
  for (const line of String(text).split('\n')) {
    if (!line.trim()) continue
    const cols = line.split('\t')
    const status = STATUS[cols[0]?.[0]]
    if (!status) continue
    if ((status === 'R' || status === 'C') && cols.length >= 3) files.push([status, cols[2], cols[1]])
    else if (cols[1]) files.push([status, cols[1]])
  }
  return files
}

export const LOG_FORMAT = '%x1e%H%x00%P%x00%an%x00%ae%x00%aI%x00%s%x00%b%x00'

/** `git log --format=LOG_FORMAT --name-status -M`. No email address survives this. */
export function parseLog(output) {
  const commits = []
  for (const chunk of String(output).split('\x1e')) {
    if (!chunk.trim()) continue
    const parts = chunk.split('\x00')
    if (parts.length < 7) continue
    const [sha, parents, name, email, date, subject, body] = parts
    const files = parseNameStatus(parts.slice(7).join('\x00'))
    commits.push({
      sha: sha.trim(),
      parents: parents.split(' ').filter(Boolean),
      author: person(name, email),
      date,
      subject,
      body: body.trim(),
      files: files.slice(0, LIMITS.commitFiles),
      moreFiles: Math.max(0, files.length - LIMITS.commitFiles),
    })
  }
  return commits
}

/** `git log --numstat --format=%x1e%H`: lines added and removed by each commit. */
export function parseNumstat(output) {
  const stats = new Map()
  for (const chunk of String(output).split('\x1e')) {
    const lines = chunk.split('\n')
    const sha = lines[0]?.trim()
    if (!/^[0-9a-f]{40}$/.test(sha ?? '')) continue
    let additions = 0
    let deletions = 0
    for (const line of lines.slice(1)) {
      const m = line.match(/^(\d+|-)\t(\d+|-)\t/)
      if (!m) continue
      if (m[1] !== '-') additions += Number(m[1])
      if (m[2] !== '-') deletions += Number(m[2])
    }
    stats.set(sha, { additions, deletions })
  }
  return stats
}

/** `git show -p` of one commit, split into one entry per file. The diff text starts at the first @@. */
export function parsePatch(text) {
  const files = []
  let cur = null
  let lines = []

  const finish = () => {
    if (!cur) return
    let additions = 0
    let deletions = 0
    for (const l of lines) {
      if (l.startsWith('+')) additions++
      else if (l.startsWith('-')) deletions++
    }
    cur.additions = additions
    cur.deletions = deletions
    if (lines.length > LIMITS.patchFileLines) {
      cur.patch = lines.slice(0, LIMITS.patchFileLines).join('\n')
      cur.truncated = lines.length - LIMITS.patchFileLines
    } else {
      cur.patch = lines.join('\n')
    }
    if (!lines.length) cur.patch = ''
    files.push(cur)
    cur = null
    lines = []
  }

  let inHunks = false
  for (const line of String(text).split('\n')) {
    if (line.startsWith('diff --git ') || line.startsWith('diff --cc ') || line.startsWith('diff --combined ')) {
      finish()
      inHunks = false
      cur = { path: '', oldPath: null, status: 'M', binary: false }
      if (line.startsWith('diff --git ')) {
        const rest = line.slice('diff --git '.length)
        const same = rest.match(/^a\/([\s\S]+) b\/\1$/)
        const two = rest.match(/^a\/([\s\S]+?) b\/([\s\S]+)$/)
        cur.path = same ? same[1] : two ? two[2] : rest
        if (!same && two) cur.oldPath = two[1]
      } else {
        cur.path = line.replace(/^diff --\w+ /, '')
        cur.combined = true
      }
      continue
    }
    if (!cur) continue
    if (!inHunks) {
      if (line.startsWith('@@')) {
        inHunks = true
        lines.push(line)
      } else if (line.startsWith('new file mode')) cur.status = 'A'
      else if (line.startsWith('deleted file mode')) cur.status = 'D'
      else if (line.startsWith('rename from ')) {
        cur.status = 'R'
        cur.oldPath = line.slice('rename from '.length)
      } else if (line.startsWith('rename to ')) cur.path = line.slice('rename to '.length)
      else if (line.startsWith('copy from ')) {
        cur.status = 'C'
        cur.oldPath = line.slice('copy from '.length)
      } else if (line.startsWith('copy to ')) cur.path = line.slice('copy to '.length)
      else if (line.startsWith('Binary files ') || line.startsWith('GIT binary patch')) cur.binary = true
      else if (line.startsWith('+++ b/')) cur.path = line.slice('+++ b/'.length)
      else if (line.startsWith('--- a/') && cur.status === 'D') cur.path = line.slice('--- a/'.length)
      continue
    }
    lines.push(line)
  }
  finish()
  for (const f of files) if (f.oldPath === f.path) f.oldPath = null
  return files
}

/** `git blame --porcelain`: runs of lines that share a commit, as [firstLine, lineCount, commitIndex]. */
export function parseBlame(output, indexOfSha) {
  const runs = []
  for (const line of String(output).split('\n')) {
    const m = line.match(/^([0-9a-f]{40}) \d+ (\d+)(?: (\d+))?$/)
    if (!m || !m[3]) continue
    const start = Number(m[2])
    const count = Number(m[3])
    const idx = indexOfSha.get(m[1]) ?? -1
    const prev = runs[runs.length - 1]
    if (prev && prev[2] === idx && prev[0] + prev[1] === start) prev[1] += count
    else runs.push([start, count, idx])
  }
  return runs.sort((a, b) => a[0] - b[0])
}

/** `git cat-file --batch` output for the blobs asked for, in the same order. */
export function parseBatch(buf, shas) {
  const out = new Map()
  let pos = 0
  for (const sha of shas) {
    const nl = buf.indexOf(10, pos)
    if (nl < 0) break
    const head = buf.toString('utf8', pos, nl).split(' ')
    if (head[1] === 'missing') {
      pos = nl + 1
      continue
    }
    const size = Number(head[2])
    out.set(sha, buf.subarray(nl + 1, nl + 1 + size))
    pos = nl + 1 + size + 1
  }
  return out
}

// ---------------------------------------------------------------- the snapshot

export const unavailable = (reason, now = new Date()) => ({
  version: 1,
  available: false,
  reason,
  generatedAt: now.toISOString(),
})

const dayKey = (iso) => iso.slice(0, 10)

/**
 * Reads the repository and returns { index, blobs, patches, blame, search }.
 *   index    -> source/index.json
 *   blobs    -> Map of file name -> Buffer, under source/blob/
 *   patches  -> Map of commit sha -> { files }, under source/commits/
 *   blame    -> Map of path id -> runs, under source/blame/
 *   search   -> { path: text }, source/search.json
 */
export function collect({ git, repo = null, branch = 'main', now = new Date() }) {
  let head
  try {
    head = git(['rev-parse', 'HEAD']).toString().trim()
  } catch {
    return { index: unavailable('no-git', now) }
  }
  if (!/^[0-9a-f]{40}$/.test(head)) return { index: unavailable('no-git', now) }

  // ----- files
  const entries = parseLsTree(git(['ls-tree', '-r', '-l', '-z', head]))
  const wanted = [...new Set(entries.filter((e) => e.size <= LIMITS.rawBytes).map((e) => e.blob))]
  const contents = wanted.length
    ? parseBatch(git(['cat-file', '--batch'], { input: `${wanted.join('\n')}\n`, buffer: true }), wanted)
    : new Map()

  const blobs = new Map()
  const search = {}
  let searchBytes = 0
  const files = []
  const textFiles = []
  for (const e of entries.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))) {
    const buf = contents.get(e.blob)
    const lang = languageOf(e.path)
    const generated = isGenerated(e.path)
    const entry = {
      path: e.path,
      size: e.size,
      kind: 'text',
      blob: e.blob,
      ext: /^[a-z0-9]{1,8}$/.test(extensionOf(e.path)) ? extensionOf(e.path) : '',
      lang: lang.name,
      hl: lang.hl,
    }
    if (e.mode === '100755') entry.exec = true
    if (e.mode === '120000') entry.symlink = true
    if (generated) entry.generated = true
    if (isImagePath(e.path)) entry.image = true
    if (!buf) {
      entry.kind = 'large'
      files.push(entry)
      continue
    }
    const binary = looksBinary(buf)
    if (binary) {
      entry.kind = 'binary'
      delete entry.lang
      delete entry.hl
    } else {
      const text = buf.toString('utf8')
      entry.lines = countLines(text)
      textFiles.push(entry)
      if (!generated && buf.length <= LIMITS.searchFileBytes && searchBytes + buf.length <= LIMITS.searchBytes) {
        search[e.path] = text
        searchBytes += buf.length
      }
    }
    blobs.set(`${e.blob}${entry.ext ? `.${entry.ext}` : ''}`, buf)
    entry.bid = pathId(e.path)
    files.push(entry)
  }

  // ----- commits
  const commits = parseLog(
    git(['log', `--max-count=${LIMITS.commits}`, `--format=${LOG_FORMAT}`, '--name-status', '-M', head]),
  )
  const stats = parseNumstat(git(['log', `--max-count=${LIMITS.commits}`, '--numstat', '-M', '--format=%x1e%H', head]))
  for (const c of commits) {
    const s = stats.get(c.sha) ?? { additions: 0, deletions: 0 }
    c.additions = s.additions
    c.deletions = s.deletions
  }
  let commitCount = commits.length
  try {
    commitCount = Number(git(['rev-list', '--count', head]).toString().trim()) || commits.length
  } catch {
    // keep the listed count
  }
  const indexOfSha = new Map(commits.map((c, i) => [c.sha, i]))

  // ----- diffs of the newest commits
  const patches = new Map()
  for (const c of commits.slice(0, LIMITS.patchCommits)) {
    let text = ''
    try {
      text = git(['show', '--format=', '-M', '-p', '--no-color', '--no-ext-diff', c.sha]).toString()
    } catch {
      continue
    }
    const list = parsePatch(text)
    let used = 0
    for (const f of list) {
      if (used + f.patch.length > LIMITS.patchBytes) {
        f.patch = null
        f.tooLarge = true
      } else used += f.patch.length
    }
    patches.set(c.sha, { files: list })
    c.patch = true
  }

  // ----- blame
  const blame = new Map()
  for (const f of textFiles) {
    if (f.lines === 0 || f.lines > LIMITS.blameLines || f.symlink) continue
    try {
      const runs = parseBlame(git(['blame', '--porcelain', head, '--', f.path]).toString(), indexOfSha)
      if (runs.length) blame.set(f.bid, runs)
    } catch {
      // a file git cannot blame simply has no blame view
    }
  }

  // ----- tags
  const tags = []
  try {
    const refs = git([
      'for-each-ref',
      '--sort=-creatordate',
      '--format=%(refname:short)%00%(objecttype)%00%(objectname)%00%(*objectname)%00%(creatordate:iso-strict)%00%(contents:subject)',
      'refs/tags',
    ]).toString()
    for (const line of refs.split('\n')) {
      if (!line.trim()) continue
      const [name, type, obj, target, date, subject] = line.split('\x00')
      const sha = type === 'tag' ? target : obj
      if (name && /^[0-9a-f]{40}$/.test(sha ?? '')) tags.push({ name, sha, date, subject: subject ?? '', annotated: type === 'tag' })
    }
  } catch {
    // no tags
  }

  // ----- people and languages
  const who = new Map()
  for (const c of commits) {
    const key = c.author.login ?? `name:${c.author.name}`
    const p = who.get(key) ?? { name: c.author.name, login: c.author.login, commits: 0, first: c.date, last: c.date }
    p.commits++
    if (c.date < p.first) p.first = c.date
    if (c.date > p.last) p.last = c.date
    who.set(key, p)
  }
  const contributors = [...who.values()].sort((a, b) => b.commits - a.commits || a.name.localeCompare(b.name))

  const langs = new Map()
  let totalLines = 0
  for (const f of textFiles) {
    if (f.generated || f.lines == null) continue
    const l = languageOf(f.path)
    const cur = langs.get(l.name) ?? { name: l.name, color: l.color, files: 0, lines: 0, bytes: 0 }
    cur.files++
    cur.lines += f.lines
    cur.bytes += f.size
    langs.set(l.name, cur)
    totalLines += f.lines
  }
  const languages = [...langs.values()].sort((a, b) => b.lines - a.lines)

  const readme = files.find((f) => !f.path.includes('/') && /^readme(\.(md|markdown|txt))?$/i.test(f.path) && f.kind === 'text')

  const index = {
    version: 1,
    available: true,
    generatedAt: now.toISOString(),
    repo,
    branch,
    head,
    commitCount,
    files,
    commits,
    tags,
    contributors,
    languages,
    totals: {
      files: files.length,
      lines: textFiles.reduce((n, f) => n + (f.lines ?? 0), 0),
      codeLines: totalLines,
      bytes: files.reduce((n, f) => n + f.size, 0),
    },
    readme: readme?.path ?? null,
    days: [...new Set(commits.map((c) => dayKey(c.date)))].length,
  }
  return { index, blobs, patches, blame, search }
}
