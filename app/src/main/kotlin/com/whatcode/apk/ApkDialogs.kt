package com.whatcode.apk

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.whatcode.settings.ApkSettings
import com.whatcode.ui.AppTheme
import com.whatcode.ui.BusyDialog
import com.whatcode.update.ApkInstallBus
import com.whatcode.update.InstallResultReceiver
import com.whatcode.update.UpdateController
import com.whatcode.update.Updater
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dialogs and actions for `.apk` files in the file explorer: tap sheet (C1), detailed info and comparison (C2, C3),
 * and install flow through [Updater] / [InstallResultReceiver] (C4).
 */
object ApkDialogs {

    private val ui = Handler(Looper.getMainLooper())

    private fun alive(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun dp(activity: Activity, v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity.applicationContext, msg, Toast.LENGTH_LONG).show()

    /** Handles a tap on [file] according to [ApkSettings.tapAction]. */
    fun onTap(
        activity: Activity,
        file: File,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        when (settings.tapAction) {
            ApkSettings.TapAction.ASK -> showTapSheet(activity, file, settings, palette, font, onShare, onRefresh)
            ApkSettings.TapAction.INFO -> showInfo(activity, file, settings, palette, font, onShare, onRefresh)
            ApkSettings.TapAction.INSTALL -> startInstall(activity, file, settings, palette, font, onShare, onRefresh)
        }
    }

    /** C1: Sheet offering Info, Install and Share for a tapped `.apk` file. */
    fun showTapSheet(
        activity: Activity,
        file: File,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        val cached = ApkRowCache.peek(file.path, file.lastModified(), file.length())
        val title = cached?.label?.let { "$it (${file.name})" } ?: file.name
        AppTheme.dialogBuilder(activity)
            .setTitle(title)
            .setItems(arrayOf("APK info", "Install", "Share")) { _, which ->
                when (which) {
                    0 -> showInfo(activity, file, settings, palette, font, onShare, onRefresh)
                    1 -> startInstall(activity, file, settings, palette, font, onShare, onRefresh)
                    else -> onShare(file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    /** C2 + C3: Reads the APK off the main thread and opens its detailed info, comparison and warnings. */
    fun showInfo(
        activity: Activity,
        file: File,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        loadReport(activity, file, needHashes = true, settings = settings) { report ->
            if (report == null) {
                toast(activity, "Could not read ${file.name}. The file may be damaged or not a valid APK.")
                return@loadReport
            }
            showReportDialog(activity, file, report, settings, palette, font, onShare, onRefresh)
        }
    }

    /** C3 + C4: Checks package comparison / confirmation, requests install permission if needed, and starts PackageInstaller. */
    fun startInstall(
        activity: Activity,
        file: File,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        loadReport(activity, file, needHashes = false, settings = settings) { report ->
            if (report == null) {
                toast(activity, "Could not read ${file.name}. The file may be damaged or not a valid APK.")
                return@loadReport
            }
            val warnDown = settings.warnDowngrade && report.compare.relation == ApkCompare.Relation.DOWNGRADE
            val warnSig = settings.warnSignature && report.compare.signatureDiffers
            if (settings.confirmInstall || warnDown || warnSig) {
                confirmInstallDialog(activity, file, report, settings, palette, font, onShare, onRefresh)
            } else {
                launchInstaller(activity, file, report, settings, onRefresh)
            }
        }
    }

    private fun loadReport(
        activity: Activity,
        file: File,
        needHashes: Boolean,
        settings: ApkSettings,
        onDone: (ApkInspector.Report?) -> Unit,
    ) {
        val cancelled = AtomicBoolean(false)
        val busy = BusyDialog(activity, ui, "Reading ${file.name}…") { cancelled.set(true) }
        busy.start()
        Thread {
            val report = ApkInspector.inspect(
                context = activity.applicationContext,
                file = file,
                needSha256 = needHashes && settings.showSha256,
                needMd5 = needHashes && settings.showMd5,
                cancelled = { cancelled.get() },
            )
            ui.post {
                busy.end()
                if (alive(activity) && !cancelled.get()) onDone(report)
            }
        }.start()
    }

    private fun showReportDialog(
        activity: Activity,
        file: File,
        r: ApkInspector.Report,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 8))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r.icon?.let { drawable ->
            val iconSize = dp(activity, 44)
            header.addView(
                ImageView(activity).apply { setImageDrawable(drawable) },
                LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(activity, 12) }
            )
        }
        val titleCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(
            TextView(activity).apply {
                text = r.appName
                setTextColor(palette.text)
                textSize = 16f
                typeface = Typeface.create(font, Typeface.BOLD)
            }
        )
        titleCol.addView(
            TextView(activity).apply {
                text = "${r.packageName}  ·  ${r.versionName ?: "?"} (${r.versionCode})"
                setTextColor(palette.muted)
                textSize = 12f
            }
        )
        header.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(header)

        addComparisonBanner(activity, body, r, settings, palette)

        val details = ApkInspector.formatDetails(r)
        val detailsView = TextView(activity).apply {
            text = details
            typeface = font
            setTextColor(palette.text)
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(0, dp(activity, 10), 0, 0)
        }
        body.addView(detailsView)

        val scroll = ScrollView(activity).apply { addView(body) }
        AppTheme.dialogBuilder(activity)
            .setTitle("APK info")
            .setView(scroll)
            .setPositiveButton("Install") { _, _ ->
                if (settings.warnSignature && r.compare.signatureDiffers ||
                    (settings.warnDowngrade && r.compare.relation == ApkCompare.Relation.DOWNGRADE)
                ) {
                    confirmInstallDialog(activity, file, r, settings, palette, font, onShare, onRefresh)
                } else {
                    launchInstaller(activity, file, r, settings, onRefresh)
                }
            }
            .setNeutralButton("Copy") { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("APK info", details))
                toast(activity, "APK info copied")
            }
            .setNegativeButton("Close", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    private fun confirmInstallDialog(
        activity: Activity,
        file: File,
        r: ApkInspector.Report,
        settings: ApkSettings,
        palette: AppTheme.Palette,
        font: Typeface,
        onShare: (File) -> Unit,
        onRefresh: () -> Unit,
    ) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 8))
        }
        box.addView(
            TextView(activity).apply {
                text = "${r.appName} (${r.packageName})\nVersion ${r.versionName ?: "?"} (${r.versionCode})"
                setTextColor(palette.text)
                textSize = 14f
                typeface = font
            }
        )
        addComparisonBanner(activity, box, r, settings, palette)
        AppTheme.dialogBuilder(activity)
            .setTitle("Install ${file.name}?")
            .setView(box)
            .setPositiveButton("Install") { _, _ ->
                launchInstaller(activity, file, r, settings, onRefresh)
            }
            .setNeutralButton("APK info") { _, _ ->
                showInfo(activity, file, settings, palette, font, onShare, onRefresh)
            }
            .setNegativeButton("Cancel", null)
            .show().also { AppTheme.styleDialog(it, activity) }
    }

    private fun addComparisonBanner(
        activity: Activity,
        parent: LinearLayout,
        r: ApkInspector.Report,
        settings: ApkSettings,
        palette: AppTheme.Palette,
    ) {
        val cmp = r.compare
        val summary = when (cmp.relation) {
            ApkCompare.Relation.UPDATE -> "Update from installed ${cmp.installedVersion ?: "?"} (${cmp.installedCode}) → ${r.versionName ?: "?"} (${r.versionCode})"
            ApkCompare.Relation.SAME -> "Same version already installed: ${cmp.installedVersion ?: "?"} (${cmp.installedCode})"
            ApkCompare.Relation.DOWNGRADE -> "Downgrade from installed ${cmp.installedVersion ?: "?"} (${cmp.installedCode}) → ${r.versionName ?: "?"} (${r.versionCode})"
            ApkCompare.Relation.UNKNOWN ->
                if (cmp.visibilityLimited) "Installed status unavailable on Android 11+ (or not installed)."
                else "Not currently installed on this device."
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 10)
            setPadding(pad, pad, pad, pad)
            background = AppTheme.rounded(activity, palette.accentSoft, 8f, palette.outline)
        }
        box.addView(
            TextView(activity).apply {
                text = summary
                setTextColor(palette.text)
                textSize = 13f
            }
        )
        if (settings.warnSignature && cmp.signatureDiffers) {
            box.addView(
                TextView(activity).apply {
                    text = "Warning: Signing certificate differs from the installed app. Updating in place will fail."
                    setTextColor(palette.accent)
                    textSize = 12f
                    setPadding(0, dp(activity, 4), 0, 0)
                }
            )
        }
        if (settings.warnDowngrade && cmp.relation == ApkCompare.Relation.DOWNGRADE) {
            box.addView(
                TextView(activity).apply {
                    text = "Warning: Android normally refuses downgrades unless the existing app is uninstalled first."
                    setTextColor(palette.accent)
                    textSize = 12f
                    setPadding(0, dp(activity, 4), 0, 0)
                }
            )
        }
        parent.addView(
            box,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(activity, 10)
            }
        )
    }

    private fun launchInstaller(
        activity: Activity,
        file: File,
        r: ApkInspector.Report,
        settings: ApkSettings,
        onRefresh: () -> Unit,
    ) {
        val updater = Updater(activity)
        if (!updater.canInstall()) {
            UpdateController.promptInstallPermission(
                activity,
                "Allow this app to install unknown apps in Android settings, then tap Install.",
            )
            return
        }
        val busy = BusyDialog(activity, ui, "Preparing ${r.appName}…", null)
        busy.start()
        ApkInstallBus.listen { result ->
            ui.post {
                if (!alive(activity)) return@post
                ApkInstallBus.listen(null)
                if (!result.success) {
                    AppTheme.dialogBuilder(activity)
                        .setTitle("Install failed")
                        .setMessage(result.message ?: "The install did not finish.")
                        .setPositiveButton("OK", null)
                        .show().also { AppTheme.styleDialog(it, activity) }
                    return@post
                }
                when (settings.afterInstall) {
                    ApkSettings.AfterInstall.ALWAYS -> {
                        Thread {
                            file.delete()
                            ui.post {
                                if (alive(activity)) {
                                    onRefresh()
                                    toast(activity, "Installed ${r.appName} and deleted ${file.name}.")
                                }
                            }
                        }.start()
                    }
                    ApkSettings.AfterInstall.ASK -> {
                        AppTheme.dialogBuilder(activity)
                            .setTitle("Installed ${r.appName}")
                            .setMessage("Delete ${file.name}?")
                            .setPositiveButton("Delete") { _, _ ->
                                Thread {
                                    file.delete()
                                    ui.post { if (alive(activity)) onRefresh() }
                                }.start()
                            }
                            .setNegativeButton("Keep", null)
                            .show().also { AppTheme.styleDialog(it, activity) }
                    }
                    ApkSettings.AfterInstall.NEVER -> toast(activity, "Installed ${r.appName}.")
                }
            }
            true
        }
        Thread {
            try {
                updater.installApk(
                    file = file,
                    packageNameHint = r.packageName,
                    source = InstallResultReceiver.SOURCE_APK,
                    apkPath = file.absolutePath,
                    apkLabel = r.appName,
                ) { done, total ->
                    ui.post { busy.update(done, total) }
                }
                ui.post { busy.end() }
            } catch (e: Exception) {
                ui.post {
                    ApkInstallBus.listen(null)
                    busy.end()
                    toast(activity, "Couldn't start the installer: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }.start()
    }
}
