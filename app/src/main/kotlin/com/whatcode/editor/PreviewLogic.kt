package com.whatcode.editor

import java.util.Locale

/**
 * Pure Markdown-to-HTML converter and preview eligibility helpers. Escapes HTML entities in Markdown text and fenced
 * blocks safely so Markdown previews render cleanly without running inline scripts. Pure JVM (unit tested).
 */
object PreviewLogic {

    fun isMarkdown(fileName: String): Boolean {
        val low = fileName.lowercase(Locale.ROOT)
        return low.endsWith(".md") || low.endsWith(".markdown")
    }

    fun isHtml(fileName: String): Boolean {
        val low = fileName.lowercase(Locale.ROOT)
        return low.endsWith(".html") || low.endsWith(".htm") || low.endsWith(".svg")
    }

    fun isPreviewable(fileName: String): Boolean = isMarkdown(fileName) || isHtml(fileName)

    fun markdownToHtml(
        markdown: String,
        title: String = "Preview",
        bgHex: String = "#121212",
        fgHex: String = "#EAEAEA",
        mutedHex: String = "#9E9E9E",
        accentHex: String = "#4EA1FF",
        codeBgHex: String = "#1E1E1E",
    ): String {
        val body = renderBody(markdown)
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${escapeHtml(title)}</title>
<style>
  body { background: $bgHex; color: $fgHex; font-family: sans-serif; line-height: 1.55; margin: 16px; }
  h1, h2, h3, h4, h5, h6 { margin: 1.1em 0 0.4em; line-height: 1.25; }
  a { color: $accentHex; }
  code { background: $codeBgHex; padding: 2px 5px; border-radius: 4px; font-family: monospace; font-size: 0.92em; }
  pre { background: $codeBgHex; padding: 12px; border-radius: 8px; overflow-x: auto; }
  pre code { background: none; padding: 0; }
  blockquote { border-left: 3px solid $accentHex; margin: 10px 0; padding: 4px 12px; color: $mutedHex; }
  hr { border: none; border-top: 1px solid $mutedHex; opacity: 0.35; margin: 16px 0; }
  ul, ol { padding-left: 22px; }
  table { border-collapse: collapse; width: 100%; margin: 10px 0; }
  th, td { border: 1px solid $mutedHex; padding: 6px 10px; text-align: left; }
</style>
</head>
<body>
$body
</body>
</html>"""
    }

    private fun renderBody(md: String): String {
        val lines = md.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            // Fenced code block
            if (trimmed.startsWith("```")) {
                out.append("<pre><code>")
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    out.append(escapeHtml(lines[i])).append('\n')
                    i++
                }
                out.append("</code></pre>\n")
                i++
                continue
            }
            // Horizontal rule
            if (trimmed.matches(Regex("^([-*_])\\s*\\1\\s*\\1(?:\\s|\\1)*$"))) {
                out.append("<hr>\n")
                i++
                continue
            }
            // ATX heading
            val heading = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            if (heading != null) {
                val lvl = heading.groupValues[1].length
                out.append("<h$lvl>").append(inline(heading.groupValues[2].trim())).append("</h$lvl>\n")
                i++
                continue
            }
            // Blockquote
            if (trimmed.startsWith(">")) {
                out.append("<blockquote>")
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    out.append(inline(lines[i].trimStart().removePrefix(">").trimStart())).append("<br>")
                    i++
                }
                out.append("</blockquote>\n")
                continue
            }
            // Unordered list
            if (trimmed.matches(Regex("^[-*+]\\s+.+$"))) {
                out.append("<ul>\n")
                while (i < lines.size && lines[i].trimStart().matches(Regex("^[-*+]\\s+.+$"))) {
                    val item = lines[i].trimStart().substring(2)
                    out.append("  <li>").append(inline(item)).append("</li>\n")
                    i++
                }
                out.append("</ul>\n")
                continue
            }
            // Ordered list
            if (trimmed.matches(Regex("^\\d+\\.\\s+.+$"))) {
                out.append("<ol>\n")
                while (i < lines.size && lines[i].trimStart().matches(Regex("^\\d+\\.\\s+.+$"))) {
                    val item = lines[i].trimStart().substringAfter(". ")
                    out.append("  <li>").append(inline(item)).append("</li>\n")
                    i++
                }
                out.append("</ol>\n")
                continue
            }
            if (line.isBlank()) {
                i++
                continue
            }
            // Paragraph
            out.append("<p>").append(inline(line))
            i++
            while (i < lines.size && lines[i].isNotBlank() && !lines[i].trimStart().startsWith("#") &&
                !lines[i].trimStart().startsWith("```") && !lines[i].trimStart().startsWith(">")
            ) {
                out.append(' ').append(inline(lines[i]))
                i++
            }
            out.append("</p>\n")
        }
        return out.toString()
    }

    private fun inline(raw: String): String {
        var s = escapeHtml(raw)
        s = s.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*")) { "<strong>${it.groupValues[1]}</strong>" }
        s = s.replace(Regex("\\*([^*]+)\\*")) { "<em>${it.groupValues[1]}</em>" }
        s = s.replace(Regex("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)")) { m ->
            "<a href=\"${m.groupValues[2]}\">${m.groupValues[1]}</a>"
        }
        return s
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
