// Which GitHub repository this checkout belongs to, as [owner, name], or null.
// GITHUB_REPOSITORY (set by Actions) or VITE_GITHUB_REPO win; otherwise the `origin` remote is read.
import { execFileSync } from 'node:child_process'

export function detectRepo() {
  const fromEnv = process.env.GITHUB_REPOSITORY || process.env.VITE_GITHUB_REPO
  if (fromEnv && /^[\w.-]+\/[\w.-]+$/.test(fromEnv)) return fromEnv.split('/')
  try {
    const url = execFileSync('git', ['remote', 'get-url', 'origin'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
    const m = url.match(/github\.com[:/]([\w.-]+)\/([\w.-]+?)(?:\.git)?$/)
    if (m) return [m[1], m[2]]
  } catch {
    // not a git checkout, or no remote
  }
  return null
}
