// The site uses hash routes so it works from any path (GitHub Pages project sites, local files).
//   #/                        home (also #/download)
//   #/docs                    documentation index
//   #/docs/key-rows           one page
//   #/docs/key-rows#changing-the-keys   a heading inside a page
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
  return { page: 'missing', slug: null, anchor: '' }
}
