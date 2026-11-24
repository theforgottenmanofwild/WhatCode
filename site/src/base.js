// Where the site is served from. "/" on its own domain or a user site, "/name/" on a GitHub Pages project site.
// It is fixed when the site is built (VITE_BASE, see vite.config.js), because the pages have real addresses
// like /docs/key-rows and need to know what comes before them.
export const BASE = (import.meta.env?.BASE_URL ?? '/').replace(/\/+$/, '')

/** The address of a page of the site: to('/docs') is '/docs', or '/name/docs' on a project site. */
export const to = (path = '/') => `${BASE}${path}`
