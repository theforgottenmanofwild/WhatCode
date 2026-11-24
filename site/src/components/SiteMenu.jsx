import { useEffect, useRef } from 'react'
import { to } from '../base.js'
import { repoUrl } from '../config.js'

// Every section of the site. `page` is the name parseRoute() gives the page ("home" is the download page).
export const SECTIONS = [
  { page: 'home', path: '/download', title: 'Download', note: 'Get the Android app' },
  { page: 'docs', path: '/docs', title: 'Docs', note: 'Guides to using the editor' },
  { page: 'forums', path: '/forums', title: 'Forums', note: 'Questions and discussions' },
  { page: 'changelog', path: '/changelog', title: 'Changelog', note: 'What changed in each release' },
  { page: 'source', path: '/source', title: 'Source', note: 'Browse the code and its history' },
]

function Arrow({ external = false }) {
  return (
    <svg viewBox="0 0 20 20" width="20" height="20" aria-hidden="true" focusable="false">
      <path
        d={external ? 'M7 5h8v8M15 5 5 15' : 'm8 4 6 6-6 6'}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * The three-line button in the header and the panel it opens, which holds every section of the site.
 * A full-width sheet under the header on phones, a card under the button on larger screens.
 */
export default function SiteMenu({ page, open, setOpen }) {
  const buttonRef = useRef(null)
  const panelRef = useRef(null)

  // While the menu is open the page behind it does not scroll, and Escape closes it.
  useEffect(() => {
    if (!open) return
    const root = document.documentElement
    root.classList.add('menu-open')
    const onKey = (e) => {
      if (e.key !== 'Escape') return
      setOpen(false)
      buttonRef.current?.focus()
    }
    window.addEventListener('keydown', onKey)
    return () => {
      root.classList.remove('menu-open')
      window.removeEventListener('keydown', onKey)
    }
  }, [open, setOpen])

  // Opening the menu moves the keyboard into it, to the page you are on.
  useEffect(() => {
    if (!open) return
    const panel = panelRef.current
    const first = panel?.querySelector('a[aria-current="page"]') ?? panel?.querySelector('a')
    first?.focus({ preventScroll: true })
  }, [open])

  const onPanelClick = (e) => {
    const a = e.target instanceof Element ? e.target.closest('a[href]') : null
    if (!a) return
    // Tapping the page you are already on would reload it: just close the menu and go to the top.
    const u = new URL(a.href, window.location.href)
    if (
      u.origin === window.location.origin &&
      u.pathname === window.location.pathname &&
      u.search === window.location.search &&
      !u.hash
    ) {
      e.preventDefault()
      window.scrollTo(0, 0)
    }
    setOpen(false)
  }

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        className="menu-button"
        aria-expanded={open}
        aria-controls="site-menu"
        aria-label={open ? 'Close menu' : 'Open menu'}
        onClick={() => setOpen(!open)}
      >
        <span className="bars" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
      </button>

      {/* a tap outside the panel closes it */}
      <button type="button" className="menu-backdrop" hidden={!open} tabIndex={-1} aria-hidden="true" onClick={() => setOpen(false)} />

      <nav id="site-menu" ref={panelRef} className="menu-panel" aria-label="Main" hidden={!open} onClick={onPanelClick}>
        <ul className="menu-list">
          {SECTIONS.map((s) => (
            <li key={s.page}>
              <a className="menu-link" href={to(s.path)} aria-current={page === s.page ? 'page' : undefined}>
                <strong>{s.title}</strong>
                <small>{s.note}</small>
                <Arrow />
              </a>
            </li>
          ))}
          {repoUrl && (
            <li>
              <a className="menu-link" href={repoUrl} target="_blank" rel="noopener noreferrer">
                <strong>GitHub</strong>
                <small>Open the repository</small>
                <Arrow external />
              </a>
            </li>
          )}
        </ul>
        <p className="menu-foot">Builds are not distributed through Google Play.</p>
      </nav>
    </>
  )
}
