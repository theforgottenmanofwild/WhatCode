import { STABLE_APK, NIGHTLY_APK, NIGHTLY_TAG } from './config.js'

function summarize(release, apkName) {
  if (!release) return null
  const asset =
    release.assets?.find((a) => a.name === apkName) ?? release.assets?.find((a) => a.name.endsWith('.apk'))
  if (!asset) return null
  const isNightly = release.tag_name === NIGHTLY_TAG
  return {
    tag: release.tag_name,
    version: isNightly
      ? (release.name || '').replace(/^Nightly\s+/i, '') || 'nightly'
      : release.tag_name.replace(/^v/, ''),
    published: release.published_at,
    url: asset.browser_download_url,
    size: asset.size,
    sha256: typeof asset.digest === 'string' && asset.digest.startsWith('sha256:') ? asset.digest.slice(7) : null,
    notesUrl: release.html_url,
    commit: isNightly && /^[0-9a-f]{40}$/.test(release.target_commitish || '') ? release.target_commitish : null,
  }
}

/** Turn the GitHub "list releases" response into the two channels the site shows. */
export function pickReleases(list) {
  const published = list.filter((r) => !r.draft)
  const stables = published.filter((r) => !r.prerelease && r.tag_name !== NIGHTLY_TAG)
  return {
    stable: summarize(stables[0], STABLE_APK),
    nightly: summarize(published.find((r) => r.tag_name === NIGHTLY_TAG), NIGHTLY_APK),
    older: stables.slice(1, 6).map((r) => summarize(r, STABLE_APK)).filter(Boolean),
  }
}

/** Every published stable release with its notes, newest first, for the Changelog page. */
export function pickChangelog(list) {
  return list
    .filter((r) => !r.draft && !r.prerelease && r.tag_name !== NIGHTLY_TAG)
    .map((r) => ({
      tag: r.tag_name,
      version: r.tag_name.replace(/^v/, ''),
      published: r.published_at,
      url: r.html_url,
      notes: (r.body || '').trim(),
    }))
    .sort((a, b) => new Date(b.published) - new Date(a.published))
}

export async function fetchReleases(apiUrl, signal) {
  const res = await fetch(apiUrl, { signal, headers: { Accept: 'application/vnd.github+json' } })
  if (res.status === 403 || res.status === 429) throw new Error('rate-limit')
  if (res.status === 404) throw new Error('not-found')
  if (!res.ok) throw new Error(`http-${res.status}`)
  const list = await res.json()
  return { ...pickReleases(list), changelog: pickChangelog(list) }
}

export function formatSize(bytes) {
  if (!Number.isFinite(bytes)) return ''
  return bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function formatDate(iso) {
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(iso))
}

export function relativeTime(iso, now = Date.now()) {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  const steps = [
    ['year', 31536000],
    ['month', 2592000],
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ]
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) return rtf.format(Math.round(seconds / size), unit)
  }
  return 'just now'
}

export const shortSha = (sha) => sha.slice(0, 7)
