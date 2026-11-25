package dev.gzclp.tracker.stretch

/**
 * Built-in catalog of stretch moves covering hips, hamstrings, quads, calves, ankles,
 * upper back, chest, shoulders, lats, and spine.
 */
object StretchMoves {

    // 1. HIPS
    val PIGEON_POSE = StretchMove(
        id = "hips_pigeon",
        name = "Pigeon Pose",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Square your hips to the mat and sink gently into the stretch.",
        isBilateral = true,
    )

    val BUTTERFLY_STRETCH = StretchMove(
        id = "hips_butterfly",
        name = "Butterfly Stretch",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Press your soles together and hinge gently forward from your hips.",
    )

    val LIZARD_LUNGE = StretchMove(
        id = "hips_lizard",
        name = "Lizard Lunge",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Sink your pelvis low while keeping your front knee stacked over your heel.",
        isBilateral = true,
    )

    // 2. HAMSTRINGS
    val STANDING_FORWARD_FOLD = StretchMove(
        id = "hamstrings_standing_fold",
        name = "Standing Forward Fold",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 35,
        cue = "Hinge from the hips with soft knees and let your torso hang heavy.",
    )

    val SEATED_SINGLE_LEG = StretchMove(
        id = "hamstrings_seated_single",
        name = "Seated Single-Leg Stretch",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Hinge forward from the hip crease toward your flexed front foot.",
        isBilateral = true,
    )

    val DOWNWARD_DOG = StretchMove(
        id = "hamstrings_downward_dog",
        name = "Downward-Facing Dog",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Drive your hips up and back while pressing your heels toward the mat.",
    )

    // 3. QUADS
    val STANDING_QUAD_STRETCH = StretchMove(
        id = "quads_standing",
        name = "Standing Quad Stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Keep your knees together and tuck your tailbone slightly.",
        isBilateral = true,
    )

    val COUCH_STRETCH = StretchMove(
        id = "quads_couch",
        name = "Kneeling Couch Stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 35,
        cue = "Squeeze your glute on the rear leg and gently press your hip forward.",
        isBilateral = true,
    )

    val KNEELING_QUAD_LEAN = StretchMove(
        id = "quads_kneeling_lean",
        name = "Kneeling Quad Lean",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Brace your core and lean your torso back in one straight line.",
    )

    // 4. CALVES
    val WALL_CALF_STRETCH = StretchMove(
        id = "calves_wall_drive",
        name = "Wall Calf Stretch",
        targetArea = BodyArea.CALVES,
        holdSec = 30,
        cue = "Drive your back heel firmly into the ground with a straight rear knee.",
        isBilateral = true,
    )

    val HEEL_DROP_STRETCH = StretchMove(
        id = "calves_heel_drop",
        name = "Stair Heel Drop",
        targetArea = BodyArea.CALVES,
        holdSec = 25,
        cue = "Lower your heel below the step level for a deep, steady calf stretch.",
        isBilateral = true,
    )

    // 5. ANKLES
    val KNEELING_ANKLE_DORSI = StretchMove(
        id = "ankles_kneeling_dorsi",
        name = "Kneeling Ankle Dorsiflexion",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Drive your front knee over your toes while keeping your heel grounded.",
        isBilateral = true,
    )

    val PLANTAR_ANKLE_SIT = StretchMove(
        id = "ankles_plantar_sit",
        name = "Plantar Ankle Sit",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Sit gently back on your shins with feet flat to stretch the top of ankles.",
    )

    // 6. UPPER BACK
    val THREAD_THE_NEEDLE = StretchMove(
        id = "upper_back_thread_needle",
        name = "Thread the Needle",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Slide one arm under your torso and rest your shoulder gently on the mat.",
        isBilateral = true,
    )

    val CAT_COW_STRETCH = StretchMove(
        id = "upper_back_cat",
        name = "Cat Pose Stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Round your upper back toward the ceiling and tuck your chin.",
    )

    val EAGLE_ARMS = StretchMove(
        id = "upper_back_eagle",
        name = "Eagle Arms Stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 25,
        cue = "Cross your elbows and lift your forearms to open across the shoulder blades.",
    )

