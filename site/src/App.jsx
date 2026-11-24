import { useCallback, useEffect, useRef, useState } from 'react'
import { repoUrl } from './config.js'
import { docBySlug } from './docs.js'
import { parseRoute } from './route.js'
import Changelog from './components/Changelog.jsx'
import Docs from './components/Docs.jsx'
import Home from './components/Home.jsx'
import { SearchDialog } from './components/Search.jsx'

function useRoute() {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash))
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash))
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
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

export default function App() {
  const route = useRoute()
  const [searchOpen, setSearchOpen] = useState(false)
  const openSearch = useCallback(() => setSearchOpen(true), [])
  const closeSearch = useCallback(() => setSearchOpen(false), [])
  const mainRef = useRef(null)
  const lastPage = useRef(null)
  useSearchShortcut(openSearch)

  useEffect(() => {
    const doc = route.page === 'docs' && route.slug ? docBySlug(route.slug) : null
    document.title = doc
      ? `${doc.title} - WhatCodeEditor`
      : route.page === 'docs'
        ? 'Documentation - WhatCodeEditor'
        : route.page === 'changelog'
          ? 'Changelog - WhatCodeEditor'
          : 'WhatCodeEditor'
  }, [route])

  // After every navigation: go to the heading named in the link, or to the top of a new page.
  useEffect(() => {
    setSearchOpen(false)
    const page = `${route.page}/${route.slug ?? ''}`
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
          <a className="wordmark" href="#/">WhatCodeEditor</a>
          <nav aria-label="Main">
            <a href="#/download" aria-current={route.page === 'home' ? 'page' : undefined}>Download</a>
            <a href="#/docs" aria-current={inDocs ? 'page' : undefined}>Docs</a>
            <a href="#/changelog" aria-current={route.page === 'changelog' ? 'page' : undefined}>Changelog</a>
            {repoUrl && <a href={repoUrl}>Source</a>}
          </nav>
          <button type="button" className="search-open" onClick={openSearch} aria-keyshortcuts="/ Control+K Meta+K">
            <SearchIcon />
            <span className="search-open-label">Search docs</span>
            <kbd aria-hidden="true">/</kbd>
          </button>
        </div>
      </header>
      <main id="main" tabIndex={-1} ref={mainRef}>
        {inDocs ? (
          <Docs slug={route.slug} onSearch={openSearch} />
        ) : route.page === 'home' ? (
          <Home />
        ) : route.page === 'changelog' ? (
          <Changelog />
        ) : (
          <section className="missing">
            <h1>Page not found</h1>
            <p>
              That address does not lead anywhere. Go to the <a href="#/download">download page</a> or the{' '}
              <a href="#/docs">documentation</a>.
            </p>
          </section>
        )}
      </main>
      <footer className="site-footer">
        <div className="footer-in">
          <p>WhatCodeEditor is a small code editor for Android. Builds are not distributed through Google Play.</p>
          <nav aria-label="Footer">
            <a href="#/download">Download</a>
            <a href="#/docs">Docs</a>
            <a href="#/changelog">Changelog</a>
            {repoUrl && <a href={`${repoUrl}/releases`}>All releases</a>}
            {repoUrl && <a href={repoUrl}>Source</a>}
          </nav>
        </div>
      </footer>
      <SearchDialog open={searchOpen} onClose={closeSearch} />
    </div>
  )
}
