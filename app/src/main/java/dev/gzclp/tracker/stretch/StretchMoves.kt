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
        startPose = Pose(
            rootX = 0.5f, rootY = 0.65f, torsoAngle = -90f,
            leftThighAngle = 60f, leftCalfAngle = 120f,
            rightThighAngle = 135f, rightCalfAngle = 175f,
            leftArmAngle = 80f, leftForearmAngle = 85f,
            rightArmAngle = 100f, rightForearmAngle = 95f,
        ),
        endPose = Pose(
            rootX = 0.48f, rootY = 0.72f, torsoAngle = -45f,
            leftThighAngle = 30f, leftCalfAngle = 140f,
            rightThighAngle = 160f, rightCalfAngle = 175f,
            leftArmAngle = 10f, leftForearmAngle = 40f,
            rightArmAngle = 20f, rightForearmAngle = 50f,
        ),
        isBilateral = true,
    )

    val BUTTERFLY_STRETCH = StretchMove(
        id = "hips_butterfly",
        name = "Butterfly Stretch",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Press your soles together and hinge gently forward from your hips.",
        startPose = Pose(
            rootX = 0.5f, rootY = 0.72f, torsoAngle = -90f,
            leftThighAngle = 30f, leftCalfAngle = 140f,
            rightThighAngle = 150f, rightCalfAngle = 40f,
            leftArmAngle = 60f, leftForearmAngle = 70f,
            rightArmAngle = 120f, rightForearmAngle = 110f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.74f, torsoAngle = -70f,
            leftThighAngle = 15f, leftCalfAngle = 155f,
            rightThighAngle = 165f, rightCalfAngle = 25f,
            leftArmAngle = 45f, leftForearmAngle = 90f,
            rightArmAngle = 135f, rightForearmAngle = 90f,
        ),
    )

    val LIZARD_LUNGE = StretchMove(
        id = "hips_lizard",
        name = "Lizard Lunge",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Sink your pelvis low while keeping your front knee stacked over your heel.",
        startPose = Pose(
            rootX = 0.52f, rootY = 0.65f, torsoAngle = -75f,
            leftThighAngle = 45f, leftCalfAngle = 95f,
            rightThighAngle = 150f, rightCalfAngle = 175f,
            leftArmAngle = 75f, leftForearmAngle = 80f,
            rightArmAngle = 85f, rightForearmAngle = 90f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.72f, torsoAngle = -50f,
            leftThighAngle = 35f, leftCalfAngle = 100f,
            rightThighAngle = 165f, rightCalfAngle = 180f,
            leftArmAngle = 60f, leftForearmAngle = 90f,
            rightArmAngle = 70f, rightForearmAngle = 90f,
        ),
        isBilateral = true,
    )

    // 2. HAMSTRINGS
    val STANDING_FORWARD_FOLD = StretchMove(
        id = "hamstrings_standing_fold",
        name = "Standing Forward Fold",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 35,
        cue = "Hinge from the hips with soft knees and let your torso hang heavy.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.52f, torsoAngle = 90f,
            leftArmAngle = 90f, leftForearmAngle = 90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
            leftThighAngle = 85f, leftCalfAngle = 90f,
            rightThighAngle = 95f, rightCalfAngle = 90f,
        ),
    )

    val SEATED_SINGLE_LEG = StretchMove(
        id = "hamstrings_seated_single",
        name = "Seated Single-Leg Stretch",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Hinge forward from the hip crease toward your flexed front foot.",
        startPose = Pose.SEATED_NEUTRAL,
        endPose = Pose(
            rootX = 0.42f, rootY = 0.72f, torsoAngle = -40f,
            leftArmAngle = 0f, leftForearmAngle = 10f,
            rightArmAngle = 0f, rightForearmAngle = 10f,
            leftThighAngle = 0f, leftCalfAngle = 0f,
            rightThighAngle = 45f, rightCalfAngle = 145f,
        ),
        isBilateral = true,
    )

    val DOWNWARD_DOG = StretchMove(
        id = "hamstrings_downward_dog",
        name = "Downward-Facing Dog",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Drive your hips up and back while pressing your heels toward the mat.",
        startPose = Pose(
            rootX = 0.5f, rootY = 0.65f, torsoAngle = -100f,
            leftThighAngle = 90f, leftCalfAngle = 180f,
            rightThighAngle = 90f, rightCalfAngle = 180f,
            leftArmAngle = 80f, leftForearmAngle = 80f,
            rightArmAngle = 80f, rightForearmAngle = 80f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.45f, torsoAngle = 135f,
            leftThighAngle = 60f, leftCalfAngle = 65f,
            rightThighAngle = 60f, rightCalfAngle = 65f,
            leftArmAngle = 135f, leftForearmAngle = 135f,
            rightArmAngle = 135f, rightForearmAngle = 135f,
        ),
    )

    // 3. QUADS
    val STANDING_QUAD_STRETCH = StretchMove(
        id = "quads_standing",
        name = "Standing Quad Stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Keep your knees together and tuck your tailbone slightly.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -90f,
            leftThighAngle = 90f, leftCalfAngle = 90f,
            rightThighAngle = 85f, rightCalfAngle = -85f,
            leftArmAngle = 80f, leftForearmAngle = 80f,
            rightArmAngle = 100f, rightForearmAngle = -60f,
        ),
        isBilateral = true,
    )

    val COUCH_STRETCH = StretchMove(
        id = "quads_couch",
        name = "Kneeling Couch Stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 35,
        cue = "Squeeze your glute on the rear leg and gently press your hip forward.",
        startPose = Pose.KNEELING_NEUTRAL,
        endPose = Pose(
            rootX = 0.48f, rootY = 0.65f, torsoAngle = -90f,
            leftThighAngle = 45f, leftCalfAngle = 90f,
            rightThighAngle = 100f, rightCalfAngle = -80f,
            leftArmAngle = 60f, leftForearmAngle = 70f,
            rightArmAngle = 100f, rightForearmAngle = 95f,
        ),
        isBilateral = true,
    )

    val KNEELING_QUAD_LEAN = StretchMove(
        id = "quads_kneeling_lean",
        name = "Kneeling Quad Lean",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Brace your core and lean your torso back in one straight line.",
        startPose = Pose.KNEELING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.68f, torsoAngle = -120f,
            leftThighAngle = 60f, leftCalfAngle = 180f,
            rightThighAngle = 60f, rightCalfAngle = 180f,
            leftArmAngle = 30f, leftForearmAngle = 40f,
            rightArmAngle = 30f, rightForearmAngle = 40f,
        ),
    )

    // 4. CALVES
    val WALL_CALF_STRETCH = StretchMove(
        id = "calves_wall_drive",
        name = "Wall Calf Stretch",
        targetArea = BodyArea.CALVES,
        holdSec = 30,
        cue = "Drive your back heel firmly into the ground with a straight rear knee.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.52f, rootY = 0.56f, torsoAngle = -75f,
            leftThighAngle = 60f, leftCalfAngle = 90f,
            rightThighAngle = 125f, rightCalfAngle = 130f,
            leftArmAngle = 0f, leftForearmAngle = 0f,
            rightArmAngle = 0f, rightForearmAngle = 0f,
        ),
        isBilateral = true,
    )

    val HEEL_DROP_STRETCH = StretchMove(
        id = "calves_heel_drop",
        name = "Stair Heel Drop",
        targetArea = BodyArea.CALVES,
        holdSec = 25,
        cue = "Lower your heel below the step level for a deep, steady calf stretch.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.52f, torsoAngle = -90f,
            leftThighAngle = 88f, leftCalfAngle = 95f,
            rightThighAngle = 92f, rightCalfAngle = 85f,
            leftArmAngle = 60f, leftForearmAngle = 60f,
            rightArmAngle = 60f, rightForearmAngle = 60f,
        ),
        isBilateral = true,
    )

    // 5. ANKLES
    val KNEELING_ANKLE_DORSI = StretchMove(
        id = "ankles_kneeling_dorsi",
        name = "Kneeling Ankle Dorsiflexion",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Drive your front knee over your toes while keeping your heel grounded.",
        startPose = Pose.KNEELING_NEUTRAL,
        endPose = Pose(
            rootX = 0.48f, rootY = 0.66f, torsoAngle = -70f,
            leftThighAngle = 40f, leftCalfAngle = 115f,
            rightThighAngle = 100f, rightCalfAngle = 180f,
            leftArmAngle = 30f, leftForearmAngle = 50f,
            rightArmAngle = 30f, rightForearmAngle = 50f,
        ),
        isBilateral = true,
    )

    val PLANTAR_ANKLE_SIT = StretchMove(
        id = "ankles_plantar_sit",
        name = "Plantar Ankle Sit",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Sit gently back on your shins with feet flat to stretch the top of ankles.",
        startPose = Pose.KNEELING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.72f, torsoAngle = -90f,
            leftThighAngle = 80f, leftCalfAngle = 180f,
            rightThighAngle = 80f, rightCalfAngle = 180f,
            leftArmAngle = 80f, leftForearmAngle = 80f,
            rightArmAngle = 80f, rightForearmAngle = 80f,
        ),
    )

    // 6. UPPER BACK
    val THREAD_THE_NEEDLE = StretchMove(
        id = "upper_back_thread_needle",
        name = "Thread the Needle",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Slide one arm under your torso and rest your shoulder gently on the mat.",
        startPose = Pose(
            rootX = 0.5f, rootY = 0.68f, torsoAngle = 180f,
            leftThighAngle = 90f, leftCalfAngle = 180f,
            rightThighAngle = 90f, rightCalfAngle = 180f,
            leftArmAngle = 90f, leftForearmAngle = 90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.72f, torsoAngle = 175f,
            leftThighAngle = 90f, leftCalfAngle = 180f,
            rightThighAngle = 90f, rightCalfAngle = 180f,
            leftArmAngle = 0f, leftForearmAngle = 0f,
            rightArmAngle = 120f, rightForearmAngle = 90f,
        ),
        isBilateral = true,
    )

    val CAT_COW_STRETCH = StretchMove(
        id = "upper_back_cat",
        name = "Cat Pose Stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Round your upper back toward the ceiling and tuck your chin.",
        startPose = Pose(
            rootX = 0.5f, rootY = 0.68f, torsoAngle = 180f,
            leftThighAngle = 90f, leftCalfAngle = 180f,
            rightThighAngle = 90f, rightCalfAngle = 180f,
            leftArmAngle = 90f, leftForearmAngle = 90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.65f, torsoAngle = 195f,
            leftThighAngle = 85f, leftCalfAngle = 180f,
            rightThighAngle = 85f, rightCalfAngle = 180f,
            leftArmAngle = 90f, leftForearmAngle = 90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
            headTiltAngle = 30f,
        ),
    )

    val EAGLE_ARMS = StretchMove(
        id = "upper_back_eagle",
        name = "Eagle Arms Stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 25,
        cue = "Cross your elbows and lift your forearms to open across the shoulder blades.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -90f,
            leftArmAngle = -45f, leftForearmAngle = -90f,
            rightArmAngle = -135f, rightForearmAngle = -90f,
            leftThighAngle = 85f, leftCalfAngle = 90f,
            rightThighAngle = 95f, rightCalfAngle = 90f,
        ),
    )

    // 7. CHEST
    val DOORWAY_PEC_STRETCH = StretchMove(
        id = "chest_doorway",
        name = "Doorway Chest Stretch",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Rest your forearm on the frame at 90 degrees and step your chest forward.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.52f, rootY = 0.55f, torsoAngle = -85f,
            leftArmAngle = 180f, leftForearmAngle = -90f,
            rightArmAngle = 80f, rightForearmAngle = 80f,
            leftThighAngle = 75f, leftCalfAngle = 85f,
            rightThighAngle = 110f, rightCalfAngle = 115f,
        ),
        isBilateral = true,
    )

    val BEHIND_BACK_CLASP = StretchMove(
        id = "chest_behind_back_clasp",
        name = "Behind-Back Chest Opener",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Interlace your fingers behind your back and gently lift your chest.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -95f,
            leftArmAngle = 120f, leftForearmAngle = 140f,
            rightArmAngle = 120f, rightForearmAngle = 140f,
            leftThighAngle = 85f, leftCalfAngle = 90f,
            rightThighAngle = 95f, rightCalfAngle = 90f,
        ),
    )

    // 8. SHOULDERS
    val CROSS_BODY_SHOULDER = StretchMove(
        id = "shoulders_cross_body",
        name = "Cross-Body Shoulder Stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Gently draw your arm across your chest without hiking your shoulder.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -90f,
            leftArmAngle = 0f, leftForearmAngle = 0f,
            rightArmAngle = 30f, rightForearmAngle = 100f,
            leftThighAngle = 85f, leftCalfAngle = 90f,
            rightThighAngle = 95f, rightCalfAngle = 90f,
        ),
        isBilateral = true,
    )

    val OVERHEAD_TRICEPS = StretchMove(
        id = "shoulders_overhead_triceps",
        name = "Overhead Triceps & Shoulder",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Reach down the center of your back and guide your elbow with the other hand.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -90f,
            leftArmAngle = -90f, leftForearmAngle = 90f,
            rightArmAngle = -80f, rightForearmAngle = -20f,
            leftThighAngle = 85f, leftCalfAngle = 90f,
            rightThighAngle = 95f, rightCalfAngle = 90f,
        ),
        isBilateral = true,
    )

    val SLEEPER_STRETCH = StretchMove(
        id = "shoulders_sleeper",
        name = "Sleeper Shoulder Stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Lie on your side and gently guide your forearm down toward the mat.",
        startPose = Pose.PRONE_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.75f, torsoAngle = 180f,
            leftArmAngle = 90f, leftForearmAngle = 0f,
            rightArmAngle = 90f, rightForearmAngle = 45f,
            leftThighAngle = 45f, leftCalfAngle = 90f,
            rightThighAngle = 45f, rightCalfAngle = 90f,
        ),
        isBilateral = true,
    )

    // 9. LATS
    val CHILDS_POSE_LAT = StretchMove(
        id = "lats_childs_pose",
        name = "Child's Pose Lat Reach",
        targetArea = BodyArea.LATS,
        holdSec = 30,
        cue = "Walk your hands to one side and sink your hips deeply back onto your heels.",
        startPose = Pose.KNEELING_NEUTRAL,
        endPose = Pose(
            rootX = 0.6f, rootY = 0.72f, torsoAngle = 180f,
            leftThighAngle = 90f, leftCalfAngle = 180f,
            rightThighAngle = 90f, rightCalfAngle = 180f,
            leftArmAngle = 170f, leftForearmAngle = 175f,
            rightArmAngle = 160f, rightForearmAngle = 165f,
        ),
        isBilateral = true,
    )

    val OVERHEAD_SIDE_BEND = StretchMove(
        id = "lats_side_bend",
        name = "Standing Side Bend",
        targetArea = BodyArea.LATS,
        holdSec = 25,
        cue = "Clasp hands overhead and gently arc your ribcage to the side.",
        startPose = Pose.STANDING_NEUTRAL,
        endPose = Pose(
            rootX = 0.5f, rootY = 0.55f, torsoAngle = -75f,
            leftArmAngle = -85f, leftForearmAngle = -80f,
            rightArmAngle = -75f, rightForearmAngle = -70f,
            leftThighAngle = 80f, leftCalfAngle = 85f,
            rightThighAngle = 95f, rightCalfAngle = 95f,
        ),
        isBilateral = true,
    )

    // 10. SPINE
    val SUPINE_SPINAL_TWIST = StretchMove(
        id = "spine_supine_twist",
        name = "Supine Spinal Twist",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Drop your knees to the side while keeping both shoulders flat on the floor.",
        startPose = Pose(
            rootX = 0.5f, rootY = 0.75f, torsoAngle = 180f,
            leftThighAngle = 0f, leftCalfAngle = 0f,
            rightThighAngle = 0f, rightCalfAngle = 0f,
            leftArmAngle = -90f, leftForearmAngle = -90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
        ),
        endPose = Pose(
            rootX = 0.5f, rootY = 0.75f, torsoAngle = 180f,
            leftThighAngle = 90f, leftCalfAngle = 135f,
            rightThighAngle = 90f, rightCalfAngle = 135f,
            leftArmAngle = -90f, leftForearmAngle = -90f,
            rightArmAngle = 90f, rightForearmAngle = 90f,
        ),
        isBilateral = true,
    )

    val COBRA_POSE = StretchMove(
        id = "spine_cobra",
        name = "Cobra Pose",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Press the floor away to extend your thoracic spine without pinching your low back.",
        startPose = Pose.PRONE_NEUTRAL,
        endPose = Pose(
            rootX = 0.52f, rootY = 0.78f, torsoAngle = -140f,
            leftThighAngle = 0f, leftCalfAngle = 0f,
            rightThighAngle = 0f, rightCalfAngle = 0f,
            leftArmAngle = 70f, leftForearmAngle = 75f,
            rightArmAngle = 70f, rightForearmAngle = 75f,
            headTiltAngle = -15f,
        ),
    )

    val SEATED_SPINAL_TWIST = StretchMove(
        id = "spine_seated_twist",
        name = "Seated Spinal Twist",
        targetArea = BodyArea.SPINE,
        holdSec = 25,
        cue = "Sit tall, elongate your spine, and rotate gently from your ribcage.",
        startPose = Pose.SEATED_NEUTRAL,
        endPose = Pose(
            rootX = 0.45f, rootY = 0.72f, torsoAngle = -90f,
            leftThighAngle = 0f, leftCalfAngle = 90f,
            rightThighAngle = 30f, rightCalfAngle = 120f,
            leftArmAngle = -20f, leftForearmAngle = 45f,
            rightArmAngle = 140f, rightForearmAngle = 90f,
        ),
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
