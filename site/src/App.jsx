import { useEffect, useState } from 'react'
import { repoUrl } from './config.js'
import { docs, docBySlug } from './docs.js'
import Docs from './components/Docs.jsx'
import Home from './components/Home.jsx'

function parseRoute(hash) {
  const parts = hash.replace(/^#\/?/, '').split('/').filter(Boolean)
  if (parts[0] === 'docs') return { page: 'docs', slug: parts[1] ?? docs[0]?.slug }
  return { page: 'home' }
}

function useRoute() {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash))
  useEffect(() => {
    const onChange = () => {
      setRoute(parseRoute(window.location.hash))
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}

export default function App() {
  const route = useRoute()

  useEffect(() => {
    const doc = route.page === 'docs' ? docBySlug(route.slug) : null
    document.title = doc ? `${doc.title} - WhatCodeEditor` : 'WhatCodeEditor'
  }, [route])

  return (
    <>
      <a className="skip" href="#main" onClick={(e) => { e.preventDefault(); document.getElementById('main')?.focus() }}>
        Skip to content
      </a>
      <header className="site-header">
        <a className="wordmark" href="#/">WhatCodeEditor</a>
        <nav aria-label="Main">
          <a href="#/" aria-current={route.page === 'home' ? 'page' : undefined}>Download</a>
          <a href="#/docs" aria-current={route.page === 'docs' ? 'page' : undefined}>Documentation</a>
          {repoUrl && <a href={repoUrl}>Source</a>}
        </nav>
      </header>
      <main id="main" tabIndex={-1}>
        {route.page === 'docs' ? <Docs slug={route.slug} /> : <Home />}
      </main>
      <footer className="site-footer">
        <p>WhatCodeEditor is a small code editor for Android. Builds are not distributed through Google Play.</p>
      </footer>
    </>
  )
}
