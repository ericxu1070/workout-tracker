package dev.gzclp.tracker

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.hasWeighIn
import dev.gzclp.tracker.remind.WeighIn
import dev.gzclp.tracker.rest.RestAlarm
import dev.gzclp.tracker.sync.Mirror
import dev.gzclp.tracker.ui.Fmt
import dev.gzclp.tracker.ui.RestUi
import dev.gzclp.tracker.ui.SyncUi
import dev.gzclp.tracker.ui.restLabel
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Process-wide owner of the store, settings and sync; outlives activity recreation. */
class AppModel private constructor(private val context: Context) {
    val prefs = Prefs(context)
    val store = Store(File(context.filesDir, Mirror.NAME))
    private val mirror = Mirror(context, prefs)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var theme by mutableStateOf(prefs.theme)
        private set
    var restTargets by mutableStateOf(prefs.restTargets())
        private set
    var sync by mutableStateOf(syncUi())
        private set
    var notice by mutableStateOf<String?>(null)
    var notificationsAllowed by mutableStateOf(true)
        private set
    var weighInReminder by mutableStateOf(prefs.weighInReminder)
        private set
    var weighInMinutes by mutableStateOf(prefs.weighInMinutes)
        private set
    /** Set when the weigh-in notification is tapped; the UI opens the log sheet and clears it. */
    var logWeightRequested by mutableStateOf(false)

    init {
        WeighIn.schedule(context)
    }

    fun now(): OffsetDateTime = OffsetDateTime.now().withNano(0)

    fun dispatch(build: (AppState) -> List<Event>) {
        store.dispatch(now(), build)
        syncLater()
    }

    fun syncLater() {
        io.execute {
            mirror.sync(store.bytes())
            main.post { sync = syncUi() }
        }
    }

    fun refresh() {
        notificationsAllowed = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        sync = syncUi()
        syncLater()
    }

    fun chooseTheme(id: String) {
        prefs.theme = id
        theme = id
    }

    fun setRest(tier: Tier, sec: Int) {
        prefs.setRest(tier, sec)
        restTargets = prefs.restTargets()
    }

    fun chooseWeighInReminder(on: Boolean) {
        prefs.weighInReminder = on
        weighInReminder = on
        WeighIn.schedule(context)
    }

    fun chooseWeighInTime(minutes: Int) {
        prefs.weighInMinutes = minutes
        weighInMinutes = minutes
        WeighIn.schedule(context)
    }

    fun dispatchWeighIn(build: (AppState) -> List<Event>) {
        dispatch(build)
        if (store.state.value.hasWeighIn(java.time.LocalDate.now())) WeighIn.dismiss(context)
    }

    fun setFolder(uri: Uri) {
        prefs.folderUri = uri.toString()
        sync = syncUi()
    }

    fun hasFolder() = prefs.folderUri != null

    /** Reads the mirrored log from the chosen folder and rebuilds local state from it. */
    fun restore() {
        val uri = prefs.folderUri?.let(Uri::parse) ?: return
        io.execute {
            val msg = try {
                val text = mirror.read(uri) ?: error("No ${Mirror.NAME} in that folder")
                val n = store.restore(text)
                prefs.mirroredBytes = -1
                mirror.sync(store.bytes())
                "Restored $n log entries"
            } catch (e: Exception) {
                e.message ?: "Restore failed"
            }
            main.post {
                notice = msg
                sync = syncUi()
            }
        }
    }

    fun startRest(ex: Exercise, setIndex: Int) = RestAlarm.start(context, restTargets.getValue(ex.tier), restLabel(ex, setIndex))
    fun cancelRest() = RestAlarm.cancel(context)

    fun rest(nowMillis: Long): RestUi? {
        val end = prefs.restEndMillis
        if (end == 0L) return null
        return RestUi(((end - nowMillis + 999) / 1000).coerceAtLeast(0), prefs.restTotalSec, prefs.restLabel)
    }

    private fun syncUi() = SyncUi(
        folder = prefs.folderUri?.let { mirror.folderName() ?: "Chosen folder" },
        lastSync = prefs.lastSyncMillis.takeIf { it > 0 }?.let {
            val t = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toOffsetDateTime()
            "${Fmt.date(t)}, ${Fmt.time(t)}"
        },
        error = prefs.lastSyncError,
    )

    companion object {
        @Volatile private var instance: AppModel? = null
        fun get(context: Context): AppModel = instance ?: synchronized(this) {
            instance ?: AppModel(context.applicationContext).also { instance = it }
        }
    }
}
