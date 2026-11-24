package dev.gzclp.tracker.food

import dev.gzclp.tracker.log.FoodEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionTest {

    @Test
    fun testPromptExampleChickenRiceBowl() {
        val range = Nutrition.range("Chicken rice bowl")
        assertEquals(600, range.low)
        assertEquals(800, range.high)
        assertEquals("600–800 kcal", range.formatted)
        assertEquals("Chicken rice bowl · 600–800 kcal", Nutrition.format("Chicken rice bowl", range))
    }

    @Test
    fun testCommonFoodsLookup() {
        val testCases = mapOf(
            "Pizza" to (500 to 800),
            "Burger" to (450 to 750),
            "French fries" to (300 to 500),
            "Salad" to (200 to 450),
            "Spaghetti" to (450 to 750),
            "Sushi" to (350 to 600),
            "Burrito" to (600 to 900),
            "Bisque" to (200 to 400),
            "Pea soup" to (200 to 400),
            "Apple pie" to (300 to 500),
            "Ice cream" to (200 to 400),
        )

        for ((food, expected) in testCases) {
            val range = Nutrition.range(food)
            assertNotNull("Range should not be null for $food", range)
            assertEquals("Low kcal mismatch for $food", expected.first, range.low)
            assertEquals("High kcal mismatch for $food", expected.second, range.high)
            assertTrue("Range low must be < high for $food", range.low < range.high)
            assertNotEquals("Never present a single exact number for $food", range.low, range.high)
        }
    }

    @Test
    fun testNeverReturnsSingleExactNumber() {
        val foodsToTest = listOf(
            "Chicken rice bowl",
            "Pizza",
            "Chicago-style pizza",
            "New York-style pizza",
            "Cheeseburger",
            "Caesar salad",
            "Chicken noodle soup",
            "Grilled salmon",
            "Fudge brownie",
            "Ramen",
            "Pad thai",
            "Unknown exotic dish 123",
            "XYZ arbitrary food",
        )

        for (food in foodsToTest) {
            val range = Nutrition.range(food)
            assertTrue("Low bound must be positive (> 0) for $food", range.low > 0)
            assertTrue("High bound must be greater than low bound for $food", range.high > range.low)
            assertNotEquals("Never present a single exact number for $food", range.low, range.high)
            assertTrue("Formatted string must contain en-dash", range.formatted.contains("–"))
        }
    }

    @Test
    fun testCaseInsensitiveAndHeuristicMatching() {
        val lower = Nutrition.range("pizza")
        val upper = Nutrition.range("PIZZA")
        val mixed = Nutrition.range("PiZzA")

        assertEquals(lower, upper)
        assertEquals(lower, mixed)
        assertEquals(500, lower.low)
        assertEquals(800, lower.high)

        val variation = Nutrition.range("Hawaiian Pizza")
        assertEquals(500, variation.low)
        assertEquals(800, variation.high)

        val saladVariation = Nutrition.range("Greek Salad with feta")
        assertEquals(200, saladVariation.low)
        assertEquals(450, saladVariation.high)
    }

    @Test
    fun testUnknownLabelFallback() {
        val range = Nutrition.range("RandomUnrecognizedDish2026")
        assertEquals(400, range.low)
        assertEquals(650, range.high)
        assertTrue(range.low < range.high)
    }

    @Test
    fun testFormatEstimate() {
        val est = FoodEstimate(
            meal = "20261201T123000-0800.jpg",
            label = "Chicken rice bowl",
            kcalLow = 600,
            kcalHigh = 800,
            confirmed = true,
        )
        val formatted = Nutrition.format(est)
        assertEquals("Chicken rice bowl · 600–800 kcal", formatted)
    }

    @Test
    fun testDatasetCitationMetadata() {
        assertTrue(Nutrition.DATASET_SOURCE.contains("USDA"))
        assertTrue(Nutrition.DATASET_SOURCE.contains("FoodData Central"))
        assertTrue(Nutrition.DATASET_LICENSE.contains("Public Domain"))
    }

    @Test
    fun testClassifierFallbackPredictions() {
        val predictions = Classifier.fallbackPredictions("test_meal.jpg", topK = 3)
        assertEquals(3, predictions.size)
        for (pred in predictions) {
            assertTrue(pred.label.isNotEmpty())
            assertTrue(pred.score > 0f)
            assertTrue(pred.kcalRange.low < pred.kcalRange.high)
            assertTrue(pred.display.contains("·"))
            assertTrue(pred.display.contains("kcal"))
        }
        val top = predictions.first()
        val est = top.toEstimate("test_meal.jpg", confirmed = true)
        assertEquals("test_meal.jpg", est.meal)
        assertEquals(top.label, est.label)
        assertEquals(top.kcalRange.low, est.kcalLow)
        assertEquals(top.kcalRange.high, est.kcalHigh)
        assertTrue(est.confirmed)
    }
}
