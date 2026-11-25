package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.form.Bone
import dev.gzclp.tracker.form.Focus
import dev.gzclp.tracker.form.Spot
import dev.gzclp.tracker.form.V

/**
 * Built-in catalog of stretch moves covering hips, hamstrings, quads, calves, ankles,
 * upper back, chest, shoulders, lats, and spine. A one-sided move's motion and focus show its first side, mostly the left.
 */
object StretchMoves {

    // 1. HIPS
    val PIGEON_POSE = StretchMove(
        id = "hips_pigeon",
        name = "Pigeon pose",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Square your hips to the mat and sink gently into the stretch.",
        focus = Focus(0.34, Spot(Bone.PELVIS, V(0.08, -0.03, -0.11))),
        isUnilateral = true,
    )

    val BUTTERFLY_STRETCH = StretchMove(
        id = "hips_butterfly",
        name = "Butterfly stretch",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Press your soles together and hinge gently forward from your hips.",
        focus = Focus.both(0.3, Spot(Bone.THIGH_L, V(0.05, 0.1, 0.0))),
    )

    val LIZARD_LUNGE = StretchMove(
        id = "hips_lizard",
        name = "Lizard lunge",
        targetArea = BodyArea.HIPS,
        holdSec = 30,
        cue = "Sink your pelvis low while keeping your front knee stacked over your heel.",
        focus = Focus(0.3, Spot(Bone.THIGH_R, V(0.0, 0.04, 0.07))),
        isUnilateral = true,
    )

    // 2. HAMSTRINGS
    val STANDING_FORWARD_FOLD = StretchMove(
        id = "hamstrings_standing_fold",
        name = "Standing forward fold",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 35,
        cue = "Hinge from the hips with soft knees and let your torso hang heavy.",
        focus = Focus.both(0.36, Spot(Bone.THIGH_L, V(0.0, 0.22, -0.07))),
    )

    val SEATED_SINGLE_LEG = StretchMove(
        id = "hamstrings_seated_single",
        name = "Seated single-leg stretch",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Hinge forward from the hip crease toward your flexed front foot.",
        focus = Focus(0.36, Spot(Bone.THIGH_L, V(0.0, 0.22, -0.07))),
        isUnilateral = true,
    )

    val DOWNWARD_DOG = StretchMove(
        id = "hamstrings_downward_dog",
        name = "Downward-facing dog",
        targetArea = BodyArea.HAMSTRINGS,
        holdSec = 30,
        cue = "Drive your hips up and back while pressing your heels toward the mat.",
        focus = Focus.both(0.42, Spot(Bone.THIGH_L, V(0.0, 0.36, -0.06))),
    )

    // 3. QUADS
    val STANDING_QUAD_STRETCH = StretchMove(
        id = "quads_standing",
        name = "Standing quad stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Keep your knees together and tuck your tailbone slightly.",
        focus = Focus(0.36, Spot(Bone.THIGH_L, V(0.0, 0.22, 0.08))),
        isUnilateral = true,
    )

    val COUCH_STRETCH = StretchMove(
        id = "quads_couch",
        name = "Kneeling couch stretch",
        targetArea = BodyArea.QUADS,
        holdSec = 35,
        cue = "Squeeze your glute on the rear leg and gently press your hip forward.",
        focus = Focus(0.38, Spot(Bone.THIGH_L, V(0.0, 0.12, 0.08))),
        isUnilateral = true,
    )

    val KNEELING_QUAD_LEAN = StretchMove(
        id = "quads_kneeling_lean",
        name = "Kneeling quad lean",
        targetArea = BodyArea.QUADS,
        holdSec = 30,
        cue = "Brace your core and lean your torso back in one straight line.",
        focus = Focus.both(0.36, Spot(Bone.THIGH_L, V(0.0, 0.22, 0.08))),
    )

    // 4. CALVES
    val WALL_CALF_STRETCH = StretchMove(
        id = "calves_wall_drive",
        name = "Wall calf stretch",
        targetArea = BodyArea.CALVES,
        holdSec = 30,
        cue = "Drive your back heel firmly into the ground with a straight rear knee.",
        focus = Focus(0.3, Spot(Bone.SHIN_L, V(0.0, 0.14, -0.05))),
        isUnilateral = true,
    )

    val HEEL_DROP_STRETCH = StretchMove(
        id = "calves_heel_drop",
        name = "Stair heel drop",
        targetArea = BodyArea.CALVES,
        holdSec = 25,
        cue = "Lower your heel below the step level for a deep, steady calf stretch.",
        focus = Focus(0.3, Spot(Bone.SHIN_L, V(0.0, 0.16, -0.05))),
        isUnilateral = true,
    )

    // 5. ANKLES
    val KNEELING_ANKLE_DORSI = StretchMove(
        id = "ankles_kneeling_dorsi",
        name = "Kneeling ankle dorsiflexion",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Drive your front knee over your toes while keeping your heel grounded.",
        focus = Focus(0.26, Spot(Bone.SHIN_L, V(0.0, 0.36, -0.04))),
        isUnilateral = true,
    )

    val PLANTAR_ANKLE_SIT = StretchMove(
        id = "ankles_plantar_sit",
        name = "Plantar ankle sit",
        targetArea = BodyArea.ANKLES,
        holdSec = 25,
        cue = "Sit gently back on your shins with feet flat to stretch the top of ankles.",
        focus = Focus.both(0.24, Spot(Bone.FOOT_L, V(0.0, 0.04, 0.0))),
    )

