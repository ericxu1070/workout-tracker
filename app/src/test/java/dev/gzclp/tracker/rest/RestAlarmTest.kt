package dev.gzclp.tracker.rest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.gzclp.tracker.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestAlarmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun clearingTheSetThatStartedTheRestEndsIt() {
        RestAlarm.start(context, 90, "Rest after bench press set 2")
        RestAlarm.cancelFor(context, "Rest after bench press set 1")
        assertNotEquals(0L, Prefs(context).restEndMillis)
        RestAlarm.cancelFor(context, "Rest after bench press set 2")
        assertEquals(0L, Prefs(context).restEndMillis)
    }
}
