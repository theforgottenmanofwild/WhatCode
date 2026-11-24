import { useEffect, useMemo, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { links } from '../config.js'
import { docBySlug, makeSlugger, neighbours, resolveHref, sections } from '../docs.js'
import DocsNav from './DocsNav.jsx'
import { SearchBox } from './Search.jsx'

// ---------- markdown rendering ----------

const textOf = (node) => node.value ?? (node.children ?? []).map(textOf).join('')

// Gives every ## and ### heading an id, by the same rule docs.js uses for "On this page" and search.
function remarkHeadingIds() {
  return (tree) => {
    const slug = makeSlugger()
    const walk = (node) => {
      if (node.type === 'heading' && node.depth >= 2 && node.depth <= 3) {
        const id = slug(textOf(node).replace(/\s+/g, ' '))
        node.data = { ...node.data, hProperties: { ...node.data?.hProperties, id } }
      }
      node.children?.forEach(walk)
    }
    walk(tree)
  }
}

const remarkPlugins = [remarkGfm, remarkHeadingIds]

function makeComponents(slug) {
  const heading = (Tag) =>
    function Heading({ id, children }) {
      return (
        <Tag id={id}>
          {children}
          {id && (
            <a className="anchor" href={`#/docs/${slug}#${id}`} aria-label="Link to this section">
              #
            </a>
          )}
        </Tag>
      )
    }
  return {
    h2: heading('h2'),
    h3: heading('h3'),
    a({ href, children }) {
      const r = resolveHref(href, slug)
      return r.external ? (
        <a href={r.href} target="_blank" rel="noopener noreferrer">{children}</a>
      ) : (
        <a href={r.href}>{children}</a>
      )
    },
    table({ children }) {
      return (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Table, scrolls sideways">
          <table>{children}</table>
        </div>
      )
    },
  }
}

// ---------- "On this page" ----------

// The heading nearest the top of the screen, for highlighting in the list.
function useActiveHeading(ids) {
  const [active, setActive] = useState(null)
  const key = ids.join('|')
  useEffect(() => {
    setActive(null)
    if (!ids.length || typeof IntersectionObserver === 'undefined') return
    const els = ids.map((id) => document.getElementById(id)).filter(Boolean)
    const visible = new Set()
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) e.isIntersecting ? visible.add(e.target.id) : visible.delete(e.target.id)
        const first = ids.find((id) => visible.has(id))
        if (first) return setActive(first)
        // Nothing in the band: the last heading already scrolled past is the current one.
        const passed = els.filter((el) => el.getBoundingClientRect().top < 120)
        setActive(passed.at(-1)?.id ?? null)
      },
      { rootMargin: '-80px 0px -65% 0px' },
    )
    els.forEach((el) => io.observe(el))
    return () => io.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return active
}

function TocList({ slug, headings, active }) {
  return (
    <ul>
      {headings.map((h) => (
        <li key={h.id} className={h.level === 3 ? 'sub' : undefined}>
          <a href={`#/docs/${slug}#${h.id}`} aria-current={h.id === active ? 'location' : undefined}>
            {h.text}
          </a>
        </li>
      ))}
    </ul>
  )
}

// ---------- one page ----------

