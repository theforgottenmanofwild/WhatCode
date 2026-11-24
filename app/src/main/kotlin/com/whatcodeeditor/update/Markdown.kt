package com.whatcodeeditor.update

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import java.util.regex.Pattern

/**
 * A small Markdown renderer for GitHub release notes, with no libraries.
 *
 * Handles what release notes use: headings, bullet and numbered lists, quotes, code blocks,
 * **bold**, `code` and [links](https://example.com). Bare web addresses, @mentions and #123
 * references become links too. Only http and https links are made clickable.
 *
 * Show the result in a TextView with a LinkMovementMethod so the links can be tapped.
 */
object Markdown {

    private const val EXCL = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d+[.)])\\s+(.*)$")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val URL = Pattern.compile("https?://[^\\s<>()\\[\\]]+")
    private val MENTION = Pattern.compile("@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))")
    private val ISSUE = Pattern.compile("#(\\d+)")
    private val GITHUB_REF = Regex("^https://github\\.com/[^/]+/[^/]+/(?:pull|issues)/(\\d+)$")
    private val GITHUB_COMMIT = Regex("^https://github\\.com/[^/]+/[^/]+/commit/([0-9a-fA-F]{7,40})$")

    private class Ctx(val repo: String?, val accent: Int, val codeBg: Int, val density: Float) {
        fun dp(v: Int): Int = (v * density).toInt()
    }

    fun render(source: String, repo: String?, accent: Int, codeBg: Int, density: Float): SpannableStringBuilder {
        val ctx = Ctx(repo, accent, codeBg, density)
        val out = SpannableStringBuilder()
        val lines = source.replace("\r\n", "\n").replace(COMMENT, "").lines()

        var inCode = false
        var lastWasList = false
        var pendingGap = false

        // Starts a new block on its own line, with a small gap after the previous one unless both are list items.
        fun startBlock(isList: Boolean, bigGap: Boolean = false) {
            if (out.isEmpty()) return
            out.append('\n')
            if (bigGap || pendingGap || (!isList && !lastWasList) || (isList != lastWasList)) {
                val at = out.length
                out.append('\n')
                out.setSpan(RelativeSizeSpan(if (bigGap) 0.7f else 0.45f), at, out.length, EXCL)
            }
            pendingGap = false
        }

        for (raw in lines) {
            val line = raw.trimEnd()

            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                if (inCode) {
                    startBlock(isList = false)
                    lastWasList = false
                }
                continue
            }
            if (inCode) {
                if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
                val at = out.length
                out.append(line)
                out.setSpan(TypefaceSpan("monospace"), at, out.length, EXCL)
                out.setSpan(BackgroundColorSpan(ctx.codeBg), at, out.length, EXCL)
                out.setSpan(RelativeSizeSpan(0.9f), at, out.length, EXCL)
                continue
            }

            if (line.isBlank()) {
                pendingGap = true
                continue
            }
            if (RULE.matches(line)) {
                pendingGap = true
                continue
            }

            val heading = HEADING.matchEntire(line)
            val bullet = BULLET.matchEntire(line)
            val numbered = NUMBERED.matchEntire(line)
            when {
                heading != null -> {
                    startBlock(isList = false, bigGap = out.isNotEmpty())
                    val level = heading.groupValues[1].length
                    val at = out.length
                    inline(out, heading.groupValues[2], ctx, links = true)
                    out.setSpan(StyleSpan(Typeface.BOLD), at, out.length, EXCL)
                    out.setSpan(RelativeSizeSpan(if (level == 1) 1.4f else if (level == 2) 1.25f else 1.1f), at, out.length, EXCL)
                    if (level <= 2) out.setSpan(ForegroundColorSpan(ctx.accent), at, out.length, EXCL)
                    lastWasList = false
                }

                bullet != null || numbered != null -> {
                    startBlock(isList = true)
                    val indent = (bullet?.groupValues?.get(1) ?: numbered!!.groupValues[1]).length / 2
                    val marker = if (bullet != null) "\u2022" else numbered!!.groupValues[2]
                    val text = bullet?.groupValues?.get(2) ?: numbered!!.groupValues[3]
                    val first = ctx.dp(6) + indent * ctx.dp(18)
                    val rest = first + ctx.dp(if (bullet != null) 16 else 24)
                    val at = out.length
                    out.append(marker).append("\u00A0\u00A0")
                    inline(out, text, ctx, links = true)
                    out.setSpan(LeadingMarginSpan.Standard(first, rest), at, out.length, EXCL)
                    lastWasList = true
                }

                line.trimStart().startsWith(">") -> {
                    startBlock(isList = false)
                    val at = out.length
                    inline(out, line.trimStart().removePrefix(">").trim(), ctx, links = true)
                    out.setSpan(StyleSpan(Typeface.ITALIC), at, out.length, EXCL)
                    out.setSpan(LeadingMarginSpan.Standard(ctx.dp(10)), at, out.length, EXCL)
                    lastWasList = false
                }

                else -> {
                    startBlock(isList = false)
                    inline(out, line.trim(), ctx, links = true)
                    lastWasList = false
                }
            }
        }
        return out
    }

    private fun isWeb(url: String) = url.startsWith("https://") || url.startsWith("http://")

    /** The text shown for a bare address: pull requests and issues as #12, commits as their short hash. */
    private fun shorten(url: String): String {
        GITHUB_REF.matchEntire(url)?.let { return "#" + it.groupValues[1] }
        GITHUB_COMMIT.matchEntire(url)?.let { return it.groupValues[1].take(7) }
        return url
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun inline(out: SpannableStringBuilder, s: String, ctx: Ctx, links: Boolean) {
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            val prev = if (i > 0) s[i - 1] else ' '
            when {
                c == '\\' && i + 1 < n && "\\`*_[]()#@<>".indexOf(s[i + 1]) >= 0 -> {
                    out.append(s[i + 1])
                    i += 2
                }

                c == '`' -> {
                    val end = s.indexOf('`', i + 1)
                    if (end > i) {
                        val at = out.length
                        out.append(s, i + 1, end)
                        out.setSpan(TypefaceSpan("monospace"), at, out.length, EXCL)
                        out.setSpan(BackgroundColorSpan(ctx.codeBg), at, out.length, EXCL)
                        i = end + 1
                    } else {
                        out.append(c)
                        i++
                    }
                }

                c == '*' && s.startsWith("**", i) -> {
                    val end = s.indexOf("**", i + 2)
                    if (end > i + 2) {
                        val at = out.length
                        inline(out, s.substring(i + 2, end), ctx, links)
                        out.setSpan(StyleSpan(Typeface.BOLD), at, out.length, EXCL)
                        i = end + 2
                    } else {
                        out.append(c)
                        i++
                    }
                }

                c == '[' && links -> {
                    val mid = s.indexOf("](", i)
                    val close = if (mid > i) s.indexOf(')', mid + 2) else -1
                    if (mid > i && close > mid) {
                        val url = s.substring(mid + 2, close).trim()
                        val at = out.length
                        inline(out, s.substring(i + 1, mid), ctx, links = false)
                        if (isWeb(url)) out.setSpan(URLSpan(url), at, out.length, EXCL)
                        i = close + 1
                    } else {
                        out.append(c)
                        i++
                    }
                }

                links && (c == 'h') && !isWordChar(prev) && (s.startsWith("http://", i) || s.startsWith("https://", i)) -> {
                    val m = URL.matcher(s)
                    m.region(i, n)
                    if (m.lookingAt()) {
                        var url = m.group()
                        while (url.isNotEmpty() && ".,;:!?'\"".indexOf(url.last()) >= 0) url = url.dropLast(1)
                        val at = out.length
                        out.append(shorten(url))
                        out.setSpan(URLSpan(url), at, out.length, EXCL)
                        i += url.length
                    } else {
                        out.append(c)
                        i++
                    }
                }

                links && c == '@' && !isWordChar(prev) && prev != '/' -> {
                    val m = MENTION.matcher(s)
                    m.region(i, n)
                    if (m.lookingAt()) {
                        val at = out.length
                        out.append(m.group())
                        out.setSpan(URLSpan("https://github.com/" + m.group(1)), at, out.length, EXCL)
                        i = m.end()
                    } else {
                        out.append(c)
                        i++
                    }
                }

                links && c == '#' && ctx.repo != null && (i == 0 || prev == ' ' || prev == '(') -> {
                    val m = ISSUE.matcher(s)
                    m.region(i, n)
                    if (m.lookingAt()) {
                        val at = out.length
                        out.append(m.group())
                        out.setSpan(URLSpan("https://github.com/${ctx.repo}/issues/${m.group(1)}"), at, out.length, EXCL)
                        i = m.end()
                    } else {
                        out.append(c)
                        i++
                    }
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
    }
}
