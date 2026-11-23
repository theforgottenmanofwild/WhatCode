// Renders the main components with sample data in Node, so that a broken import,
// a missing prop or a bad API-response shape fails here instead of in a browser.
//   npm run smoke
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const sha = 'a'.repeat(40)
const apiSample = [
  { tag_name: 'nightly', name: 'Nightly 0.0.1-nightly.57', prerelease: true, draft: false,
    published_at: '2026-11-24T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/nightly', target_commitish: sha,
    assets: [{ name: 'WhatCodeEditor-nightly.apk', browser_download_url: 'https://example.test/n.apk', size: 688128, digest: 'sha256:' + 'b'.repeat(64) },
             { name: 'WhatCodeEditor-nightly.apk.sha256', browser_download_url: 'https://example.test/n.sha', size: 90 }] },
  { tag_name: 'v0.2.0', name: 'WhatCodeEditor 0.2.0', prerelease: false, draft: false,
    published_at: '2026-11-20T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/v0.2.0', target_commitish: 'main',
    assets: [{ name: 'WhatCodeEditor.apk', browser_download_url: 'https://example.test/s.apk', size: 688128 }] },
  { tag_name: 'v0.0.1', name: 'WhatCodeEditor 0.0.1', prerelease: false, draft: false,
    published_at: '2026-10-01T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/v0.0.1',
    assets: [{ name: 'WhatCodeEditor.apk', browser_download_url: 'https://example.test/old.apk', size: 680000 }] },
  { tag_name: 'v0.3.0', name: 'draft', prerelease: false, draft: true, assets: [] },
]

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'error',
  define: { 'import.meta.env.VITE_GITHUB_REPO': JSON.stringify('o/r') },
})

try {
  const { pickReleases, formatSize, relativeTime } = await server.ssrLoadModule('/src/releases.js')
  const { ChannelCard, OlderStable } = await server.ssrLoadModule('/src/components/Downloads.jsx')
  const { default: PhoneMock } = await server.ssrLoadModule('/src/components/PhoneMock.jsx')
  const { default: Docs } = await server.ssrLoadModule('/src/components/Docs.jsx')
  const { docs } = await server.ssrLoadModule('/src/docs.js')

  // release selection
  const picked = pickReleases(apiSample)
  assert.equal(picked.stable.version, '0.2.0', 'newest non-draft, non-prerelease wins')
  assert.equal(picked.nightly.version, '0.0.1-nightly.57')
  assert.equal(picked.nightly.commit, sha)
  assert.equal(picked.nightly.sha256, 'b'.repeat(64))
  assert.equal(picked.stable.commit, null)
  assert.deepEqual(picked.older.map((r) => r.version), ['0.0.1'])
  assert.deepEqual(pickReleases([]), { stable: null, nightly: null, older: [] })
  assert.equal(formatSize(688128), '672 KB')
  assert.match(relativeTime('2026-11-20T10:00:00Z', Date.parse('2026-11-24T10:00:00Z')), /4 days ago/)

  // cards in every state
  const card = (props) => renderToStaticMarkup(h(ChannelCard, props))
  const stable = card({ kind: 'stable', status: 'ready', release: picked.stable })
  assert.match(stable, /Download APK/)
  assert.match(stable, /href="https:\/\/example\.test\/s\.apk"/)
  const nightly = card({ kind: 'nightly', status: 'ready', release: picked.nightly })
  assert.match(nightly, /0\.0\.1-nightly\.57/)
  assert.match(nightly, /aaaaaaa/, 'short commit shown')
  assert.match(nightly, /SHA-256/)
  assert.match(card({ kind: 'stable', status: 'ready', release: null }), /No stable release/)
  assert.match(card({ kind: 'nightly', status: 'ready', release: null }), /No nightly build/)
  assert.match(card({ kind: 'stable', status: 'loading' }), /Checking/)
  const err = card({ kind: 'nightly', status: 'error', reason: 'rate-limit', onRetry() {} })
  assert.match(err, /limiting requests/)
  assert.match(err, /releases\/download\/nightly\/WhatCodeEditor-nightly\.apk/)
  assert.match(card({ kind: 'stable', status: 'error', reason: 'x' }), /releases\/latest\/download\/WhatCodeEditor\.apk/)
  assert.match(renderToStaticMarkup(h(OlderStable, { items: picked.older })), /0\.0\.1/)

  // phone preview + docs
  assert.match(renderToStaticMarkup(h(PhoneMock, { animate: false })), /tok-kw/)
  assert.equal(docs.length, 5, 'five docs pages')
  assert.equal(docs[0].slug, 'getting-started')
  for (const d of docs) {
    const html = renderToStaticMarkup(h(Docs, { slug: d.slug }))
    assert.ok(html.includes(`<h1>${d.title.replace(/&/g, '&amp;')}</h1>`), `${d.slug} renders its title`)
    assert.ok(!/href="\.\/\d+-/.test(html), `${d.slug}: no raw .md links left`)
  }
  assert.match(renderToStaticMarkup(h(Docs, { slug: 'nope' })), /Page not found/)
  assert.match(renderToStaticMarkup(h(Docs, { slug: 'getting-started' })), /href="#\/docs\/using-the-editor"/)

  console.log('smoke: ok')
} finally {
  await server.close()
}
