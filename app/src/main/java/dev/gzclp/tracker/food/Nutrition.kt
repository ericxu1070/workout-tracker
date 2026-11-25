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
 * The ranges in assets/food/calories.json are rough per-serving buckets assigned by food category
 * (21 distinct ranges across 1,989 labels), not values looked up in USDA FoodData Central or any other dataset.
 * Calorie estimates are always presented as a low–high serving range, never an exact single number.
 */
object Nutrition {

    data class KcalRange(val low: Int, val high: Int) {
        init {
            require(low > 0) { "kcal low must be positive: $low" }
            require(low < high) { "kcal range must be a range (low < high), got $low..$high" }
        }

        val formatted: String get() = "$low–$high kcal"
        override fun toString(): String = formatted
    }

    /** Labels missing from the table share this bucket, the one most labels fell into. */
    val DEFAULT = KcalRange(400, 650)

    @Volatile
    private var table: Map<String, KcalRange>? = null

    private fun load(context: Context): Map<String, KcalRange> = table ?: synchronized(this) {
        table ?: runCatching {
            val root = Json.parseToJsonElement(context.assets.open("food/calories.json").bufferedReader().use { it.readText() }).jsonObject
            root.mapValues { (_, v) -> v.jsonArray.let { KcalRange(it[0].jsonPrimitive.content.toInt(), it[1].jsonPrimitive.content.toInt()) } }
        }.getOrElse { emptyMap() }.also { table = it }
    }

    /** The bucketed range for a classifier [label]; never a single exact number. */
    fun range(label: String, context: Context): KcalRange = load(context)[label] ?: DEFAULT
}
