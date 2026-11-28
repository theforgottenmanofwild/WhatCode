import { useEffect, useMemo, useState } from 'react'
import ExplorerMock from './ExplorerMock.jsx'

// The pieces of the sample and their colors were made by the app's own highlighter
// (Highlighter.kt) reading main.js, so the preview shows what the editor really draws.
const SEGMENTS = [
  ['com', '// Count the vowels in a word'],
  ['', '\n'],
  ['kw', 'function'],
  ['', ' '],
  ['fn', 'vowels'],
  ['', '(word) {\n  '],
  ['kw', 'const'],
  ['', ' found = { '],
  ['prop', 'total'],
  ['', ': '],
  ['num', '0'],
  ['', ' };\n  '],
  ['ctl', 'for'],
  ['', ' ('],
  ['kw', 'const'],
  ['', ' c '],
  ['kw', 'of'],
  ['', ' word) {\n    '],
  ['ctl', 'if'],
  ['', ' ('],
  ['re', '/[aeiou]/i'],
  ['', '.'],
  ['fn', 'test'],
  ['', '(c)) {\n      found.total += '],
  ['num', '1'],
  ['', ';\n    }\n  }\n  '],
  ['ctl', 'return'],
  ['', ' '],
  ['kw', 'new'],
  ['', ' '],
  ['type', 'Result'],
  ['', '(found);\n}\n'],
]

const SOURCE = SEGMENTS.map(([, t]) => t).join('')
const LANGUAGE = 'JavaScript'

// The two rows of extra keys, as in the app (each row scrolls sideways there).
const KEY_ROWS = [
  ['SHFT', '\u21E5', '\u2315', '\u21B6', '\u21B7', '\u2191', '\u2913', 'ESC'],
  ['CTRL', 'ALT', '\u21CA', '\u21C8', '\u2190', '\u2193', '\u2192', '//'],
]

function classify() {
  const cls = []
  for (const [c, t] of SEGMENTS) for (let i = 0; i < t.length; i++) cls.push(c)
  return cls
}

function runs(text, cls, count) {
  const out = []
  let start = 0
  for (let i = 1; i <= count; i++) {
    if (i === count || cls[i] !== cls[start]) {
      out.push({ c: cls[start], t: text.slice(start, i) })
      start = i
    }
  }
  return out
}

// The two screens of the app. It starts on the file explorer, and so does the preview.
const VIEWS = [
  { key: 'explorer', title: 'File explorer', label: 'Preview of the file explorer listing a project folder' },
  { key: 'editor', title: 'Code editor', label: 'Preview of the editor with JavaScript code being typed' },
]

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

export default function PhoneMock({ animate = true, initialView = 'explorer' }) {
  const [view, setView] = useState(initialView)
  const editing = view === 'editor'
  const cls = useMemo(() => classify(), [])
  const [count, setCount] = useState(() => (animate && !prefersReducedMotion() ? 0 : SOURCE.length))

  useEffect(() => {
    // The code is typed only while the editor is showing.
    if (!editing || !animate || prefersReducedMotion()) return
    let timer
    const tick = () => {
      setCount((n) => {
        if (n >= SOURCE.length) return n
        timer = setTimeout(tick, SOURCE[n] === '\n' ? 220 : 42)
        return n + 1
      })
    }
    timer = setTimeout(tick, 700)
    return () => clearTimeout(timer)
  }, [animate, editing])

  const typed = SOURCE.slice(0, count)
  const line = typed.split('\n').length
  const col = count - typed.lastIndexOf('\n')

  return (
    <div className="phone-preview">
      <figure className="phone" role="img" aria-label={VIEWS.find((v) => v.key === view).label}>
        <div className="phone-screen" aria-hidden="true">
          {editing ? (
            <>
              <div className="ed-bar">
                <span className="ed-burger">{'\u2630'}</span>
                <span className="ed-title">{'● main.js'}</span>
                <span className="ed-menu">{'\u22EE'}</span>
              </div>
              <pre className="ed-text">
                {runs(typed, cls, count).map((r, i) =>
                  r.c ? <span className={`tok-${r.c}`} key={i}>{r.t}</span> : r.t,
                )}
                <span className="ed-caret" />
              </pre>
              <div className="ed-keys">
                {KEY_ROWS.map((row, r) => (
                  <div className="ed-symbols" key={r}>
                    {row.map((k) => (
                      <span className="ed-key" key={k}>{k}</span>
                    ))}
                  </div>
                ))}
              </div>
              <div className="ed-status">{`Ln ${line}, Col ${col}  ·  ${count} chars  ·  ${LANGUAGE}`}</div>
            </>
          ) : (
            <ExplorerMock />
          )}
        </div>
      </figure>
      <div className="phone-tabs" role="group" aria-label="Screen shown in the preview">
        {VIEWS.map((v) => (
          <button type="button" key={v.key} aria-pressed={view === v.key} onClick={() => setView(v.key)}>
            {v.title}
          </button>
        ))}
      </div>
    </div>
  )
}
