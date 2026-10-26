package com.liquidglass.xiaomistats.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

/**
 * Рендерер элементов Liquid Glass для RemoteViews:
 * 1. Отрисовывает стеклянную карточку виджета с внутренним глянцевым рефлексом,
 *    полупрозрачным тонированием и ярким диагональным бликом рамки (как в виджете расписания).
 * 2. Отрисовывает кольцо активности с мягким стеклянным треком и неоново-коралловым
 *    индикатором прогресса.
 */
object GlassRingRenderer {

    /**
     * Создаёт Bitmap фона Liquid Glass карточки с тонким specular-бликом по периметру.
     */
    fun renderGlassCardBackground(
        widthPx: Int = 520,
        heightPx: Int = 520,
        cornerRadiusPx: Float = 112f,
        glassOpacityPercent: Int = 22,
        borderGlowPercent: Int = 55
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pad = 4f
        val rect = RectF(pad, pad, widthPx - pad, heightPx - pad)

        // 1. Основная полупрозрачная стеклянная заливка (эффект тонированного жидкого стекла)
        val baseAlpha = ((glassOpacityPercent.coerceIn(5, 85) / 100f) * 255).toInt()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                intArrayOf(
                    Color.argb((baseAlpha * 1.15f).toInt().coerceAtMost(235), 34, 38, 46),
                    Color.argb(baseAlpha, 18, 20, 26),
                    Color.argb((baseAlpha * 1.25f).toInt().coerceAtMost(240), 12, 14, 18)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint)

        // 2. Верхний внутренний стеклянный рефлекс (Subtle Specular Sheen)
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, heightPx * 0.48f,
                intArrayOf(
                    Color.argb(38, 255, 255, 255),
                    Color.argb(10, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, sheenPaint)

        // 3. Стеклянная рамка (Liquid Glass Rim Highlight) — ярче в верхнем левом и нижнем правом углах
        val borderAlpha = ((borderGlowPercent.coerceIn(15, 100) / 100f) * 235).toInt()
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                intArrayOf(
                    Color.argb(borderAlpha, 255, 255, 255),
                    Color.argb((borderAlpha * 0.32f).toInt(), 255, 255, 255),
                    Color.argb((borderAlpha * 0.18f).toInt(), 255, 255, 255),
                    Color.argb((borderAlpha * 0.65f).toInt(), 255, 255, 255)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint)

        return bitmap
    }

    /**
     * Отрисовывает кольцо прогресса шагов в стиле оригинального виджета «Статистика»,
     * адаптированное под стеклянный фон (полупрозрачный терракотово-стеклянный трек
     * и яркая кораллово-оранжевая капсула прогресса).
     */
    fun renderActivityRing(
        sizePx: Int = 260,
        progressFraction: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeWidth = sizePx * 0.195f
        val padding = strokeWidth * 0.85f
        val arcRect = RectF(padding, padding, sizePx - padding, sizePx - padding)

        // 1. Подложка кольца (полупрозрачное тёпло-коричневое стекло с тонким контуром)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(115, 78, 42, 36) // #734E2A24
        }
        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        // Тонкий стеклянный блик на самом кольце
        val trackHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = 2f
            color = Color.argb(45, 255, 210, 195)
        }
        canvas.drawOval(
            RectF(
                padding - strokeWidth / 2f,
                padding - strokeWidth / 2f,
                sizePx - padding + strokeWidth / 2f,
                sizePx - padding + strokeWidth / 2f
            ),
            trackHighlightPaint
        )

        // 2. Вычисляем угол заполнения (даже при малом числе шагов рисуем аккуратную капсулу сверху, как на скриншоте)
        val sweepAngle = max(14f, (progressFraction.coerceIn(0f, 1f) * 360f))
        val startAngle = -90f - (if (sweepAngle <= 20f) sweepAngle / 2f else 0f)

        // 3. Мягкое свечение вокруг активной дуги
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 1.05f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(90, 255, 114, 82)
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, glowPaint)

        // 4. Основная кораллово-оранжевая дуга прогресса (#FF7657 -> #FF6342)
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 0.92f
            strokeCap = Paint.Cap.ROUND
            shader = LinearGradient(
                0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                intArrayOf(
                    Color.parseColor("#FF8364"),
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
