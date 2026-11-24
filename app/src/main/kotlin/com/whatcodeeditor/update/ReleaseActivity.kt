package com.whatcodeeditor.update

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.whatcodeeditor.ui.AppTheme

/**
 * A full-screen page about a release, in two modes:
 *
 *  - [MODE_UPDATE]: a newer build was found (or is downloaded). Shows the release notes and the
 *    Download / Install buttons. "Later" simply closes it; it appears again next time the app opens.
 *  - [MODE_ABOUT]: opened from Settings. Shows what's new in the installed version, plus links to the
 *    project. A link is shown only if it answers, so nothing leads nowhere.
 *
 * The release notes are the description written on the GitHub release, which the stable release
 * workflow takes from RELEASE_NOTES.md.
 */
class ReleaseActivity : Activity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_UPDATE = "update"
        const val MODE_ABOUT = "about"
        const val DISCORD_URL = "https://discord.gg/bJxwDj492x"
    }

    private val palette by lazy { AppTheme.palette(this) }
    private val colorBg: Int get() = palette.background
    private val colorBar: Int get() = palette.surface
    private val colorKey: Int get() = palette.key
    private val colorText: Int get() = palette.text
    private val colorMuted: Int get() = palette.muted
    private val colorAccent: Int get() = palette.accent
    private val colorCode: Int get() = palette.code
    private val colorStatus: Int get() = palette.status

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var updater: Updater
    private lateinit var updates: UpdateController
    private var mode = MODE_UPDATE

    private lateinit var barTitle: TextView
    private lateinit var heading: TextView
    private lateinit var meta: TextView
    private lateinit var notesTitle: TextView
    private lateinit var notesView: TextView
    private lateinit var linksTitle: TextView
    private lateinit var linksRow: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var progressLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var buttons: LinearLayout

    private var downloading = false

    @Volatile
    private var cancelled = false

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        AppTheme.applyWindow(this)
        mode = if (intent?.getStringExtra(EXTRA_MODE) == MODE_ABOUT) MODE_ABOUT else MODE_UPDATE
        updater = Updater(applicationContext)
        updates = UpdateController(this) { render() }
        buildUi()
        if (mode == MODE_ABOUT) loadAbout()
        render()
    }

    override fun onResume() {
        super.onResume()
        updates.refresh()
    }

    override fun onDestroy() {
        // A download belongs to this screen: leaving it stops the download.
        cancelled = true
        super.onDestroy()
    }

    private fun alive(): Boolean = !isFinishing && !isDestroyed

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------------------------------------------------------------------- UI

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) palette.onAccent else colorText)
        AppTheme.button(this@ReleaseActivity, this, palette, primary)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(
                    insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                )
            }
            insets
        }

        // Top bar: back arrow and the page title.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(4), dp(4), dp(10), dp(4))
        }
        val back = button("\u2039") { finish() }.apply {
            textSize = 26f
            contentDescription = "Back"
            setBackgroundColor(colorBar)
            setPadding(0, 0, 0, 0)
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(44), dp(40)))
        barTitle = TextView(this).apply {
            text = if (mode == MODE_ABOUT) "What's new" else "Update available"
            setTextColor(colorText)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        }
        bar.addView(barTitle)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Scrolling content: version, release notes, links.
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        heading = TextView(this).apply {
            setTextColor(colorText)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(heading)
        meta = TextView(this).apply {
            setTextColor(colorMuted)
            textSize = 14f
            setPadding(0, dp(4), 0, 0)
        }
        content.addView(meta)

        notesTitle = sectionTitle("WHAT'S NEW")
        content.addView(notesTitle)
        notesView = TextView(this).apply {
            setTextColor(colorText)
            textSize = 16f
            setLineSpacing(0f, 1.15f)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(colorAccent)
        }
        content.addView(notesView)

        linksTitle = sectionTitle("LINKS").apply { visibility = View.GONE }
        content.addView(linksTitle)
        linksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        content.addView(linksRow)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(content, AppTheme.scrollContentParams(this))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom panel: download progress and the action buttons.
        bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBar)
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }
        progressLabel = TextView(this).apply {
            setTextColor(colorText)
            textSize = 13f
            visibility = View.GONE
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        bottom.addView(progressLabel)
        bottom.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bottom.addView(buttons)
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (mode == MODE_ABOUT) bottom.visibility = View.GONE

        setContentView(root)
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(colorAccent)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(28), 0, dp(10))
    }

    private fun showNotes(markdown: String) {
        notesView.text = Markdown.render(markdown, updater.repo, colorAccent, colorCode, resources.displayMetrics.density)
    }

    private fun addButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
        buttons.addView(
            button(label, primary, onClick),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(dp(8), 0, 0, 0) }
        )
    }

    // ------------------------------------------------------------ update mode

    /** Redraws the page for the current state. */
    private fun render() {
        if (!::buttons.isInitialized) return
        if (mode == MODE_ABOUT) {
            heading.text = "WhatCodeEditor"
            meta.text = "Version ${updater.installedName} \u00B7 ${updater.channel.label} channel"
            return
        }

        val p = updater.pending()
        val a = updater.available()
        // A newer build than the downloaded one takes priority; otherwise the downloaded file is installed.
        val offer = a != null && a.versionName != p?.versionName
        buttons.removeAllViews()

        when {
            downloading -> {
                // The version being downloaded stays on screen; only the buttons change.
                addButton("Cancel") { cancelled = true }
            }

            offer -> {
                heading.text = a!!.versionName
                meta.text = "${a.channel.label.replaceFirstChar { it.uppercase() }} \u00B7 ${updates.mb(a.size)} \u00B7 you have ${updater.installedName}" +
                    (p?.let { "\nThis replaces the downloaded ${it.versionName}." } ?: "")
                showNotes(a.notes.ifBlank { "No release notes were published for this version." })
                addButton("Later") { finish() }
                addButton("Download", primary = true) { startDownload(a) }
            }

            p != null -> {
                heading.text = p.versionName
                meta.text = "${p.channel.label.replaceFirstChar { it.uppercase() }} \u00B7 downloaded (${updates.mb(p.file.length())}) \u00B7 you have ${updater.installedName}"
                showNotes(p.notes.ifBlank { "No release notes were published for this version." })
                updater.lastResult?.let { meta.text = "${meta.text}\n$it" }
                addButton("Delete") { updates.confirmDelete() }
                addButton("Later") { finish() }
                addButton("Install", primary = true) { updates.install() }
            }

            else -> finish() // nothing left to offer (installed, or deleted)
        }
        progressLabel.visibility = if (downloading) View.VISIBLE else View.GONE
        progressBar.visibility = if (downloading) View.VISIBLE else View.GONE
    }

    private fun startDownload(r: Release) {
        downloading = true
        cancelled = false
        progressLabel.text = "Starting\u2026"
        progressBar.isIndeterminate = true
        render()
        Thread {
            var error: String? = null
            try {
                updater.download(r, { cancelled }) { done, total ->
                    ui.post {
                        if (!alive()) return@post
                        if (total > 0) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = (done * 100 / total).toInt()
                        }
                        progressLabel.text = "Downloading ${r.versionName}: ${updates.mb(done)} of ${updates.mb(total)}"
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            ui.post {
                downloading = false
                if (!alive()) return@post
                when {
                    cancelled -> toast("Download cancelled.")
                    error != null -> toast("Download failed: $error")
                    else -> updater.setAvailable(null) // now it is a downloaded file waiting to be installed
                }
                render()
                if (!cancelled && error == null) updates.refresh()
            }
        }.start()
    }

    // -------------------------------------------------------------- about mode

    private fun loadAbout() {
        notesView.text = "Loading\u2026"
        notesView.setTextColor(colorMuted)

        Thread {
            var info: ReleaseInfo? = null
            var error: String? = null
            try {
                info = updater.fetchInstalledInfo()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            ui.post {
                if (!alive()) return@post
                notesView.setTextColor(colorText)
                when {
                    error != null -> notesView.text = "Couldn't load the release notes: $error"
                    info == null || info.notes.isBlank() -> notesView.text = "No release notes are published for this version."
                    else -> showNotes(info.notes)
                }
            }
        }.start()

        // Only links that answer are shown, so none of them leads nowhere.
        Thread {
            val found = mutableListOf<Pair<String, String>>()
            updater.repoUrl?.let { base ->
                if (updater.reachable(base)) {
                    found += "GitHub" to base
                    found += "Releases" to "$base/releases"
                }
            }
            updater.websiteUrl?.let { site ->
                if (updater.reachable(site)) {
                    found += "Website" to site
                    found += "Docs" to "${site}docs"
                }
            }
            // The community server is always offered; it doesn't depend on the repository being reachable.
            found += "Discord" to DISCORD_URL
            ui.post { if (alive()) showLinks(found) }
        }.start()
    }

    private fun showLinks(links: List<Pair<String, String>>) {
        if (links.isEmpty()) return
        linksTitle.visibility = View.VISIBLE
        linksRow.visibility = View.VISIBLE
        linksRow.removeAllViews()
        for ((label, url) in links) {
            linksRow.addView(
                button(label) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        toast("No app can open $url")
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(0, 0, dp(8), 0) }
            )
        }
    }
}
