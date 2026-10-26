package com.liquidglass.xiaomistats.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * Рендерер элементов Liquid Glass для RemoteViews:
 * 1. Отрисовывает стеклянную карточку виджета в точных пропорциях ячейки рабочего стола
 *    (чтобы углы и рамка никогда не растягивались и совпадали с виджетом расписания).
 * 2. Отрисовывает кольцо активности в пропорциях оригинального виджета «Статистика».
 */
object GlassRingRenderer {

    /**
     * Создаёт Bitmap фона Liquid Glass карточки с учётом реальных пропорций виджета.
     */
    fun renderGlassCardBackground(
        widthPx: Int = 480,
        heightPx: Int = 480,
        cornerRadiusPx: Float = 72f,
        glassOpacityPercent: Int = 18,
        borderGlowPercent: Int = 60
    ): Bitmap {
        val w = widthPx.coerceAtLeast(120)
        val h = heightPx.coerceAtLeast(120)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeW = max(2.2f, min(w, h) * 0.007f)
        val pad = strokeW / 2f + 1f
        val rect = RectF(pad, pad, w - pad, h - pad)

        // 1. Полупрозрачная стеклянная подложка (как в виджете расписания)
        val baseAlpha = ((glassOpacityPercent.coerceIn(5, 85) / 100f) * 255).toInt()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.argb((baseAlpha * 1.05f).toInt().coerceAtMost(230), 28, 32, 40),
                    Color.argb(baseAlpha, 18, 20, 26),
                    Color.argb((baseAlpha * 1.15f).toInt().coerceAtMost(235), 12, 14, 18)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)

        // 2. Мягкий внутренний стеклянный блик сверху
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, h * 0.42f,
                intArrayOf(
                    Color.argb(26, 255, 255, 255),
                    Color.argb(6, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, sheenPaint)

        // 3. Чёткий тонкий стеклянный кант по всему периметру (в точности как у виджета расписания)
        val borderAlpha = ((borderGlowPercent.coerceIn(15, 100) / 100f) * 220).toInt()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeW
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(
                    Color.argb(borderAlpha, 255, 255, 255),
                    Color.argb((borderAlpha * 0.55f).toInt(), 255, 255, 255),
                    Color.argb((borderAlpha * 0.45f).toInt(), 255, 255, 255),
                    Color.argb((borderAlpha * 0.80f).toInt(), 255, 255, 255)
                ),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)

        return bitmap
    }

    /**
     * Отрисовывает кольцо прогресса шагов в точных пропорциях оригинального виджета «Статистика».
     */
    fun renderActivityRing(
        sizePx: Int = 260,
        progressFraction: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = sizePx * 0.21f
        val padding = strokeWidth / 2f + 6f
        val arcRect = RectF(padding, padding, sizePx - padding, sizePx - padding)

        // 1. Подложка кольца (полупрозрачное тёпло-коричневое стекло, как в оригинале)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(135, 68, 38, 33)
        }
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        // 2. Вычисляем угол заполнения (аккуратная коралловая капсула сверху при малом числе шагов)
        val sweepAngle = max(14f, (progressFraction.coerceIn(0f, 1f) * 360f))
        val startAngle = -90f - (if (sweepAngle <= 20f) sweepAngle / 2f else 0f)

        // 3. Основная кораллово-оранжевая дуга прогресса (#FF7B5A -> #FF6846)
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 0.92f
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                intArrayOf(
                    Color.parseColor("#FF8060"),
                    Color.parseColor("#FF6846")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, progressPaint)

        return bitmap
    }
}
