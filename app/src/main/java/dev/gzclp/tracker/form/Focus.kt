package dev.gzclp.tracker.form

/** A point where a stretch is felt: [at] in [bone]'s frame, for the figure standing in [STAR]. */
class Spot(val bone: Bone, val at: V) {
    /** The same point on the other half of the body. */
    fun mirrored() = Spot(bone.mirrored(), V(-at.x, at.y, at.z))
}

private fun Bone.mirrored() = when {
    name.endsWith("_L") -> Bone.valueOf(name.dropLast(1) + "R")
    name.endsWith("_R") -> Bone.valueOf(name.dropLast(1) + "L")
    else -> this
}

/**
 * The figure with its arms straight out and its legs apart: distances in it run along the body, so a hand resting on a
 * shin, or one leg beside the other, sits far from it.
 */
private val STAR = Poser().apply {
    pelvis(V(0.0, STAND, 0.0))
    for (s in SIDES) {
        arm(s, raise = 90.0, plane = 0.0)
        leg(s, raise = 35.0, plane = 0.0)
    }
}

/**
 * Where a stretch is felt: full strength within half of [reach] of its [spots], fading smoothly to nothing [reach] metres
 * away along the body. Each surface's shares are worked out once, on the [STAR], so they follow the body through any pose.
 */
class Focus(val reach: Double, vararg val spots: Spot) {
    private val points = spots.map { STAR[it.bone].at(it.at.x, it.at.y, it.at.z) }
    private val made = HashMap<Pair<Mesh, Bone>, FloatArray>()

    /** How strongly the point [p] of the [STAR] feels the stretch, 0..1. */
    fun at(p: V) = points.maxOf { jerk(2 * (1 - (p - it).len / reach)) }

    /** Each vertex's share, 0..1, of [skin] bent by [bones]. */
    fun of(skin: Skin, bones: Array<Bone>): FloatArray = made.getOrPut(skin.mesh to bones[0]) {
        val pos = FloatArray(skin.mesh.verts * 3)
        skin.pose(STAR, bones, pos)
        FloatArray(skin.mesh.verts) { at(V(pos[it * 3].toDouble(), pos[it * 3 + 1].toDouble(), pos[it * 3 + 2].toDouble())).toFloat() }
    }

    /** Each vertex's share, 0..1, of [bone]'s own rigid [mesh]. */
    fun of(mesh: Mesh, bone: Bone): FloatArray = made.getOrPut(mesh to bone) {
        val out = FloatArray(mesh.verts)
        var i = 0
        mesh.each(STAR[bone]) { x, y, z -> out[i++] = at(V(x, y, z)).toFloat() }
        out
    }

    /** Each vertex's share, 0..1, of the [Figure] mesh. */
    val figure: FloatArray by lazy {
        val pos = FloatArray(Figure.mesh.verts * 3)
        Figure.pose(STAR, pos)
        FloatArray(Figure.mesh.verts) { at(V(pos[it * 3].toDouble(), pos[it * 3 + 1].toDouble(), pos[it * 3 + 2].toDouble())).toFloat() }
    }

    companion object {
        /** The same spot on both sides of the body, for a stretch that works both at once. */
        fun both(reach: Double, spot: Spot) = Focus(reach, spot, spot.mirrored())
    }
}
