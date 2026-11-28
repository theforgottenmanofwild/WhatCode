// Renders the main components with sample data in Node, so that a broken import,
// a missing prop or a bad API-response shape fails here instead of in a browser.
//   npm run smoke
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { forumSmoke } from './smoke-forum.mjs'
import { sourceSmoke } from './smoke-source.mjs'
import { socialSmoke } from './smoke-social.mjs'
import { siteMapSmoke } from './smoke-sitemap.mjs'
import { readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// What docs/ holds right now, read from the disk with the naming rules of docs/README.md (a folder is a section, a
// file is a page, numbers only set the order). The checks below compare the site's docs code with this, so adding,
// removing or renaming a page or a section never needs a change to the test.
const byName = (a, b) => a.localeCompare(b, 'en', { numeric: true })
const stripNumber = (name) => name.replace(/^\d+-/, '')
const docsDir = fileURLToPath(new URL('../../docs/', import.meta.url))
const onDisk = readdirSync(docsDir, { withFileTypes: true })
  .filter((e) => e.isDirectory() && !e.name.startsWith('.'))
  .map((e) => e.name)
  .sort(byName)
  .map((folder) => {
    const name = stripNumber(folder).replace(/-/g, ' ')
    return {
      folder,
      title: name.charAt(0).toUpperCase() + name.slice(1),
      files: readdirSync(join(docsDir, folder))
        .filter((f) => f.endsWith('.md') && !f.startsWith('.'))
        .sort(byName),
    }
  })
  .filter((s) => s.files.length)
const diskPages = onDisk.flatMap((s) => s.files.map((file) => ({ folder: s.folder, file, slug: stripNumber(file).replace(/\.md$/, '') })))

const sha = 'a'.repeat(40)
const apiSample = [
  { tag_name: 'nightly', name: 'Nightly 0.0.1-nightly.57', prerelease: true, draft: false,
    published_at: '2026-11-24T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/nightly', target_commitish: sha,
    assets: [{ name: 'WhatCode-nightly.apk', browser_download_url: 'https://example.test/n.apk', size: 688128, digest: 'sha256:' + 'b'.repeat(64) },
             { name: 'WhatCode-nightly.apk.sha256', browser_download_url: 'https://example.test/n.sha', size: 90 }] },
  { tag_name: 'v0.2.0', name: 'WhatCode 0.2.0', prerelease: false, draft: false,
    published_at: '2026-11-20T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/v0.2.0', target_commitish: 'main',
    assets: [{ name: 'WhatCode.apk', browser_download_url: 'https://example.test/s.apk', size: 688128 }] },
  { tag_name: 'v0.0.1', name: 'WhatCode 0.0.1', prerelease: false, draft: false,
    published_at: '2026-10-01T10:00:00Z', html_url: 'https://github.com/o/r/releases/tag/v0.0.1',
    assets: [{ name: 'WhatCode.apk', browser_download_url: 'https://example.test/old.apk', size: 680000 }] },
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
  assert.match(err, /releases\/download\/nightly\/WhatCode-nightly\.apk/)
  assert.match(card({ kind: 'stable', status: 'error', reason: 'x' }), /releases\/latest\/download\/WhatCode\.apk/)
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
  const files = renderToStaticMarkup(h(PhoneMock, { animate: false }))
  assert.match(files, /aria-label="Preview of the file explorer/, 'the preview starts on the explorer, as the app does')
  for (const name of ['Phone storage', 'main\.js', 'README\.md']) assert.match(files, new RegExp(name))
  for (const tab of ['File explorer', 'Code editor']) assert.match(files, new RegExp(`>${tab}</button>`))
  const phone = renderToStaticMarkup(h(PhoneMock, { animate: false, initialView: 'editor' }))
  for (const c of ['tok-kw', 'tok-ctl', 'tok-fn', 'tok-type', 'tok-prop', 'tok-re']) assert.match(phone, new RegExp(c))
  assert.match(phone, /JavaScript<\/div>/, 'the status bar names the real language')
  assert.ok(!/C-like/.test(phone), 'no vague "C-like" label')
  // docs structure: a folder is a section, a file is a page (compared with what is in docs/ right now)
  assert.ok(docs.length > 0, 'there are docs pages')
  assert.equal(docs.length, diskPages.length, 'every .md file under docs/*/ is a page')
  assert.deepEqual(sections.map((x) => x.title), onDisk.map((s) => s.title), 'every folder is a section, in order')
  assert.deepEqual(sections.map((x) => x.docs.length), onDisk.map((s) => s.files.length), 'every section has its files')
  assert.deepEqual(docs.map((d) => d.slug), diskPages.map((p) => p.slug), 'pages are in folder and file order')
  assert.equal(new Set(docs.map((d) => d.slug)).size, docs.length, 'page names are unique across sections')
  for (const d of docs) assert.ok(d.title && d.summary, `${d.slug} has a title and a summary`)
  // previous and next follow that order, and cross from one section to the next
  docs.forEach((d, i) => {
    assert.equal(neighbours(d.slug).prev?.slug, docs[i - 1]?.slug, `${d.slug}: previous page`)
    assert.equal(neighbours(d.slug).next?.slug, docs[i + 1]?.slug, `${d.slug}: next page`)
  })

  // links between pages: every one must resolve to a real page and a real heading
  for (const d of docs) {
    for (const m of d.raw.matchAll(/\]\(([^)\s]+)\)/g)) {
      const r = resolveHref(m[1], d.slug)
      assert.ok(!/\.md/.test(r.href), `${d.slug}: ${m[1]} not resolved`)
      const hit = r.href.match(/^\/docs\/([\w-]+)(?:#(.+))?$/)
      if (!hit) continue
      const target = docs.find((x) => x.slug === hit[1])
      assert.ok(target, `${d.slug}: link to missing page ${m[1]}`)
      if (hit[2]) assert.ok(target.headings.some((h) => h.id === hit[2]), `${d.slug}: link to missing heading ${m[1]}`)
    }
  }
  // a link by file path becomes the page address, from the same folder or from another one, with or without a heading
  const first = diskPages[0]
  const withHeading = docs.find((d) => d.headings.length)
  assert.equal(resolveHref(`../${first.folder}/${first.file}`).href, `/docs/${first.slug}`)
  assert.equal(resolveHref(`./${first.file}`).href, `/docs/${first.slug}`)
  if (withHeading) {
    const hd = withHeading.headings[0]
    assert.equal(resolveHref(`../${withHeading.folder}/${withHeading.file}#${hd.id}`).href, `/docs/${withHeading.slug}#${hd.id}`)
    assert.equal(resolveHref(`#${hd.id}`, withHeading.slug).href, `/docs/${withHeading.slug}#${hd.id}`)
  }
  assert.equal(resolveHref('/download').href, '/download')
  assert.equal(resolveHref('#x', first.slug).href, `/docs/${first.slug}#x`)
  assert.equal(resolveHref('https://example.test').external, true)

  // routes
  assert.deepEqual(parseRoute(''), { page: 'home', slug: null, anchor: '' })
  assert.equal(parseRoute('/download').page, 'home')
  assert.deepEqual(parseRoute('/docs'), { page: 'docs', slug: null, anchor: '' })
  assert.deepEqual(parseRoute(`/docs/${first.slug}#some-heading`), { page: 'docs', slug: first.slug, anchor: 'some-heading' })
  assert.deepEqual(parseRoute('/changelog'), { page: 'changelog', slug: null, anchor: '' })
  assert.equal(parseRoute('/changelog#release-v0.2.0').anchor, 'release-v0.2.0')
  assert.equal(parseRoute('/nope').page, 'missing')

  // search: every page can be found by its title, and a heading that only one page has links to that heading
  for (const d of docs) assert.ok(search(d.title).some((x) => x.doc.slug === d.slug), `${d.slug} is found by its title`)
  const headingTexts = docs.flatMap((d) => d.headings.map((hd) => hd.text.toLowerCase()))
  for (const d of docs) {
    const hd = d.headings.find((x) => headingTexts.filter((t) => t === x.text.toLowerCase()).length === 1)
    if (!hd) continue
    assert.ok(search(hd.text).some((x) => x.href === `/docs/${d.slug}#${hd.id}`), `${d.slug}: the heading "${hd.text}" is found and links to itself`)
  }
  const withSlashes = docs.some((d) => d.chunks.some((c) => c.text.includes('//')))
  if (withSlashes) assert.ok(search('//').length > 0, 'symbols can be searched')
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
  const next = neighbours(docs[0].slug).next
  if (next) assert.match(renderToStaticMarkup(h(Docs, { slug: docs[0].slug })), new RegExp(`href="/docs/${next.slug}"`))
  const index = renderToStaticMarkup(h(Docs, {}))
  for (const sec of sections) assert.ok(index.includes(sec.title), `index lists ${sec.title}`)
  for (const d of docs) assert.ok(index.includes(`href="/docs/${d.slug}"`), `index links ${d.slug}`)
  assert.match(renderToStaticMarkup(h(SearchBox, {})), /Search the documentation/)
  assert.match(renderToStaticMarkup(h(Home, {})), /Learn the app/)

  await forumSmoke(server)
  await sourceSmoke(server)
  await socialSmoke(server)
  await siteMapSmoke(server)

  console.log('smoke: ok')
} finally {
  await server.close()
}
