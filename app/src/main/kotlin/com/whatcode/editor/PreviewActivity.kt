package com.whatcode.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.whatcode.ui.AppTheme
import com.whatcode.ui.Icon
import com.whatcode.ui.Icons

/**
 * Live Markdown / HTML preview using Android's [WebView]. JavaScript is disabled by default and controlled strictly
 * by Settings > Code Editor > Tabs (`preview_javascript`); Markdown is converted safely to HTML with inline scripts
 * stripped/escaped via [PreviewLogic].
 */
class PreviewActivity : Activity() {

    private lateinit var palette: AppTheme.Palette
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        palette = AppTheme.palette(this)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Preview"
        val sourceText = pendingText ?: intent.getStringExtra(EXTRA_TEXT).orEmpty()
        pendingText = null
        val fileName = intent.getStringExtra(EXTRA_NAME) ?: title
        val allowJs = intent.getBooleanExtra(EXTRA_ALLOW_JS, false)
        val isMarkdown = PreviewLogic.isMarkdown(fileName)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(palette.surface)
            setPadding(dp(4), dp(4), dp(12), dp(4))
        }
        topBar.addView(
            Icons.button(this, Icon.PREVIOUS, "Back", palette.text) { finish() },
            LinearLayout.LayoutParams(dp(42), dp(42)),
        )
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(
            TextView(this).apply {
                text = title
                setTextColor(palette.text)
                textSize = 15f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            },
        )
        val jsBadge = if (isMarkdown) "Markdown" else if (allowJs) "HTML (JS enabled)" else "HTML (JS disabled)"
        col.addView(
            TextView(this).apply {
                text = "$jsBadge  ·  Preview"
                setTextColor(palette.muted)
                textSize = 11f
            },
        )
        topBar.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this).apply { setBackgroundColor(palette.outline) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        webView = WebView(this).apply {
            setBackgroundColor(palette.editor)
            settings.javaScriptEnabled = !isMarkdown && allowJs
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url ?: return true
                    if (u.scheme == "http" || u.scheme == "https") {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, u))
                        } catch (_: Exception) {
                        }
                    }
                    return true
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val html = if (isMarkdown) {
            PreviewLogic.markdownToHtml(
                markdown = sourceText,
                title = title,
                bgHex = hexRgb(palette.editor),
                fgHex = hexRgb(palette.text),
                mutedHex = hexRgb(palette.muted),
                accentHex = hexRgb(palette.accent),
                codeBgHex = hexRgb(palette.surface),
            )
        } else {
            sourceText
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun hexRgb(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_ALLOW_JS = "allow_js"
        private var pendingText: String? = null

        fun launch(context: Context, fileName: String, content: String, allowJs: Boolean) {
            // Avoid TransactionTooLargeException on big Markdown/HTML buffers.
            pendingText = content
            context.startActivity(
                Intent(context, PreviewActivity::class.java)
                    .putExtra(EXTRA_TITLE, fileName)
                    .putExtra(EXTRA_NAME, fileName)
                    .putExtra(EXTRA_ALLOW_JS, allowJs),
            )
        }
    }
}
