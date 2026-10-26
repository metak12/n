package com.liquidglass.xiaomistats

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liquidglass.xiaomistats.data.StepStats
import com.liquidglass.xiaomistats.data.XiaomiStepRepository
import com.liquidglass.xiaomistats.databinding.ActivityMainBinding
import com.liquidglass.xiaomistats.widget.GlassRingRenderer
import com.liquidglass.xiaomistats.widget.LiquidGlassStatsWidget
import com.liquidglass.xiaomistats.widget.StepSyncWorker
import kotlinx.coroutines.launch

/**
 * Главный экран приложения настройки виджета «Статистика (Liquid Glass)»:
 * - Запрашивает разрешение ACTIVITY_RECOGNITION для чтения шагов Xiaomi / Android.
 * - Отображает интерактивное превью виджета в реальном времени.
 * - Позволяет настроить прозрачность стекла, яркость блика рамки и дневную цель шагов.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: XiaomiStepRepository
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        refreshPreviewAndWidgets()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = XiaomiStepRepository(this)

        // Проверяем, запущена ли Activity как конфигуратор нового виджета
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setupControls()
        checkAndRequestPermissions()
        StepSyncWorker.enqueuePeriodicWork(this)
        refreshPreviewAndWidgets()
    }

    private fun setupControls() {
        val currentOpacity = repository.getGlassOpacity()
        val currentGlow = repository.getBorderGlow()
        val currentGoal = repository.getStepGoal()

        binding.seekOpacity.progress = currentOpacity
        binding.tvOpacityValue.text = "${currentOpacity}%"

        binding.seekBorderGlow.progress = currentGlow
        binding.tvBorderGlowValue.text = "${currentGlow}%"

        binding.etStepGoal.setText(currentGoal.toString())

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val opacity = binding.seekOpacity.progress.coerceAtLeast(5)
                val glow = binding.seekBorderGlow.progress.coerceAtLeast(15)
                binding.tvOpacityValue.text = "${opacity}%"
                binding.tvBorderGlowValue.text = "${glow}%"
                repository.setGlassOpacity(opacity)
                repository.setBorderGlow(glow)
                refreshPreviewAndWidgets()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekOpacity.setOnSeekBarChangeListener(seekListener)
        binding.seekBorderGlow.setOnSeekBarChangeListener(seekListener)

        binding.btnSyncXiaomi.setOnClickListener {
            val goalInput = binding.etStepGoal.text.toString().toIntOrNull() ?: 6000
            repository.setStepGoal(goalInput)
            repository.setDemoSteps(null) // Сбрасываем демо и читаем реальный шагомер Xiaomi
            refreshPreviewAndWidgets()
            Toast.makeText(
                this,
                "Синхронизировано с шагомером Xiaomi",
                Toast.LENGTH_SHORT
            ).show()

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultValue)
                finish()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    private fun refreshPreviewAndWidgets() {
        lifecycleScope.launch {
            val stats = repository.getTodayStats()
            val opacity = repository.getGlassOpacity()
            val glow = repository.getBorderGlow()

            binding.previewCardBg.setImageBitmap(
                GlassRingRenderer.renderGlassCardBackground(
                    widthPx = 520,
                    heightPx = 520,
                    cornerRadiusPx = 112f,
                    glassOpacityPercent = opacity,
                    borderGlowPercent = glow
                )
            )
            binding.previewRing.setImageBitmap(
                GlassRingRenderer.renderActivityRing(
                    sizePx = 260,
                    progressFraction = stats.progressFraction
                )
            )

            binding.previewStepsValue.text = stats.steps.toString()
            binding.previewStepsUnit.text = " " + StepStats.formatStepWord(stats.steps)
            binding.previewCaloriesValue.text = stats.caloriesKcal.toString()
            binding.previewMinutesValue.text = stats.activeMinutes.toString()
            binding.tvDataSource.text = "Источник данных: ${stats.sourceName}"

            LiquidGlassStatsWidget.updateAllWidgets(this@MainActivity)
        }
    }
}
