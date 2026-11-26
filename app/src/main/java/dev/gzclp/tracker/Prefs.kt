package dev.gzclp.tracker

import android.content.Context
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Tier

/** Device-local settings. Program data never lives here; it lives in the log. */
class Prefs(context: Context) {
    companion object {
        /**
         * What a fresh install starts in. Performance: on a Galaxy S21, Loaded's glass added about 160 ms to a cold launch and 2 ms
         * to the median frame, so it is opt-in.
         */
        const val LOADED_BY_DEFAULT = false
    }

    private val p = context.getSharedPreferences("gzclp", Context.MODE_PRIVATE).also {
        // Liquid Glass was a theme before it was a switch: its colors are Midnight now.
        if (it.getString("theme", null) == "glass") it.edit().putString("theme", "midnight").putBoolean("glass", true).apply()
    }

    var theme: String
        get() = p.getString("theme", "tokyo")!!
        set(v) = p.edit().putString("theme", v).apply()

    /**
     * Loaded (Liquid Glass and every Loaded effect) or Performance. Before Motion was a choice, the Liquid Glass switch stood for it:
     * a glass setting someone chose carries over, and only an untouched one takes the default.
     */
    var loaded: Boolean
        get() = p.getBoolean("loaded", if (p.contains("glass")) p.getBoolean("glass", false) else LOADED_BY_DEFAULT)
        set(v) = p.edit().putBoolean("loaded", v).apply()

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

    var showPlates: Boolean
        get() = p.getBoolean("show_plates", true)
        set(v) = p.edit().putBoolean("show_plates", v).apply()

    var plates: Plates
        get() = Plates(p.getInt("plate_bar", 45), Plates.SIZES.associateWith { p.getInt("plate_pairs_${Plates.label(it)}", Plates.DEFAULT.getValue(it)) })
        set(v) {
            val e = p.edit().putInt("plate_bar", v.bar)
            v.pairs.forEach { (size, n) -> e.putInt("plate_pairs_${Plates.label(size)}", n) }
            e.apply()
        }

    /** The body the app draws (the muscle map, and the form figure once it has a female build): female when true, male by default. */
    var bodyFemale: Boolean
        get() = p.getBoolean("body_female", false)
        set(v) = p.edit().putBoolean("body_female", v).apply()

    /** The form figure's build: "athletic" (the default), "bodybuilder" or "normal". */
    var bodyBuild: String
        get() = p.getString("body_build", null) ?: "athletic"
        set(v) = p.edit().putString("body_build", v).apply()

    var weighInReminder: Boolean
        get() = p.getBoolean("weigh_in_reminder", true)
        set(v) = p.edit().putBoolean("weigh_in_reminder", v).apply()
    /** Minutes after midnight. */
    var weighInMinutes: Int
        get() = p.getInt("weigh_in_minutes", 7 * 60)
        set(v) = p.edit().putInt("weigh_in_minutes", v).apply()

    /** What the idle Workout tab shows: 0 Lifting, 1 Cardio, 2 Stretching. Falls back to the older Lifting | Cardio flag. */
    var workoutMode: Int
        get() = p.getInt("workout_mode", if (p.getBoolean("cardio_mode", false)) 1 else 0)
        set(v) = p.edit().putInt("workout_mode", v).apply()

    var cardioGoalMin: Int
        get() = p.getInt("cardio_goal_min", 150)
        set(v) = p.edit().putInt("cardio_goal_min", v).apply()

    /** Opted in to writing workouts and weigh-ins to Health Connect. */
    var healthConnect: Boolean
        get() = p.getBoolean("health_connect", false)
        set(v) = p.edit().putBoolean("health_connect", v).apply()

    var autoAdaptWeights: Boolean
        get() = p.getBoolean("auto_adapt_weights", false)
        set(v) = p.edit().putBoolean("auto_adapt_weights", v).apply()

    /** On for a fresh install, off for an existing one until opted in; the first read stores the answer. */
    var saveToGallery: Boolean
        get() {
            if (!p.contains("save_to_gallery")) p.edit().putBoolean("save_to_gallery", p.all.isEmpty()).apply()
            return p.getBoolean("save_to_gallery", false)
        }
        set(v) = p.edit().putBoolean("save_to_gallery", v).apply()

    /** The water goal set by hand, null to follow the recommended one; 0 is stored for null. */
    var waterGoalOverrideMl: Int?
        get() {
            // A goal stepped to before goals followed activity carries over, unless it was the old 2500 default.
            if (!p.contains("water_goal_override_ml")) {
                val old = p.getInt("water_goal_ml", 2500)
                p.edit().putInt("water_goal_override_ml", if (old == 2500) 0 else old).remove("water_goal_ml").apply()
            }
            return p.getInt("water_goal_override_ml", 0).takeIf { it > 0 }
        }
        set(v) = p.edit().putInt("water_goal_override_ml", v ?: 0).apply()

    var waterCupMl: Int
        get() = p.getInt("water_cup_ml", 250)
        set(v) = p.edit().putInt("water_cup_ml", v).apply()

    var waterReminder: Boolean
        get() = p.getBoolean("water_reminder", false)
        set(v) = p.edit().putBoolean("water_reminder", v).apply()

    /** When the water pace reminder last posted, epoch millis; 0 for never. */
    var waterRemindedMillis: Long
        get() = p.getLong("water_reminded_millis", 0L)
        set(v) = p.edit().putLong("water_reminded_millis", v).apply()

    var mealReminder: Boolean
        get() = p.getBoolean("meal_reminder", true)
        set(v) = p.edit().putBoolean("meal_reminder", v).apply()

    var stretchReminder: Boolean
        get() = p.getBoolean("stretch_reminder", false)
        set(v) = p.edit().putBoolean("stretch_reminder", v).apply()

    var stretchReminderMinutes: Int
        get() = p.getInt("stretch_reminder_minutes", 20 * 60)
        set(v) = p.edit().putInt("stretch_reminder_minutes", v).apply()

    /** The stretch player speaks its cues aloud. */
    var stretchSound: Boolean
        get() = p.getBoolean("stretch_sound", false)
        set(v) = p.edit().putBoolean("stretch_sound", v).apply()

    /** The stretch player taps the phone at each cue. */
    var stretchHaptics: Boolean
        get() = p.getBoolean("stretch_haptics", true)
        set(v) = p.edit().putBoolean("stretch_haptics", v).apply()

    var stretchRoutine: String
        get() = p.getString("stretch_routine", "after_workout") ?: "after_workout"
        set(v) = p.edit().putString("stretch_routine", v).apply()
}
