import { useMemo } from 'react'
import ReactMarkdown from 'react-markdown'
import rehypeRaw from 'rehype-raw'
import rehypeSanitize from 'rehype-sanitize'
import remarkGfm from 'remark-gfm'
import { resolveRepoImage, resolveRepoLink } from '../source.js'

// A Markdown file in the repository, shown as GitHub shows it. HTML inside it is cleaned with GitHub's own allow-list.
// Links to other files in the repository stay in the site; images come from this copy of the files.
const rehypePlugins = [rehypeRaw, rehypeSanitize]
const remarkRehypeOptions = { allowDangerousHtml: true }

const plain = (c) =>
  typeof c === 'string' ? c : Array.isArray(c) ? c.map(plain).join('') : c?.props?.children != null ? plain(c.props.children) : ''

/** "Using the editor!" -> "using-the-editor", as GitHub names headings. */
export const slugify = (text) =>
  String(text)
    .toLowerCase()
    .trim()
    .replace(/[^\p{L}\p{N}\s_-]/gu, '')
    .replace(/\s/g, '-')

// A link like #usage would be read as a page route. Scroll to the heading instead.
function jump(e, href) {
  e.preventDefault()
  let id = href.slice(1)
  try {
    id = decodeURIComponent(id)
  } catch {
    // keep as written
  }
  const target = document.getElementById(id) ?? document.getElementById(`user-content-${id}`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function makeComponents(source, dir) {
  const heading = (Tag) =>
    function Heading({ node, children, ...rest }) {
      return (
        <Tag {...rest} id={slugify(plain(children)) || undefined}>
          {children}
        </Tag>
      )
    }
  return {
    h1: heading('h1'),
    h2: heading('h2'),
    h3: heading('h3'),
    h4: heading('h4'),
    h5: heading('h5'),
    h6: heading('h6'),
    a({ node, href = '', children, ...rest }) {
      const r = resolveRepoLink(source, href, dir)
      if (r.kind === 'anchor') {
        return (
          <a {...rest} href={r.href} onClick={(e) => jump(e, r.href)}>
            {children}
          </a>
        )
      }
      if (r.kind === 'file' || r.kind === 'tree') return <a {...rest} href={r.href}>{children}</a>
      if (r.kind === 'external' && /^(https?:|mailto:)/i.test(r.href)) {
        return (
          <a {...rest} href={r.href} target="_blank" rel="noopener noreferrer nofollow ugc">
            {children}
          </a>
        )
      }
      return <span>{children}</span>
    },
    img({ node, src, alt, ...rest }) {
      return <img {...rest} src={resolveRepoImage(source, src, dir)} alt={alt ?? ''} loading="lazy" decoding="async" referrerPolicy="no-referrer" />
    },
    table({ node, children }) {
      return (
        <div className="table-wrap" tabIndex={0} role="region" aria-label="Table, scrolls sideways">
          <table>{children}</table>
        </div>
      )
    },
  }
}

/** `dir` is the folder the file is in, so that "../docs/x.md" means the right file. */
export default function SourceMarkdown({ source, dir = '', children }) {
  const components = useMemo(() => makeComponents(source, dir), [source, dir])
  return (
    <div className="fmd src-md">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={rehypePlugins}
        remarkRehypeOptions={remarkRehypeOptions}
        components={components}
      >
        {String(children ?? '').replace(/\r\n?/g, '\n')}
      </ReactMarkdown>
    </div>
  )
}
