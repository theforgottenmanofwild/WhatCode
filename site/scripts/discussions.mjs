// Turns a repository's GitHub Discussions into one plain JSON snapshot for the Forums pages.
//
// GitHub only serves Discussions through its GraphQL API, and that API needs a token even for a public repository,
// so a static site cannot ask for them from the visitor's browser. The website workflow runs this at build time
// with its own token (see fetch-discussions.mjs), and runs again whenever a discussion changes and on a schedule.
//
// This file does no network access itself: it is given `run(query, variables)`, which makes it easy to test.

const ACTOR = `fragment A on Actor { login avatarUrl url }`

const COMMENT = `fragment C on DiscussionComment {
  id url createdAt lastEditedAt deletedAt body
  author { ...A }
  authorAssociation
  isAnswer isMinimized minimizedReason upvoteCount
  reactionGroups { content reactors { totalCount } }
}`

const DISCUSSION = `fragment D on Discussion {
  id number title url body
  createdAt updatedAt lastEditedAt
  author { ...A }
  authorAssociation
  editor { login }
  category { slug }
  labels(first: 25) { nodes { name color description } }
  locked activeLockReason
  closed closedAt stateReason
  isAnswered answerChosenAt answerChosenBy { login }
  upvoteCount
  reactionGroups { content reactors { totalCount } }
  poll { question totalVoteCount options(first: 25) { nodes { id option totalVoteCount } } }
  comments(first: 50) {
    totalCount
    pageInfo { hasNextPage endCursor }
    nodes {
      ...C
      replies(first: 50) { totalCount pageInfo { hasNextPage endCursor } nodes { ...C } }
    }
  }
}`

const FRAGMENTS = [ACTOR, COMMENT, DISCUSSION].join('\n')

const MAIN = `query Forum($owner: String!, $name: String!, $after: String, $meta: Boolean!) {
  repository(owner: $owner, name: $name) {
    hasDiscussionsEnabled
    discussionCategories(first: 50) @include(if: $meta) {
      nodes { id name slug emojiHTML description isAnswerable createdAt updatedAt }
    }
    pinnedDiscussions(first: 20) @include(if: $meta) {
      nodes { gradientStopColors discussion { number } }
    }
    discussions(first: 15, after: $after, orderBy: { field: CREATED_AT, direction: DESC }) {
      pageInfo { hasNextPage endCursor }
      nodes { ...D }
    }
  }
}
${FRAGMENTS}`

const MORE_COMMENTS = `query MoreComments($id: ID!, $after: String) {
  node(id: $id) {
    ... on Discussion {
      comments(first: 50, after: $after) {
        pageInfo { hasNextPage endCursor }
        nodes { ...C replies(first: 50) { totalCount pageInfo { hasNextPage endCursor } nodes { ...C } } }
      }
    }
  }
}
${ACTOR}
${COMMENT}`

const MORE_REPLIES = `query MoreReplies($id: ID!, $after: String) {
  node(id: $id) {
    ... on DiscussionComment {
      replies(first: 50, after: $after) {
        pageInfo { hasNextPage endCursor }
        nodes { ...C }
      }
    }
  }
}
${ACTOR}
${COMMENT}`

/** Someone who deleted their account has no author. The site shows them as "ghost". */
const actor = (a) => (a ? { login: a.login, avatar: a.avatarUrl, url: a.url } : null)

/** Only the reactions somebody actually used, as { content: 'THUMBS_UP', count: 3 }. */
const reactions = (groups = []) =>
  groups.map((g) => ({ content: g.content, count: g.reactors.totalCount })).filter((r) => r.count > 0)

/** `emojiHTML` is `<div>🙏</div>`. Taking the text keeps every emoji GitHub offers, with no lookup table here. */
export function categoryEmoji(html) {
  const text = String(html ?? '').replace(/<[^>]*>/g, '').trim()
  return text || '💬'
}

function normalizeComment(c, replies = []) {
  const deleted = Boolean(c.deletedAt)
  return {
    id: c.id,
    url: c.url,
    createdAt: c.createdAt,
    editedAt: c.lastEditedAt ?? null,
    deleted,
    body: deleted ? '' : (c.body ?? ''),
    author: actor(c.author),
    association: c.authorAssociation ?? 'NONE',
    isAnswer: Boolean(c.isAnswer),
    // Hidden by a moderator: the reason is kept so the page can say why, and the text stays behind a click.
    minimized: c.isMinimized ? String(c.minimizedReason ?? 'hidden').toLowerCase() : null,
    upvotes: c.upvoteCount ?? 0,
    reactions: reactions(c.reactionGroups),
    replies: replies.map((r) => normalizeComment(r)),
  }
}

