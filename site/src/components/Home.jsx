import Downloads from './Downloads.jsx'
import PhoneMock from './PhoneMock.jsx'

export default function Home() {
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <h1>Edit code on your phone without hunting for brackets.</h1>
          <p className="lede">
            WhatCodeEditor opens and saves source files, highlights Kotlin, Python, JSON, HTML and more, indents as you
            type, and keeps <code>{'{ } ( ) ;'}</code> on a row above the keyboard. No account needed, and it asks before it updates itself.
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
          downloads anything. See <a href="#/docs/updating">Updating the app</a>. Switching between stable and nightly
          and checking a download are covered in the <a href="#/docs/getting-started">documentation</a>.
        </p>
      </section>
    </>
  )
}
