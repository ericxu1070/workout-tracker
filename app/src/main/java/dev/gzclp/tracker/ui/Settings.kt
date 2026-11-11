package dev.gzclp.tracker.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Slot
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.ui.Type.tabular

data class SyncUi(val folder: String?, val lastSync: String?, val error: String?)

/** [message] explains a failed write or why writing turned off. */
data class HealthUi(val on: Boolean, val message: String?)

@Composable
fun SettingsScreen(
    started: Boolean,
    programId: String,
    increments: Increments,
    themeId: String,
    laptop: Palette?,
    restTargets: Map<Tier, Int>,
    sync: SyncUi,
    notificationsAllowed: Boolean,
    weighInReminder: Boolean,
    weighInMinutes: Int,
    health: HealthUi,
    plates: Plates,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onRest: (Tier, Int) -> Unit,
    onProgram: (String) -> Unit,
    onIncrements: (Increments) -> Unit,
    onDays: () -> Unit,
    onChooseFolder: () -> Unit,
    onRestore: () -> Unit,
    onAllowNotifications: () -> Unit,
    onWeighInReminder: (Boolean) -> Unit,
    onWeighInTime: (Int) -> Unit,
    onHealthConnect: (Boolean) -> Unit,
    onPlates: (Plates) -> Unit,
    warmups: Boolean = true,
    onWarmups: (Boolean) -> Unit = {},
) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Txt("Settings", Type.largeTitle)

        if (started) {
            GroupHeader("Program")
            ProgramPicker(programId, onProgram)
            Spacer(Modifier.height(Space.lg))
            Group {
                GroupRow(first = true, onClick = onDays) {
                    Txt("Exercises per workout", Type.body, Modifier.weight(1f))
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
            GroupFooter("Switching keeps your history. Each exercise keeps its own progress.")

            GroupHeader("Cycle steps")
            Group {
                val rows = listOf(
                    Triple("Upper body", increments.upperStep) { v: Int -> increments.copy(upperStep = v) },
                    Triple("Lower body", increments.lowerStep) { v: Int -> increments.copy(lowerStep = v) },
                    Triple("Next cycle", increments.cycleBump) { v: Int -> increments.copy(cycleBump = v) },
                )
                rows.forEachIndexed { i, (label, v, with) ->
                    GroupRow(first = i == 0) {
                        Txt(label, Type.body, Modifier.weight(1f), maxLines = 1)
                        MiniStep(Icons.minus, "Smaller step for $label") { onIncrements(with((v - 5).coerceAtLeast(0))) }
                        Txt("+$v lb", Type.body.tabular(), Modifier.width(72.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                        MiniStep(Icons.plus, "Larger step for $label") { onIncrements(with((v + 5).coerceAtMost(50))) }
                    }
                }
            }
            GroupFooter("Cycle programs add a step each week: 3×12, then 3×10, then 3×8. Lower body covers squat, deadlift, Romanian deadlift, and leg press; everything else uses the upper body step. After a heavy week with every set at 8 or more, the next cycle starts higher by the next-cycle amount.")
        }

        GroupHeader("Bar and plates")
        Group {
            GroupRow(first = true) {
                Txt("Bar", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Lighter bar") { onPlates(plates.copy(bar = (plates.bar - 5).coerceAtLeast(5))) }
                Txt(Fmt.lb(plates.bar), Type.body.tabular(), Modifier.widthIn(min = 96.dp).padding(horizontal = Space.sm), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Heavier bar") { onPlates(plates.copy(bar = (plates.bar + 5).coerceAtMost(100))) }
            }
            Plates.SIZES.forEach { size ->
                val n = plates.pairs[size] ?: 0
                val name = "${Plates.label(size)} lb"
                GroupRow(first = false) {
                    Txt("$name plates", Type.body, Modifier.weight(1f), maxLines = 1)
                    MiniStep(Icons.minus, "One less pair of $name plates") { onPlates(plates.copy(pairs = plates.pairs + (size to (n - 1).coerceAtLeast(0)))) }
                    Txt(if (n == 1) "1 pair" else "$n pairs", Type.body.tabular(), Modifier.widthIn(min = 96.dp).padding(horizontal = Space.sm), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.Center)
                    MiniStep(Icons.plus, "One more pair of $name plates") { onPlates(plates.copy(pairs = plates.pairs + (size to (n + 1).coerceAtMost(20)))) }
                }
            }
        }
        GroupFooter("Barbell lifts round each target to the nearest weight this bar and these plates make, and every set shows the plates for one side.")

        GroupHeader("Theme")
        Group {
            Palettes.all.forEachIndexed { i, t ->
                GroupRow(first = i == 0, onClick = { onTheme(t.id) }) {
                    Txt(t.name, Type.body, Modifier.weight(1f))
                    if (t.id == themeId) Icon(Icons.check, p.accent, 20.dp)
                }
            }
            GroupRow(first = false, onClick = { onTheme(LaptopTheme.ID) }) {
                Column(Modifier.weight(1f)) {
                    Txt("Laptop", Type.body)
                    Txt(laptop?.name?.split('-')?.joinToString(" ") { it.replaceFirstChar(Char::uppercase) } ?: if (sync.folder == null) "Choose a sync folder first" else "Not found in the sync folder", Type.footnote, color = p.fg2, maxLines = 1)
                }
                if (themeId == LaptopTheme.ID) Icon(Icons.check, p.accent, 20.dp)
            }
        }
        GroupFooter("Laptop follows the Omarchy theme your laptop syncs to this folder, updated each time you open the app.")

        GroupHeader("Rest timer")
        Group {
            Tier.entries.forEachIndexed { i, tier ->
                GroupRow(first = i == 0) {
                    Txt(tier.label, Type.body, Modifier.weight(1f))
                    val v = restTargets.getValue(tier)
                    MiniStep(Icons.minus, "Shorter ${tier.label} rest") { onRest(tier, (v - 15).coerceAtLeast(15)) }
                    Txt(Fmt.clock(v.toLong()), Type.body.tabular(), Modifier.width(56.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                    MiniStep(Icons.plus, "Longer ${tier.label} rest") { onRest(tier, (v + 15).coerceAtMost(600)) }
                }
            }
        }

        GroupHeader("Warm-ups")
        Group {
            GroupRow(first = true) {
                Txt("T1 warm-up sets", Type.body, Modifier.weight(1f))
                Toggle(warmups, "T1 warm-up sets", onWarmups)
            }
        }
        GroupFooter("Before each GZCLP T1 lift: 40%×5, 60%×5, and 80%×3 of the first working set, rounded to 5 lb. They are not logged and never count toward progression.")

        GroupHeader("Weigh-in")
        Group {
            GroupRow(first = true) {
                Txt("Morning reminder", Type.body, Modifier.weight(1f))
                Toggle(weighInReminder, "Morning reminder", onWeighInReminder)
            }
            if (weighInReminder) GroupRow(first = false) {
                Txt("Time", Type.body, Modifier.weight(1f))
                MiniStep(Icons.minus, "Earlier reminder") { onWeighInTime((weighInMinutes - 15).mod(24 * 60)) }
                Txt(Fmt.clockTime(weighInMinutes), Type.body.tabular(), Modifier.width(96.dp).padding(horizontal = Space.sm), align = androidx.compose.ui.text.style.TextAlign.Center)
                MiniStep(Icons.plus, "Later reminder") { onWeighInTime((weighInMinutes + 15) % (24 * 60)) }
            }
        }
        GroupFooter(
            when {
                !weighInReminder -> "Turn on for a daily nudge to log your weight."
                !notificationsAllowed -> "Allow notifications below to get the reminder."
                else -> "Skipped on days you have already weighed in."
            },
        )

        GroupHeader("Health Connect")
        Group {
            GroupRow(first = true) {
                Txt("Write workouts and weight", Type.body, Modifier.weight(1f))
                Toggle(health.on, "Write workouts and weight to Health Connect", onHealthConnect)
            }
        }
        GroupFooter(
            health.message ?: if (health.on) "Each finished workout and weigh-in goes to Health Connect from now on. Nothing is read back."
            else "Off. Turn on to send finished workouts and weigh-ins to Health Connect.",
        )

        GroupHeader("Sync folder")
        Group {
            GroupRow(first = true, onClick = onChooseFolder) {
                Txt("Folder", Type.body, Modifier.weight(1f))
                Txt(sync.folder ?: "Choose", Type.body, color = if (sync.folder == null) p.accent else p.fg2, maxLines = 1)
            }
            if (!started) {
                GroupRow(first = false, onClick = onRestore) {
                    Txt("Restore from folder", Type.body, color = p.accent)
                }
            }
        }
        GroupFooter(
            when {
                sync.folder == null -> "The log stays on this phone until you choose a folder for Syncthing."
                sync.error != null -> "Last sync failed: ${sync.error}. Logging continues on this phone."
                sync.lastSync != null -> "Last synced ${sync.lastSync}"
                else -> "Not synced yet"
            },
        )

        if (!notificationsAllowed) {
            GroupHeader("Notifications")
            Group {
                GroupRow(first = true, onClick = onAllowNotifications) {
                    Txt("Allow notifications", Type.body, color = p.accent)
                }
            }
            GroupFooter("Rest-over alerts and the weigh-in reminder need notifications to reach you with the screen off.")
        }
    }
}

@Composable
fun DaysScreen(state: AppState, onBack: () -> Unit, onAdd: (ProgramDay) -> Unit, onRemove: (ProgramDay, String) -> Unit) {
    val p = LocalPalette.current
    val program = state.program
    ScreenFrame {
        NavRow(onBack)
        Txt(program.name, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt(
            if (program.gzclp) "T3 lifts per workout, 3×15+. Weight goes up 5 lb when the last set reaches 25."
            else "Exercises per workout. Each one runs the 3-week cycle.",
            Type.subhead, color = p.fg2,
        )
        program.days.forEach { day ->
            val fixed = state.slots(program, day).filter { program.gzclp && it.tier != Tier.T3 }
            GroupHeader(day.title + fixed.joinToString("") { ", ${state.name(it.ex)} ${it.tier.label}" })
            Group {
                val ids = Commands.dayList(state, program, day)
                ids.forEachIndexed { i, id ->
                    val ex = state.exercise(Slot(id, if (program.gzclp) Tier.T3 else Tier.CYCLE))
                    GroupRow(first = i == 0) {
                        Column(Modifier.weight(1f)) {
                            Txt(ex.name, Type.body, maxLines = 1)
                            Txt(ex.weight?.let(Fmt::lb) ?: "Starting weight asked at the workout", Type.footnote.tabular(), color = p.fg2)
                        }
                        IconButton(Icons.x, "Remove ${ex.name}", { onRemove(day, id) }, tint = p.fg2)
                    }
                }
                GroupRow(first = ids.isEmpty(), onClick = { onAdd(day) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.plus, p.accent, 20.dp)
                        Spacer(Modifier.width(Space.sm))
                        Txt("Add exercise", Type.body, color = p.accent)
                    }
                }
            }
        }
    }
}
