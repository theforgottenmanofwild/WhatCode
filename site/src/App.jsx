import { to } from './base.js'
import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { DISCORD_URL, repoUrl } from './config.js'
import { docBySlug } from './docs.js'
import { currentAddress, followLinks, parseRoute, subscribe } from './route.js'
import Changelog from './components/Changelog.jsx'
import Docs from './components/Docs.jsx'
import Home from './components/Home.jsx'
import { SearchDialog } from './components/Search.jsx'
import SiteMenu from './components/SiteMenu.jsx'

// The forums bring in an HTML parser and sanitiser for post text; only people who open them download that.
const Forums = lazy(() => import('./components/Forums.jsx'))
// The code browser brings in the file viewer and diff logic; only people who open Source download it.
const Source = lazy(() => import('./components/Source.jsx'))

function useRoute() {
  const [address, setAddress] = useState(currentAddress)
  useEffect(() => {
    const update = () => setAddress(currentAddress())
    window.addEventListener('popstate', update)
    window.addEventListener('hashchange', update)
    document.addEventListener('click', followLinks)
    const stop = subscribe(update)
    return () => {
      window.removeEventListener('popstate', update)
      window.removeEventListener('hashchange', update)
      document.removeEventListener('click', followLinks)
      stop()
    }
  }, [])
  return useMemo(() => parseRoute(address), [address])
}

// "/" or Ctrl/Cmd+K opens search, unless the person is typing somewhere.
function useSearchShortcut(open) {
  useEffect(() => {
    const onKey = (e) => {
      const t = e.target
      const typing = t instanceof HTMLElement && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))
      const combo = (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k'
      if (combo || (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey)) {
        e.preventDefault()
        open()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
      <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2" />
      <path d="m13 13 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

// The editor's own </> mark, as in the app icon.
function Mark() {
  return (
    <span className="wordmark-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32" width="20" height="20" focusable="false">
        <path
          d="M12 9 5 16l7 7M20 9l7 7-7 7"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  )
}

export default function App() {
  const route = useRoute()
  const [searchOpen, setSearchOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  // Search and the menu never sit on top of each other.
  const openSearch = useCallback(() => {
    setMenuOpen(false)
    setSearchOpen(true)
  }, [])
  const closeSearch = useCallback(() => setSearchOpen(false), [])
  const mainRef = useRef(null)
  const lastPage = useRef(null)
  useSearchShortcut(openSearch)

  useEffect(() => {
    // The forums name the page after the discussion, which they only know once it has loaded.
    // The source pages name themselves after the file or commit, which they only know once the code has loaded.
    if (route.page === 'forums' || route.page === 'source') return
    const doc = route.page === 'docs' && route.slug ? docBySlug(route.slug) : null
    document.title = doc
      ? `${doc.title} - WhatCode`
      : route.page === 'docs'
        ? 'Documentation - WhatCode'
        : route.page === 'changelog'
          ? 'Changelog - WhatCode'
          : 'WhatCode'
  }, [route])

  // After every navigation: go to the heading named in the link, or to the top of a new page.
  useEffect(() => {
    setSearchOpen(false)
    setMenuOpen(false)
    const page = [route.page, route.slug, route.category && `c:${route.category}`, route.thread, route.view, route.path, route.sha, route.mode]
      .filter(Boolean)
      .join('/')
    const samePage = lastPage.current === page
    const first = lastPage.current === null
    lastPage.current = page
    const target = route.anchor ? document.getElementById(route.anchor) : null
    const calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (target) target.scrollIntoView({ behavior: samePage && !calm ? 'smooth' : 'auto' })
    else if (!samePage || !route.anchor) window.scrollTo(0, 0)
    if (!samePage && !first) mainRef.current?.focus({ preventScroll: true })
  }, [route])

  const inDocs = route.page === 'docs'

  return (
    <div className={inDocs ? 'page page-wide' : 'page'}>
      <a className="skip" href="#main" onClick={(e) => { e.preventDefault(); mainRef.current?.focus() }}>
        Skip to content
      </a>
      <header className="site-header">
        <div className="bar">
          <a className="wordmark" href={to('/')}>
            <Mark />
            <span className="wordmark-text">WhatCode</span>
          </a>
          <button
            type="button"
            className="search-open"
            onClick={openSearch}
            aria-label="Search the documentation"
            aria-keyshortcuts="/ Control+K Meta+K"
          >
            <SearchIcon />
            <span className="search-open-label" aria-hidden="true">Search docs</span>
            <kbd aria-hidden="true">/</kbd>
          </button>
          <SiteMenu page={route.page} open={menuOpen} setOpen={setMenuOpen} />
        </div>
      </header>
      {/* inert: while the menu covers the page, the keyboard stays in the menu */}
      <main id="main" tabIndex={-1} ref={mainRef} inert={menuOpen}>
        {inDocs ? (
          <Docs slug={route.slug} onSearch={openSearch} />
        ) : route.page === 'home' ? (
          <Home />
        ) : route.page === 'forums' ? (
          <Suspense
            fallback={
              <section className="forum-missing">
                <h1>Forums</h1>
                <p className="state" role="status">Loading…</p>
              </section>
            }
          >
            <Forums route={route} />
          </Suspense>
        ) : route.page === 'source' ? (
          <Suspense
            fallback={
              <section className="forum-missing">
                <h1>Source</h1>
                <p className="state" role="status">Loading…</p>
              </section>
            }
          >
            <Source route={route} />
          </Suspense>
        ) : route.page === 'changelog' ? (
          <Changelog />
        ) : (
          <section className="missing">
            <h1>Page not found</h1>
            <p>
              That address does not lead anywhere. Go to the <a href={to('/download')}>download page</a> or the{' '}
              <a href={to('/docs')}>documentation</a>.
            </p>
          </section>
        )}
      </main>
      <footer className="site-footer" inert={menuOpen}>
        <div className="footer-in">
          <p>WhatCode is a small code editor for Android. Builds are not distributed through Google Play.</p>
          <nav aria-label="Footer">
            <a href={to('/download')}>Download</a>
            <a href={to('/docs')}>Docs</a>
            <a href={to('/forums')}>Forums</a>
            <a href={to('/changelog')}>Changelog</a>
            {repoUrl && <a href={`${repoUrl}/releases`}>All releases</a>}
            <a href={to('/source')}>Source</a>
            {repoUrl && <a href={repoUrl}>GitHub</a>}
            <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer">Discord</a>
          </nav>
        </div>
      </footer>
      <SearchDialog open={searchOpen} onClose={closeSearch} />
    </div>
  )
}
