import { useEffect, useMemo, useState } from 'react'

// Colors follow the app's real highlighter: only keywords, strings, comments
// and numbers are colored; type names such as String and Int are not.
const SOURCE = `// Count the vowels in a word
fun vowels(word: String): Int {
    var n = 0
    for (c in word) {
        if (c in "aeiou") n += 1
    }
    return n
}
`

const KEYS = ['Tab', '{', '}', '(', ')', '[', ']', ';', ':', '"', "'", '<', '>', '/']

function classify(src) {
  const cls = new Array(src.length).fill('')
  const re = /(\/\/[^\n]*)|("(?:\\.|[^"\\\n])*")|\b(fun|var|val|for|in|if|return)\b|\b(\d+)\b/g
  let m
  while ((m = re.exec(src))) {
    const c = m[1] ? 'com' : m[2] ? 'str' : m[3] ? 'kw' : 'num'
    for (let i = m.index; i < m.index + m[0].length; i++) cls[i] = c
  }
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

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

export default function PhoneMock({ animate = true }) {
  const cls = useMemo(() => classify(SOURCE), [])
  const [count, setCount] = useState(() => (animate && !prefersReducedMotion() ? 0 : SOURCE.length))

  useEffect(() => {
    if (!animate || prefersReducedMotion()) return
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
  }, [animate])

  const typed = SOURCE.slice(0, count)
  const line = typed.split('\n').length
  const col = count - typed.lastIndexOf('\n')

  return (
    <figure className="phone" role="img" aria-label="Preview of the editor with Kotlin code being typed">
      <div className="phone-screen" aria-hidden="true">
        <div className="ed-bar">
          <span className="ed-title">{'● Main.kt'}</span>
          <span className="ed-menu">{'\u22EE'}</span>
        </div>
        <pre className="ed-text">
          {runs(typed, cls, count).map((r, i) =>
            r.c ? <span className={`tok-${r.c}`} key={i}>{r.t}</span> : r.t,
          )}
          <span className="ed-caret" />
        </pre>
        <div className="ed-symbols">
          {KEYS.map((k) => (
            <span className="ed-key" key={k}>{k}</span>
          ))}
        </div>
        <div className="ed-status">{`Ln ${line}, Col ${col}  ·  ${count} chars  ·  C-like`}</div>
      </div>
    </figure>
  )
}
