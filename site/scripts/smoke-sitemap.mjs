// The site map part of the smoke test. Called from smoke.mjs with the same Vite server.
//   - the list is built from the docs, the forum snapshot and the source snapshot, and every link in it is an address
//     the site opens
//   - it is complete for the docs, and a group whose snapshot is missing still links to its own page
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const here = (p) => fileURLToPath(new URL(p, import.meta.url))
const readJson = async (file) => {
  try {
    return JSON.parse(await readFile(file, 'utf8'))
  } catch {
    return null
  }
}

export async function siteMapSmoke(server) {
  const { parseRoute } = await server.ssrLoadModule('/src/route.js')
  const { buildSiteMap, countPages } = await server.ssrLoadModule('/src/sitemap.js')
  const { SiteMapView } = await server.ssrLoadModule('/src/components/SiteMap.jsx')
  const { SECTIONS } = await server.ssrLoadModule('/src/components/SiteMenu.jsx')
  const { sections, docs } = await server.ssrLoadModule('/src/docs.js')
  const { indexForum } = await server.ssrLoadModule('/src/forum.js')
  const { indexSource } = await server.ssrLoadModule('/src/source.js')

  // the page has an address
  assert.deepEqual(parseRoute('/sitemap'), { page: 'sitemap', slug: null, anchor: '' })
  assert.equal(parseRoute('/sitemap/').page, 'sitemap')
  assert.equal(parseRoute('/sitemap/extra').page, 'missing')

  const links = (map) => map.flatMap((g) => [g.href, ...g.sections.flatMap((s) => [s.href, ...s.links.map((l) => l.href)])]).filter(Boolean)
  const inSite = (href) => href.startsWith('/')

  // nothing loaded yet: the sections and the docs are there, the two snapshots say they are loading
  const early = buildSiteMap({ nav: SECTIONS, docs: sections })
  assert.deepEqual(early.map((g) => g.id), SECTIONS.map((s) => s.page), 'one group for each section of the site')
  assert.equal(early.find((g) => g.id === 'forums').status, 'loading')
  assert.equal(early.find((g) => g.id === 'source').status, 'loading')
  for (const d of docs) assert.ok(links(early).includes(`/docs/${d.slug}`), `the site map links ${d.slug}`)

  // loaded
  const forumData = await readJson(here('../public/discussions.json'))
  const sourceData = await readJson(here('../public/source-data/index.json'))
  const forum = forumData?.available ? { status: 'ready', forum: indexForum(forumData) } : { status: 'unavailable' }
  const source = sourceData?.available ? { status: 'ready', source: indexSource(sourceData) } : { status: 'unavailable' }
  const full = buildSiteMap({ nav: SECTIONS, docs: sections, forum, source, external: [{ title: 'GitHub', href: 'https://github.com/o/r', external: true }] })
  assert.equal(full.at(-1).id, 'elsewhere')

  for (const href of links(full)) {
    if (!inSite(href)) continue
    assert.notEqual(parseRoute(href.split('#')[0]).page, 'missing', `${href} is an address the site opens`)
  }
  assert.equal(new Set(links(full)).size, links(full).length, 'no address is listed twice')
  if (forum.status === 'ready') {
    for (const d of forumData.discussions) assert.ok(links(full).includes(`/forums/${d.number}`), `discussion ${d.number} is listed`)
    for (const c of forumData.categories) assert.ok(links(full).includes(`/forums/c/${c.slug}`), `category ${c.slug} is listed`)
  }
  if (source.status === 'ready') {
    assert.ok(links(full).includes('/source/commits'))
    const top = new Set(sourceData.files.filter((f) => f.path.includes('/')).map((f) => f.path.split('/')[0]))
    for (const dir of top) assert.ok(links(full).includes(`/source/tree/${encodeURIComponent(dir)}`), `folder ${dir} is listed`)
    assert.ok(!links(full).some((x) => x.startsWith('/source/blob/')), 'files are not listed one by one')
  }

  // drawn
  const html = renderToStaticMarkup(h(SiteMapView, { map: full }))
  assert.match(html, /<h1 id="sitemap-title">Site map<\/h1>/)
  assert.ok(html.includes(`${countPages(full).toLocaleString('en')} pages`), 'says how many pages it lists')
  for (const d of docs) assert.ok(html.includes(`href="/docs/${d.slug}"`), `${d.slug} is drawn`)
  assert.match(html, /href="https:\/\/github\.com\/o\/r"[^>]*rel="noopener noreferrer"/, 'links that leave the site are marked')

  const waiting = renderToStaticMarkup(h(SiteMapView, { map: early }))
  assert.match(waiting, /Loading the list/)
  const failed = renderToStaticMarkup(h(SiteMapView, { map: buildSiteMap({ nav: SECTIONS, docs: sections, forum: { status: 'error' } }), retry: { forums() {} } }))
  assert.match(failed, /could not be loaded/)
  assert.match(failed, /href="\/forums"/, 'a group that failed still links to its own page')
}
