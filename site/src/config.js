// Which GitHub repository's releases this site lists.
// 1) VITE_GITHUB_REPO=owner/name at build time, else
// 2) detected from a GitHub Pages address (owner.github.io/name).
function detectRepo() {
  const fromEnv = import.meta.env.VITE_GITHUB_REPO
  if (fromEnv && /^[\w.-]+\/[\w.-]+$/.test(fromEnv)) return fromEnv
  if (typeof location !== 'undefined' && location.hostname.endsWith('.github.io')) {
    const owner = location.hostname.split('.')[0]
    const name = location.pathname.split('/')[1]
    if (owner && name) return `${owner}/${name}`
  }
  return null
}

export const repo = detectRepo()
export const repoUrl = repo ? `https://github.com/${repo}` : null

// Asset names are fixed by the workflows, so direct links keep working even
// when the GitHub API is unavailable.
export const STABLE_APK = 'WhatCodeEditor.apk'
export const NIGHTLY_APK = 'WhatCodeEditor-nightly.apk'
export const NIGHTLY_TAG = 'nightly'

export const links = repo && {
  api: `https://api.github.com/repos/${repo}/releases?per_page=30`,
  allReleases: `${repoUrl}/releases`,
  stableDirect: `${repoUrl}/releases/latest/download/${STABLE_APK}`,
  nightlyDirect: `${repoUrl}/releases/download/${NIGHTLY_TAG}/${NIGHTLY_APK}`,
  commit: (sha) => `${repoUrl}/commit/${sha}`,
  editDoc: (file) => `${repoUrl}/edit/main/docs/${file}`,
}
