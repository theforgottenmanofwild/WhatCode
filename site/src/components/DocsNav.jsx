import { useEffect, useState } from 'react'
import { sections } from '../docs.js'

function Chevron() {
  return (
    <svg className="chevron" viewBox="0 0 12 12" width="12" height="12" aria-hidden="true" focusable="false">
      <path d="m3 4.5 3 3 3-3" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** The documentation menu: sections you can fold, with the pages of each. Scales to many sections and pages. */
export default function DocsNav({ slug, onNavigate }) {
  const currentKey = sections.find((s) => s.docs.some((d) => d.slug === slug))?.key
  // Only what the reader folded or unfolded by hand is stored; everything else follows the defaults.
  const [manual, setManual] = useState({})
  const isOpen = (key) => manual[key] ?? (key === currentKey || sections.length <= 3)

  // Opening a page in another section unfolds that section.
  useEffect(() => {
    if (currentKey) setManual((m) => (m[currentKey] === false ? { ...m, [currentKey]: true } : m))
  }, [currentKey])

  return (
    <nav
      className="docs-nav"
      aria-label="Documentation"
      onClick={(e) => e.target.closest('a') && onNavigate?.()}
    >
      <a className="nav-all" href="#/docs" aria-current={slug ? undefined : 'page'}>
        All topics
      </a>
      {sections.map((s) => {
        const open = isOpen(s.key)
        const listId = `nav-${s.key}`
        return (
          <div className="nav-section" key={s.key}>
            <button
              type="button"
              className="nav-section-btn"
              aria-expanded={open}
              aria-controls={listId}
              onClick={() => setManual((m) => ({ ...m, [s.key]: !open }))}
            >
              <span>{s.title}</span>
              <span className="nav-count">{s.docs.length}</span>
              <Chevron />
            </button>
            <ul id={listId} hidden={!open}>
              {s.docs.map((d) => (
                <li key={d.slug}>
                  <a href={`#/docs/${d.slug}`} aria-current={d.slug === slug ? 'page' : undefined}>
                    {d.title}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}
