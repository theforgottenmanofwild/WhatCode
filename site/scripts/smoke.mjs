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
  const { pickReleases, pickChangelog, formatSize, relativeTime } = await server.ssrLoadModule('/src/releases.js')
  const { ChannelCard, OlderStable } = await server.ssrLoadModule('/src/components/Downloads.jsx')
  const { ChangelogView } = await server.ssrLoadModule('/src/components/Changelog.jsx')
  const { default: PhoneMock } = await server.ssrLoadModule('/src/components/PhoneMock.jsx')
  const { default: Docs } = await server.ssrLoadModule('/src/components/Docs.jsx')
  const { docs, sections, search, resolveHref, neighbours } = await server.ssrLoadModule('/src/docs.js')
  const { parseRoute } = await server.ssrLoadModule('/src/route.js')
  const { default: Home } = await server.ssrLoadModule('/src/components/Home.jsx')
  const { SearchBox } = await server.ssrLoadModule('/src/components/Search.jsx')

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

  // changelog: stable releases only, newest first, each with its notes
  const withNotes = apiSample.map((r) =>
    r.tag_name === 'v0.2.0'
      ? { ...r, body: `## ✨ Newly added\n\n- Added **X** ([\`abc1234\`](https://github.com/o/r/commit/${sha}))` }
      : r,
  )
  const log = pickChangelog(withNotes)
  assert.deepEqual(log.map((r) => r.version), ['0.2.0', '0.0.1'], 'no nightly, no draft, newest first')
  assert.match(log[0].notes, /Newly added/)
  assert.equal(log[1].notes, '', 'a release without a description has empty notes')
  assert.deepEqual(pickChangelog([]), [])
  assert.deepEqual(pickChangelog([apiSample[0]]), [], 'a nightly alone leaves the changelog empty')
  const view = (props) => renderToStaticMarkup(h(ChangelogView, props))
  const full = view({ status: 'ready', changelog: log })
  assert.match(full, /<h1[^>]*>Changelog<\/h1>/)
  assert.match(full, /<h2[^>]*>0\.2\.0<\/h2>/)
  assert.match(full, /<h3>✨ Newly added<\/h3>/, 'note headings sit below the release heading')
  assert.match(full, /href="https:\/\/github\.com\/o\/r\/commit\//)
  assert.match(full, /rel="noopener noreferrer"/)
  const none = view({ status: 'ready', changelog: [] })
  assert.match(none, /<h1[^>]*>Changelog<\/h1>/)
  assert.ok(!/<article|<h2|class="state"/.test(none), 'nothing under the title while there is no stable release')
  assert.match(view({ status: 'loading' }), /Loading/)
  assert.match(view({ status: 'error', onRetry() {} }), /could not be loaded/)

  // phone preview + docs
  const phone = renderToStaticMarkup(h(PhoneMock, { animate: false }))
  for (const c of ['tok-kw', 'tok-ctl', 'tok-fn', 'tok-type', 'tok-prop', 'tok-re']) assert.match(phone, new RegExp(c))
  assert.match(phone, /JavaScript<\/div>/, 'the status bar names the real language')
  assert.ok(!/C-like/.test(phone), 'no vague "C-like" label')
  // docs structure: a folder is a section, a file is a page
  assert.equal(docs.length, 9, 'nine docs pages')
  assert.deepEqual(sections.map((x) => x.title), ['Basics', 'Editing', 'The app'])
  assert.deepEqual(sections.map((x) => x.docs.length), [2, 5, 2])
  assert.equal(docs[0].slug, 'getting-started')
  assert.equal(new Set(docs.map((d) => d.slug)).size, docs.length, 'page names are unique across sections')
  for (const d of docs) assert.ok(d.title && d.summary, `${d.slug} has a title and a summary`)
  assert.deepEqual(neighbours('getting-started').prev, undefined)
  assert.equal(neighbours('getting-started').next.slug, 'using-the-editor')
  assert.equal(neighbours('using-the-editor').next.slug, 'key-rows', 'next crosses sections')

  // links between pages: every one must resolve to a real page and a real heading
  for (const d of docs) {
    for (const m of d.raw.matchAll(/\]\(([^)\s]+)\)/g)) {
      const r = resolveHref(m[1], d.slug)
      assert.ok(!/\.md/.test(r.href), `${d.slug}: ${m[1]} not resolved`)
      const hit = r.href.match(/^#\/docs\/([\w-]+)(?:#(.+))?$/)
      if (!hit) continue
      const target = docs.find((x) => x.slug === hit[1])
      assert.ok(target, `${d.slug}: link to missing page ${m[1]}`)
      if (hit[2]) assert.ok(target.headings.some((h) => h.id === hit[2]), `${d.slug}: link to missing heading ${m[1]}`)
    }
  }
  assert.equal(resolveHref('../02-editing/01-key-rows.md#changing-the-keys').href, '#/docs/key-rows#changing-the-keys')
  assert.equal(resolveHref('./02-find-and-replace.md').href, '#/docs/find-and-replace')
  assert.equal(resolveHref('#/download').href, '#/download')
  assert.equal(resolveHref('#x', 'key-rows').href, '#/docs/key-rows#x')
  assert.equal(resolveHref('https://example.test').external, true)

  // routes
  assert.deepEqual(parseRoute(''), { page: 'home', slug: null, anchor: '' })
  assert.equal(parseRoute('#/download').page, 'home')
  assert.deepEqual(parseRoute('#/docs'), { page: 'docs', slug: null, anchor: '' })
  assert.deepEqual(parseRoute('#/docs/key-rows#changing-the-keys'), { page: 'docs', slug: 'key-rows', anchor: 'changing-the-keys' })
  assert.deepEqual(parseRoute('#/changelog'), { page: 'changelog', slug: null, anchor: '' })
  assert.equal(parseRoute('#/changelog#release-v0.2.0').anchor, 'release-v0.2.0')
  assert.equal(parseRoute('#/nope').page, 'missing')

  // search
  assert.equal(search('find')[0].doc.slug, 'find-and-replace')
  assert.equal(search('changing keys')[0].href, '#/docs/key-rows#changing-the-keys', 'a heading hit links to the heading')
  assert.ok(search('stable nightly').some((h) => h.doc.slug === 'release-channels'))
  assert.ok(search('//').some((h) => h.doc.slug === 'key-rows'), 'symbols can be searched')
  assert.deepEqual(search('xyzzyplugh'), [])
  assert.deepEqual(search('a'), [], 'one letter is too vague')
  assert.deepEqual(search('   '), [])
  assert.ok(search('update').length <= 10)

  // pages render, with heading ids that match what search and "On this page" link to
  for (const d of docs) {
    const html = renderToStaticMarkup(h(Docs, { slug: d.slug }))
    assert.ok(html.includes(`<h1>${d.title.replace(/&/g, '&amp;')}</h1>`), `${d.slug} renders its title`)
    assert.ok(!/href="\.{0,2}\/?[\w/-]*\d+-[\w-]+\.md/.test(html), `${d.slug}: no raw .md links left`)
    for (const hd of d.headings) assert.ok(html.includes(`id="${hd.id}"`), `${d.slug}: heading id ${hd.id}`)
  }
  assert.match(renderToStaticMarkup(h(Docs, { slug: 'nope' })), /Page not found/)
  assert.match(renderToStaticMarkup(h(Docs, { slug: 'getting-started' })), /href="#\/docs\/using-the-editor"/)
  const index = renderToStaticMarkup(h(Docs, {}))
  for (const sec of sections) assert.ok(index.includes(sec.title), `index lists ${sec.title}`)
  for (const d of docs) assert.ok(index.includes(`href="#/docs/${d.slug}"`), `index links ${d.slug}`)
  assert.match(renderToStaticMarkup(h(SearchBox, {})), /Search the documentation/)
  assert.match(renderToStaticMarkup(h(Home, {})), /Learn the editor/)

  console.log('smoke: ok')
} finally {
  await server.close()
}
