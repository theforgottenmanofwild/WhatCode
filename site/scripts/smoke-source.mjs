// The Source part of the smoke test: route parsing, the pure helpers, and every source page rendered with the real
// snapshot (public/source-data/, written by `npm run source`). Called from smoke.mjs with the same Vite server.
// Without a snapshot (or with an "unavailable" one) only the parts that need none run.
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

const SNAPSHOT = fileURLToPath(new URL('../public/source-data/index.json', import.meta.url))

async function readSnapshot() {
  try {
    return JSON.parse(await readFile(SNAPSHOT, 'utf8'))
  } catch {
    return null
  }
}

export async function sourceSmoke(server) {
  const { parseRoute } = await server.ssrLoadModule('/src/route.js')
  const S = await server.ssrLoadModule('/src/source.js')
  const { SourceView } = await server.ssrLoadModule('/src/components/Source.jsx')

  // ---- routes
  const r = (hash) => parseRoute(hash)
  assert.equal(r('/source').view, 'home')
  assert.equal(r('/source/tree').view, 'home')
  assert.deepEqual([r('/source/tree/app/src').view, r('/source/tree/app/src').path], ['tree', 'app/src'])
  assert.deepEqual([r('/source/blob/README.md').view, r('/source/blob/README.md').path], ['blob', 'README.md'])
  assert.equal(r('/source/blob/a%20b/c.txt#L3-L5').path, 'a b/c.txt')
  assert.equal(r('/source/blob/a%20b/c.txt#L3-L5').anchor, 'L3-L5')
  assert.equal(r('/source/blob').page, 'missing', 'a file view needs a path')
  assert.equal(r('/source/commits').view, 'commits')
  assert.equal(r('/source/history').view, 'commits')
  assert.deepEqual([r('/source/history/docs').view, r('/source/history/docs').path], ['history', 'docs'])
  assert.equal(r(`/source/commit/${'AB12cd3'}`).sha, 'ab12cd3')
  assert.equal(r('/source/commit/zzzz').page, 'missing')
  assert.equal(r('/source/tags').view, 'tags')
  assert.deepEqual([r('/source/search').mode, r('/source/search').query], ['files', ''])
  assert.deepEqual([r('/source/search/code/foo').mode, r('/source/search/code/foo').query], ['code', 'foo'])
  assert.equal(r('/source/search/nope/x').page, 'missing')
  assert.equal(r('/source/nope').page, 'missing')

  // ---- links round-trip through the router
  const back = (href) => r(href)
  assert.equal(back(S.blobHref('docs/a b.md', 'L9')).path, 'docs/a b.md')
  assert.equal(back(S.treeHref('app/src')).path, 'app/src')
  assert.equal(back(S.historyHref('app')).view, 'history')
  assert.equal(back(S.searchHref('code', 'a/b c')).query, 'a/b c')

  // ---- small helpers
  assert.deepEqual(S.parseLineRange('L10'), { from: 10, to: 10 })
  assert.deepEqual(S.parseLineRange('L20-L10'), { from: 10, to: 20 })
  assert.equal(S.parseLineRange('nope'), null)
  assert.equal(S.lineAnchor(3, 3), 'L3')
  assert.equal(S.lineAnchor(3, 8), 'L3-L8')
  assert.equal(S.formatBytes(0), '0 B')
  assert.match(S.formatBytes(1536), /1\.5 KB/)
  assert.deepEqual(S.splitHighlighted('<span class="a">x\ny</span>z'), ['<span class="a">x</span>', '<span class="a">y</span>z'])
  assert.deepEqual(S.breadcrumbs('a/b/c').map((p) => p.path), ['a', 'a/b', 'a/b/c'])

  // ---- the states that need no snapshot
  const view = (state, route) => renderToStaticMarkup(h(SourceView, { state, route: r(route), onRetry() {} }))
  assert.match(view({ status: 'loading' }, '/source'), /Loading/)
  assert.match(view({ status: 'error', reason: 'missing' }, '/source'), /npm run source/)
  assert.match(view({ status: 'error', reason: 'http-500' }, '/source'), /could not be loaded/)
  assert.match(view({ status: 'unavailable', reason: 'no-git' }, '/source'), /without the project/)

  // ---- every page, with the real snapshot
  const data = await readSnapshot()
  if (!data?.available) {
    console.warn('smoke: no source snapshot (run npm run source); the page checks were skipped.')
    return
  }
  const source = S.indexSource(data)
  const ready = { status: 'ready', source }
  const page = (route) => view(ready, route)

  assert.ok(source.files.length > 0 && source.commits.length > 0, 'the snapshot has files and commits')

  const home = page('/source')
  assert.match(home, /src-tabs/)
  assert.match(home, /<h1>Source<\/h1>/)
  for (const row of S.listDir(source, '')) {
    const href = row.type === 'dir' ? S.treeHref(row.path) : S.blobHref(row.path)
    assert.ok(home.includes(`href="${href.replace(/&/g, '&amp;')}"`), `home lists ${row.path}`)
  }

  // a folder
  const dir = [...source.dirs.keys()].find((p) => p && !p.includes('/'))
  if (dir) {
    const html = page(`/source/tree/${dir}`)
    assert.ok(html.includes(`>${dir.split('/').pop()}</h1>`) || html.includes(dir), `folder page names ${dir}`)
    assert.match(html, /Up one folder/)
  }
  assert.match(page('/source/tree/no/such/folder'), /Folder not found/)
  assert.match(page(`/source/tree/${source.files[0].path}`), /That is a file/)

  // a file: the page knows its name and shows its lines once they load (no text is rendered before the fetch)
  const text = source.files.find((f) => f.kind === 'text')
  if (text) {
    const html = page(`/source/blob/${text.path}`)
    assert.ok(html.includes(`<h1>${text.name.replace(/&/g, '&amp;')}</h1>`), 'file page shows the file name')
    assert.match(html, /History/)
  }
  assert.match(page('/source/blob/no/such/file.txt'), /File not found/)
  assert.match(page(`/source/blob/${[...source.dirs.keys()].find(Boolean) ?? 'x'}`), /(That is a folder|File not found)/)

  // commits
  const commits = page('/source/commits')
  assert.match(commits, /<h1>Commits<\/h1>/)
  const newest = source.commits[0]
  assert.ok(commits.includes(S.commitHref(newest.sha)), 'the commit list links the newest commit')
  const one = page(`/source/commit/${newest.sha}`)
  assert.ok(one.includes(newest.sha), 'the commit page shows the full id')
  assert.match(page(`/source/commit/${newest.short}`), new RegExp(newest.short), 'a short id finds the commit')
  assert.match(page('/source/commit/deadbeef'), /Commit not found/)
  if (text) assert.match(page(`/source/history/${text.path}`), /<h1>History<\/h1>/)

  // tags and search
  const tags = page('/source/tags')
  assert.match(tags, /<h1>Tags<\/h1>/)
  for (const t of source.tags) assert.ok(tags.includes(t.name), `tags page lists ${t.name}`)
  assert.match(page('/source/search'), /Find a file by name/)
  assert.match(page('/source/search/files/readme'), /found/)

  // finding files by name
  const first = source.files[0]
  assert.equal(S.findFiles(source, first.name, 5)[0].file.path.includes(first.name.slice(0, 2)), true)
  assert.deepEqual(S.findFiles(source, 'zzzzqqqqxxxx', 5), [])

  // links in a README that point at other files of the repository stay inside the site
  const readme = S.readmeOf(source, '')
  if (readme) {
    const link = S.resolveRepoLink(source, readme.path, '')
    assert.equal(link.kind, 'file', 'a link to a file of the repository stays in the site')
    assert.equal(link.href, S.blobHref(readme.path))
  }
  assert.equal(S.resolveRepoLink(source, 'https://example.test/x', '').kind, 'external')
  assert.equal(S.resolveRepoLink(source, '#top', '').kind, 'anchor')
}
