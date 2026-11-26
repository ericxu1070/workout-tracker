package dev.gzclp.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.AppModel
import dev.gzclp.tracker.food.Classifier
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.food.Nutrition
import dev.gzclp.tracker.food.Prediction
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.FoodEstimate
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.OffsetDateTime

/** What I ate on [day]: one photo per meal, oldest first, with camera and gallery buttons for the next one. */
@Composable
fun FoodScreen(
    meals: List<Meal>,
    day: LocalDate,
    today: LocalDate,
    error: String?,
    onDay: (LocalDate) -> Unit,
    onSnap: () -> Unit,
    onUpload: () -> Unit,
    onOpen: (Meal) -> Unit,
) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val model = remember(context) { runCatching { AppModel.get(context) }.getOrNull() }
    val storeState = model?.store?.state?.collectAsState()?.value
    var showWaterTracker by rememberSaveable { mutableStateOf(false) }
    if (showWaterTracker && model != null && storeState != null) {
        BackHandler(LocalTabShown.current.value) { showWaterTracker = false }
        WaterScreen(
            state = storeState,
            day = day,
            goalMl = model.waterGoalMl(storeState, day),
            cupSizeMl = model.waterCupMl,
            onBack = { showWaterTracker = false },
            // A past day's drink is stamped at this time of day on that date, so it lands on the day being viewed.
            onLogWater = { ml -> model.dispatch { WaterLog.logWater(it, ml, model.now().with(day)) } },
            onClearWater = { id -> model.dispatch { WaterLog.clearWater(id) } },
            onCupSizeChange = model::chooseWaterCup,
            pace = model.waterReminder,
        )
        return
    }
    val currentEstimates = storeState?.confirmedEstimates().orEmpty()
    // Only a tapped or edited estimate is logged (confirmed); the classifier's guesses stay in the meal sheet.
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

        Spacer(Modifier.height(Space.md))
        GroupHeader("Water")
        Group {
            GroupRow(first = true, onClick = { showWaterTracker = true }) {
                val intake = storeState?.let { WaterLog.totalMlForDay(it, day) } ?: 0
                val goal = if (model != null && storeState != null) model.waterGoalMl(storeState, day) else WaterLog.DEFAULT_GOAL_ML
                val frac = if (goal > 0) (intake.toFloat() / goal).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.size(24.dp)) {
                    val w = 3.dp.toPx()
                    val inset = w / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                    val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(p.separator, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                    drawArc(p.accent, -90f, 360f * frac, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Txt("Water intake", Type.body)
                    Txt("$intake / $goal ml", Type.footnote, color = p.fg2)
                }
                Icon(Icons.chevronRight, p.fg3, 20.dp)
            }
        }
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
                    Column(Modifier.weight(1f).padding(start = Space.lg)) {
                        // Named once estimated, so the list scans by what was eaten; the time drops to the subtitle.
                        val est = currentEstimates[m.file.name]
                        if (est == null) Txt(Fmt.time(m.at), Type.body.tabular(), maxLines = 1)
                        else {
                            Txt(est.label, Type.body, maxLines = 1)
                            Txt("${Fmt.time(m.at)} · ${est.kcalLow}–${est.kcalHigh} kcal", Type.footnote.tabular(), color = p.fg2, maxLines = 1)
                        }
                    }
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
        }
    }
}

