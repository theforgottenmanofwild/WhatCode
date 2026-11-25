// The forum part of the smoke test: the discussion snapshot, its indexing, and every forum page rendered with sample data.
// Called from smoke.mjs with the same Vite server.
import assert from 'node:assert/strict'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

// ---------------------------------------------------------------- sample data (the shape fetch-discussions.mjs writes)

const person = (login, id) => ({ login, avatar: `https://avatars.githubusercontent.com/u/${id}?v=4`, url: `https://github.com/${login}` })
const owner = person('owner', 1)
const alice = person('alice', 2)
const bob = person('bob', 3)
const carol = person('carol', 4)

const comment = (id, o = {}) => ({
  id,
  url: `https://github.com/o/r/discussions/2#discussioncomment-${id}`,
  createdAt: '2026-11-02T10:00:00Z',
  editedAt: null,
  deleted: false,
  body: `text of ${id}`,
  author: alice,
  association: 'CONTRIBUTOR',
  isAnswer: false,
  minimized: null,
  upvotes: 0,
  reactions: [],
  replies: [],
  ...o,
})

const disc = (number, o = {}) => ({
  number,
  title: `Discussion ${number}`,
  url: `https://github.com/o/r/discussions/${number}`,
  body: '',
  createdAt: '2026-11-01T10:00:00Z',
  updatedAt: '2026-11-01T10:00:00Z',
  editedAt: null,
  editor: null,
  author: owner,
  association: 'OWNER',
  category: 'general',
  labels: [],
  locked: false,
  lockReason: null,
  closed: false,
  closedAt: null,
  stateReason: null,
  answered: false,
  answerChosenAt: null,
  answerChosenBy: null,
  upvotes: 0,
  reactions: [],
  poll: null,
  comments: [],
  ...o,
})

const category = (slug, name, emoji, description, answerable = false) => ({ id: `DIC_${slug}`, slug, name, emoji, description, answerable })

