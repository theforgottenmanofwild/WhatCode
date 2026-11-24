// The syntax highlighter for the file viewer. It is a separate file so that people who never open a file never
// download it (see loadSyntax in useSource.js). Only the languages this project's files use are included.
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import diff from 'highlight.js/lib/languages/diff'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import dos from 'highlight.js/lib/languages/dos'
import gradle from 'highlight.js/lib/languages/gradle'
import ini from 'highlight.js/lib/languages/ini'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import makefile from 'highlight.js/lib/languages/makefile'
import markdown from 'highlight.js/lib/languages/markdown'
import plaintext from 'highlight.js/lib/languages/plaintext'
import properties from 'highlight.js/lib/languages/properties'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import { splitHighlighted } from './source.js'

const LANGUAGES = {
  bash, css, diff, dockerfile, dos, gradle, ini, java, javascript, json, kotlin, makefile,
  markdown, plaintext, properties, python, sql, typescript, xml, yaml,
}
for (const [name, def] of Object.entries(LANGUAGES)) hljs.registerLanguage(name, def)

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** One piece of safe HTML per line of `code`. A language it does not know is shown plain. */
export function highlightLines(code, language) {
  let html
  try {
    html = language && hljs.getLanguage(language) ? hljs.highlight(code, { language, ignoreIllegals: true }).value : escapeHtml(code)
  } catch {
    html = escapeHtml(code)
  }
  return splitHighlighted(html)
}
