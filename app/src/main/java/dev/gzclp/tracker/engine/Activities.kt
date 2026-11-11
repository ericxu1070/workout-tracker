package dev.gzclp.tracker.engine

/** The unit distance is entered in for an activity; the log always stores meters. */
enum class DistanceUnit(val meters: Double, val label: String) { MI(1609.344, "mi"), YD(0.9144, "yd"), M(1.0, "m") }

/** A cardio activity. [unit] is null when distance does not apply. [aliases] only widen search. */
data class Activity(val id: String, val name: String, val unit: DistanceUnit? = null, val aliases: List<String> = emptyList())

object Activities {
    private fun a(id: String, name: String, unit: DistanceUnit? = null, vararg aliases: String) = Activity(id, name, unit, aliases.toList())
    private val MI = DistanceUnit.MI
    private val YD = DistanceUnit.YD
    private val M = DistanceUnit.M

    val all: List<Activity> = listOf(
        a("running", "Running", MI, "jog", "jogging"),
        a("treadmill", "Treadmill run", MI, "treadmill"),
        a("walking", "Walking", MI, "walk"),
        a("hiking", "Hiking", MI, "hike", "trail"),
        a("cycling", "Cycling", MI, "bike", "biking"),
        a("stationary_bike", "Stationary bike", MI, "spin", "spinning", "indoor cycling"),
        a("swimming", "Swimming (pool)", YD, "swim", "laps"),
        a("open_water_swim", "Open water swim", YD, "swim", "ocean", "lake"),
        a("rowing_machine", "Rowing machine", M, "erg", "rower"),
        a("rowing", "Rowing (on water)", M, "crew", "scull"),
        a("paddling", "Kayaking and paddling", MI, "kayak", "canoe", "paddleboard", "sup"),
        a("elliptical", "Elliptical", MI, "cross trainer"),
        a("inline_skating", "Inline skating", MI, "rollerblade", "roller skating"),
        a("cross_country_skiing", "Cross-country skiing", MI, "nordic", "xc ski"),
        a("snowshoeing", "Snowshoeing", MI),
        a("wheelchair", "Wheelchair", MI, "push"),
        a("stair_climber", "Stair climber", null, "stairmaster", "stepmill"),
        a("stairs", "Stair climbing", null, "steps"),
        a("jump_rope", "Jump rope", null, "skipping"),
        a("hiit", "HIIT", null, "intervals", "tabata", "circuit", "wod"),
        a("boot_camp", "Boot camp"),
        a("fitness_class", "Fitness class", null, "aerobics", "class"),
        a("calisthenics", "Calisthenics", null, "bodyweight"),
        a("yoga", "Yoga"),
        a("pilates", "Pilates"),
        a("stretching", "Stretching", null, "mobility", "foam rolling"),
        a("dancing", "Dancing", null, "dance", "zumba"),
        a("boxing", "Boxing", null, "heavy bag", "sparring"),
        a("kickboxing", "Kickboxing", null, "muay thai"),
        a("martial_arts", "Martial arts", null, "bjj", "jiu jitsu", "judo", "karate", "taekwondo", "mma", "wrestling"),
        a("climbing", "Climbing", null, "bouldering", "rock climbing"),
        a("gymnastics", "Gymnastics"),
        a("basketball", "Basketball", null, "hoops"),
        a("soccer", "Soccer", null, "football", "futsal"),
        a("american_football", "American football", null, "flag football"),
        a("volleyball", "Volleyball", null, "beach volleyball"),
        a("baseball", "Baseball"),
        a("softball", "Softball"),
        a("ice_hockey", "Ice hockey", null, "hockey"),
        a("roller_hockey", "Roller hockey"),
        a("rugby", "Rugby"),
        a("lacrosse", "Lacrosse"),
        a("ultimate", "Ultimate frisbee", null, "frisbee", "disc"),
        a("cricket", "Cricket"),
        a("handball", "Handball"),
        a("water_polo", "Water polo"),
        a("tennis", "Tennis"),
        a("pickleball", "Pickleball"),
        a("badminton", "Badminton"),
        a("squash", "Squash"),
        a("racquetball", "Racquetball"),
        a("table_tennis", "Table tennis", null, "ping pong"),
        a("golf", "Golf"),
        a("fencing", "Fencing"),
        a("skiing", "Skiing", null, "ski", "downhill", "alpine"),
        a("snowboarding", "Snowboarding"),
        a("ice_skating", "Ice skating", null, "skate"),
        a("surfing", "Surfing"),
        a("other", "Other"),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String): Activity? = byId[id]

    const val NAME_MAX = 40

    /** Case-insensitive substring match over names and aliases; names starting with [q] first, then alphabetical. Blank lists all. */
    fun search(q: String): List<Activity> {
        val t = q.trim().lowercase()
        return all.filter { t in it.name.lowercase() || it.aliases.any { a -> t in a } }
            .sortedWith(compareBy({ !it.name.lowercase().startsWith(t) }, { it.name.lowercase() }))
    }

    /**
     * The activity the user typed: the built-in with that name, else a custom one whose id is the name slugged
     * like a custom exercise, suffixed when it collides with a built-in id. Null for a blank name.
     */
    fun typed(name: String): Activity? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(NAME_MAX).trim()
        if (clean.isEmpty()) return null
        all.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val base = clean.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "activity" }
        val id = generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { it !in byId }
        return Activity(id, clean)
    }
}
