import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { collectPages, renderPage, renderRobots, renderSitemap } from './scripts/social.mjs'

// The pages have real addresses (/docs/key-rows), so the site has to know what comes before them.
//   VITE_BASE=/name/   a GitHub Pages project site (https://owner.github.io/name/); the website workflow sets it
//   (nothing)          "/": a site on its own domain or a user site, and `npm run dev`
const base = (process.env.VITE_BASE || '/').replace(/\/*$/, '/').replace(/^\/*/, '/')

// Where the root of the site is served from, as a full address (https://owner.github.io/name). Link previews need
// full addresses for og:url and og:image. The website workflow sets it; without it those two tags are left out.
const siteUrl = (process.env.VITE_SITE_URL || '').replace(/\/+$/, '')

// GitHub Pages has no server rules: an address that is not a file (/docs/key-rows) gets 404.html, with the status
// 404. The site then opens the right page for any address, but a link preview (Discord, Slack, search engines) reads
// the HTML it is given and skips a page that came with a 404 or has nothing to say about itself.
// So every page with its own address gets a real file, dist/docs/key-rows/index.html: the home page with that page's
// title, description and Open Graph tags (see scripts/social.mjs). Served with status 200, it still starts the site.
// 404.html stays as the fallback for every address that has no file of its own.
function pagesFallback() {
  let root = '.'
  let outDir = 'dist'
  return {
    name: 'pages-fallback',
    apply: 'build',
    configResolved: (config) => {
      root = config.root
      outDir = join(config.root, config.build.outDir)
    },
    closeBundle: async () => {
      const template = await readFile(join(outDir, 'index.html'), 'utf8')
      const pages = collectPages({
        docsDir: join(root, '..', 'docs'),
        discussionsFile: join(root, 'public', 'discussions.json'),
        sourceFile: join(root, 'public', 'source-data', 'index.json'),
      })
      const render = (page) => renderPage(template, page, { siteUrl })

      const home = render(pages[0])
      await writeFile(join(outDir, 'index.html'), home)
      await writeFile(join(outDir, '404.html'), renderPage(template, pages[0], { siteUrl, address: false }))

      let written = 0
      for (const page of pages.slice(1)) {
        const file = join(outDir, ...page.parts, 'index.html')
        if (existsSync(file)) continue // never replace a file the build made
        await mkdir(dirname(file), { recursive: true })
        await writeFile(file, render(page))
        written++
      }
      console.log(`pages-fallback: wrote 404.html and ${written} pages with their own link preview`)

      // Search engines: sitemap.xml lists every page above (not Source, see SITEMAP_SKIP), robots.txt points at it.
      // Both need the full address of the site, so without VITE_SITE_URL neither is written.
      if (siteUrl) {
        await writeFile(join(outDir, 'sitemap.xml'), renderSitemap(pages, { siteUrl }))
        await writeFile(join(outDir, 'robots.txt'), renderRobots({ siteUrl }))
        console.log('pages-fallback: wrote sitemap.xml and robots.txt')
      }
    },
  }
}

// `npm run dev` and `npm run preview` have no build step, so sitemap.xml and robots.txt are made when they are asked
// for, from the docs, forum snapshot and source snapshot as they are on disk right now. The address in them is
// VITE_SITE_URL if it is set, otherwise the one the request came to (http://localhost:5173/), so the output can be
// opened and checked without deploying.
function sitemapServer() {
  let root = '.'
  const handler = (req, res, next) => {
    const path = (req.url || '').split('?')[0]
    const file = path.slice(base.length - 1) // '/sitemap.xml' whatever the base is
    if (!path.startsWith(base) || (file !== '/sitemap.xml' && file !== '/robots.txt')) return next()
    const origin = siteUrl || `http://${req.headers.host}${base}`.replace(/\/+$/, '')
    if (file === '/robots.txt') {
      res.setHeader('Content-Type', 'text/plain; charset=utf-8')
      return res.end(renderRobots({ siteUrl: origin }))
    }
    const pages = collectPages({
      docsDir: join(root, '..', 'docs'),
      discussionsFile: join(root, 'public', 'discussions.json'),
      sourceFile: join(root, 'public', 'source-data', 'index.json'),
    })
    res.setHeader('Content-Type', 'application/xml; charset=utf-8')
    res.end(renderSitemap(pages, { siteUrl: origin }))
  }
  return {
    name: 'sitemap-server',
    configResolved: (config) => {
      root = config.root
    },
    configureServer: (server) => void server.middlewares.use(handler),
    configurePreviewServer: (server) => void server.middlewares.use(handler),
  }
}

export default defineConfig({
  base,
  plugins: [react(), pagesFallback(), sitemapServer()],
  // The docs live in ../docs (shared with the repo), outside this folder.
  server: { fs: { allow: ['..'] } },
})
