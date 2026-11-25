package com.whatcode.files

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers which files were open (and where the cursor was) when the app was left, so the editor can reopen them
 * the next time it starts instead of showing an empty "untitled" file. Only files that have an address are
 * remembered; a blank, never-saved file has nothing to reopen. Used by [MainActivity].
 */
class SessionStore(context: Context) {

    /** One remembered file: its address, cursor and scroll position. */
    class Entry(val uri: String, val selStart: Int, val selEnd: Int, val scrollY: Int)

    /** The remembered files, and which of them ([active], an index into [entries]) was showing. */
    class Session(val entries: List<Entry>, val active: Int)

    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun save(entries: List<Entry>, active: Int) {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("uri", e.uri)
                    .put("start", e.selStart)
                    .put("end", e.selEnd)
                    .put("scroll", e.scrollY)
            )
        }
        prefs.edit().putString(KEY_FILES, array.toString()).putInt(KEY_ACTIVE, active).apply()
    }

    /** The remembered files, or null when there are none (or the saved data can't be read). */
    fun load(): Session? = try {
        val array = JSONArray(prefs.getString(KEY_FILES, null) ?: return null)
        val entries = (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Entry(o.getString("uri"), o.optInt("start"), o.optInt("end"), o.optInt("scroll"))
        }
        if (entries.isEmpty()) null else Session(entries, prefs.getInt(KEY_ACTIVE, 0))
    } catch (_: Exception) {
        null
    }

    /** Forgets the remembered files. */
    fun clear() {
        prefs.edit().remove(KEY_FILES).remove(KEY_ACTIVE).commit()
    }

    /**
     * Call right before reopening the remembered files. If the app dies while they open, the mark stays set and
     * [startFailed] tells the next start to skip them, so one bad file can't make the app crash on every launch.
     * Written at once (commit), because the process may be gone a moment later.
     */
    fun markStarting() {
        prefs.edit().putBoolean(KEY_STARTING, true).commit()
    }

    /** Call once the app has started fine, or is being left. */
    fun markStarted() {
        prefs.edit().putBoolean(KEY_STARTING, false).commit()
    }

    /** True when the last start never got as far as [markStarted]. */
    fun startFailed(): Boolean = prefs.getBoolean(KEY_STARTING, false)

    companion object {
        private const val KEY_FILES = "files"
        private const val KEY_ACTIVE = "active"
        private const val KEY_STARTING = "starting"
    }
}
