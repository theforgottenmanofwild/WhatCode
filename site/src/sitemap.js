// The site map: every page of the site, listed from what the site itself knows when it is opened.
// Nothing here is written down by hand or at build time. The documentation comes from the pages in docs/, the forums
// from the discussion snapshot and the source pages from the code snapshot, so a new doc page, discussion or folder
// shows up the next time the site map is opened, with no change to this file. No React in here, so the smoke test
// can check it directly.
import { categoryHref, threadHref } from './forum.js'
import { to } from './base.js'
import { commitsHref, searchHref, tagsHref, treeHref } from './source.js'

const byNumberDesc = (a, b) => b.number - a.number

/**
 * What the site map shows.
 *   nav       the sections of the site, as the menu lists them: [{ page, path, title, note }]
 *   docs      the documentation sections (docs.js): [{ title, docs: [{ slug, title }] }]
 *   forum     the forum's load state (useForum): { status, forum? }, or nothing when it is not loaded yet
 *   source    the code's load state (useSource): { status, source? }, or nothing when it is not loaded yet
 *   external  links that leave the site: [{ title, href, note }]
 *
 * Returns a list of groups: { id, title, href, note, status, sections: [{ title, href?, links, collapsed? }] }.
 * `status` is 'ready' when the group is complete, 'loading', 'unavailable' or 'error' when the part of it that
 * needs a snapshot has none (the group's own link is still there), and 'ready' for groups that need no snapshot.
 * Source files and commits are not listed one by one, as in sitemap.xml: there are hundreds, and the folders lead to them.
 */
export function buildSiteMap({ nav = [], docs = [], forum = null, source = null, external = [] } = {}) {
  const groups = nav.map((n) => ({ id: n.page, title: n.title, href: to(n.path), note: n.note, status: 'ready', sections: [] }))
  const group = (id) => groups.find((g) => g.id === id)

  const docsGroup = group('docs')
  if (docsGroup) {
    docsGroup.sections = docs.map((s) => ({
      title: s.title,
      links: s.docs.map((d) => ({ title: d.title, href: to(`/docs/${d.slug}`) })),
    }))
  }

  const forumsGroup = group('forums')
  if (forumsGroup) {
    forumsGroup.status = forum?.status ?? 'loading'
    if (forum?.status === 'ready') {
      const { categories, discussions } = forum.forum
      forumsGroup.sections = categories.map((c) => ({
        title: c.name,
        href: categoryHref(c.slug),
        // The discussions can be many, so they stay folded until someone opens the category.
        collapsed: true,
        links: discussions
          .filter((d) => d.category === c.slug)
          .sort(byNumberDesc)
          .map((d) => ({ title: d.title, href: threadHref(d.number) })),
      }))
    }
  }

  const sourceGroup = group('source')
  if (sourceGroup) {
    sourceGroup.status = source?.status ?? 'loading'
    if (source?.status === 'ready') {
      const src = source.source
      const { files, commits, tags } = src
      sourceGroup.note = `${sourceGroup.note} (${files.length.toLocaleString('en')} files, ${commits.length.toLocaleString('en')} commits)`
      sourceGroup.sections = [
        {
          title: 'Browse',
          links: [
            { title: 'Commits', href: commitsHref() },
            ...(tags.length ? [{ title: 'Tags', href: tagsHref() }] : []),
            { title: 'Find a file', href: searchHref('files') },
            { title: 'Search the code', href: searchHref('code') },
          ],
        },
        {
          title: 'Folders',
          links: (src.dirs.get('')?.dirs ?? []).map((path) => ({ title: `${src.dirs.get(path).name}/`, href: treeHref(path) })),
        },
      ].filter((s) => s.links.length)
    }
  }

  if (external.length) {
    groups.push({ id: 'elsewhere', title: 'Elsewhere', href: null, note: 'Other places that belong to WhatCode', status: 'ready', sections: [{ title: null, external: true, links: external }] })
  }
  return groups
}

/** How many pages inside the site a map lists, the group pages included. */
export function countPages(map) {
  let n = 0
  for (const g of map) {
    if (g.href) n++
    for (const s of g.sections) {
      if (s.external) continue
      if (s.href) n++
      n += s.links.length
    }
  }
  return n
}
