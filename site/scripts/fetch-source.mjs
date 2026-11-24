// Writes public/source-data/, the snapshot the Source pages show.
//   npm run source
//
// Reads the git repository this site lives in: no token and no network needed. It needs the whole history to show
// it, so the website workflow checks out with fetch-depth: 0. In a shallow checkout the history is simply short.
// When this is not a git checkout at all it writes an "unavailable" snapshot and succeeds, so a plain build works.
import { execFileSync } from 'node:child_process'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { collect, unavailable } from './source.mjs'
import { detectRepo } from './repo.mjs'

const OUT = fileURLToPath(new URL('../public/source-data/', import.meta.url))

function topLevel() {
  try {
    return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim()
  } catch {
    return null
  }
}

function branchName(cwd) {
  if (process.env.GITHUB_REF_TYPE === 'branch' && process.env.GITHUB_REF_NAME) return process.env.GITHUB_REF_NAME
  try {
    return execFileSync('git', ['symbolic-ref', '--short', '-q', 'HEAD'], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || 'main'
  } catch {
    return 'main'
  }
}

async function put(name, data) {
  const file = join(OUT, name)
  await mkdir(dirname(file), { recursive: true })
  await writeFile(file, data)
}

const json = (v) => JSON.stringify(v)

await rm(OUT, { recursive: true, force: true })

const cwd = topLevel()
if (!cwd) {
  await put('index.json', json(unavailable('no-git')))
  console.warn('source: this is not a git checkout; wrote an empty snapshot.')
} else {
  const git = (args, { input, buffer } = {}) =>
    execFileSync('git', ['-c', 'core.quotepath=false', ...args], {
      cwd,
      input,
      maxBuffer: 1024 * 1024 * 1024,
      stdio: ['pipe', 'pipe', 'pipe'],
    })

  try {
    const repo = detectRepo()
    const result = collect({ git, repo: repo ? repo.join('/') : null, branch: branchName(cwd) })
    const { index } = result
    await put('index.json', json(index))
    if (index.available) {
      for (const [name, buf] of result.blobs) await put(`blob/${name}`, buf)
      for (const [sha, patch] of result.patches) await put(`commits/${sha}.json`, json(patch))
      for (const [id, runs] of result.blame) await put(`blame/${id}.json`, json(runs))
      await put('search.json', json(result.search))
      console.log(
        `source: ${index.files.length} files, ${index.commits.length} commits, ${index.tags.length} tags, ` +
          `${index.contributors.length} contributors, ${result.blame.size} blame files`,
      )
    } else {
      console.warn('source: no repository found; wrote an empty snapshot.')
    }
  } catch (err) {
    console.error(`source: failed: ${err.message}`)
    process.exit(1)
  }
}
