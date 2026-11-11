package dev.gzclp.tracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.OffsetDateTime

/** What I ate on [day]: one photo per meal, oldest first, with camera and gallery buttons for the next one. */
@Composable
fun FoodScreen(meals: List<Meal>, day: LocalDate, today: LocalDate, error: String?, onDay: (LocalDate) -> Unit, onSnap: () -> Unit, onUpload: () -> Unit, onOpen: (Meal) -> Unit) {
    val p = LocalPalette.current
    val shown = meals.filter { it.at.toLocalDate() == day }
    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Txt("Food", Type.largeTitle)
        Spacer(Modifier.height(Space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniStep(Icons.chevronLeft, "Day before") { onDay(day.minusDays(1)) }
            Txt(Fmt.day(day, today), Type.headline, Modifier.weight(1f), align = TextAlign.Center)
            MiniStep(Icons.chevronRight, "Day after", enabled = day < today) { onDay(day.plusDays(1)) }
        }
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PillButton("Take photo", onSnap, Modifier.weight(1f), primary = true, icon = Icons.camera)
            PillButton("Upload", onUpload, Modifier.weight(1f), icon = Icons.image)
        }
        if (error != null) GroupFooter(error, color = p.red)
        if (shown.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Txt(if (day == today) "No meals yet today" else "No meals logged", Type.headline)
            return@ScreenFrame
        }
        GroupHeader("${shown.size} ${if (shown.size == 1) "meal" else "meals"}")
        Group {
            shown.forEachIndexed { i, m ->
                GroupRow(first = i == 0, onClick = { onOpen(m) }) {
                    MealPhoto(m, 240, Modifier.size(72.dp).clip(RoundedCornerShape(Radius.md)))
                    Txt(Fmt.time(m.at), Type.body.tabular(), Modifier.weight(1f).padding(start = Space.lg), maxLines = 1)
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
        }
    }
}

/** A meal's photo, decoded to about [maxSide] pixels and cropped to fill [modifier]; [ownShape] sizes it to the photo (portrait capped at 3:4). */
@Composable
fun MealPhoto(meal: Meal, maxSide: Int, modifier: Modifier, ownShape: Boolean = false) {
    // shortcut: decodes on the main thread during composition; fine for a day's few ~150 KB photos, move to a background loader if days get long.
    // The upload file is reused for every pick, so its modified time keys a new decode.
    val bmp = remember(meal.file, meal.file.lastModified(), maxSide) { FoodLog.downscale(meal.file, maxSide)?.asImageBitmap() }
    if (bmp == null) Box(modifier.background(LocalPalette.current.fill))
    else Image(
        bmp, "Meal at ${Fmt.time(meal.at)}",
        if (ownShape) modifier.aspectRatio(maxOf(bmp.width.toFloat() / bmp.height, 0.75f)) else modifier, contentScale = ContentScale.Crop,
    )
}

/** Why a picked photo's own time can't be the meal's, or null when it can. */
fun photoTimeNote(taken: OffsetDateTime?, now: OffsetDateTime): String? = when {
    taken == null -> "This photo doesn't say when it was taken. Set when you ate it."
    taken.isAfter(now) -> "This photo's time is in the future. Set when you ate it."
    !FoodLog.feasible(taken, now) -> "This photo was taken ${Fmt.date(taken)}, over a week ago. Set when you ate it."
    else -> null
}

/** A picked photo before it is saved: its day and time, which the user can change. */
@Composable
fun AddMealSheet(s: SheetState.AddMeal, onTime: () -> Unit, update: (SheetState) -> Unit, onSave: () -> Unit) {
    val today = s.now.toLocalDate()
    MealPhoto(Meal(s.photo, s.at), 640, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(Radius.lg)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniStep(Icons.chevronLeft, "Day before") { update(s.copy(at = s.at.minusDays(1))) }
        Txt(Fmt.day(s.at.toLocalDate(), today), Type.body, Modifier.weight(1f), align = TextAlign.Center)
        MiniStep(Icons.chevronRight, "Day after", enabled = !s.at.plusDays(1).isAfter(s.now)) { update(s.copy(at = s.at.plusDays(1))) }
    }
    PillButton(Fmt.time(s.at), onTime, Modifier.fillMaxWidth(), onSurface = true)
    val future = s.at.isAfter(s.now)
    PillButton(if (future) "Time is in the future" else "Save meal", onSave, Modifier.fillMaxWidth(), primary = true, enabled = !future)
}

/** The meal sheet: the photo large, with Delete. */
@Composable
fun MealSheet(meal: Meal, today: LocalDate, onDelete: () -> Unit, onDone: () -> Unit) {
    Txt(Fmt.weighInTime(meal.at, today), Type.title2)
    MealPhoto(meal, FoodLog.MAX_SIDE, Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)), ownShape = true)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PillButton("Delete", onDelete, Modifier.weight(1f), onSurface = true)
        PillButton("Done", onDone, Modifier.weight(1f), primary = true)
    }
}
