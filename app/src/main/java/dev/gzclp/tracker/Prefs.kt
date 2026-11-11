package dev.gzclp.tracker

import android.content.Context
import dev.gzclp.tracker.engine.Tier

/** Device-local settings. Program data never lives here; it lives in the log. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("gzclp", Context.MODE_PRIVATE)

    var theme: String
        get() = p.getString("theme", "jade")!!
        set(v) = p.edit().putString("theme", v).apply()

    /** The last good laptop theme, in omarchy-theme.toml's format. */
    var laptopTheme: String?
        get() = p.getString("laptop_theme", null)
        set(v) = p.edit().putString("laptop_theme", v).apply()

    fun rest(tier: Tier): Int = p.getInt("rest_${tier.id}", tier.defaultRestSec)
    fun setRest(tier: Tier, sec: Int) = p.edit().putInt("rest_${tier.id}", sec).apply()
    fun restTargets(): Map<Tier, Int> = Tier.entries.associateWith { rest(it) }

    var folderUri: String?
        get() = p.getString("folder_uri", null)
        set(v) = p.edit().putString("folder_uri", v).putLong("mirrored_bytes", -1).apply()

    /** Bytes of the local log known to be in the mirror file. -1 forces a full rewrite. */
    var mirroredBytes: Long
        get() = p.getLong("mirrored_bytes", -1)
        set(v) = p.edit().putLong("mirrored_bytes", v).apply()

    var lastSyncMillis: Long
        get() = p.getLong("last_sync", 0)
        set(v) = p.edit().putLong("last_sync", v).apply()

    var lastSyncError: String?
        get() = p.getString("last_sync_error", null)
        set(v) = p.edit().putString("last_sync_error", v).apply()

    /** Wall-clock end of the running rest, 0 when none. */
    var restEndMillis: Long
        get() = p.getLong("rest_end", 0)
        set(v) = p.edit().putLong("rest_end", v).apply()
    var restTotalSec: Int
        get() = p.getInt("rest_total", 0)
        set(v) = p.edit().putInt("rest_total", v).apply()
    var restLabel: String
        get() = p.getString("rest_label", "")!!
        set(v) = p.edit().putString("rest_label", v).apply()

    var warmups: Boolean
        get() = p.getBoolean("warmups", true)
        set(v) = p.edit().putBoolean("warmups", v).apply()

    var weighInReminder: Boolean
        get() = p.getBoolean("weigh_in_reminder", true)
        set(v) = p.edit().putBoolean("weigh_in_reminder", v).apply()
    /** Minutes after midnight. */
    var weighInMinutes: Int
        get() = p.getInt("weigh_in_minutes", 7 * 60)
        set(v) = p.edit().putInt("weigh_in_minutes", v).apply()
}
