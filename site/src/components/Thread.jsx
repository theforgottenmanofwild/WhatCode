import { to } from '../base.js'
import { useEffect, useMemo, useState } from 'react'
import {
  COMMENT_SORTS,
  categoryHref,
  commentAnchor,
  excerpt,
  flattenComments,
  hiddenReasonLabel,
  lockReasonLabel,
  plural,
  pollShares,
  sortComments,
  threadHref,
} from '../forum.js'
import { Avatar, ExternalIcon, Label, Markdown, Person, Reactions, SnapshotNote, StatusPills, Time } from './ForumParts.jsx'

// ---------- one comment (and, under it, its replies) ----------

function Comment({ c, forum, number, target, reply = false }) {
  const hiddenWhy = c.minimized ? hiddenReasonLabel(c.minimized) : null
  const isTarget = target === commentAnchor(c.id)
  // A comment a moderator hid stays collapsed, unless the link points straight at it.
  const [open, setOpen] = useState(isTarget)
  useEffect(() => {
    if (isTarget) setOpen(true)
  }, [isTarget])

  const replies = c.replies ?? []
  const classes = ['post', 'comment', reply && 'is-reply', c.isAnswer && 'is-answer', isTarget && 'is-target'].filter(Boolean).join(' ')

  return (
    <article className={classes} id={commentAnchor(c.id)} aria-label={c.deleted ? 'Deleted comment' : `Comment by ${c.author?.login ?? 'ghost'}`}>
      {c.deleted ? (
        <p className="post-gone">This comment was deleted.</p>
      ) : (
        <>
          <header className="post-head">
            <Avatar person={c.author} size={reply ? 28 : 36} />
            <div className="post-who">
              <Person person={c.author} association={c.association} />
              <span className="post-when">
                <a href={threadHref(number, commentAnchor(c.id))}>
                  <Time iso={c.createdAt} />
                </a>
                {c.editedAt && (
                  <>
                    {' '}· edited <Time iso={c.editedAt} />
                  </>
                )}
              </span>
            </div>
            {c.isAnswer && <span className="badge badge-answer">✓ Answer</span>}
            {hiddenWhy && <span className="badge badge-hidden">Hidden as {hiddenWhy}</span>}
          </header>

          {hiddenWhy && !open ? (
            <p className="post-gone">
              A maintainer hid this comment as {hiddenWhy}.{' '}
              <button type="button" className="textbtn" aria-expanded="false" onClick={() => setOpen(true)}>
                Show comment
              </button>
            </p>
          ) : (
            <>
              <Markdown forum={forum}>{c.body}</Markdown>
              {hiddenWhy && (
                <p className="post-gone">
                  <button type="button" className="textbtn" aria-expanded="true" onClick={() => setOpen(false)}>
                    Hide comment
                  </button>
                </p>
              )}
              <Reactions upvotes={c.upvotes} reactions={c.reactions} />
            </>
          )}
        </>
      )}

      {replies.length > 0 && (
        <div className="replies" role="group" aria-label={plural(replies.length, 'reply', 'replies')}>
          {replies.map((r) => (
            <Comment key={r.id} c={r} forum={forum} number={number} target={target} reply />
          ))}
        </div>
      )}
    </article>
  )
}

// ---------- a poll ----------

function Poll({ poll, voteUrl }) {
  const shares = pollShares(poll)
  const total = poll.options.reduce((n, o) => n + o.votes, 0) || poll.total || 0
  return (
    <section className="poll" aria-label="Poll">
      <h3>{poll.question}</h3>
      <ul>
        {shares.map((o) => (
          <li key={o.id} className={o.leading ? 'is-leading' : undefined}>
            <div className="poll-row">
              <span>{o.text}</span>
              <span className="poll-num">
                {o.percent}% · {plural(o.votes, 'vote')}
              </span>
            </div>
            <div className="poll-bar" aria-hidden="true">
              <span style={{ width: `${o.percent}%` }} />
            </div>
          </li>
        ))}
      </ul>
      <p className="poll-foot">
        {plural(total, 'vote')} in all.{' '}
        {voteUrl && (
          <a href={voteUrl} target="_blank" rel="noopener noreferrer">
            Vote on GitHub <ExternalIcon />
          </a>
        )}
      </p>
    </section>
  )
}

// ---------- the details beside a discussion ----------

