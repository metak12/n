package com.liquidglass.xiaomistats.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.liquidglass.xiaomistats.MainActivity
import com.liquidglass.xiaomistats.R
import com.liquidglass.xiaomistats.data.StepStats
import com.liquidglass.xiaomistats.data.XiaomiStepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Провайдер рабочего стола для виджета «Статистика» в стиле Liquid Glass.
 */
class LiquidGlassStatsWidget : AppWidgetProvider() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        StepSyncWorker.enqueuePeriodicWork(context)
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_STEPS || intent.action == Intent.ACTION_USER_PRESENT) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, LiquidGlassStatsWidget::class.java)
                    )
                    updateWidgets(context, manager, ids)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_STEPS = "com.liquidglass.xiaomistats.ACTION_REFRESH_STEPS"

        suspend fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LiquidGlassStatsWidget::class.java)
            )
            updateWidgets(context, manager, ids)
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return

            val repo = XiaomiStepRepository(context)
            val stats = repo.getTodayStats()
            val glassOpacity = repo.getGlassOpacity()
            val borderGlow = repo.getBorderGlow()
            val density = context.resources.displayMetrics.density

            val ringBitmap = GlassRingRenderer.renderActivityRing(
                sizePx = 260,
                progressFraction = stats.progressFraction
            )

            for (widgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150).coerceAtLeast(110)
                val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150).coerceAtLeast(110)

                val widthPx = (minW * density * 1.5f).roundToInt().coerceIn(240, 960)
                val heightPx = (minH * density * 1.5f).roundToInt().coerceIn(240, 960)
                val cornerRadiusPx = 22f * density * 1.5f

                val glassCardBitmap = GlassRingRenderer.renderGlassCardBackground(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    glassOpacityPercent = glassOpacity,
                    borderGlowPercent = borderGlow
                )

                val views = RemoteViews(context.packageName, R.layout.widget_liquid_stats)

                // Устанавливаем стеклянную подложку в точных пропорциях ячейки и кольцо прогресса
                views.setImageViewBitmap(R.id.img_glass_background, glassCardBitmap)
                views.setImageViewBitmap(R.id.img_activity_ring, ringBitmap)

                // Заполняем показатели (шаги, ккал, мин)
                views.setTextViewText(R.id.tv_steps_value, stats.steps.toString())
                views.setTextViewText(
                    R.id.tv_steps_unit,
                    " " + StepStats.formatStepWord(stats.steps)
                )

                views.setTextViewText(R.id.tv_calories_value, stats.caloriesKcal.toString())
                views.setTextViewText(R.id.tv_minutes_value, stats.activeMinutes.toString())

                // По клику на кольцо — мгновенное обновление шагов из провайдера Xiaomi
                val refreshIntent = Intent(context, LiquidGlassStatsWidget::class.java).apply {
                    action = ACTION_REFRESH_STEPS
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.img_activity_ring, refreshPendingIntent)

                // По клику на нижнюю область — открытие экрана настроек Liquid Glass
                val openAppIntent = Intent(context, MainActivity::class.java)
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId + 1000,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_stats_container, openAppPendingIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
