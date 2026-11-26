package dev.gzclp.tracker.form

/** The moves the famous routines need: Limber 11, Simple 6, Starting to Stretch, Sun salutation A, and legs up the wall. */
internal val MOBILITY: Map<String, () -> Motion> = mapOf(
    "hamstrings_legs_up_wall" to {
        // On the back with the hips close to a wall: the feet slide up it from bent knees until the legs rest straight, the arms out and palms up.
        stretch { d ->
            pelvis(V(0.0, 0.3, 0.0), pitch = -90.0)
            spine(waist = -4.0, chest = 2.0)
            head(nod = 8.0)
            rest(0.0)
            for (s in SIDES) {
                leg(s, raise = lerp(115.0, 86.0, d), plane = 91.0, knee = lerp(59.0, 3.0, d), ankle = lerp(-50.0, -5.0, d))
                reachFloor(s, V(s * 0.8, 0.0, -0.6), 0.53, V(0.0, -0.2, 1.0))
            }
            block(Frame.WORLD.moved(V(0.0, 0.7, 0.18)), 1.2, 1.4, 0.1)
        }
    },
)
