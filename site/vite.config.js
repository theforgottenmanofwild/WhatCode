import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { collectPages, renderPage } from './scripts/social.mjs'

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
    },
  }
}

export default defineConfig({
  base,
  plugins: [react(), pagesFallback()],
  // The docs live in ../docs (shared with the repo), outside this folder.
  server: { fs: { allow: ['..'] } },
})
