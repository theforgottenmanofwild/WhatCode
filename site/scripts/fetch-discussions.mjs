// Writes public/discussions.json, the snapshot the Forums pages show.
//   npm run discussions
//
// Needs a token that can read the repository (GITHUB_TOKEN in the website workflow, or GH_TOKEN on your machine).
// With no token it writes an empty "unavailable" snapshot and succeeds, so a local build still works.
// With a token, any failure stops the build: the site stays as it was instead of publishing an empty forum.
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { fetchSnapshot, unavailable } from './discussions.mjs'
import { detectRepo } from './repo.mjs'

const OUT = fileURLToPath(new URL('../public/discussions.json', import.meta.url))

async function write(snapshot) {
  await mkdir(dirname(OUT), { recursive: true })
  await writeFile(OUT, JSON.stringify(snapshot))
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function makeRun(token) {
  return async function run(query, variables) {
    for (let attempt = 1; ; attempt++) {
      const res = await fetch('https://api.github.com/graphql', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          'User-Agent': 'whatcode-site',
        },
        body: JSON.stringify({ query, variables }),
      })
      // GitHub sometimes answers 502/503 for a moment; try a few times before giving up.
      if ([502, 503, 504].includes(res.status) && attempt < 4) {
        await sleep(attempt * 2000)
        continue
      }
      if (!res.ok) throw new Error(`GitHub answered ${res.status} ${res.statusText}`)
      const json = await res.json()
      if (json.errors?.length) throw new Error(json.errors.map((e) => e.message).join('; '))
      return json.data
    }
  }
}

const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN
const repo = detectRepo()

if (!token || !repo) {
  await write(unavailable(!token ? 'no-token' : 'no-repository'))
  console.warn(`discussions: no ${!token ? 'token (set GITHUB_TOKEN or GH_TOKEN)' : 'repository'}; wrote an empty snapshot.`)
} else {
  const [owner, name] = repo
  try {
    const snapshot = await fetchSnapshot({ owner, name, run: makeRun(token) })
    await write(snapshot)
    if (!snapshot.available) {
      console.warn(`discussions: Discussions is turned off for ${owner}/${name}; wrote an empty snapshot.`)
    } else {
      const comments = snapshot.discussions.reduce(
        (n, d) => n + d.comments.length + d.comments.reduce((m, c) => m + c.replies.length, 0),
        0,
      )
      console.log(
        `discussions: ${snapshot.discussions.length} discussions, ${comments} comments, ${snapshot.categories.length} categories`,
      )
    }
  } catch (err) {
    console.error(`discussions: failed: ${err.message}`)
    process.exit(1)
  }
}
