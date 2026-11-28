package com.whatcode.settings.explorer

import android.os.Environment
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.whatcode.explorer.ExplorerSettings
import com.whatcode.explorer.logic.ExplorerPaths
import com.whatcode.ui.SettingsPage
import java.io.File

/** Settings > File explorer > Start and navigation: the start folder, typing a path, pull to refresh and Back. */
class ExplorerStartActivity : SettingsPage("Start and navigation") {

    private val settings by lazy { ExplorerSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(sectionTitle("START IN"))
        choices(ExplorerSettings.Start.values().toList(), settings.start, { it.label }) { settings.start = it }
        if (settings.start == ExplorerSettings.Start.FOLDER) folderBox()
        content.addView(muted("Where the explorer opens when the app starts. A folder that is gone opens Phone storage."))

        content.addView(sectionTitle("MOVING AROUND"))
        switchRow(
            "Type a path in the path bar", settings.pathBox,
            note = "Tap the name of the folder you are in, at the end of the path, to type where to go.",
        ) { settings.pathBox = it }
        switchRow("Pull down to refresh", settings.pullToRefresh, note = "Pull the top of the list down to read the folder again.") {
            settings.pullToRefresh = it
        }
        switchRow(
            "Back goes up a folder", settings.backGoesUp,
            note = "Back leaves the app only from the top. Off leaves it from any folder.",
        ) { settings.backGoesUp = it }

        defaultsSection(
            question = "Start in Phone storage, with the path box, pull to refresh and Back going up a folder?",
            done = "Start and navigation returned to default",
        ) { settings.resetNavigation() }
    }

    /** The box to type the start folder in, with the folder in use, if any, already in it. */
    private fun folderBox() {
        val input = EditText(this).apply {
            setText(settings.startFolder.ifEmpty { Environment.getExternalStorageDirectory().path })
            hint = "/storage/emulated/0/Projects"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(colorText)
            setHintTextColor(colorMuted)
            textSize = 14f
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val use = {
            val path = ExplorerPaths.normalize(input.text.toString().trim())
            if (!path.startsWith("/") || path.length > ExplorerSettings.MAX_PATH || !File(path).isDirectory) {
                input.error = "There is no folder at this path, or it can't be reached."
            } else {
                settings.startFolder = path
                render()
            }
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) use()
            action == EditorInfo.IME_ACTION_DONE
        }
        content.addView(
            wide("Use this folder") { use() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, dp(6), 0, dp(4)) }
        )
        val last = settings.lastFolder
        if (last.isNotEmpty() && last != settings.startFolder) {
            content.addView(
                wide("Use the last folder shown: ${File(last).name.ifEmpty { last }}") {
                    settings.startFolder = last
                    render()
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, 0, 0, dp(4)) }
            )
        }
        content.addView(muted(if (settings.startFolder.isEmpty()) "No folder chosen yet." else "In use: ${settings.startFolder}"))
    }
}