/** A meal's photo, decoded to about [maxSide] pixels and cropped to fill [modifier]; [ownShape] sizes it to the photo (portrait capped at 3:4). */
@Composable
fun MealPhoto(meal: Meal, maxSide: Int, modifier: Modifier, ownShape: Boolean = false) {
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

/** The meal sheet: the photo large, with Delete, and top 3 calorie estimates with confirmation/editing. */
@Composable
fun MealSheet(
    meal: Meal,
    today: LocalDate,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val model = remember(context) { runCatching { AppModel.get(context) }.getOrNull() }
    val storeState = model?.store?.state?.collectAsState()?.value
    val dispatchEstimate: (FoodEstimate) -> Unit = { est -> model?.dispatch { listOf(est) } }
    val currentEst = storeState?.confirmedEstimates()?.get(meal.file.name)

    var suggestions by remember { mutableStateOf<List<Prediction>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var editLabel by remember(currentEst) { mutableStateOf(currentEst?.label ?: "") }
    var editLow by remember(currentEst) { mutableStateOf(currentEst?.kcalLow ?: 500) }
    var editHigh by remember(currentEst) { mutableStateOf(currentEst?.kcalHigh ?: 700) }

    LaunchedEffect(meal.file.name) { suggestions = Classifier.classify(context, meal.file, topK = 3) }

    Txt(Fmt.weighInTime(meal.at, today), Type.title2)
    MealPhoto(meal, FoodLog.MAX_SIDE, Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)), ownShape = true)

    Spacer(Modifier.height(Space.md))
    if (editing) {
        Txt("Edit estimate", Type.headline)
        Spacer(Modifier.height(Space.sm))
        BasicTextField(
            value = editLabel,
            onValueChange = { editLabel = it },
            textStyle = Type.body.copy(color = p.fg),
            modifier = Modifier
                .fillMaxWidth()
                .background(p.fill, RoundedCornerShape(Radius.md))
                .padding(Space.md),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (editLabel.isEmpty()) Txt("Food name", Type.body, color = p.fg3)
                innerTextField()
            }
        )
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Txt("Low", Type.caption, color = p.fg2)
                Spacer(Modifier.height(4.dp))
                Stepper(
                    value = editLow.toString(),
                    onMinus = { if (editLow > 50) editLow -= 25 },
                    onPlus = { editLow += 25; if (editHigh <= editLow) editHigh = editLow + 50 },
                    unit = "kcal"
                )
            }
            Column(Modifier.weight(1f)) {
                Txt("High", Type.caption, color = p.fg2)
                Spacer(Modifier.height(4.dp))
                Stepper(
                    value = editHigh.toString(),
                    onMinus = { if (editHigh - 25 > editLow) editHigh -= 25 },
                    onPlus = { editHigh += 25 },
                    unit = "kcal"
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PillButton("Cancel", { editing = false }, Modifier.weight(1f), onSurface = true)
            PillButton(
                "Save estimate",
                {
                    val validLabel = editLabel.trim().ifEmpty { "Meal" }
                    val finalLow = editLow.coerceAtLeast(50)
                    val finalHigh = maxOf(finalLow + 50, editHigh)
                    dispatchEstimate(FoodEstimate(meal.file.name, validLabel, finalLow, finalHigh, confirmed = true))
                    editing = false
                },
                Modifier.weight(1f),
                primary = true
            )
        }
    } else {
        if (currentEst != null) {
            GroupHeader("Confirmed estimate")
            Group {
                GroupRow(first = true) {
                    Column(Modifier.weight(1f)) {
                        Txt(currentEst.label, Type.headline)
                        Txt("${currentEst.kcalLow}–${currentEst.kcalHigh} kcal", Type.subhead, color = p.fg2)
                    }
                    SmallPill("Edit", {
                        editLabel = currentEst.label
                        editLow = currentEst.kcalLow
                        editHigh = currentEst.kcalHigh
                        editing = true
                    })
                }
            }
            Spacer(Modifier.height(Space.sm))
        }

        val showSuggestions = suggestions
        if (showSuggestions.isNotEmpty()) GroupHeader(if (currentEst != null) "Other suggestions" else "Top estimates")
        if (showSuggestions.isNotEmpty()) Group {
            showSuggestions.forEachIndexed { idx, pred ->
                GroupRow(first = idx == 0, onClick = {
                    dispatchEstimate(pred.toEstimate(meal.file.name))
                }) {
                    Column(Modifier.weight(1f)) {
                        Txt(pred.label, Type.body)
                        Txt(pred.kcalRange.formatted, Type.caption, color = p.fg2)
                    }
                    if (currentEst?.label == pred.label) {
                        Icon(Icons.check, p.accent, 18.dp)
                    } else {
                        Icon(Icons.chevronRight, p.fg3, 16.dp)
                    }
                }
            }
        }
        if (currentEst == null) {
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SmallPill("Custom estimate", {
                    val initial = currentEst ?: showSuggestions.firstOrNull()?.let {
                        FoodEstimate(meal.file.name, it.label, it.kcalRange.low, it.kcalRange.high, false)
                    }
                    editLabel = initial?.label ?: ""
                    editLow = initial?.kcalLow ?: 500
                    editHigh = initial?.kcalHigh ?: 700
                    editing = true
                })
            }
        }
    }

    Spacer(Modifier.height(Space.md))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PillButton("Delete meal", onDelete, Modifier.weight(1f), onSurface = true, danger = true)
        PillButton("Done", onDone, Modifier.weight(1f), primary = true)
    }
}

/** Unconfirmed guesses an earlier build logged are not the user's answer, so they never show. */
private fun dev.gzclp.tracker.log.AppState.confirmedEstimates(): Map<String, FoodEstimate> = foodEstimates.filterValues { it.confirmed }