function DocBody({ slug, doc }) {
  const components = useMemo(() => makeComponents(slug), [slug])
  const headings = doc?.headings ?? []
  const active = useActiveHeading(headings.map((h) => h.id))
  const { prev, next } = neighbours(slug)
  const showToc = headings.length >= 2

  return (
    <>
      <div className="docs-main">
        <article className="doc">
          {doc ? (
            <>
              <p className="eyebrow">
                <a href="#/docs">Docs</a> <span aria-hidden="true">›</span> {doc.section.title}
              </p>
              {showToc && (
                <details className="toc-inline">
                  <summary>On this page</summary>
                  <TocList slug={slug} headings={headings} active={active} />
                </details>
              )}
              <ReactMarkdown remarkPlugins={remarkPlugins} components={components}>
                {doc.raw}
              </ReactMarkdown>
              {links && (
                <p className="edit-link">
                  <a href={links.editDoc(doc.path)} target="_blank" rel="noopener noreferrer">
                    Suggest a change to this page
                  </a>
                </p>
              )}
            </>
          ) : (
            <>
              <h1>Page not found</h1>
              <p>
                There is no documentation page called <code>{slug}</code>. Pick one from the menu, or{' '}
                <a href="#/docs">see all topics</a>.
              </p>
            </>
          )}
        </article>

        {doc && (prev || next) && (
          <nav className="pager" aria-label="Previous and next page">
            {prev && (
              <a className="pager-prev" href={`#/docs/${prev.slug}`} rel="prev">
                <span>Previous</span>
                <strong>{prev.title}</strong>
              </a>
            )}
            {next && (
              <a className="pager-next" href={`#/docs/${next.slug}`} rel="next">
                <span>Next</span>
                <strong>{next.title}</strong>
              </a>
            )}
          </nav>
        )}
      </div>

      {showToc && (
        <aside className="docs-toc" aria-label="On this page">
          <h2>On this page</h2>
          <TocList slug={slug} headings={headings} active={active} />
        </aside>
      )}
    </>
  )
}

// ---------- the index of all topics ----------

const SHOWN_PER_SECTION = 6

function Topic({ section }) {
  const [all, setAll] = useState(false)
  const shown = all ? section.docs : section.docs.slice(0, SHOWN_PER_SECTION)
  const id = `topic-${section.key}`
  return (
    <section className="topic" aria-labelledby={id}>
      <h2 id={id}>
        {section.title} <span className="nav-count">{section.docs.length}</span>
      </h2>
      <ul>
        {shown.map((d) => (
          <li key={d.slug}>
            <a href={`#/docs/${d.slug}`}>
              <span className="topic-title">{d.title}</span>
              {d.summary && <span className="topic-sum">{d.summary}</span>}
            </a>
          </li>
        ))}
      </ul>
      {section.docs.length > SHOWN_PER_SECTION && (
        <button type="button" className="textbtn" aria-expanded={all} onClick={() => setAll((v) => !v)}>
          {all ? 'Show fewer' : `Show all ${section.docs.length} pages`}
        </button>
      )}
    </section>
  )
}

function DocsIndex() {
  return (
    <div className="docs-index">
      <header className="docs-hero">
        <h1>Documentation</h1>
        <p className="lede">How to install, use and update WhatCodeEditor. Search, or pick a topic.</p>
      </header>
      <SearchBox>
        <div className="topics">
          {sections.map((s) => (
            <Topic key={s.key} section={s} />
          ))}
        </div>
      </SearchBox>
    </div>
  )
}

// ---------- the documentation area ----------

function Chevron() {
  return (
    <svg className="chevron" viewBox="0 0 12 12" width="12" height="12" aria-hidden="true" focusable="false">
      <path d="m3 4.5 3 3 3-3" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export default function Docs({ slug, onSearch }) {
  const doc = slug ? docBySlug(slug) : null
  const [menuOpen, setMenuOpen] = useState(false)
  useEffect(() => setMenuOpen(false), [slug])

  if (!slug) return <DocsIndex />

  return (
    <div className="docs">
      <aside className={`docs-side${menuOpen ? ' is-open' : ''}`}>
        <button
          type="button"
          className="docs-menu-toggle"
          aria-expanded={menuOpen}
          aria-controls="docs-side-panel"
          onClick={() => setMenuOpen((o) => !o)}
        >
          <span className="crumb">
            <small>{doc?.section.title ?? 'Documentation'}</small>
            <strong>{doc?.title ?? 'Page not found'}</strong>
          </span>
          <span className="menu-label">
            Topics <Chevron />
          </span>
        </button>
        <div className="docs-side-panel" id="docs-side-panel">
          {onSearch && (
            <button type="button" className="side-search" onClick={onSearch}>
              <span>Search docs</span>
              <kbd>/</kbd>
            </button>
          )}
          <DocsNav slug={slug} onNavigate={() => setMenuOpen(false)} />
        </div>
      </aside>
      <DocBody slug={slug} doc={doc} />
    </div>
  )
}
