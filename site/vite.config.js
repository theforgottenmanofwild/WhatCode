import { copyFile } from 'node:fs/promises'
import { join } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The pages have real addresses (/docs/key-rows), so the site has to know what comes before them.
//   VITE_BASE=/name/   a GitHub Pages project site (https://owner.github.io/name/); the website workflow sets it
//   (nothing)          "/": a site on its own domain or a user site, and `npm run dev`
const base = (process.env.VITE_BASE || '/').replace(/\/*$/, '/').replace(/^\/*/, '/')

// GitHub Pages has no server rules: an address that is not a file (/docs/key-rows) gets 404.html. Making that file
// a copy of the home page lets the site open the right page for any address, whether it was typed, bookmarked or reloaded.
function pagesFallback() {
  let outDir = 'dist'
  return {
    name: 'pages-fallback',
    apply: 'build',
    configResolved: (config) => {
      outDir = join(config.root, config.build.outDir)
    },
    closeBundle: () => copyFile(join(outDir, 'index.html'), join(outDir, '404.html')),
  }
}

export default defineConfig({
  base,
  plugins: [react(), pagesFallback()],
  // The docs live in ../docs (shared with the repo), outside this folder.
  server: { fs: { allow: ['..'] } },
})
