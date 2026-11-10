package dev.gzclp.tracker.ui

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
import dev.gzclp.tracker.engine.Day
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.ui.Type.tabular

data class SyncUi(val folder: String?, val lastSync: String?, val error: String?)

@Composable
fun SettingsScreen(
    started: Boolean,
    themeId: String,
    restTargets: Map<Tier, Int>,
    sync: SyncUi,
    notificationsAllowed: Boolean,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onRest: (Tier, Int) -> Unit,
    onAccessories: () -> Unit,
    onChooseFolder: () -> Unit,
    onRestore: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Txt("Settings", Type.largeTitle)

        GroupHeader("Theme")
        Group {
            Palettes.all.forEachIndexed { i, t ->
                GroupRow(first = i == 0, onClick = { onTheme(t.id) }) {
                    Txt(t.name, Type.body, Modifier.weight(1f))
                    if (t.id == themeId) Icon(Icons.check, p.accent, 20.dp)
                }
            }
        }

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

        if (started) {
            GroupHeader("Program")
            Group {
                GroupRow(first = true, onClick = onAccessories) {
                    Txt("Accessories", Type.body, Modifier.weight(1f))
                    Icon(Icons.chevronRight, p.fg3, 20.dp)
                }
            }
        }

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
                    Txt("Allow rest alerts", Type.body, color = p.accent)
                }
            }
            GroupFooter("Rest-over alerts need notifications to reach you with the screen off.")
        }
    }
}

@Composable
private fun MiniStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Pressable(onClick, Modifier.size(44.dp), CircleShape, p.fill, label = label) { Icon(icon, p.fg, 18.dp) }
}

@Composable
fun AccessoriesScreen(state: AppState, onBack: () -> Unit, onAdd: (Day) -> Unit, onRemove: (Day, String) -> Unit) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Txt("Accessories", Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt("T3 lifts per workout, 3×15+. Weight goes up 5 lb when the last set reaches 25.", Type.subhead, color = p.fg2)
        Day.entries.forEach { day ->
            GroupHeader("${day.name} · ${day.t1.label}, ${day.t2.label}")
            Group {
                val ids = state.dayT3[day].orEmpty()
                ids.mapNotNull { state.t3[it] }.forEachIndexed { i, t ->
                    GroupRow(first = i == 0) {
                        Column(Modifier.weight(1f)) {
                            Txt(t.name, Type.body, maxLines = 1)
                            Txt(Fmt.lb(t.state.weight), Type.footnote.tabular(), color = p.fg2)
                        }
                        IconButton(Icons.x, "Remove ${t.name}", { onRemove(day, t.id) }, tint = p.fg2)
                    }
                }
                GroupRow(first = ids.isEmpty(), onClick = { onAdd(day) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.plus, p.accent, 20.dp)
                        Spacer(Modifier.width(Space.sm))
                        Txt("Add accessory", Type.body, color = p.accent)
                    }
                }
            }
        }
    }
}
