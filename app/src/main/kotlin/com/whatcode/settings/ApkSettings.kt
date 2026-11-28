package com.whatcode.settings

import android.content.Context

/**
 * How the explorer handles `.apk` files, saved on the device. Changed on Settings > File explorer > APK files and
 * included in [SettingsBackup].
 */
class ApkSettings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** What tapping a `.apk` file in the explorer does. */
    enum class TapAction(val label: String) {
        ASK("Ask what to do (show options)"),
        INFO("Show APK info"),
        INSTALL("Start installing"),
    }

    /** Whether to offer or delete the `.apk` file after a successful install. */
    enum class AfterInstall(val label: String) {
        ASK("Ask after installing"),
        ALWAYS("Delete automatically"),
        NEVER("Always keep the file"),
    }

    var tapAction: TapAction
        get() = read(KEY_TAP, TapAction.values(), TapAction.ASK)
        set(v) = prefs.edit().putString(KEY_TAP, v.name).apply()

    var confirmInstall: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_INSTALL, DEFAULT_CONFIRM_INSTALL)
        set(v) = prefs.edit().putBoolean(KEY_CONFIRM_INSTALL, v).apply()

    var afterInstall: AfterInstall
        get() = read(KEY_AFTER_INSTALL, AfterInstall.values(), AfterInstall.ASK)
        set(v) = prefs.edit().putString(KEY_AFTER_INSTALL, v.name).apply()

    var showRowAppInfo: Boolean
        get() = prefs.getBoolean(KEY_ROW_META, DEFAULT_ROW_META)
        set(v) = prefs.edit().putBoolean(KEY_ROW_META, v).apply()

    var iconCacheMb: Int
        get() = prefs.getInt(KEY_CACHE_MB, DEFAULT_CACHE_MB).coerceIn(MIN_CACHE_MB, MAX_CACHE_MB)
        set(v) = prefs.edit().putInt(KEY_CACHE_MB, v.coerceIn(MIN_CACHE_MB, MAX_CACHE_MB)).apply()

    var showSha256: Boolean
        get() = prefs.getBoolean(KEY_SHA256, DEFAULT_SHA256)
        set(v) = prefs.edit().putBoolean(KEY_SHA256, v).apply()

    var showMd5: Boolean
        get() = prefs.getBoolean(KEY_MD5, DEFAULT_MD5)
        set(v) = prefs.edit().putBoolean(KEY_MD5, v).apply()

    var warnDowngrade: Boolean
        get() = prefs.getBoolean(KEY_WARN_DOWNGRADE, DEFAULT_WARN_DOWNGRADE)
        set(v) = prefs.edit().putBoolean(KEY_WARN_DOWNGRADE, v).apply()

    var warnSignature: Boolean
        get() = prefs.getBoolean(KEY_WARN_SIGNATURE, DEFAULT_WARN_SIGNATURE)
        set(v) = prefs.edit().putBoolean(KEY_WARN_SIGNATURE, v).apply()

    fun resetToDefault() {
        prefs.edit().clear().apply()
    }

    private fun <T : Enum<T>> read(key: String, values: Array<T>, fallback: T): T {
        val saved = prefs.getString(key, null)
        return values.firstOrNull { it.name == saved } ?: fallback
    }

    companion object {
        const val FILE = "explorer_apk"

        const val KEY_TAP = "apk_tap"
        const val KEY_CONFIRM_INSTALL = "confirm_install"
        const val KEY_AFTER_INSTALL = "after_install"
        const val KEY_ROW_META = "row_app_info"
        const val KEY_CACHE_MB = "icon_cache_mb"
        const val KEY_SHA256 = "show_sha256"
        const val KEY_MD5 = "show_md5"
        const val KEY_WARN_DOWNGRADE = "warn_downgrade"
        const val KEY_WARN_SIGNATURE = "warn_signature"

        const val DEFAULT_CONFIRM_INSTALL = true
        const val DEFAULT_ROW_META = true
        const val DEFAULT_CACHE_MB = 4
        const val MIN_CACHE_MB = 1
        const val MAX_CACHE_MB = 32
        const val DEFAULT_SHA256 = true
        const val DEFAULT_MD5 = true
        const val DEFAULT_WARN_DOWNGRADE = true
        const val DEFAULT_WARN_SIGNATURE = true

        val CACHE_PRESETS = listOf(1, 2, 4, 8, 16)

        val FLAGS: Set<String> = setOf(
            KEY_CONFIRM_INSTALL, KEY_ROW_META, KEY_SHA256, KEY_MD5, KEY_WARN_DOWNGRADE, KEY_WARN_SIGNATURE,
        )

        val CHOICES: Map<String, Set<String>> = mapOf(
            KEY_TAP to TapAction.values().map { it.name }.toSet(),
            KEY_AFTER_INSTALL to AfterInstall.values().map { it.name }.toSet(),
        )
    }
}
