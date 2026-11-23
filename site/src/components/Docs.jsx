import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { links } from '../config.js'
import { docs, docBySlug } from '../docs.js'

// Docs link to each other as ./02-using-the-editor.md so they work on GitHub too.
function resolveHref(href = '') {
  const m = href.match(/^(?:\.\/)?\d+-([\w-]+)\.md(#.*)?$/)
  if (m) return { href: `#/docs/${m[1]}`, external: false }
  if (href.startsWith('#')) return { href, external: false }
  return { href, external: /^https?:\/\//.test(href) }
}

const components = {
  a({ href, children }) {
    const r = resolveHref(href)
    return r.external ? (
      <a href={r.href} target="_blank" rel="noopener noreferrer">{children}</a>
    ) : (
      <a href={r.href}>{children}</a>
    )
  },
  table({ children }) {
    return (
      <div className="table-wrap">
        <table>{children}</table>
      </div>
    )
  },
}

export default function Docs({ slug }) {
  const doc = docBySlug(slug)
  return (
    <div className="docs">
      <nav className="docs-nav" aria-label="Documentation">
        <ul>
          {docs.map((d) => (
            <li key={d.slug}>
              <a href={`#/docs/${d.slug}`} aria-current={d.slug === slug ? 'page' : undefined}>
                {d.title}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <article className="doc">
        {doc ? (
          <>
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
              {doc.raw}
            </ReactMarkdown>
            {links && (
              <p className="edit-link">
                <a href={links.editDoc(doc.file)} target="_blank" rel="noopener noreferrer">
                  Suggest a change to this page
                </a>
              </p>
            )}
          </>
        ) : (
          <>
            <h1>Page not found</h1>
            <p>
              There is no documentation page called <code>{slug}</code>. Pick one from the list, or{' '}
              <a href="#/docs/getting-started">start with Getting started</a>.
            </p>
          </>
        )}
      </article>
    </div>
  )
}
