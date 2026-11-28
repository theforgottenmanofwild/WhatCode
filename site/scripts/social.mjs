// The title and description of every page of the site, and the <head> that makes a link to it show an embed.
//
// The site is one page that draws everything in the browser. Discord, Slack, search engines and other link previews
// do not run it: they read the HTML they are given and nothing else. So the build writes a real index.html for every
// address (/docs/key-rows/index.html), the same page as the home page with its own title, description and Open Graph
// tags. The browser still starts the site as usual and shows whatever the address says.
//
// This file reads the same sources as the pages (docs/, the forum snapshot, the source snapshot) but needs no Vite,
// so the build and the smoke test can both use it.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

export const SITE_NAME = 'WhatCode'
export const HOME_DESCRIPTION = 'Download WhatCode, a small dev tool app for Android with a file explorer and a code editor, and read how to use it.'
/** Discord shows about 350 characters; a shorter text reads better and never gets cut in the middle of a word. */
export const MAX_DESCRIPTION = 200
/** The picture next to the embed. It is in public/, so it is served from the root of the site. */
export const CARD_IMAGE = 'social-card.png'
export const CARD_SIZE = 512

const byName = (a, b) => a.localeCompare(b, 'en', { numeric: true })
const stripNumber = (name) => name.replace(/^\d+-/, '')
const sentenceCase = (text) => text.charAt(0).toUpperCase() + text.slice(1)

// ---------------------------------------------------------------- text

