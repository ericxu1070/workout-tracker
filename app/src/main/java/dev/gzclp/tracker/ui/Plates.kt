package dev.gzclp.tracker.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.log.Exercise

/** The bar and plates set in Settings. */
val LocalPlates = staticCompositionLocalOf { Plates() }

/** The prescription as loaded: a barbell lift's target rounds to the nearest weight the plates make. The log keeps the engine's weight. */
fun Plates.prescribe(ex: Exercise): Exercise =
    if (ex.id in Catalog.barbell && ex.weight != null) ex.copy(weight = round(ex.weight)) else ex

/** "45, 25, 2.5 a side", "Empty bar", or null when the plates can't make [total]. */
fun Plates.perSideLabel(total: Int): String? = perSide(total)?.let { side ->
    if (side.isEmpty()) "Empty bar" else side.joinToString(", ", postfix = " a side") { Plates.label(it) }
}
