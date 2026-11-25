// The link preview part of the smoke test: what a link to each page shows in Discord and other chats. Called from
// smoke.mjs with the same Vite server.
//   - renderPage puts the title, description and Open Graph tags into the home page's HTML, escaped, once
//   - every page collectPages lists is an address the site really opens, with the same title as the page itself
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'
import { CARD_IMAGE, collectPages, HOME_DESCRIPTION, MAX_DESCRIPTION, renderPage, shorten } from './social.mjs'

const here = (p) => fileURLToPath(new URL(p, import.meta.url))

const TEMPLATE = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="description" content="${HOME_DESCRIPTION}" />
    <meta name="theme-color" content="#181818" />
    <title>WhatCode</title>
  </head>
  <body><div id="root"></div></body>
</html>`

const count = (text, re) => (text.match(re) ?? []).length

export async function socialSmoke(server) {
  // ---- the <head>
  const page = { path: '/docs/key-rows', parts: ['docs', 'key-rows'], title: 'The "key" <rows> $&', description: 'Tap a key & go. "Quoted" <b>text</b>' }
  const withUrl = renderPage(TEMPLATE, page, { siteUrl: 'https://owner.github.io/name/' })
  assert.equal(count(withUrl, /<title>/g), 1, 'one title')
  assert.equal(count(withUrl, /<meta name="description"/g), 1, 'one description')
  assert.match(withUrl, /<title>The &quot;key&quot; &lt;rows&gt; \$&amp; · WhatCode<\/title>/)
  assert.match(withUrl, /<meta property="og:title" content="The &quot;key&quot; &lt;rows&gt; \$&amp;" \/>/)
  assert.match(withUrl, /content="Tap a key &amp; go\. &quot;Quoted&quot; &lt;b&gt;text&lt;\/b&gt;"/)
  assert.match(withUrl, /<meta property="og:url" content="https:\/\/owner\.github\.io\/name\/docs\/key-rows\/" \/>/)
  assert.match(withUrl, /<link rel="canonical" href="https:\/\/owner\.github\.io\/name\/docs\/key-rows\/" \/>/)
  assert.match(withUrl, new RegExp(`<meta property="og:image" content="https://owner\\.github\\.io/name/${CARD_IMAGE}" />`))
  assert.match(withUrl, /<meta property="og:site_name" content="WhatCode" \/>/)
  assert.match(withUrl, /<meta name="theme-color" content="#181818" \/>/, 'the embed colour is kept')
  assert.ok(withUrl.indexOf('og:title') < withUrl.indexOf('</head>'), 'tags are inside the head')

  const noUrl = renderPage(TEMPLATE, page)
  for (const gone of ['og:url', 'og:image', 'canonical']) assert.ok(!noUrl.includes(gone), `no ${gone} without a site address`)
  assert.match(noUrl, /og:title/)

  const home = renderPage(TEMPLATE, { path: '/', title: 'WhatCode', description: HOME_DESCRIPTION }, { siteUrl: 'https://o.test' })
  assert.match(home, /<title>WhatCode<\/title>/, 'the home page is not "WhatCode · WhatCode"')
  assert.match(home, /og:url" content="https:\/\/o\.test\/"/)

  const fallback = renderPage(TEMPLATE, { path: '/', title: 'WhatCode', description: HOME_DESCRIPTION }, { siteUrl: 'https://o.test', address: false })
  assert.ok(!fallback.includes('canonical') && !fallback.includes('og:url'), '404.html names no address of its own')
  assert.match(fallback, /og:image" content="https:\/\/o\.test\//, '404.html keeps the picture')

  assert.equal(shorten('a b c', 10), 'a b c')
  assert.ok(shorten('word '.repeat(100)).length <= MAX_DESCRIPTION + 1)
  assert.match(shorten('word '.repeat(100)), /word…$/, 'cut at a word')

  // ---- the pages
  const pages = collectPages({
    docsDir: here('../../docs'),
    discussionsFile: here('../public/discussions.json'),
    sourceFile: here('../public/source-data/index.json'),
  })
  const { parseRoute } = await server.ssrLoadModule('/src/route.js')
  const { docs } = await server.ssrLoadModule('/src/docs.js')

  const seen = new Set()
  for (const p of pages) {
    assert.ok(!seen.has(p.path), `${p.path} is listed once`)
    seen.add(p.path)
    assert.ok(p.title, `${p.path} has a title`)
    assert.ok(p.description && p.description.length <= MAX_DESCRIPTION + 1, `${p.path} has a description that fits`)
    assert.notEqual(parseRoute(p.path).page, 'missing', `${p.path} is an address the site opens`)
    if (p.parts) assert.ok(p.parts.every((s) => s && s !== '..' && s !== '.'), `${p.path} stays inside the site`)
  }
  assert.equal(pages[0].path, '/')

  // Every documentation page has a preview, with the title and first paragraph the page itself shows.
  for (const d of docs) {
    const p = pages.find((x) => x.path === `/docs/${d.slug}`)
    assert.ok(p, `/docs/${d.slug} has a preview`)
    assert.equal(p.title, d.title, `/docs/${d.slug} has the page's title`)
    if (d.summary) assert.equal(p.description, shorten(d.summary), `/docs/${d.slug} has the page's first paragraph`)
  }
  for (const path of ['/docs', '/changelog', '/forums', '/source', '/source/commits', '/source/tags']) {
    assert.ok(pages.some((x) => x.path === path), `${path} has a preview`)
  }

  // Every discussion, file, folder and commit that the pages know has one too.
  const forum = await readJson(here('../public/discussions.json'))
  if (forum?.available) {
    for (const d of forum.discussions) assert.ok(seen.has(`/forums/${d.number}`), `/forums/${d.number} has a preview`)
    for (const c of forum.categories) assert.ok(seen.has(`/forums/c/${c.slug}`), `/forums/c/${c.slug} has a preview`)
  }
  const source = await readJson(here('../public/source-data/index.json'))
  if (source?.available) {
    for (const f of source.files) {
      const route = parseRoute(`/source/blob/${f.path.split('/').map(encodeURIComponent).join('/')}`)
      assert.equal(route.path, f.path, `${f.path} opens as itself`)
      assert.ok(pages.some((x) => x.title === f.path && x.path.startsWith('/source/blob/')), `${f.path} has a preview`)
    }
    for (const c of source.commits) assert.ok(seen.has(`/source/commit/${c.sha}`), `commit ${c.sha.slice(0, 7)} has a preview`)
  }
}

async function readJson(file) {
  try {
    return JSON.parse(await (await import('node:fs/promises')).readFile(file, 'utf8'))
  } catch {
    return null
  }
}