const sample = {
  version: 1,
  available: true,
  generatedAt: '2026-11-24T12:00:00Z',
  repo: 'o/r',
  categories: [
    category('announcements', 'Announcements', '📣', 'Updates from maintainers'),
    category('general', 'General', '💬', 'Chat about anything'),
    category('ideas', 'Ideas', '💡', 'Share ideas for new features'),
    category('polls', 'Polls', '🗳️', 'Take a vote'),
    category('q-a', 'Q&A', '🙏', 'Ask the community for help', true),
    category('show-and-tell', 'Show and tell', '🙌', 'Show what you made'),
  ],
  pinned: [{ number: 1, colors: ['58A6FF', '56D364'] }],
  discussions: [
    disc(1, {
      title: 'Welcome to the WhatCode editor',
      category: 'announcements',
      upvotes: 3,
      reactions: [{ content: 'HEART', count: 2 }, { content: 'ROCKET', count: 1 }],
      body:
        '<!--\r\n  a note for maintainers\r\n-->\r\n## 👋 Welcome!\r\nFirst line\r\nSecond line\r\n\r\n' +
        '<script>alert(1)</script><img src="x" onerror="alert(2)">\r\n\r\n[bad](javascript:alert(3)) ' +
        '[the keys question](https://github.com/o/r/discussions/2#discussioncomment-9) and https://example.test/page ' +
        'thanks @alice, see #2 and #77.\r\n\r\n- [x] read this\r\n- [ ] say hello\r\n',
    }),
    disc(2, {
      title: 'How do I change the keys?',
      category: 'q-a',
      createdAt: '2026-11-02T09:00:00Z',
      labels: [{ name: 'bug', color: 'd73a4a', description: 'Something is broken' }, { name: 'docs', color: '0075ca', description: '' }],
      body: 'Where is the **key editor**?\n\n```kotlin\nval x = 1\n```\n',
      answered: true,
      answerChosenBy: 'owner',
      answerChosenAt: '2026-11-04T12:00:00Z',
      comments: [
        comment('DC_A', {
          upvotes: 2,
          reactions: [{ content: 'THUMBS_UP', count: 2 }],
          replies: [
            comment('DC_R1', { author: bob, createdAt: '2026-11-03T10:00:00Z', body: 'reply from bob' }),
            comment('DC_R2', { deleted: true, body: '', createdAt: '2026-11-03T11:00:00Z' }),
          ],
        }),
        comment('DC_B', {
          author: owner,
          association: 'OWNER',
          isAnswer: true,
          createdAt: '2026-11-04T10:00:00Z',
          editedAt: '2026-11-05T10:00:00Z',
          body: 'Open **Settings**, then Key rows.',
        }),
        comment('DC_C', { minimized: 'spam', body: 'buy cheap stuff', createdAt: '2026-11-06T10:00:00Z' }),
        comment('DC_D', {
          deleted: true,
          body: '',
          replies: [comment('DC_R3', { author: carol, association: 'NONE', body: 'reply under a deleted comment' })],
        }),
      ],
    }),
    disc(3, {
      title: 'Which language next?',
      category: 'polls',
      poll: {
        question: 'Which language should be highlighted next?',
        total: 10,
        options: [
          { id: 'o1', text: 'Rust', votes: 6 },
          { id: 'o2', text: 'Go', votes: 4 },
          { id: 'o3', text: 'Zig', votes: 0 },
        ],
      },
    }),
    disc(4, {
      title: 'Dark mode please',
      category: 'ideas',
      closed: true,
      closedAt: '2026-11-10T10:00:00Z',
      stateReason: 'RESOLVED',
      locked: true,
      lockReason: 'TOO_HEATED',
      createdAt: '2026-11-08T10:00:00Z',
      comments: [
        comment('DC_E', {
          author: { login: 'mallory', avatar: 'https://evil.test/a.png', url: 'https://evil.test/m' },
          association: 'FIRST_TIME_CONTRIBUTOR',
          body: 'me too',
          createdAt: '2026-11-09T10:00:00Z',
        }),
      ],
    }),
    disc(5, { title: 'An old post', author: null, association: 'NONE', createdAt: '2026-10-01T10:00:00Z', body: 'written by someone who left' }),
    disc(6, { title: 'Another question', category: 'q-a', author: bob, association: 'NONE', createdAt: '2026-11-12T10:00:00Z', body: 'Is there a tablet layout?' }),
  ],
}

