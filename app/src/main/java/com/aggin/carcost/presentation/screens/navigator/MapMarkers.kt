package com.aggin.carcost.presentation.screens.navigator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Значки мест на карте.
 *
 * Раньше места отмечались стандартной меткой MapKit — крошечной точкой без
 * подписи и без цвета. На карте города такие точки теряются среди самой карты:
 * человек нажимал «Заправки», получал десяток неразличимых пятнышек и не мог
 * ни понять, где что, ни нажать в нужное.
 *
 * Рисуем каплю с эмодзи внутри: цвет отделяет один вид мест от другого, эмодзи
 * говорит, что это, а размер делает метку пригодной для попадания пальцем.
 * Готовой картинкой обойтись нельзя — нужен цвет по виду места, а держать
 * шесть файлов в ресурсах ради шести кружков расточительно.
 */
object MapMarkers {

    /** Ширина капли в пикселях. Подобрана так, чтобы палец попадал без промаха */
    private const val WIDTH = 84
    private const val HEIGHT = 108

    fun poiBitmap(category: PoiCategory, selected: Boolean = false): Bitmap {
        val color = category.markerColor()
        return pinBitmap(color, category.emoji(), selected)
    }

    /** Метка точки назначения — крупнее прочих: это главная точка на экране */
    fun destinationBitmap(): Bitmap = pinBitmap(Color.rgb(211, 47, 47), "🏁", true)

    /**
     * Произвольная метка: цвет и значок задаёт вызывающий.
     *
     * Нужна карте расходов — там свои категории со своими цветами, уже
     * описанными в Labels. Заводить второй рисовальщик капель ради этого
     * незачем: форма метки должна быть одинаковой во всём приложении.
     */
    fun customBitmap(color: Int, emoji: String, selected: Boolean = false): Bitmap =
        pinBitmap(color, emoji, selected)

    private fun pinBitmap(color: Int, emoji: String, selected: Boolean): Bitmap {
        val scale = if (selected) 1.25f else 1f
        val w = (WIDTH * scale).toInt()
        val h = (HEIGHT * scale).toInt()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(60, 0, 0, 0)
        }
        // Тень под каплей: без неё метка сливается со светлой картой
        canvas.drawOval(RectF(w * 0.25f, h * 0.86f, w * 0.75f, h * 0.98f), shadow)

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = w * 0.06f
        }

        val circleRadius = w * 0.42f
        val cx = w / 2f
        val cy = circleRadius + w * 0.04f

        // Хвостик капли — треугольник от круга к точке на карте
        val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val path = android.graphics.Path().apply {
            moveTo(cx - w * 0.18f, cy + circleRadius * 0.72f)
            lineTo(cx + w * 0.18f, cy + circleRadius * 0.72f)
            lineTo(cx, h * 0.92f)
            close()
        }
        canvas.drawPath(path, tail)

        canvas.drawCircle(cx, cy, circleRadius, body)
        canvas.drawCircle(cx, cy, circleRadius, border)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = w * 0.42f
        }
        // По вертикали эмодзи ставится по средней линии шрифта, а не по центру
        // круга: иначе картинка визуально съезжает вниз
        val metrics = text.fontMetrics
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji, cx, baseline, text)

        return bitmap
    }
}

/** Цвет вида места. Разные виды не должны сливаться при одновременном показе */
private fun PoiCategory.markerColor(): Int = when (this) {
    PoiCategory.GAS_STATION -> Color.rgb(230, 74, 25)
    PoiCategory.SERVICE -> Color.rgb(56, 142, 60)
    PoiCategory.PARKING -> Color.rgb(25, 118, 210)
    PoiCategory.CAFE -> Color.rgb(198, 40, 40)
    PoiCategory.BANK -> Color.rgb(106, 27, 154)
    PoiCategory.SUPERMARKET -> Color.rgb(245, 124, 0)
}

private fun PoiCategory.emoji(): String = when (this) {
    PoiCategory.GAS_STATION -> "⛽"
    PoiCategory.SERVICE -> "🔧"
    PoiCategory.PARKING -> "🅿️"
    PoiCategory.CAFE -> "☕"
    PoiCategory.BANK -> "🏧"
    PoiCategory.SUPERMARKET -> "🛒"
}
