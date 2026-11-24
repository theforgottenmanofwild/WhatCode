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
  return { page: 'missing', slug: null, anchor: '' }
}