/** Every reply of one comment, following the cursor when a comment has more than one page of them. */
async function allReplies(run, comment) {
  const replies = [...(comment.replies?.nodes ?? [])]
  let page = comment.replies?.pageInfo
  while (page?.hasNextPage) {
    const more = (await run(MORE_REPLIES, { id: comment.id, after: page.endCursor })).node.replies
    replies.push(...more.nodes)
    page = more.pageInfo
  }
  return replies
}

/** Every top-level comment of a discussion, each with all of its replies. */
async function allComments(run, discussion) {
  const nodes = [...discussion.comments.nodes]
  let page = discussion.comments.pageInfo
  while (page.hasNextPage) {
    const more = (await run(MORE_COMMENTS, { id: discussion.id, after: page.endCursor })).node.comments
    nodes.push(...more.nodes)
    page = more.pageInfo
  }
  const out = []
  for (const c of nodes) out.push(normalizeComment(c, await allReplies(run, c)))
  return out
}

export async function normalizeDiscussion(run, d) {
  return {
    number: d.number,
    title: d.title,
    url: d.url,
    body: d.body ?? '',
    createdAt: d.createdAt,
    updatedAt: d.updatedAt,
    editedAt: d.lastEditedAt ?? null,
    editor: d.editor?.login ?? null,
    author: actor(d.author),
    association: d.authorAssociation ?? 'NONE',
    category: d.category?.slug ?? null,
    labels: (d.labels?.nodes ?? []).map((l) => ({ name: l.name, color: l.color, description: l.description ?? '' })),
    locked: Boolean(d.locked),
    lockReason: d.activeLockReason ?? null,
    closed: Boolean(d.closed),
    closedAt: d.closedAt ?? null,
    stateReason: d.stateReason ?? null,
    answered: Boolean(d.isAnswered),
    answerChosenAt: d.answerChosenAt ?? null,
    answerChosenBy: d.answerChosenBy?.login ?? null,
    upvotes: d.upvoteCount ?? 0,
    reactions: reactions(d.reactionGroups),
    poll: d.poll
      ? {
          question: d.poll.question,
          total: d.poll.totalVoteCount,
          options: d.poll.options.nodes.map((o) => ({ id: o.id, text: o.option, votes: o.totalVoteCount })),
        }
      : null,
    comments: await allComments(run, d),
  }
}

/** What the site shows when there is nothing to show, and why. */
export const unavailable = (reason, now = new Date()) => ({
  version: 1,
  available: false,
  reason,
  generatedAt: now.toISOString(),
})

export async function fetchSnapshot({ owner, name, run, now = new Date() }) {
  const first = await run(MAIN, { owner, name, after: null, meta: true })
  const repo = first.repository
  if (!repo) throw new Error(`The repository ${owner}/${name} was not found, or the token cannot see it.`)
  if (!repo.hasDiscussionsEnabled) return unavailable('disabled', now)

  const raw = [...repo.discussions.nodes]
  let page = repo.discussions.pageInfo
  while (page.hasNextPage) {
    const next = (await run(MAIN, { owner, name, after: page.endCursor, meta: false })).repository.discussions
    raw.push(...next.nodes)
    page = next.pageInfo
  }

  const discussions = []
  for (const d of raw) discussions.push(await normalizeDiscussion(run, d))

  return {
    version: 1,
    available: true,
    generatedAt: now.toISOString(),
    repo: `${owner}/${name}`,
    categories: repo.discussionCategories.nodes.map((c) => ({
      id: c.id,
      name: c.name,
      slug: c.slug,
      emoji: categoryEmoji(c.emojiHTML),
      description: c.description ?? '',
      answerable: Boolean(c.isAnswerable),
    })),
    pinned: repo.pinnedDiscussions.nodes.map((p) => ({
      number: p.discussion.number,
      colors: p.gradientStopColors ?? [],
    })),
    discussions,
  }
}
