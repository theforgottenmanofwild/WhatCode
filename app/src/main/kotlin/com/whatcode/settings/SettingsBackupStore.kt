package com.whatcode.settings

import android.content.Context

/**
 * Connects [SettingsBackup] to the settings saved on the device: [export] makes the JSON text of what is saved, and
 * [import] replaces the saved settings with what a backup holds.
 */
object SettingsBackupStore {

    /** The JSON text of every saved setting that a backup holds, written by the app version [version]. */
    fun export(context: Context, version: String): String {
        val saved = LinkedHashMap<String, Map<String, Any?>>()
        for (section in SettingsBackup.SECTIONS) {
            val prefs = context.getSharedPreferences(section.file, Context.MODE_PRIVATE)
            saved[section.file] = prefs.all.filterKeys { section.kindOf(it) != null }
        }
        return SettingsBackup.toJson(version, saved)
    }

    /**
     * Puts the settings of [backup] in place of the saved ones. For each section the backup has, the settings of that
     * section are cleared first, so a setting the backup does not mention goes back to its default; sections it does
     * not have are left as they are. The screens read the new settings the next time they are shown.
     */
    fun import(context: Context, backup: SettingsBackup.Backup) {
        for ((file, values) in backup.sections) {
            val section = SettingsBackup.section(file) ?: continue
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            for (key in prefs.all.keys) if (section.kindOf(key) != null) edit.remove(key)
            for ((key, value) in values) {
                when (value) {
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is String -> edit.putString(key, value)
                }
            }
            edit.commit()
        }
    }
}
