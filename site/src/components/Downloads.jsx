import { useState } from 'react'
import { links } from '../config.js'
import { formatDate, formatSize, relativeTime, shortSha } from '../releases.js'
import { useReleases } from '../useReleases.js'

const COPY = {
  stable: {
    title: 'Stable',
    blurb: 'Tested by hand and published when a maintainer decides it is ready. Choose this for everyday use.',
    empty: 'No stable release has been published yet. The nightly build is available in the meantime.',
  },
  nightly: {
    title: 'Nightly',
    blurb: 'Built from the latest commit with no manual testing. It has the newest changes and may break.',
    empty: 'No nightly build yet. One is created after the next commit to the main branch.',
  },
}

const ERROR_TEXT = {
  'rate-limit': 'GitHub is limiting requests from your network, so build details are unavailable.',
  'not-found': 'The releases for this project could not be found.',
}

function Checksum({ value }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      /* clipboard unavailable: the value is still selectable */
    }
  }
  return (
    <details className="checksum">
      <summary>SHA-256 checksum</summary>
      <p className="hash">{value}</p>
      <button type="button" className="textbtn" onClick={copy}>
        {copied ? 'Copied' : 'Copy checksum'}
      </button>
    </details>
  )
}

export function ChannelCard({ kind, status, release, reason, onRetry }) {
  const copy = COPY[kind]
  const headingId = `channel-${kind}`
  const direct = links && (kind === 'stable' ? links.stableDirect : links.nightlyDirect)

  return (
    <article className={`channel channel-${kind}`} aria-labelledby={headingId}>
      <header className="channel-head">
        <h2 id={headingId}>{copy.title}</h2>
        {release && <p className="version">{release.version}</p>}
      </header>
      <p className="blurb">{copy.blurb}</p>

      {status === 'loading' && (
        <p className="state" role="status">Checking for the latest build…</p>
      )}

      {status === 'unconfigured' && (
        <p className="state">Builds will appear here once this site is connected to a GitHub repository.</p>
      )}

      {status === 'ready' && !release && <p className="state">{copy.empty}</p>}

      {status === 'ready' && release && (
        <>
          <dl className="facts">
            <div>
              <dt>Published</dt>
              <dd>
                <time dateTime={release.published}>{formatDate(release.published)}</time>, {relativeTime(release.published)}
              </dd>
            </div>
            <div>
              <dt>Size</dt>
              <dd>{formatSize(release.size)}</dd>
            </div>
            {release.commit && (
              <div>
                <dt>Commit</dt>
                <dd>
                  <a href={links.commit(release.commit)}>{shortSha(release.commit)}</a>
                </dd>
              </div>
            )}
          </dl>
          <div className="actions">
            <a className="btn" href={release.url}>Download APK</a>
            <a className="textlink" href={release.notesUrl}>Release notes</a>
          </div>
          {release.sha256 && <Checksum value={release.sha256} />}
        </>
      )}

      {status === 'error' && (
        <>
          <p className="state" role="alert">
            {ERROR_TEXT[reason] ?? 'Build details could not be loaded.'} You can still download the latest file directly.
          </p>
          <div className="actions">
            <a className="btn" href={direct}>Download APK</a>
            <button type="button" className="textbtn" onClick={onRetry}>Try again</button>
          </div>
        </>
      )}
    </article>
  )
}

export function OlderStable({ items }) {
  if (!items?.length) return null
  return (
    <details className="older">
      <summary>Older stable versions</summary>
      <ul>
        {items.map((r) => (
          <li key={r.tag}>
            <a href={r.url}>{r.version}</a>
            <span>{formatDate(r.published)}</span>
            <span>{formatSize(r.size)}</span>
          </li>
        ))}
      </ul>
    </details>
  )
}

export default function Downloads() {
  const { status, stable, nightly, older, reason, retry } = useReleases()
  return (
    <section className="downloads" aria-label="Downloads">
      <div className="channels">
        <ChannelCard kind="stable" status={status} release={stable} reason={reason} onRetry={retry} />
        <ChannelCard kind="nightly" status={status} release={nightly} reason={reason} onRetry={retry} />
      </div>
      <OlderStable items={older} />
      {links && (
        <p className="all-releases">
          <a href={links.allReleases}>All releases on GitHub</a>
        </p>
      )}
    </section>
  )
}