/** Markdown inline syntax -> the text a reader sees (the same as site/src/docs.js). */
export function plain(md) {
  return md
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, '')
    .replace(/`+/g, '')
    .replace(/\*+|~~/g, '')
    .replace(/(^|[^\w])_([^_\n]+)_(?=[^\w]|$)/g, '$1$2')
    .replace(/\s+/g, ' ')
    .trim()
}

/** One line of text, cut at a word and ended with … when it is longer than max. */
export function shorten(text, max = MAX_DESCRIPTION) {
  const t = String(text ?? '').replace(/\s+/g, ' ').trim()
  if (t.length <= max) return t
  const cut = t.slice(0, max)
  return `${cut.slice(0, Math.max(cut.lastIndexOf(' '), Math.floor(max * 0.6))).trimEnd()}…`
}

/** A few plain words from the start of some Markdown (the same as excerpt() in site/src/forum.js). */
export function excerpt(markdown, max = MAX_DESCRIPTION) {
  const text = String(markdown ?? '')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/(```|~~~)[\s\S]*?(\1|$)/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, ' ')
    .replace(/^\s{0,3}(#{1,6}\s+|>\s?|[-*+]\s+(\[[ xX]\]\s+)?|\d+\.\s+)/gm, '')
    .replace(/[*`~]+|(?<!\w)_+|_+(?!\w)/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return shorten(text, max)
}

const escapeHtml = (s) =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// ---------------------------------------------------------------- the pages

/** The title and first paragraph of a documentation page (the same rules as parsePage in site/src/docs.js). */
export function parseDoc(raw) {
  const lines = raw.replace(/\r\n/g, '\n').split('\n')
  let title = null
  let summary = ''
  let intro = true
  let inFence = false
  let para = []
  const endPara = () => {
    if (intro && !summary && para.length) summary = plain(para.join(' '))
    para = []
  }
  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      if (intro && !summary) endPara()
      continue
    }
    if (inFence) continue
    const m = line.match(/^(#{1,3})\s+(.+?)\s*#*\s*$/)
    if (m) {
      if (m[1].length === 1 && title === null) {
        title = plain(m[2])
        continue
      }
      if (m[1].length >= 2) {
        endPara()
        intro = false
        continue
      }
    }
    if (intro && !summary) {
      const t = line.trim()
      if (t === '') endPara()
      else if (/^(\||[-*+]\s|\d+\.\s|>)/.test(t) && !para.length) intro = false
      else para.push(t)
    }
  }
  endPara()
  return { title, summary }
}

const readJson = (file) => {
  try {
    return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null
  } catch {
    return null
  }
}

/** A path from the data, as folder names, or null if it could point outside the site. */
function segments(path) {
  const parts = String(path).split('/')
  return parts.every((p) => p && p !== '.' && p !== '..' && !/[\\\0]/.test(p)) ? parts : null
}

/** An address as it is written in a link: each name encoded, with a leading slash. */
export const encodePath = (parts) => `/${parts.map(encodeURIComponent).join('/')}`

/**
 * Every page that has its own address: { path: '/docs/key-rows', title, description }.
 * What is missing (no forum snapshot, no source snapshot) is left out; the page then falls back to the home page's
 * tags, like any address that is not listed.
 */
export function collectPages({ docsDir, discussionsFile, sourceFile }) {
  const pages = [{ path: '/', title: SITE_NAME, description: HOME_DESCRIPTION }]
  const add = (parts, title, description) => {
    if (!parts) return
    pages.push({ parts, path: encodePath(parts), title: shorten(title, 120), description: shorten(description) })
  }

  // ---- docs
  const docs = []
  if (existsSync(docsDir)) {
    const folders = readdirSync(docsDir, { withFileTypes: true }).filter((e) => e.isDirectory()).map((e) => e.name)
    for (const folder of folders.sort(byName)) {
      const files = readdirSync(join(docsDir, folder)).filter((f) => f.endsWith('.md')).sort(byName)
      for (const file of files) {
        const slug = stripNumber(file).replace(/\.md$/, '')
        const { title, summary } = parseDoc(readFileSync(join(docsDir, folder, file), 'utf8'))
        docs.push({ slug, folder, title: title ?? slug, summary })
      }
    }
  }
  const topics = docs.map((d) => d.title).join(', ')
  add(['docs'], 'Documentation', topics ? `How to use ${SITE_NAME}: ${topics}.` : `How to use ${SITE_NAME}.`)
  for (const d of docs) {
    const section = sentenceCase(stripNumber(d.folder).replace(/-/g, ' '))
    add(['docs', d.slug], d.title, d.summary || `${section} in the ${SITE_NAME} documentation.`)
  }

  add(['changelog'], 'Changelog', `What changed in every stable release of ${SITE_NAME}.`)
  add(['sitemap'], 'Site map', `Every page of ${SITE_NAME}: the documentation, forums, changelog and source.`)

  // ---- forums
  add(['forums'], 'Forums', `Questions, ideas and announcements from the ${SITE_NAME} community.`)
  const forum = readJson(discussionsFile)
  if (forum?.available) {
    const names = new Map()
    for (const c of forum.categories ?? []) {
      names.set(c.slug, c.name)
      add(['forums', 'c', c.slug], c.name, c.description || `Discussions in ${c.name}.`)
    }
    for (const d of forum.discussions ?? []) {
      if (!Number.isInteger(d.number) || d.number < 1) continue
      const where = names.get(d.category)
      add(['forums', String(d.number)], d.title, excerpt(d.body) || (where ? `A discussion in ${where}.` : 'A discussion.'))
    }
  }

  // ---- source
  add(['source'], 'Source', `Browse the code, history and tags of ${SITE_NAME}.`)
  add(['source', 'commits'], 'Commits', `Every commit of ${SITE_NAME}, newest first.`)
  add(['source', 'tags'], 'Tags', `The tags of ${SITE_NAME}.`)
  const source = readJson(sourceFile)
  if (source?.available) {
    const folders = new Set()
    for (const f of source.files ?? []) {
      const parts = segments(f.path)
      if (!parts) continue
      for (let i = 1; i < parts.length; i++) folders.add(parts.slice(0, i).join('/'))
      const bits = [f.lang, f.lines ? `${f.lines.toLocaleString('en')} lines` : null].filter(Boolean)
      add(['source', 'blob', ...parts], f.path, `${bits.join(', ') || 'A file'} in ${SITE_NAME}.`)
    }
    for (const dir of [...folders].sort(byName)) {
      add(['source', 'tree', ...dir.split('/')], dir, `The files in ${dir}, in ${SITE_NAME}.`)
    }
    for (const c of source.commits ?? []) {
      if (!/^[0-9a-f]{40}$/i.test(c.sha ?? '')) continue
      const when = String(c.date ?? '').slice(0, 10)
      add(['source', 'commit', c.sha.toLowerCase()], c.subject || 'A commit', `Commit ${c.sha.slice(0, 7)}${c.author?.name ? ` by ${c.author.name}` : ''}${when ? `, ${when}` : ''}.`)
    }
  }

  return pages
}

// ---------------------------------------------------------------- the sitemap

/**
 * The first part of the addresses that sitemap.xml leaves out. The Source pages are a copy of the repository
 * (hundreds of files and commits), so they would bury the pages people actually search for.
 */
export const SITEMAP_SKIP = ['source']

/**
 * sitemap.xml for Search Console's Sitemaps report: the full address of every page, written exactly as its canonical
 * tag names it (renderPage). A sitemap holds full addresses only, so without siteUrl this returns '' and none is written.
 */
export function renderSitemap(pages, { siteUrl = '', skip = SITEMAP_SKIP } = {}) {
  const root = String(siteUrl).replace(/\/+$/, '')
  if (!root) return ''
  const lines = pages
    .filter((p) => !skip.includes(p.parts?.[0]))
    .map((p) => `  <url><loc>${escapeHtml(p.path === '/' ? `${root}/` : `${root}${p.path}/`)}</loc></url>`)
  return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${lines.join('\n')}\n</urlset>\n`
}

/**
 * robots.txt: lets every crawler in and points at sitemap.xml. The Sitemap line needs a full address, so without
 * siteUrl it is left out. (Crawlers only read robots.txt at the root of a host, so on a GitHub Pages project site
 * https://owner.github.io/name/ it is ignored; submit sitemap.xml in Search Console instead. On a user site or a
 * custom domain it works as written.)
 */
export function renderRobots({ siteUrl = '' } = {}) {
  const root = String(siteUrl).replace(/\/+$/, '')
  return `User-agent: *\nAllow: /\n${root ? `\nSitemap: ${root}/sitemap.xml\n` : ''}`
}

// ---------------------------------------------------------------- the <head>

/**
 * The home page's HTML with this page's title, description and Open Graph tags in it.
 * siteUrl is where the site's root is served from (https://owner.github.io/name); without it the tags that need a full
 * address (og:url, og:image, canonical) are left out, since a relative one is no use to a link preview.
 */
export function renderPage(html, page, { siteUrl = '', address = true } = {}) {
  const root = String(siteUrl).replace(/\/+$/, '')
  const home = page.path === '/'
  const title = home ? SITE_NAME : `${page.title} · ${SITE_NAME}`
  const description = page.description || HOME_DESCRIPTION
  // address: false is for 404.html, which stands in for many addresses and so has no address of its own to name.
  const url = root && address ? (home ? `${root}/` : `${root}${page.path}/`) : ''

  const meta = (attr, name, content) => `<meta ${attr}="${name}" content="${escapeHtml(content)}" />`
  const tags = [
    url && `<link rel="canonical" href="${escapeHtml(url)}" />`,
    meta('property', 'og:site_name', SITE_NAME),
    meta('property', 'og:type', 'website'),
    meta('property', 'og:title', home ? SITE_NAME : page.title),
    meta('property', 'og:description', description),
    url && meta('property', 'og:url', url),
    root && meta('property', 'og:image', `${root}/${CARD_IMAGE}`),
    root && meta('property', 'og:image:width', String(CARD_SIZE)),
    root && meta('property', 'og:image:height', String(CARD_SIZE)),
    root && meta('property', 'og:image:alt', `${SITE_NAME} logo`),
    meta('name', 'twitter:card', 'summary'),
  ].filter(Boolean)

  return html
    .replace(/<title>[\s\S]*?<\/title>/, () => `<title>${escapeHtml(title)}</title>`)
    .replace(/<meta name="description"[^>]*>/, () => meta('name', 'description', description))
    .replace(/[ \t]*<\/head>/, () => `${tags.map((t) => `    ${t}`).join('\n')}\n  </head>`)
}
