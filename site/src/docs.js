// Documentation lives in ../docs (repo root) so it reads well on GitHub too.
// Files are ordered by their numeric prefix: 01-getting-started.md, 02-...
const files = import.meta.glob('../../docs/*.md', { query: '?raw', import: 'default', eager: true })

export const docs = Object.entries(files)
  .map(([path, raw]) => {
    const file = path.split('/').pop()
    const slug = file.replace(/^\d+-/, '').replace(/\.md$/, '')
    const title = raw.match(/^#\s+(.+)$/m)?.[1] ?? slug
    return { file, slug, title, raw }
  })
  .sort((a, b) => a.file.localeCompare(b.file))

export const docBySlug = (slug) => docs.find((d) => d.slug === slug)