    // 7. CHEST
    val DOORWAY_PEC_STRETCH = StretchMove(
        id = "chest_doorway",
        name = "Doorway Chest Stretch",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Rest your forearm on the frame at 90 degrees and step your chest forward.",
        isBilateral = true,
    )

    val BEHIND_BACK_CLASP = StretchMove(
        id = "chest_behind_back_clasp",
        name = "Behind-Back Chest Opener",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Interlace your fingers behind your back and gently lift your chest.",
    )

    // 8. SHOULDERS
    val CROSS_BODY_SHOULDER = StretchMove(
        id = "shoulders_cross_body",
        name = "Cross-Body Shoulder Stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Gently draw your arm across your chest without hiking your shoulder.",
        isBilateral = true,
    )

    val OVERHEAD_TRICEPS = StretchMove(
        id = "shoulders_overhead_triceps",
        name = "Overhead Triceps & Shoulder",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Reach down the center of your back and guide your elbow with the other hand.",
        isBilateral = true,
    )

    val SLEEPER_STRETCH = StretchMove(
        id = "shoulders_sleeper",
        name = "Sleeper Shoulder Stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Lie on your side and gently guide your forearm down toward the mat.",
        isBilateral = true,
    )

    // 9. LATS
    val CHILDS_POSE_LAT = StretchMove(
        id = "lats_childs_pose",
        name = "Child's Pose Lat Reach",
        targetArea = BodyArea.LATS,
        holdSec = 30,
        cue = "Walk your hands to one side and sink your hips deeply back onto your heels.",
        isBilateral = true,
    )

    val OVERHEAD_SIDE_BEND = StretchMove(
        id = "lats_side_bend",
        name = "Standing Side Bend",
        targetArea = BodyArea.LATS,
        holdSec = 25,
        cue = "Clasp hands overhead and gently arc your ribcage to the side.",
        isBilateral = true,
    )

    // 10. SPINE
    val SUPINE_SPINAL_TWIST = StretchMove(
        id = "spine_supine_twist",
        name = "Supine Spinal Twist",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Drop your knees to the side while keeping both shoulders flat on the floor.",
        isBilateral = true,
    )

    val COBRA_POSE = StretchMove(
        id = "spine_cobra",
        name = "Cobra Pose",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Press the floor away to extend your thoracic spine without pinching your low back.",
    )

    val SEATED_SPINAL_TWIST = StretchMove(
        id = "spine_seated_twist",
        name = "Seated Spinal Twist",
        targetArea = BodyArea.SPINE,
        holdSec = 25,
        cue = "Sit tall, elongate your spine, and rotate gently from your ribcage.",
        isBilateral = true,
    )

    /** Complete list of all 26 moves across 10 body areas. */
    val ALL: List<StretchMove> = listOf(
        PIGEON_POSE, BUTTERFLY_STRETCH, LIZARD_LUNGE,
        STANDING_FORWARD_FOLD, SEATED_SINGLE_LEG, DOWNWARD_DOG,
        STANDING_QUAD_STRETCH, COUCH_STRETCH, KNEELING_QUAD_LEAN,
        WALL_CALF_STRETCH, HEEL_DROP_STRETCH,
        KNEELING_ANKLE_DORSI, PLANTAR_ANKLE_SIT,
        THREAD_THE_NEEDLE, CAT_COW_STRETCH, EAGLE_ARMS,
        DOORWAY_PEC_STRETCH, BEHIND_BACK_CLASP,
        CROSS_BODY_SHOULDER, OVERHEAD_TRICEPS, SLEEPER_STRETCH,
        CHILDS_POSE_LAT, OVERHEAD_SIDE_BEND,
        SUPINE_SPINAL_TWIST, COBRA_POSE, SEATED_SPINAL_TWIST,
    )

    fun of(id: String): StretchMove? = ALL.firstOrNull { it.id == id }

    fun forArea(area: BodyArea): List<StretchMove> = ALL.filter { it.targetArea == area }
}
