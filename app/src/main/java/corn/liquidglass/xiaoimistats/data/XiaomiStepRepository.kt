package com.liquidglass.xiaomistats.data

import android.content.Context
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * Репозиторий чтения шагов:
 * 1. Пытается прочитать шаги за текущие сутки из системного провайдера Xiaomi
 *    (`content://com.miui.providers.steps/item`), который используется в MIUI / HyperOS.
 * 2. Если провайдер недоступен или заблокирован разрешениями прошивки, читает
 *    аппаратный датчик `Sensor.TYPE_STEP_COUNTER` и вычисляет шаги с начала дня.
 * 3. Позволяет задать ручную коррекцию / демо-режим из приложения.
 */
class XiaomiStepRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getTodayStats(): StepStats = withContext(Dispatchers.IO) {
        val goal = getStepGoal()
        val demoSteps = prefs.getInt(KEY_DEMO_STEPS, -1)

        if (demoSteps >= 0) {
            return@withContext StepStats(
                steps = demoSteps,
                goalSteps = goal,
                sourceName = "Демо / Ручной ввод"
            )
        }

        // 1. Пробуем системный провайдер шагомера Xiaomi (MIUI / HyperOS)
        val miuiSteps = queryMiuiStepsProvider()
        if (miuiSteps != null && miuiSteps >= 0) {
            saveLastKnownSteps(miuiSteps)
            return@withContext StepStats(
                steps = miuiSteps,
                goalSteps = goal,
                sourceName = "Xiaomi MIUI/HyperOS Steps"
            )
        }

        // 2. Фолбэк на аппаратный датчик шагов Android (TYPE_STEP_COUNTER)
        val sensorSteps = readHardwareStepSensor()
        if (sensorSteps != null && sensorSteps >= 0) {
            saveLastKnownSteps(sensorSteps)
            return@withContext StepStats(
                steps = sensorSteps,
                goalSteps = goal,
                sourceName = "Hardware Step Sensor"
            )
        }

        // 3. Возвращаем последнее сохранённое значение за сегодня (или стартовые 304 шага)
        val fallbackSteps = prefs.getInt(KEY_LAST_KNOWN_STEPS, 304)
        StepStats(
            steps = fallbackSteps,
            goalSteps = goal,
            sourceName = "Кэш шагомера"
        )
    }

    /**
     * Запрос к системному ContentProvider шагомера Xiaomi:
     * URI: content://com.miui.providers.steps/item
     * Поля: _begin_time, _end_time, _mode, _steps
     */
    private fun queryMiuiStepsProvider(): Int? {
        return try {
            val uri = Uri.parse("content://com.miui.providers.steps/item")
            val startOfDayMillis = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val projection = arrayOf("_begin_time", "_end_time", "_mode", "_steps")
            val selection = "_begin_time >= ?"
            val selectionArgs = arrayOf(startOfDayMillis.toString())

            var totalSteps = 0
            var hasRows = false

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "_id ASC"
            )

            cursor?.use { c ->
                val stepsIndex = c.getColumnIndex("_steps")
                if (stepsIndex != -1) {
                    while (c.moveToNext()) {
                        hasRows = true
                        totalSteps += c.getInt(stepsIndex)
                    }
                }
            }

            if (hasRows) totalSteps else null
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Чтение аппаратного счётчика шагов Sensor.TYPE_STEP_COUNTER с привязкой к текущим суткам.
     */
    private suspend fun readHardwareStepSensor(): Int? {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return null
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return null

        val rawCounter = withTimeoutOrNull(1200L) {
            suspendCancellableCoroutine<Int?> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        val value = event?.values?.firstOrNull()?.toInt()
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(value)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                val registered = sensorManager.registerListener(
                    listener,
                    stepSensor,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
                if (!registered && cont.isActive) {
                    cont.resume(null)
                }
                cont.invokeOnCancellation {
                    sensorManager.unregisterListener(listener)
                }
            }
        } ?: return null

        val todayEpochDay = LocalDate.now().toEpochDay()
        val savedEpochDay = prefs.getLong(KEY_BASELINE_DAY, -1L)
        var baseline = prefs.getInt(KEY_BASELINE_SENSOR, -1)

        // Если наступил новый день или телефон перезагружался (rawCounter < baseline)
        if (savedEpochDay != todayEpochDay || baseline < 0 || rawCounter < baseline) {
            baseline = rawCounter
            prefs.edit()
                .putLong(KEY_BASELINE_DAY, todayEpochDay)
                .putInt(KEY_BASELINE_SENSOR, baseline)
                .apply()
        }

        return (rawCounter - baseline).coerceAtLeast(0)
    }

    private fun saveLastKnownSteps(steps: Int) {
        prefs.edit().putInt(KEY_LAST_KNOWN_STEPS, steps).apply()
    }

    fun getStepGoal(): Int = prefs.getInt(KEY_STEP_GOAL, 6000)

    fun setStepGoal(goal: Int) {
        prefs.edit().putInt(KEY_STEP_GOAL, goal.coerceIn(500, 50000)).apply()
    }

    fun getGlassOpacity(): Int = prefs.getInt(KEY_GLASS_OPACITY, 22)

    fun setGlassOpacity(opacityPercent: Int) {
        prefs.edit().putInt(KEY_GLASS_OPACITY, opacityPercent.coerceIn(5, 85)).apply()
    }

    fun getBorderGlow(): Int = prefs.getInt(KEY_BORDER_GLOW, 55)

    fun setBorderGlow(glowPercent: Int) {
        prefs.edit().putInt(KEY_BORDER_GLOW, glowPercent.coerceIn(15, 100)).apply()
    }

    fun setDemoSteps(steps: Int?) {
        prefs.edit().putInt(KEY_DEMO_STEPS, steps ?: -1).apply()
    }

    fun getDemoSteps(): Int? {
        val v = prefs.getInt(KEY_DEMO_STEPS, -1)
        return if (v >= 0) v else null
    }

    companion object {
        const val PREFS_NAME = "liquid_glass_xiaomi_prefs"
        private const val KEY_STEP_GOAL = "step_goal"
        private const val KEY_GLASS_OPACITY = "glass_opacity"
        private const val KEY_BORDER_GLOW = "border_glow"
        private const val KEY_BASELINE_DAY = "baseline_epoch_day"
        private const val KEY_BASELINE_SENSOR = "baseline_sensor_value"
        private const val KEY_LAST_KNOWN_STEPS = "last_known_steps"
        private const val KEY_DEMO_STEPS = "demo_steps_override"
    }
}
