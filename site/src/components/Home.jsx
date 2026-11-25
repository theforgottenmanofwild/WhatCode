import { to } from '../base.js'
import { sections } from '../docs.js'
import Downloads from './Downloads.jsx'
import PhoneMock from './PhoneMock.jsx'

// A few pages from each documentation section, so the docs are one tap from the front page.
function DocsTeaser() {
  return (
    <section className="teaser" aria-labelledby="teaser-title">
      <div className="teaser-head">
        <h2 id="teaser-title">Learn the editor</h2>
        <a href={to('/docs')}>All documentation</a>
      </div>
      <div className="teaser-grid">
        {sections.map((s) => (
          <div className="teaser-card" key={s.key}>
            <h3>{s.title}</h3>
            <ul>
              {s.docs.slice(0, 3).map((d) => (
                <li key={d.slug}>
                  <a href={to(`/docs/${d.slug}`)}>{d.title}</a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <h1>Edit code on your phone without hunting for brackets.</h1>
          <p className="lede">
            WhatCode opens and saves source files, highlights more than 30 languages and file types, from JavaScript, Kotlin and Python to HTML, CSS and JSON, indents as you
            type, and keeps <code>{'{ } ( ) ;'}</code>, undo, find and replace and save on scrollable rows above the keyboard. No account needed, and it asks before it updates itself.
          </p>
          <Downloads />
        </div>
        <PhoneMock />
      </section>

      <section className="install" aria-labelledby="install-title">
        <h2 id="install-title">Install on Android</h2>
        <ol>
          <li>Download the APK on your phone. It needs Android 8.0 or newer.</li>
          <li>Open the file from your browser's downloads or your Files app.</li>
          <li>If asked, allow that app to install unknown apps.</li>
          <li>Tap Install, then Open.</li>
        </ol>
        <p>
          After that the app keeps itself current: it checks for a newer build of its own channel and asks before it
          downloads anything. See <a href={to('/docs/updating')}>Updating the app</a>. Switching between stable and nightly
          and checking a download are covered in the <a href={to('/docs/getting-started')}>documentation</a>.
        </p>
      </section>

      <DocsTeaser />
    </>
  )
}
