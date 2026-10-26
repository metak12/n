package com.liquidglass.xiaomistats.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Модель статистики активности для виджета Liquid Glass.
 */
data class StepStats(
    val steps: Int,
    val goalSteps: Int = 6000,
    val caloriesKcal: Int = calculateCalories(steps),
    val activeMinutes: Int = calculateActiveMinutes(steps),
    val sourceName: String = "Xiaomi Pedometer"
) {
    val progressFraction: Float
        get() = if (goalSteps > 0) (steps.toFloat() / goalSteps.toFloat()).coerceIn(0f, 1f) else 0f

    companion object {
        /**
         * Расчёт килокалорий по формуле Mi Fitness / Xiaomi Steps (~0.068 ккал на шаг при среднем темпе,
         * что даёт ~21 ккал на 304 шага, как в оригинальном виджете Статистики).
         */
        fun calculateCalories(steps: Int): Int {
            if (steps <= 0) return 0
            return max(1, (steps * 0.069f).roundToInt())
        }

        /**
         * Расчёт минут активности (~100 шагов в минуту, т.е. 304 шага ≈ 3 мин).
         */
        fun calculateActiveMinutes(steps: Int): Int {
            if (steps <= 0) return 0
            return max(1, (steps / 100f).roundToInt())
        }

        /**
         * Правильное склонение слова «шаг» / «шага» / «шагов» для русского языка.
         */
        fun formatStepWord(steps: Int): String {
            val mod100 = steps % 100
            val mod10 = steps % 10
            return when {
                mod100 in 11..19 -> "шагов"
                mod10 == 1 -> "шаг"
                mod10 in 2..4 -> "шага"
                else -> "шагов"
            }
        }
    }
}
