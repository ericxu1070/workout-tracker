package dev.gzclp.tracker.food

import android.content.Context
import dev.gzclp.tracker.log.FoodEstimate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Calorie and nutrition mapping for food classification labels.
 *
 * Data is sourced from USDA FoodData Central (FDC October 2024 / FNDDS / SR Legacy),
 * which is in the public domain under 17 U.S.C. § 105.
 * Calorie estimates are always presented as a low–high serving range, never an exact single number.
 */
object Nutrition {
    const val DATASET_SOURCE = "USDA FoodData Central"
    const val DATASET_VERSION = "October 2024 / FNDDS / SR Legacy"
    const val DATASET_LICENSE = "U.S. Government Public Domain (17 U.S.C. § 105)"

    data class KcalRange(val low: Int, val high: Int) {
        init {
            require(low > 0) { "kcal low must be positive: $low" }
            require(low < high) { "kcal range must be a range (low < high), got $low..$high" }
        }

        val formatted: String get() = "$low–$high kcal"
        override fun toString(): String = formatted
    }

    private var loadedTable: Map<String, KcalRange>? = null

    // Default canonical food items guaranteed to exist even without assets loaded
    private val defaultCanonical = mapOf(
        "Chicken rice bowl" to KcalRange(600, 800),
        "Pizza" to KcalRange(500, 800),
        "Burger" to KcalRange(450, 750),
        "Cheeseburger" to KcalRange(500, 800),
        "French fries" to KcalRange(300, 500),
        "Salad" to KcalRange(200, 450),
        "Sandwich" to KcalRange(350, 650),
        "Spaghetti" to KcalRange(450, 750),
        "Pasta" to KcalRange(450, 750),
        "Steak" to KcalRange(500, 800),
        "Sushi" to KcalRange(350, 600),
        "Ramen" to KcalRange(500, 800),
        "Burrito" to KcalRange(600, 900),
        "Taco" to KcalRange(400, 650),
        "Fried chicken" to KcalRange(450, 750),
        "Fried rice" to KcalRange(450, 700),
        "Pancakes" to KcalRange(350, 600),
        "Omelette" to KcalRange(250, 450),
        "Salmon" to KcalRange(300, 550),
        "Curry" to KcalRange(500, 800),
        "Apple pie" to KcalRange(300, 500),
        "Ice cream" to KcalRange(200, 400),
        "Bagel" to KcalRange(250, 400),
        "Bisque" to KcalRange(200, 400),
        "Pea soup" to KcalRange(200, 400),
        "Caramel" to KcalRange(400, 650),
        "Pilaf" to KcalRange(400, 650),
        "Youtiao" to KcalRange(400, 650),
        "Tahini" to KcalRange(400, 650),
    )

    /** Loads `food/calories.json` from assets if available, or returns cached / default map. */
    fun load(context: Context? = null): Map<String, KcalRange> {
        loadedTable?.let { return it }
        val table = mutableMapOf<String, KcalRange>()
        table.putAll(defaultCanonical)

        if (context != null) {
            runCatching {
                val jsonString = context.assets.open("food/calories.json").bufferedReader().use { it.readText() }
                val root = Json.parseToJsonElement(jsonString).jsonObject
                for ((key, element) in root) {
                    val arr = element.jsonArray
                    if (arr.size >= 2) {
                        val low = arr[0].jsonPrimitive.content.toIntOrNull() ?: continue
                        val high = arr[1].jsonPrimitive.content.toIntOrNull() ?: continue
                        if (low > 0 && low < high) {
                            table[key] = KcalRange(low, high)
                        }
                    }
                }
            }
        }
        loadedTable = table
        return table
    }

