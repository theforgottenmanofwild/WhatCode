// The file explorer as the app draws it in the dark blue theme: the same rows, file icons and + button
// (FileExplorer.kt, ExplorerRows.kt and FileIcons.kt). The folder shown is a made-up sample.

// Folders first, then files by name, as the app sorts them. A file has a size; `color` and `label` are its icon.
const ENTRIES = [
  { name: '..', dir: true, up: true },
  { name: 'assets', dir: true },
  { name: 'src', dir: true },
  { name: '.gitignore', size: '48 B', color: '#a1adbb' },
  { name: 'index.html', size: '1.2 KB', color: '#e44d26', label: '<>' },
  { name: 'main.js', size: '204 B', color: '#f7df1e', label: 'JS' },
  { name: 'README.md', size: '640 B', color: '#9cdcfe', label: 'MD' },
  { name: 'style.css', size: '2.4 KB', color: '#42a5f5', label: '#' },
]
const DATE = 'Nov 28, 2026'

function FolderIcon() {
  return (
    <svg className="ex-icon ex-folder" viewBox="0 0 24 24" focusable="false">
      <path d="M3 6h6.5l2 2.5H21V19H3z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    </svg>
  )
}

function FileIcon({ color, label }) {
  return (
    <svg className="ex-icon" viewBox="0 0 24 24" focusable="false" fill="none" stroke={color} strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 3h8l5 5v13H6z" fill={color} fillOpacity="0.16" strokeWidth="1.6" />
      <path d="M14 3v5h5" strokeWidth="1.6" />
      {label ? (
        <text x="12.5" y="17.3" textAnchor="middle" fontSize="7" fontWeight="700" fill={color} stroke="none">
          {label}
        </text>
      ) : (
        <path d="M9 12h7M9 15h7M9 18h4" strokeWidth="1.4" />
      )}
    </svg>
  )
}

export default function ExplorerMock() {
  return (
    <>
      <div className="ex-bar">
        <span className="ed-burger">{'\u2630'}</span>
        <span>Phone storage</span>
        <span className="ex-sep">{'\u203A'}</span>
        <span className="ex-here">Projects</span>
      </div>
      <div className="ex-list">
        {ENTRIES.map((e) => (
          <div className="ex-row" key={e.name}>
            {e.dir ? <FolderIcon /> : <FileIcon color={e.color} label={e.label} />}
            <span className="ex-text">
              <span>{e.name}</span>
              {!e.up && <span className="ex-detail">{e.dir ? DATE : `${e.size}  \u00B7  ${DATE}`}</span>}
            </span>
            {!e.up && <span className="ex-more">{'\u22EE'}</span>}
          </div>
        ))}
        <span className="ex-add">+</span>
      </div>
    </>
  )
}
