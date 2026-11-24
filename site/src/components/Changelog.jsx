import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { formatDate } from '../releases.js'
import { useReleases } from '../useReleases.js'

const remarkPlugins = [remarkGfm]

// The notes use "##" for their sections; the page already has an h1 and an h2 per release.
const components = {
  h1: 'h3',
  h2: 'h3',
  h3: 'h4',
  h4: 'h4',
  h5: 'h4',
  h6: 'h4',
  a: ({ node, ...props }) => <a {...props} target="_blank" rel="noopener noreferrer" />,
}

function Release({ release }) {
  const id = `release-${release.tag}`
  return (
    <article className="release" aria-labelledby={id}>
      <header className="release-head">
        <h2 id={id}>{release.version}</h2>
        {release.published && <time dateTime={release.published}>{formatDate(release.published)}</time>}
      </header>
      {release.notes && (
        <div className="release-notes">
          <ReactMarkdown remarkPlugins={remarkPlugins} components={components}>
            {release.notes}
          </ReactMarkdown>
        </div>
      )}
    </article>
  )
}

/** The page body for a given load state. With no stable release yet it shows nothing under the title. */
export function ChangelogView({ status, changelog = [], onRetry }) {
  return (
    <section className="changelog" aria-labelledby="changelog-title">
      <h1 id="changelog-title">Changelog</h1>
      {status === 'loading' && (
        <p className="state" role="status">Loading…</p>
      )}
      {status === 'error' && (
        <p className="state" role="alert">
          The changelog could not be loaded.{' '}
          <button type="button" className="textbtn" onClick={onRetry}>Try again</button>
        </p>
      )}
      {status === 'ready' && changelog.map((r) => <Release key={r.tag} release={r} />)}
    </section>
  )
}

export default function Changelog() {
  const { status, changelog, retry } = useReleases()
  return <ChangelogView status={status} changelog={changelog} onRetry={retry} />
}
