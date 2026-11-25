package dev.gzclp.tracker.food

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NutritionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyModelLabelGetsARangeNotANumber() {
        val labels = context.assets.open("food/labels.txt").bufferedReader().readLines()
            .filter { it != "__background__" && !it.startsWith("/") }
        assertTrue(labels.size > 1900)
        labels.forEach { assertTrue(it, Nutrition.range(it, context).let { r -> r.low in 1 until r.high }) }
    }

    @Test
    fun tableValuesWinAndUnknownLabelsGetTheDefault() {
        assertEquals(Nutrition.KcalRange(550, 850), Nutrition.range("Burrito", context))
        assertEquals(Nutrition.DEFAULT, Nutrition.range("RandomUnrecognizedDish2026", context))
    }

    @Test
    fun anUnreadablePhotoGetsNoMadeUpEstimate() {
        assertTrue(Classifier.classifySync(context, File("does_not_exist.jpg")).isEmpty())
    }
}
