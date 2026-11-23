import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// base './' + hash routing lets the built site work from any path (GitHub Pages project sites, previews, local files served statically).
export default defineConfig({
  base: './',
  plugins: [react()],
  // The docs live in ../docs (shared with the repo), outside this folder.
  server: { fs: { allow: ['..'] } },
})
