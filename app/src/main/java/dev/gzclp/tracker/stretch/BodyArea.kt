package dev.gzclp.tracker.stretch

/**
 * Target body areas for stretching routines and moves.
 * Covers all 10 core muscle and joint regions.
 */
enum class BodyArea(val displayName: String) {
    HIPS("Hips"),
    HAMSTRINGS("Hamstrings"),
    QUADS("Quads"),
    CALVES("Calves"),
    ANKLES("Ankles"),
    UPPER_BACK("Upper Back"),
    CHEST("Chest"),
    SHOULDERS("Shoulders"),
    LATS("Lats"),
    SPINE("Spine");

    val id: String get() = name.lowercase()
}
