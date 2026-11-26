import { DISCORD_URL, repoUrl } from '../config.js'
import { sections as docSections } from '../docs.js'
import { buildSiteMap, countPages } from '../sitemap.js'
import { useForum } from '../useForum.js'
import { useSource } from '../useSource.js'
import { SECTIONS } from './SiteMenu.jsx'

const EXTERNAL = [
  ...(repoUrl ? [{ title: 'GitHub', href: repoUrl, note: 'Open the repository', external: true }] : []),
  { title: 'Discord', href: DISCORD_URL, note: 'Chat with the community', external: true },
]

function Links({ links }) {
  return (
    <ul className="sitemap-links">
      {links.map((l) => (
        <li key={l.href}>
          <a href={l.href} {...(l.external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
            {l.title}
          </a>
          {l.note && <small>{l.note}</small>}
        </li>
      ))}
    </ul>
  )
}

function Section({ section }) {
  const heading = section.href ? <a href={section.href}>{section.title}</a> : section.title
  if (section.collapsed) {
    return (
      <details className="sitemap-section">
        <summary>
          {heading}
          <span className="muted"> · {section.links.length}</span>
        </summary>
        {section.links.length > 0 && <Links links={section.links} />}
      </details>
    )
  }
  return (
    <div className="sitemap-section">
      {section.title && <h3>{heading}</h3>}
      <Links links={section.links} />
    </div>
  )
}

function Status({ group, onRetry }) {
  if (group.status === 'loading') return <p className="state" role="status">Loading the list…</p>
  if (group.status === 'error') {
    return (
      <p className="state" role="alert">
        The list could not be loaded.{' '}
        {onRetry && <button type="button" className="textbtn" onClick={onRetry}>Try again</button>}
      </p>
    )
  }
  // 'unavailable': the build had nothing to copy, so the group is just its own page.
  return null
}

/** The page body for a site map that is already built, so the smoke test can draw it with sample data. */
export function SiteMapView({ map, retry = {} }) {
  return (
    <section className="sitemap" aria-labelledby="sitemap-title">
      <h1 id="sitemap-title">Site map</h1>
      <p className="muted sitemap-count">
        {countPages(map).toLocaleString('en')} pages, listed as the site finds them.
      </p>
      <div className="sitemap-groups">
        {map.map((g) => (
          <section key={g.id} className="sitemap-group" aria-labelledby={`sitemap-${g.id}`}>
            <h2 id={`sitemap-${g.id}`}>{g.href ? <a href={g.href}>{g.title}</a> : g.title}</h2>
            <p className="muted">{g.note}</p>
            <Status group={g} onRetry={retry[g.id]} />
            {g.sections.map((s) => (
              <Section key={s.title ?? 'links'} section={s} />
            ))}
          </section>
        ))}
      </div>
    </section>
  )
}

/**
 * Draws the site map when it is opened: the documentation from the pages the site was built with, the forums and
 * the source from the snapshots it loads (the same two files the Forums and Source pages read, loaded once).
 */
export default function SiteMap() {
  const forum = useForum()
  const source = useSource()
  const map = buildSiteMap({ nav: SECTIONS, docs: docSections, forum, source, external: EXTERNAL })
  return <SiteMapView map={map} retry={{ forums: forum.retry, source: source.retry }} />
}