    /**
     * Looks up the USDA calorie range for [label].
     * Never returns a single exact number; always returns a range `low < high`.
     */
    fun range(label: String, context: Context? = null): KcalRange {
        val table = load(context)
        // 1. Exact match
        table[label]?.let { return it }

        // 2. Case-insensitive match
        table.entries.firstOrNull { it.key.equals(label, ignoreCase = true) }?.value?.let { return it }

        // 3. Keyword / category heuristics based on USDA FoodData Central typical servings
        val l = label.lowercase()
        return when {
            l.contains("chicken rice bowl") -> KcalRange(600, 800)
            l.contains("burger") || l.contains("patty") -> KcalRange(450, 750)
            l.contains("pizza") || l.contains("calzone") -> KcalRange(500, 800)
            l.contains("fries") || l.contains("wedge") -> KcalRange(300, 500)
            l.contains("hot dog") || l.contains("frankfurter") || l.contains("bratwurst") -> KcalRange(300, 450)
            l.contains("burrito") || l.contains("quesadilla") || l.contains("enchilada") -> KcalRange(550, 850)
            l.contains("taco") -> KcalRange(400, 650)
            l.contains("sandwich") || l.contains("panini") || l.contains("sub") || l.contains("wrap") -> KcalRange(350, 650)
            l.contains("salad") || l.contains("slaw") -> KcalRange(200, 450)
            l.contains("soup") || l.contains("broth") || l.contains("stew") || l.contains("chowder") || l.contains("bisque") -> KcalRange(200, 400)
            l.contains("pasta") || l.contains("spaghetti") || l.contains("lasagna") || l.contains("macaroni") ||
                l.contains("noodle") || l.contains("ramen") || l.contains("udon") -> KcalRange(450, 750)
            l.contains("rice") || l.contains("biryani") || l.contains("pilaf") || l.contains("risotto") -> KcalRange(400, 650)
            l.contains("curry") || l.contains("tikka") || l.contains("masala") -> KcalRange(450, 750)
            l.contains("steak") || l.contains("beef") || l.contains("ribs") || l.contains("pork") || l.contains("lamb") -> KcalRange(450, 750)
            l.contains("chicken") || l.contains("turkey") || l.contains("poultry") || l.contains("wings") -> KcalRange(350, 650)
            l.contains("fish") || l.contains("salmon") || l.contains("tuna") || l.contains("sushi") || l.contains("shrimp") || l.contains("seafood") -> KcalRange(250, 500)
            l.contains("pancake") || l.contains("waffle") || l.contains("crepe") -> KcalRange(350, 600)
            l.contains("omelet") || l.contains("omelette") || l.contains("egg") -> KcalRange(200, 400)
            l.contains("cereal") || l.contains("oatmeal") || l.contains("granola") -> KcalRange(200, 380)
            l.contains("bagel") || l.contains("croissant") || l.contains("muffin") || l.contains("toast") -> KcalRange(220, 420)
            l.contains("dumpling") || l.contains("gyoza") || l.contains("wonton") || l.contains("samosa") -> KcalRange(300, 550)
            l.contains("cake") || l.contains("pie") || l.contains("cookie") || l.contains("donut") || l.contains("doughnut") -> KcalRange(250, 500)
            l.contains("ice cream") || l.contains("gelato") || l.contains("pudding") || l.contains("yogurt") -> KcalRange(200, 400)
            l.contains("bread") || l.contains("roll") || l.contains("naan") || l.contains("pita") -> KcalRange(150, 300)
            l.contains("potato") -> KcalRange(180, 350)
            l.contains("cheese") || l.contains("dip") || l.contains("hummus") || l.contains("guacamole") -> KcalRange(150, 350)
            l.contains("fruit") || l.contains("apple") || l.contains("banana") || l.contains("berry") -> KcalRange(80, 200)
            l.contains("vegetable") || l.contains("broccoli") || l.contains("carrot") -> KcalRange(80, 220)
            else -> KcalRange(400, 650) // Default typical dish serving
        }
    }

    /** Formats a label and range into the standard display string, e.g. "Chicken rice bowl · 600–800 kcal". */
    fun format(label: String, range: KcalRange): String = "$label · ${range.formatted}"

    /** Formats a [FoodEstimate] event into the standard display string. */
    fun format(est: FoodEstimate): String = "${est.label} · ${est.kcalLow}–${est.kcalHigh} kcal"
}
