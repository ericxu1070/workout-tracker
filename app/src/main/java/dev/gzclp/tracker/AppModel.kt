package dev.gzclp.tracker

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.health.HealthRecords
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Entry
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.hasWeighIn
import dev.gzclp.tracker.remind.WeighIn
import dev.gzclp.tracker.rest.RestAlarm
import dev.gzclp.tracker.sync.Mirror
import dev.gzclp.tracker.ui.Fmt
import dev.gzclp.tracker.ui.HealthUi
import dev.gzclp.tracker.ui.LaptopTheme
import dev.gzclp.tracker.ui.Palette
import dev.gzclp.tracker.ui.Palettes
import dev.gzclp.tracker.ui.RestUi
import dev.gzclp.tracker.ui.SyncUi
import dev.gzclp.tracker.ui.restLabel
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/** Process-wide owner of the store, settings and sync; outlives activity recreation. */
class AppModel private constructor(private val context: Context) {
    val prefs = Prefs(context)
    val store = Store(File(context.filesDir, Mirror.NAME))
    private val mirror = Mirror(context, prefs)
    /** Meal photos stay in app-private files; [mirror] never sees them. */
    private val food = FoodLog(File(context.filesDir, "food"))
    /** Where the camera writes a photo before [addMeal] downscales it into [food]. */
    val capture = File(context.cacheDir, "camera/meal.jpg")
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var theme by mutableStateOf(prefs.theme)
        private set
    /** Null until a laptop theme file has been read once. */
    var laptop by mutableStateOf(prefs.laptopTheme?.let(LaptopTheme::parse))
        private set
    val palette: Palette get() = if (theme == LaptopTheme.ID) laptop ?: Palettes.jade else Palettes.of(theme)
    var restTargets by mutableStateOf(prefs.restTargets())
        private set
    var plates by mutableStateOf(prefs.plates)
        private set
    var sync by mutableStateOf(syncUi())
        private set
    var notice by mutableStateOf<String?>(null)
    var notificationsAllowed by mutableStateOf(true)
        private set
    var warmups by mutableStateOf(prefs.warmups)
        private set
    var weighInReminder by mutableStateOf(prefs.weighInReminder)
        private set
    var weighInMinutes by mutableStateOf(prefs.weighInMinutes)
        private set
    var health by mutableStateOf(HealthUi(prefs.healthConnect, null))
        private set
    var meals by mutableStateOf(food.meals())
        private set
    /** Why the last photo was not saved, shown on the Food tab until the next one. */
    var mealError by mutableStateOf<String?>(null)
    /** Set when the weigh-in notification is tapped; the UI opens the log sheet and clears it. */
    var logWeightRequested by mutableStateOf(false)

    init {
        WeighIn.schedule(context)
    }

    fun now(): OffsetDateTime = OffsetDateTime.now().withNano(0)

    fun dispatch(build: (AppState) -> List<Event>) {
        val before = store.state.value
        val entries = store.dispatch(now(), build)
        syncLater()
        if (prefs.healthConnect && entries.isNotEmpty()) writeHealth(before, entries)
    }

    /** Best effort: a failed write is shown in Settings and never blocks logging. Not retried. */
    private fun writeHealth(before: AppState, entries: List<Entry>) = io.execute {
        val error = runCatching {
            val w = HealthRecords.of(before, entries)
            if (!w.isEmpty) runBlocking {
                val client = HealthConnectClient.getOrCreate(context)
                if (w.upserts.isNotEmpty()) client.insertRecords(w.upserts)
                if (w.deletedWeights.isNotEmpty()) client.deleteRecords(WeightRecord::class, emptyList(), w.deletedWeights)
            }
        }.exceptionOrNull()
        main.post { if (health.on) health = health.copy(message = error?.let { "Last write failed: ${it.message ?: it.javaClass.simpleName}" }) }
    }

    /** Null when Health Connect can be used, otherwise why not. */
    fun healthUnavailable(): String? = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> null
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update Health Connect from the Play Store, then turn this on again."
        else -> "Health Connect is not available on this phone."
    }

    fun setHealth(on: Boolean, message: String? = null) {
        prefs.healthConnect = on
        health = HealthUi(on, message)
    }

    /** Turns writing off if Health Connect went away or its access was removed since the last check. */
    private fun checkHealth() {
        if (!prefs.healthConnect) return
        io.execute {
            val reason = healthUnavailable() ?: runCatching {
                runBlocking { HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }
            }.getOrNull()?.takeUnless { it.containsAll(HealthRecords.PERMISSIONS) }?.let { "Health Connect access was removed, so writing stopped." }
            if (reason != null) main.post { setHealth(false, reason) }
        }
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
        readLaptopTheme()
        checkHealth()
    }

    /** Never writes the file; a missing or unreadable one keeps the whole cached palette. */
    private fun readLaptopTheme() {
        val uri = prefs.folderUri?.let(Uri::parse) ?: return
        io.execute {
            val text = runCatching { mirror.read(uri, LaptopTheme.FILE) }.getOrNull() ?: return@execute
            main.post {
                val next = LaptopTheme.parse(text) ?: return@post
                prefs.laptopTheme = LaptopTheme.encode(next)
                laptop = next
            }
        }
    }

    fun chooseTheme(id: String) {
        prefs.theme = id
        theme = id
    }

    fun setRest(tier: Tier, sec: Int) {
        prefs.setRest(tier, sec)
        restTargets = prefs.restTargets()
    }

    fun chooseWarmups(on: Boolean) {
        prefs.warmups = on
        warmups = on
    }

    fun choosePlates(v: Plates) {
        prefs.plates = v
        plates = v
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
        readLaptopTheme()
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

    /** Saves the photo the camera just wrote to [capture] as a meal at the current time. */
    fun addMeal() {
        val at = now()
        io.execute {
            val error = runCatching { food.add(capture, at) }.exceptionOrNull()
            main.post {
                meals = food.meals()
                mealError = error?.let { "Meal not saved: ${it.message ?: it.javaClass.simpleName}" }
            }
        }
    }

    fun deleteMeal(meal: Meal) {
        food.delete(meal)
        meals = food.meals()
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