function Details({ d, forum, category }) {
  const lock = lockReasonLabel(d.lockReason)
  const replies = d.commentCount - d.topLevelCount
  return (
    <aside className="thread-side" aria-label="Discussion details">
      <dl className="side-facts">
        {category && (
          <div>
            <dt>Category</dt>
            <dd>
              <a href={categoryHref(category.slug)}>
                <span aria-hidden="true">{category.emoji}</span> {category.name}
              </a>
            </dd>
          </div>
        )}
        <div>
          <dt>Started</dt>
          <dd><Time iso={d.createdAt} /></dd>
        </div>
        <div>
          <dt>Last activity</dt>
          <dd><Time iso={d.lastActivity} /></dd>
        </div>
        {d.editedAt && (
          <div>
            <dt>Edited</dt>
            <dd>
              <Time iso={d.editedAt} />
              {d.editor && <> by {d.editor}</>}
            </dd>
          </div>
        )}
        <div>
          <dt>Comments</dt>
          <dd>
            {d.commentCount}
            {replies > 0 && <small> ({plural(replies, 'reply', 'replies')})</small>}
          </dd>
        </div>
        <div>
          <dt>Upvotes</dt>
          <dd>{d.upvotes}</dd>
        </div>
        {d.closed && (
          <div>
            <dt>Closed</dt>
            <dd>{d.closedAt ? <Time iso={d.closedAt} /> : 'Yes'}</dd>
          </div>
        )}
        {d.locked && (
          <div>
            <dt>Locked</dt>
            <dd>{lock ?? 'Yes'}</dd>
          </div>
        )}
        {d.answered && d.answerChosenAt && (
          <div>
            <dt>Answer chosen</dt>
            <dd>
              <Time iso={d.answerChosenAt} />
              {d.answerChosenBy && <> by {d.answerChosenBy}</>}
            </dd>
          </div>
        )}
      </dl>

      {d.labels.length > 0 && (
        <section aria-labelledby="side-labels">
          <h2 id="side-labels">Labels</h2>
          <ul className="side-labels">
            {d.labels.map((l) => (
              <li key={l.name}>
                <Label label={l} />
                {l.description && <span>{l.description}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="side-people">
        <h2 id="side-people">
          {plural(d.participants.length, 'participant')}
        </h2>
        <ul className="people">
          {d.participants.map((p) => (
            <li key={p.login}>
              <Avatar person={p} size={24} />
              <Person person={p} />
            </li>
          ))}
        </ul>
      </section>

      {forum.repoUrl && (
        <p className="side-link">
          <a href={`${forum.repoUrl}/discussions/${d.number}`} target="_blank" rel="noopener noreferrer">
            View on GitHub <ExternalIcon />
          </a>
        </p>
      )}
    </aside>
  )
}

// ---------- a whole discussion ----------

export default function Thread({ forum, number, anchor }) {
  const d = forum.byNumber.get(number)
  const [sort, setSort] = useState('old')
  const comments = useMemo(() => (d ? sortComments(d.comments, sort) : []), [d, sort])
  const latest = useMemo(() => {
    if (!d) return null
    const all = flattenComments(d.comments).filter((c) => !c.deleted)
    return all.reduce((best, c) => (!best || new Date(c.createdAt) > new Date(best.createdAt) ? c : best), null)
  }, [d])

  // The forum loads after the page does, so a link to one comment scrolls once the comment exists.
  useEffect(() => {
    if (anchor) document.getElementById(anchor)?.scrollIntoView({ block: 'start' })
  }, [anchor, d])

  if (!d) {
    return (
      <section className="forum-missing">
        <h1>Discussion not found</h1>
        <p>
          There is no discussion #{number} in this copy of the forums. It may be newer than the last copy.{' '}
          <a href={to('/forums')}>See all discussions</a>
          {forum.repoUrl && (
            <>
              , or{' '}
              <a href={`${forum.repoUrl}/discussions/${number}`} target="_blank" rel="noopener noreferrer">
                look for it on GitHub <ExternalIcon />
              </a>
            </>
          )}
          .
        </p>
      </section>
    )
  }

  const category = forum.bySlug.get(d.category)
  const lock = lockReasonLabel(d.lockReason)
  const gh = forum.repoUrl ? `${forum.repoUrl}/discussions/${d.number}` : null
  const answer = d.answer

  return (
    <div className="thread">
      <nav className="crumbs" aria-label="Breadcrumb">
        <a href={to('/forums')}>Forums</a>
        {category && (
          <>
            <span aria-hidden="true"> › </span>
            <a href={categoryHref(category.slug)}>{category.name}</a>
          </>
        )}
        <span aria-hidden="true"> › </span>
        <span aria-current="page">#{d.number}</span>
      </nav>

      <header className="thread-head">
        <h1>
          {d.title} <span className="thread-num">#{d.number}</span>
        </h1>
        <div className="thread-status">
          <StatusPills d={d} answerable={category?.answerable} />
          {d.pinned && <span className="pill pill-pin">Pinned</span>}
          {d.poll && <span className="pill">Poll</span>}
          {category && (
            <a className="chip" href={categoryHref(category.slug)}>
              <span aria-hidden="true">{category.emoji}</span> {category.name}
            </a>
          )}
        </div>
      </header>

      <div className="thread-grid">
        <div className="thread-post">
          {d.locked && (
            <p className="notice notice-locked" role="note">
              <strong>Locked{lock ? ` as ${lock}` : ''}.</strong> Only people with write access to the repository can still reply.
            </p>
          )}
          {d.closed && (
            <p className="notice" role="note">
              <strong>Closed</strong>
              {d.closedAt && <> <Time iso={d.closedAt} /></>}
              {d.stateReason === 'RESOLVED' && ' as resolved'}
              {d.stateReason === 'OUTDATED' && ' as outdated'}
              {d.stateReason === 'DUPLICATE' && ' as a duplicate'}.
            </p>
          )}

          <article className="post post-main" aria-label="The discussion">
            <header className="post-head">
              <Avatar person={d.author} size={40} />
              <div className="post-who">
                <Person person={d.author} association={d.association} />
                <span className="post-when">
                  started <Time iso={d.createdAt} />
                  {d.editedAt && (
                    <>
                      {' '}· edited <Time iso={d.editedAt} />
                      {d.editor && d.editor !== d.author?.login && <> by {d.editor}</>}
                    </>
                  )}
                </span>
              </div>
            </header>
            {d.body.trim() ? <Markdown forum={forum}>{d.body}</Markdown> : <p className="post-gone">No description.</p>}
            {d.poll && <Poll poll={d.poll} voteUrl={gh} />}
            <Reactions upvotes={d.upvotes} reactions={d.reactions} />
          </article>

          {d.answered && (
            <section className="answer-box" aria-label="Chosen answer">
              <h2>✓ Answered</h2>
              {answer ? (
                <>
                  <p className="answer-by">
                    <Person person={answer.author} association={answer.association} />
                    {d.answerChosenBy && <> · chosen by {d.answerChosenBy}</>}
                  </p>
                  <p className="answer-text">{excerpt(answer.body, 280) || 'See the answer in the comments.'}</p>
                  <a href={threadHref(d.number, commentAnchor(answer.id))}>Go to the answer</a>
                </>
              ) : (
                <p className="answer-text">An answer was chosen.</p>
              )}
            </section>
          )}
        </div>

        <Details d={d} forum={forum} category={category} />

        <section className="thread-comments comments" aria-labelledby="comments-title">
          <div className="comments-head">
            <h2 id="comments-title">{plural(d.commentCount, 'comment')}</h2>
            <div className="comments-tools">
              {latest && d.comments.length > 1 && (
                <a href={threadHref(d.number, commentAnchor(latest.id))}>Jump to latest</a>
              )}
              {d.comments.length > 1 && (
                <label className="select">
                  <span>Sort</span>
                  <select value={sort} onChange={(e) => setSort(e.target.value)}>
                    {COMMENT_SORTS.map((s) => (
                      <option key={s.id} value={s.id}>{s.label}</option>
                    ))}
                  </select>
                </label>
              )}
            </div>
          </div>

          {comments.length === 0 && <p className="empty">No comments yet.</p>}
          {comments.map((c) => (
            <Comment key={c.id} c={c} forum={forum} number={d.number} target={anchor} />
          ))}

          {gh && (
            <div className="join">
              {d.locked ? (
                <p>This discussion is locked. Only people with write access to the repository can reply.</p>
              ) : (
                <>
                  <p>
                    Replies are written on GitHub, with a free account, and show up here after the next copy.
                  </p>
                  <a className="btn" href={gh} target="_blank" rel="noopener noreferrer">
                    Reply on GitHub <ExternalIcon />
                  </a>
                </>
              )}
            </div>
          )}
        </section>
      </div>

      <SnapshotNote forum={forum} />
    </div>
  )
}
