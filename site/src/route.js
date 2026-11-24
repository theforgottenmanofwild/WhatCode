// The site has real addresses, so a page can be linked, bookmarked and reloaded: /docs/key-rows, not /#/docs/key-rows.
//   /                         home (also /download)
//   /docs                     documentation index
//   /docs/key-rows            one page
//   /docs/key-rows#changing-the-keys   a heading inside a page
//   /changelog                the notes of every stable release
//   /forums                   the forums: every category and discussion
//   /forums/c/q-a             one category
//   /forums/12                one discussion
//   /forums/12#comment-ID     a comment inside it
//   /source                   the code: the files at the top, the README, the languages, tags and people
//   /source/tree/app/src      one folder
//   /source/blob/README.md    one file (#L10 or #L10-L20 marks lines)
//   /source/commits           every commit; /source/history/PATH the commits that touched a file or folder
//   /source/commit/SHA        one commit with its diff
//   /source/tags              the tags
//   /source/search/code/foo   find files (files) or text in the code (code)
// On a GitHub Pages project site all of these come after /name (see base.js). parseRoute takes what comes after that.
import { BASE } from './base.js'

const safe = (s) => {
  try {
    return decodeURIComponent(s)
  } catch {
    return s
  }
}

/** The route for an address inside the site, with its #anchor: parseRoute('/docs/key-rows#changing-the-keys'). */
export function parseRoute(address = '') {
  const [path = '', anchor = ''] = address.split('#')
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

// ---------- moving around without reloading ----------

/** The part of a path that comes after the site's base, or null when the path is not part of this site. */
export function stripBase(pathname) {
  if (!BASE) return pathname || '/'
  if (pathname === BASE) return '/'
  return pathname.startsWith(`${BASE}/`) ? pathname.slice(BASE.length) : null
}

/** Where the browser is now, as parseRoute wants it. */
export const currentAddress = () => (stripBase(window.location.pathname) ?? '/') + window.location.hash

const listeners = new Set()
const notify = () => listeners.forEach((fn) => fn())

/** Calls fn after every move made by navigate(). Returns a function that stops it. */
export function subscribe(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** Go to an address of the site without loading the page again. */
export function navigate(url, { replace = false } = {}) {
  const u = new URL(url, window.location.href)
  window.history[replace ? 'replaceState' : 'pushState'](null, '', u.pathname + u.search + u.hash)
  notify()
}

/** Links to other pages of the site are followed here instead of loading the whole site again. Plain clicks only. */
export function followLinks(e) {
  if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
  const a = e.target instanceof Element ? e.target.closest('a[href]') : null
  if (!a || (a.target && a.target !== '_self') || a.hasAttribute('download')) return
  const u = new URL(a.href, window.location.href)
  if (u.origin !== window.location.origin || stripBase(u.pathname) === null) return
  // The same page: the browser scrolls to the #anchor itself (and says so with popstate).
  if (u.pathname === window.location.pathname && u.search === window.location.search) return
  e.preventDefault()
  navigate(u.href)
}

/** An old address like /name/#/docs/key-rows becomes /name/docs/key-rows, so links made before the change still work. */
export function upgradeLegacyAddress() {
  const h = window.location.hash
  if (h.startsWith('#/')) window.history.replaceState(null, '', BASE + h.slice(1))
}
