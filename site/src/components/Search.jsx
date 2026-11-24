import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { docs, queryTerms, search } from '../docs.js'

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

// Text with the searched words wrapped in <mark>.
function Marked({ text, terms }) {
  if (!terms.length) return text
  const re = new RegExp(`(${terms.map(escapeRe).sort((a, b) => b.length - a.length).join('|')})`, 'gi')
  return text.split(re).map((part, i) => (i % 2 ? <mark key={i}>{part}</mark> : part))
}

/**
 * Search field plus its results. With `children`, the children show while the field is empty
 * (the docs index puts its section list there). Used inline and inside the search dialog.
 */
export function SearchBox({ autoFocus = false, onNavigate, children, label = 'Search the documentation' }) {
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listRef = useRef(null)
  const baseId = useId()
  const hits = useMemo(() => search(query), [query])
  const terms = useMemo(() => queryTerms(query), [query])
  const searching = query.trim().length > 0

  useEffect(() => setActive(0), [query])
  useEffect(() => {
    listRef.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' })
  }, [active])

  const go = (hit) => {
    if (!hit) return
    window.location.hash = hit.href
    onNavigate?.()
  }

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown' && hits.length) {
      e.preventDefault()
      setActive((i) => (i + 1) % hits.length)
    } else if (e.key === 'ArrowUp' && hits.length) {
      e.preventDefault()
      setActive((i) => (i - 1 + hits.length) % hits.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      go(hits[active])
    } else if (e.key === 'Escape' && query) {
      // First Escape clears the field; a second one closes the dialog.
      e.preventDefault()
      e.stopPropagation()
      setQuery('')
    }
  }

  const listId = `${baseId}-results`
  return (
    <div className="searchbox">
      <div className="search-field">
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true" focusable="false">
          <circle cx="8.5" cy="8.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2" />
          <path d="m13 13 4.5 4.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <input
          type="search"
          role="combobox"
          aria-label={label}
          aria-expanded={searching && hits.length > 0}
          aria-controls={listId}
          aria-activedescendant={hits.length ? `${baseId}-hit-${active}` : undefined}
          aria-autocomplete="list"
          placeholder="Search the documentation"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="none"
          spellCheck={false}
          enterKeyHint="go"
          autoFocus={autoFocus}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={onKeyDown}
        />
      </div>

      <p className="sr-only" role="status">
        {searching ? `${hits.length} ${hits.length === 1 ? 'result' : 'results'}` : ''}
      </p>

      {searching && hits.length > 0 && (
        <ul className="hits" id={listId} role="listbox" aria-label="Search results" ref={listRef}>
          {hits.map((hit, i) => (
            <li
              key={hit.href}
              id={`${baseId}-hit-${i}`}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'hit is-active' : 'hit'}
              onMouseMove={() => i !== active && setActive(i)}
            >
              <a href={hit.href} tabIndex={-1} onClick={() => onNavigate?.()}>
                <span className="hit-path">
                  {hit.doc.section.title} <span aria-hidden="true">›</span> <Marked text={hit.doc.title} terms={terms} />
                  {hit.heading && (
                    <>
                      {' '}
                      <span aria-hidden="true">›</span> <Marked text={hit.heading} terms={terms} />
                    </>
                  )}
                </span>
                <span className="hit-snippet">
                  <Marked text={hit.snippet} terms={terms} />
                </span>
              </a>
            </li>
          ))}
        </ul>
      )}

      {searching && hits.length === 0 && (
        <p className="no-hits">
          Nothing matches <strong>{query.trim()}</strong>. Try fewer or different words, or{' '}
          <a href="#/docs" onClick={() => onNavigate?.()}>browse all topics</a>.
        </p>
      )}

      {!searching && children}
      {!searching && !children && (
        <p className="search-hint">
          Searches all {docs.length} pages. <span className="hide-touch">Use ↑ ↓ and Enter to pick a result.</span>
        </p>
      )}
    </div>
  )
}

/** The search opened from the header (or with / or Ctrl+K): a full-screen sheet on phones, a centred panel on larger screens. */
export function SearchDialog({ open, onClose }) {
  const ref = useRef(null)

  useEffect(() => {
    const d = ref.current
    if (!d || typeof d.showModal !== 'function') return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="search-dialog"
      aria-label="Search the documentation"
      onClose={onClose}
      onClick={(e) => {
        if (e.target === ref.current) onClose() // a click on the backdrop
      }}
    >
      {open && (
        <div className="search-panel">
          <SearchBox autoFocus onNavigate={onClose} />
          <button type="button" className="dialog-close" onClick={onClose}>
            Close
          </button>
        </div>
      )}
    </dialog>
  )
}
