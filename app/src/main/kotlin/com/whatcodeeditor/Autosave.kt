package com.whatcodeeditor

import android.content.Context

/**
 * How autosave behaves, saved on the device. Changed on the Settings > Code Editor > Autosave page
 * ([AutosaveActivity]) and read by the editor screen ([MainActivity]) whenever it needs to decide.
 */
class AutosaveSettings(context: Context) {

    private val prefs = context.getSharedPreferences("autosave", Context.MODE_PRIVATE)

    /** Save the open file by itself after typing stops. Off until the person turns it on. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", DEFAULT_ENABLED)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** Seconds of not typing before the file is saved. */
    var delaySeconds: Int
        get() = prefs.getInt("delay", DEFAULT_DELAY).let { if (it in DELAYS) it else DEFAULT_DELAY }
        set(v) = prefs.edit().putInt("delay", v).apply()

    /** Also save as the app is left (another app is opened, or this one is closed). */
    var saveOnLeave: Boolean
        get() = prefs.getBoolean("on_leave", DEFAULT_ON_LEAVE)
        set(v) = prefs.edit().putBoolean("on_leave", v).apply()

    fun resetToDefault() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_ENABLED = false
        const val DEFAULT_DELAY = 5
        const val DEFAULT_ON_LEAVE = true

        /** The choices on the settings page, in seconds. */
        val DELAYS: List<Int> = listOf(1, 2, 5, 10, 30, 60, 300)

        fun label(seconds: Int): String = when {
            seconds < 60 -> "$seconds ${if (seconds == 1) "second" else "seconds"}"
            seconds == 60 -> "1 minute"
            else -> "${seconds / 60} minutes"
        }
    }
}
