// The site uses hash routes so it works from any path (GitHub Pages project sites, local files).
//   #/                        home (also #/download)
//   #/docs                    documentation index
//   #/docs/key-rows           one page
//   #/docs/key-rows#changing-the-keys   a heading inside a page
//   #/changelog               the notes of every stable release
//   #/forums                  the forums: every category and discussion
//   #/forums/c/q-a            one category
//   #/forums/12               one discussion
//   #/forums/12#comment-ID    a comment inside it
//   #/source                  the code: the files at the top, the README, the languages, tags and people
//   #/source/tree/app/src     one folder
//   #/source/blob/README.md   one file (#L10 or #L10-L20 marks lines)
//   #/source/commits          every commit; #/source/history/PATH the commits that touched a file or folder
//   #/source/commit/SHA       one commit with its diff
//   #/source/tags             the tags
//   #/source/search/code/foo  find files (files) or text in the code (code)
const safe = (s) => {
  try {
    return decodeURIComponent(s)
  } catch {
    return s
  }
}

export function parseRoute(hash = '') {
  const [path = '', anchor = ''] = hash.replace(/^#/, '').split('#')
  const parts = path.split('/').filter(Boolean)
  if (parts.length === 0 || parts[0] === 'download') return { page: 'home', slug: null, anchor: safe(anchor) }
  if (parts[0] === 'docs') return { page: 'docs', slug: parts[1] ? safe(parts[1]) : null, anchor: safe(anchor) }
  if (parts[0] === 'changelog') return { page: 'changelog', slug: null, anchor: safe(anchor) }
  if (parts[0] === 'forums') {
    const rest = parts.slice(1)
    const forums = { page: 'forums', slug: null, category: null, thread: null, anchor: safe(anchor) }
    if (rest.length === 0 || (rest.length === 1 && rest[0] === 'c')) return forums
    if (rest.length === 2 && rest[0] === 'c') return { ...forums, category: safe(rest[1]) }
    if (rest.length === 1 && /^[1-9]\d{0,8}$/.test(rest[0])) return { ...forums, thread: Number(rest[0]) }
  }
  if (parts[0] === 'source') {
    const base = {
      page: 'source',
      slug: null,
      category: null,
      thread: null,
      view: 'home',
      path: '',
      sha: null,
      mode: null,
      query: '',
      anchor: safe(anchor),
    }
    const [view, ...more] = parts.slice(1)
    const path = more.map(safe).join('/')
    if (!view) return base
    if (view === 'tree') return { ...base, view: path ? 'tree' : 'home', path }
    if (view === 'blob' && path) return { ...base, view: 'blob', path }
    if (view === 'history') return { ...base, view: path ? 'history' : 'commits', path }
    if (view === 'commits' && !more.length) return { ...base, view: 'commits' }
    if (view === 'commit' && more.length === 1 && /^[0-9a-f]{4,40}$/i.test(more[0])) {
      return { ...base, view: 'commit', sha: more[0].toLowerCase() }
    }
    if (view === 'tags' && !more.length) return { ...base, view: 'tags' }
    if (view === 'search' && more.length <= 2 && (!more[0] || more[0] === 'files' || more[0] === 'code')) {
      return { ...base, view: 'search', mode: more[0] ?? 'files', query: more[1] ? safe(more[1]) : '' }
    }
  }
  return { page: 'missing', slug: null, anchor: '' }
}