export async function forumSmoke(server) {
  const forumJs = await server.ssrLoadModule('/src/forum.js')
  const { indexForum, queryDiscussions, sortComments, flattenComments, pollShares, excerpt, avatarSrc, labelStyle, pinStripe } = forumJs
  const { linkifyText, localHref, searchTerms, plural, threadHref, categoryHref, commentAnchor } = forumJs
  const { ForumsView } = await server.ssrLoadModule('/src/components/Forums.jsx')
  const { loadForum } = await server.ssrLoadModule('/src/useForum.js')
  const { parseRoute } = await server.ssrLoadModule('/src/route.js')
  const pipeline = await import('./discussions.mjs')

  // ---- routes
  assert.deepEqual(parseRoute('/forums'), { page: 'forums', slug: null, category: null, thread: null, anchor: '' })
  assert.equal(parseRoute('/forums/c/q-a').category, 'q-a')
  assert.equal(parseRoute('/forums/12').thread, 12)
  assert.equal(parseRoute('/forums/12#comment-DC_x').anchor, 'comment-DC_x')
  for (const bad of ['/forums/0', '/forums/abc', '/forums/12/x', '/forums/c/a/b']) assert.equal(parseRoute(bad).page, 'missing', bad)
  assert.equal(threadHref(7, commentAnchor('DC_1')), '/forums/7#comment-DC_1')
  assert.equal(categoryHref('q-a'), '/forums/c/q-a')

  // ---- indexing
  const forum = indexForum(sample)
  assert.equal(forum.stats.discussions, 6)
  assert.equal(forum.byNumber.get(2).commentCount, 5, 'deleted comments are not counted, replies are')
  assert.equal(forum.byNumber.get(2).topLevelCount, 3)
  assert.equal(forum.stats.comments, 6)
  assert.equal(forum.stats.people, 5, 'owner, alice, bob, carol, mallory')
  assert.equal(forum.stats.answered, 1)
  assert.equal(forum.bySlug.get('q-a').count, 2)
  assert.equal(forum.bySlug.get('q-a').comments, 5)
  assert.equal(forum.bySlug.get('q-a').latest, 6, 'the most recently active discussion is the latest')
  assert.equal(forum.bySlug.get('show-and-tell').latest, null)
  assert.equal(forum.bySlug.get('show-and-tell').count, 0)
  assert.equal(forum.byNumber.get(2).answer.id, 'DC_B')
  assert.equal(forum.byNumber.get(2).participants[0].login, 'owner', 'the author comes first')
  assert.equal(forum.byNumber.get(1).pinned, true)
  assert.deepEqual(forum.labels.map((l) => l.name), ['bug', 'docs'])
  assert.equal(flattenComments(sample.discussions[1].comments).length, 7)
  assert.equal(indexForum({ discussions: [], categories: [] }).stats.discussions, 0, 'an empty snapshot indexes')

  // ---- listing, filtering, searching
  const numbers = (opts) => queryDiscussions(forum, opts).map((d) => d.number)
  assert.equal(numbers({})[0], 1, 'a pinned discussion comes first')
  assert.deepEqual(numbers({ status: 'answered' }), [2])
  assert.deepEqual(numbers({ status: 'unanswered' }), [6], 'only answerable categories can be unanswered')
  assert.deepEqual(numbers({ status: 'closed' }), [4])
  assert.ok(!numbers({ status: 'open' }).includes(4))
  assert.deepEqual(numbers({ label: 'bug' }), [2])
  assert.deepEqual(numbers({ category: 'q-a', sort: 'new' }), [6, 2])
  assert.deepEqual(numbers({ category: 'q-a', sort: 'old' }), [2, 6])
  assert.deepEqual(numbers({ query: 'dark' }), [4])
  assert.deepEqual(numbers({ query: 'cheap' }), [2], 'text of comments is searched')
  assert.deepEqual(numbers({ query: 'KOTLIN key' }), [2], 'every word must match, in any case')
  assert.deepEqual(numbers({ query: 'key editor' }), [2, 1], 'a title match ranks first')
  assert.deepEqual(numbers({ query: 'carol' }), [2], 'people are searched')
  assert.deepEqual(numbers({ query: 'zzzz' }), [])
  assert.equal(numbers({ sort: 'top' })[0], 1)
  assert.deepEqual(searchTerms('  Dark   mode '), ['dark', 'mode'])
  assert.equal(plural(1, 'comment'), '1 comment')
  assert.equal(plural(0, 'reply', 'replies'), '0 replies')

  // ---- comments and polls
  assert.deepEqual(sortComments(sample.discussions[1].comments, 'top').map((c) => c.id)[0], 'DC_A')
  assert.deepEqual(sortComments(sample.discussions[1].comments, 'new').map((c) => c.id)[0], 'DC_C')
  assert.deepEqual(sortComments(sample.discussions[1].comments, 'old')[0].replies.map((r) => r.id), ['DC_R1', 'DC_R2'], 'replies stay oldest first')
  const shares = pollShares(sample.discussions[2].poll)
  assert.deepEqual(shares.map((s) => s.percent), [60, 40, 0])
  assert.deepEqual(shares.map((s) => s.leading), [true, false, false])
  assert.deepEqual(pollShares({ options: [{ id: 'a', text: 'A', votes: 0 }], total: 0 }).map((s) => [s.percent, s.leading]), [[0, false]], 'no votes, no leader')

  // ---- small helpers
  assert.ok(avatarSrc('https://avatars.githubusercontent.com/u/1?v=4', 48).includes('s=48'))
  assert.equal(avatarSrc('https://evil.test/a.png'), null, 'avatars load from GitHub only')
  assert.equal(avatarSrc('http://avatars.githubusercontent.com/u/1'), null)
  assert.equal(avatarSrc(null), null)
  assert.deepEqual(labelStyle('zzzzzz'), {})
  assert.equal(labelStyle('ffffff').color, '#1b2430', 'dark text on a light label')
  assert.equal(labelStyle('000000').color, '#ffffff', 'light text on a dark label')
  assert.ok(pinStripe(['58A6FF', '56D364']).backgroundImage.includes('#58A6FF'))
  assert.deepEqual(pinStripe(['58A6FF']), {})
  assert.equal(excerpt('<!-- x -->\n## Hi\n**bold** [a link](https://x.test) `code`'), 'Hi bold a link code')
  assert.ok(excerpt('word '.repeat(100), 50).length <= 51)
  assert.ok(excerpt('word '.repeat(100), 50).endsWith('…'))
  assert.equal(localHref('https://github.com/o/r/discussions/2#discussioncomment-1', forum), '/forums/2')
  assert.equal(localHref('https://github.com/o/r/discussions/404', forum), null, 'a discussion not in the copy stays on GitHub')
  assert.equal(localHref('https://github.com/other/r/discussions/2', forum), null, 'another repository stays on GitHub')
  assert.equal(localHref('https://github.com/o/r/discussions/categories/q-a', forum), '/forums/c/q-a')
  assert.equal(localHref('https://github.com/o/r/discussions', forum), '/forums')
  const parts = linkifyText('hi @alice and #2 and #77', { known: forum.known, repo: 'o/r' })
  assert.deepEqual(parts.filter((p) => p.type === 'link').map((p) => p.url), ['https://github.com/alice', '/forums/2', 'https://github.com/o/r/issues/77'])

  // ---- pages
  const ready = { status: 'ready', forum }
  const page = (hash, state = ready) => renderToStaticMarkup(h(ForumsView, { state, route: parseRoute(hash), onRetry() {} }))

  const home = page('/forums')
  assert.match(home, /<h1>Forums<\/h1>/)
  for (const c of sample.categories) assert.ok(home.includes(c.slug === 'q-a' ? 'Q&amp;A' : c.name), `home lists ${c.name}`)
  assert.match(home, /href="\/forums\/c\/q-a"/)
  assert.match(home, /href="\/forums\/2"/)
  assert.match(home, /Pinned/)
  assert.match(home, /class="pin-stripe"/)
  assert.match(home, /No discussions yet/, 'an empty category says so')
  assert.match(home, /Marked answers/)
  assert.match(home, /Start a discussion/)
  assert.match(home, /https:\/\/github\.com\/o\/r\/discussions\/new\/choose/)
  assert.match(home, /aria-label="Search discussions"/)
  assert.match(home, /aria-pressed="true"[^>]*>All</)
  assert.match(home, />Unanswered</)
  assert.match(home, /aria-label="Filter by label"/)
  assert.match(home, /6 discussions/)
  assert.ok(!home.includes('evil.test'), 'no picture or link from an unknown host')
  assert.match(home, /Last copied/)

  // a discussion with HTML, mentions, task lists and soft line breaks
  const welcome = page('/forums/1')
  assert.match(welcome, /<h1>Welcome to the WhatCode editor <span[^>]*>#1<\/span><\/h1>/)
  assert.ok(!/<script|onerror|javascript:/i.test(welcome), 'scripts, handlers and javascript: links are removed')
  assert.match(welcome, /<span>bad<\/span>/, 'the text of a removed link stays')
  assert.ok(!welcome.includes('a note for maintainers'), 'HTML comments are not shown')
  assert.match(welcome, /First line<br\/>\s*Second line/, 'a single line break stays a line break')
  assert.match(welcome, /href="\/forums\/2"[^>]*>the keys question</, 'a link to another discussion stays in the site')
  assert.match(welcome, /href="\/forums\/2"[^>]*>#2</)
  assert.match(welcome, /href="https:\/\/github\.com\/o\/r\/issues\/77"/)
  assert.match(welcome, /href="https:\/\/github\.com\/alice"[^>]*>@alice</)
  assert.match(welcome, /<a href="https:\/\/example\.test\/page" target="_blank" rel="noopener noreferrer nofollow ugc"/)
  assert.match(welcome, /type="checkbox"[^>]*checked/)
  assert.match(welcome, /❤️/)
  assert.match(welcome, /aria-label="3 upvotes"/)
  assert.match(welcome, /Owner<\/span>/, 'the author is marked as the owner')
  assert.match(welcome, /No comments yet/)
  assert.match(welcome, /Pinned/)

  // an answered question: answer box, hidden and deleted comments, replies, labels, participants
  const q = page('/forums/2')
  assert.match(q, /<h1>How do I change the keys\? /)
  assert.match(q, /Chosen answer/)
  assert.match(q, /Go to the answer/)
  assert.match(q, /href="\/forums\/2#comment-DC_B"/)
  assert.match(q, /✓ Answer/)
  assert.match(q, /chosen by owner/)
  assert.match(q, /Open <strong>Settings<\/strong>, then Key rows\./)
  assert.match(q, /edited/)
  assert.match(q, /Hidden as spam/)
  assert.match(q, /Show comment/)
  assert.ok(!q.includes('buy cheap stuff'), 'a hidden comment stays collapsed')
  assert.equal(q.split('This comment was deleted.').length - 1, 2, 'both deleted comments say so')
  assert.match(q, /reply under a deleted comment/, 'replies under a deleted comment stay')
  assert.match(q, /reply from bob/)
  assert.match(q, /aria-label="1 reply"/)
  assert.match(q, /background-color:#d73a4a/)
  assert.match(q, /Something is broken/)
  assert.match(q, /val x = 1/)
  assert.match(q, /👍/)
  assert.match(q, /4 participants/)
  assert.match(q, /5 comments/)
  assert.match(q, /Jump to latest/)
  assert.match(q, /Oldest first/)
  assert.match(q, /Reply on GitHub/)
  assert.match(q, /href="\/forums\/c\/q-a"/, 'the breadcrumb goes to the category')
  assert.match(q, /View on GitHub/)

  // a link to a hidden comment opens it and marks it
  const target = page('/forums/2#comment-DC_C')
  assert.match(target, /buy cheap stuff/)
  assert.match(target, /is-target/)
  assert.match(target, /Hide comment/)

  // a poll
  const poll = page('/forums/3')
  assert.match(poll, /Which language should be highlighted next\?/)
  assert.match(poll, /60% · 6 votes/)
  assert.match(poll, /0% · 0 votes/)
  assert.match(poll, /10 votes in all/)
  assert.match(poll, /is-leading/)

  // closed and locked, and a commenter whose picture and link come from somewhere else
  const closed = page('/forums/4')
  assert.match(closed, /Locked as too heated/)
  assert.match(closed, /Closed as resolved/)
  assert.match(closed, /No description\./)
  assert.match(closed, /First-time contributor/)
  assert.match(closed, /mallory/)
  assert.ok(!closed.includes('evil.test'), 'a picture or profile on another host is dropped')
  assert.match(closed, /This discussion is locked/)

  // a deleted author
  const old = page('/forums/5')
  assert.match(old, />ghost</)
  assert.match(old, /written by someone who left/)

  // not found
  const nope = page('/forums/99')
  assert.match(nope, /Discussion not found/)
  assert.match(nope, /https:\/\/github\.com\/o\/r\/discussions\/99/)
  assert.match(page('/forums/c/nowhere'), /Category not found/)

  // a category
  const cat = page('/forums/c/q-a')
  assert.match(cat, /<h1><span[^>]*>🙏<\/span> Q&amp;A<\/h1>/)
  assert.match(cat, /Ask the community for help/)
  assert.match(cat, /Another question/)
  assert.match(cat, /How do I change the keys\?/)
  assert.ok(!cat.includes('Welcome to the WhatCode editor'), 'only this category')
  assert.match(cat, /aria-current="page"[^>]*>[^<]*<span[^>]*>🙏/)
  assert.match(cat, /discussions\/new\?category=q-a/)
  assert.match(cat, /2 discussions/)
  assert.match(page('/forums/c/show-and-tell'), /There are no discussions here yet/)

  // an empty forum, and the states with nothing to show
  const empty = indexForum({ ...sample, discussions: [], pinned: [] })
  const none = page('/forums', { status: 'ready', forum: empty })
  assert.match(none, /There are no discussions here yet/)
  assert.match(none, /0 discussions/)
  assert.match(page('/forums', { status: 'loading' }), /Loading/)
  assert.match(page('/forums', { status: 'unavailable', reason: 'no-token' }), /built without access to GitHub/)
  assert.match(page('/forums', { status: 'unavailable', reason: 'disabled' }), /turned off/)
  assert.match(page('/forums/2', { status: 'unavailable', reason: 'whatever' }), /not available/)
  assert.match(page('/forums', { status: 'error', reason: 'missing' }), /no copy of the discussions/)
  const failed = page('/forums', { status: 'error', reason: 'http-500' })
  assert.match(failed, /could not be loaded/)
  assert.match(failed, /Try again/)

  // ---- loading the snapshot file
  const realFetch = globalThis.fetch
  const answer = (status, body) => async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined) throw new SyntaxError('not json')
      return body
    },
  })
  try {
    globalThis.fetch = answer(200, sample)
    const ok = await loadForum()
    assert.equal(ok.status, 'ready')
    assert.equal(ok.forum.stats.discussions, 6)
    globalThis.fetch = answer(200, { version: 1, available: false, reason: 'no-token', generatedAt: 'x' })
    assert.deepEqual(await loadForum(), { status: 'unavailable', reason: 'no-token', generatedAt: 'x' })
    globalThis.fetch = answer(404)
    await assert.rejects(loadForum(), /missing/)
    globalThis.fetch = answer(200)
    await assert.rejects(loadForum(), /missing/, 'a page where the file should be is a missing file')
    globalThis.fetch = answer(200, { version: 2 })
    await assert.rejects(loadForum(), /format/)
    globalThis.fetch = answer(500, {})
    await assert.rejects(loadForum(), /http-500/)
  } finally {
    globalThis.fetch = realFetch
  }

  // ---- the build-time fetcher: pages of comments and replies are followed, and the result is what the site reads
  const actorNode = { login: 'a', avatarUrl: 'https://avatars.githubusercontent.com/u/9', url: 'https://github.com/a' }
  const node = (id, extra = {}) => ({
    id, url: `https://github.com/o/r/discussions/1#${id}`, createdAt: '2026-11-01T00:00:00Z', lastEditedAt: null, deletedAt: null,
    body: id, author: actorNode, authorAssociation: 'MEMBER', isAnswer: false, isMinimized: false, minimizedReason: null, upvoteCount: 1,
    reactionGroups: [{ content: 'HEART', reactors: { totalCount: 2 } }, { content: 'EYES', reactors: { totalCount: 0 } }], ...extra,
  })
  const calls = []
  const run = async (query, vars) => {
    calls.push(query.match(/query (\w+)/)[1])
    if (query.includes('query Forum')) {
      return {
        repository: {
          hasDiscussionsEnabled: true,
          discussionCategories: { nodes: [{ id: 'C1', name: 'Q&A', slug: 'q-a', emojiHTML: '<div>🙏</div>', description: null, isAnswerable: true }] },
          pinnedDiscussions: { nodes: [{ gradientStopColors: ['58A6FF', '56D364'], discussion: { number: 1 } }] },
          discussions: {
            pageInfo: { hasNextPage: false, endCursor: null },
            nodes: [{
              id: 'D1', number: 1, title: 'T', url: 'https://github.com/o/r/discussions/1', body: 'b', createdAt: '2026-11-01T00:00:00Z',
              updatedAt: '2026-11-01T00:00:00Z', lastEditedAt: null, author: null, authorAssociation: 'NONE', editor: null,
              category: { slug: 'q-a' }, labels: { nodes: [{ name: 'bug', color: 'd73a4a', description: null }] }, locked: false,
              activeLockReason: null, closed: false, closedAt: null, stateReason: null, isAnswered: false, answerChosenAt: null,
              answerChosenBy: null, upvoteCount: 0, reactionGroups: [], poll: null,
              comments: {
                totalCount: 2, pageInfo: { hasNextPage: true, endCursor: 'c1' },
                nodes: [node('DC_1', { replies: { totalCount: 2, pageInfo: { hasNextPage: true, endCursor: 'r1' }, nodes: [node('DC_R1')] } })],
              },
            }],
          },
        },
      }
    }
    if (query.includes('query MoreComments')) {
      assert.deepEqual([vars.id, vars.after], ['D1', 'c1'])
      return { node: { comments: { pageInfo: { hasNextPage: false, endCursor: null }, nodes: [node('DC_2', { isMinimized: true, minimizedReason: 'SPAM', replies: { totalCount: 0, pageInfo: { hasNextPage: false }, nodes: [] } })] } } }
    }
    assert.deepEqual([vars.id, vars.after], ['DC_1', 'r1'])
    return { node: { replies: { pageInfo: { hasNextPage: false, endCursor: null }, nodes: [node('DC_R2')] } } }
  }
  const snap = await pipeline.fetchSnapshot({ owner: 'o', name: 'r', run, now: new Date('2026-11-24T00:00:00Z') })
  assert.deepEqual(calls, ['Forum', 'MoreComments', 'MoreReplies'])
  assert.equal(snap.available, true)
  assert.equal(snap.categories[0].emoji, '🙏')
  assert.equal(snap.categories[0].answerable, true)
  assert.equal(snap.discussions[0].author, null, 'a deleted account has no author')
  assert.deepEqual(snap.discussions[0].comments.map((c) => c.id), ['DC_1', 'DC_2'])
  assert.deepEqual(snap.discussions[0].comments[0].replies.map((c) => c.id), ['DC_R1', 'DC_R2'])
  assert.equal(snap.discussions[0].comments[1].minimized, 'spam')
  assert.deepEqual(snap.discussions[0].comments[0].reactions, [{ content: 'HEART', count: 2 }], 'unused reactions are dropped')
  assert.deepEqual(snap.pinned, [{ number: 1, colors: ['58A6FF', '56D364'] }])
  const fromSnap = indexForum(JSON.parse(JSON.stringify(snap)))
  assert.equal(fromSnap.byNumber.get(1).commentCount, 4)
  assert.match(page('/forums/1', { status: 'ready', forum: fromSnap }), /ghost/)
  const off = await pipeline.fetchSnapshot({ owner: 'o', name: 'r', run: async () => ({ repository: { hasDiscussionsEnabled: false } }) })
  assert.deepEqual([off.available, off.reason], [false, 'disabled'])
  assert.equal(pipeline.unavailable('no-token').available, false)
  await assert.rejects(pipeline.fetchSnapshot({ owner: 'o', name: 'r', run: async () => ({ repository: null }) }), /not found/)
  assert.equal(pipeline.categoryEmoji('<div></div>'), '💬', 'a category with no emoji gets a speech bubble')
}