    // 6. UPPER BACK
    val THREAD_THE_NEEDLE = StretchMove(
        id = "upper_back_thread_needle",
        name = "Thread the needle",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Slide one arm under your torso and rest your shoulder gently on the mat.",
        focus = Focus(0.24, Spot(Bone.CHEST, V(0.1, 0.18, -0.1))),
        isUnilateral = true,
    )

    val CAT_COW_STRETCH = StretchMove(
        id = "upper_back_cat",
        name = "Cat pose stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 30,
        cue = "Round your upper back toward the ceiling and tuck your chin.",
        focus = Focus(0.36, Spot(Bone.CHEST, V(0.0, 0.15, -0.11))),
    )

    val EAGLE_ARMS = StretchMove(
        id = "upper_back_eagle",
        name = "Eagle arms stretch",
        targetArea = BodyArea.UPPER_BACK,
        holdSec = 25,
        cue = "Cross your elbows and lift your forearms to open across the shoulder blades.",
        isUnilateral = true,
        focus = Focus(0.34, Spot(Bone.CHEST, V(0.0, 0.18, -0.11))),
    )

    // 7. CHEST
    val DOORWAY_PEC_STRETCH = StretchMove(
        id = "chest_doorway",
        name = "Doorway chest stretch",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Rest your forearm on the frame at 90 degrees and step your chest forward.",
        focus = Focus(0.22, Spot(Bone.CHEST, V(0.12, 0.17, 0.09))),
        isUnilateral = true,
    )

    val BEHIND_BACK_CLASP = StretchMove(
        id = "chest_behind_back_clasp",
        name = "Behind-back chest opener",
        targetArea = BodyArea.CHEST,
        holdSec = 30,
        cue = "Interlace your fingers behind your back and gently lift your chest.",
        focus = Focus.both(0.3, Spot(Bone.CHEST, V(0.09, 0.17, 0.1))),
    )

    // 8. SHOULDERS
    val CROSS_BODY_SHOULDER = StretchMove(
        id = "shoulders_cross_body",
        name = "Cross-body shoulder stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Gently draw your arm across your chest without hiking your shoulder.",
        focus = Focus(0.26, Spot(Bone.ARM_L, V(0.0, 0.04, -0.04))),
        isUnilateral = true,
    )

    val OVERHEAD_TRICEPS = StretchMove(
        id = "shoulders_overhead_triceps",
        name = "Overhead triceps & shoulder",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Reach down the center of your back and guide your elbow with the other hand.",
        focus = Focus(0.26, Spot(Bone.ARM_L, V(0.0, 0.16, -0.04))),
        isUnilateral = true,
    )

    val SLEEPER_STRETCH = StretchMove(
        id = "shoulders_sleeper",
        name = "Sleeper shoulder stretch",
        targetArea = BodyArea.SHOULDERS,
        holdSec = 25,
        cue = "Lie on your side and gently guide your forearm down toward the mat.",
        focus = Focus(0.24, Spot(Bone.ARM_L, V(0.0, 0.03, -0.04))),
        isUnilateral = true,
    )

    // 9. LATS
    val CHILDS_POSE_LAT = StretchMove(
        id = "lats_childs_pose",
        name = "Child's pose lat reach",
        targetArea = BodyArea.LATS,
        holdSec = 30,
        cue = "Walk your hands to one side and sink your hips deeply back onto your heels.",
        focus = Focus(0.28, Spot(Bone.CHEST, V(0.13, 0.08, -0.05))),
        isUnilateral = true,
    )

    val OVERHEAD_SIDE_BEND = StretchMove(
        id = "lats_side_bend",
        name = "Standing side bend",
        targetArea = BodyArea.LATS,
        holdSec = 25,
        cue = "Clasp hands overhead and gently arc your ribcage to the side.",
        focus = Focus(0.28, Spot(Bone.CHEST, V(0.13, 0.08, -0.05))),
        isUnilateral = true,
    )

    // 10. SPINE
    val SUPINE_SPINAL_TWIST = StretchMove(
        id = "spine_supine_twist",
        name = "Supine spinal twist",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Drop your knees to the side while keeping both shoulders flat on the floor.",
        focus = Focus(0.36, Spot(Bone.WAIST, V(0.05, 0.08, -0.1))),
        isUnilateral = true,
    )

    val COBRA_POSE = StretchMove(
        id = "spine_cobra",
        name = "Cobra pose",
        targetArea = BodyArea.SPINE,
        holdSec = 30,
        cue = "Press the floor away to extend your thoracic spine without pinching your low back.",
        focus = Focus(0.36, Spot(Bone.WAIST, V(0.0, 0.12, 0.0))),
    )

    val SEATED_SPINAL_TWIST = StretchMove(
        id = "spine_seated_twist",
        name = "Seated spinal twist",
        targetArea = BodyArea.SPINE,
        holdSec = 25,
        cue = "Sit tall, elongate your spine, and rotate gently from your ribcage.",
        focus = Focus(0.36, Spot(Bone.WAIST, V(0.0, 0.16, -0.08))),
        isUnilateral = true,
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
