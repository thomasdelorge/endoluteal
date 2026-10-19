package fr.luteal.core.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import fr.luteal.core.model.report.DailyMetricPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

internal data class PdfChartSeries(
    val label: String,
    val color: Int,
    val points: List<DailyMetricPoint>,
    val smoothed: List<DailyMetricPoint>
)

internal object PdfChartDrawer {
    private val dateTick = DateTimeFormatter.ofPattern("dd/MM")

    fun draw(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        seriesList: List<PdfChartSeries>,
        yMax: Float = 10f
    ) {
        if (seriesList.all { it.points.isEmpty() }) return
        val allDates = seriesList.flatMap { it.points.map { it.date } }.distinct().sorted()
        if (allDates.isEmpty()) return
        val minDate = allDates.first()
        val maxDate = allDates.last()
        val daySpan = ChronoUnit.DAYS.between(minDate, maxDate).coerceAtLeast(1).toFloat()

        val plotLeft = left + 28f
        val plotBottom = top + height - 22f
        val plotTop = top + 8f
        val plotRight = left + width - 8f
        val plotW = plotRight - plotLeft
        val plotH = plotBottom - plotTop

        val gridPaint = Paint().apply {
            color = Color.parseColor("#E2E2E6")
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val axisPaint = Paint().apply {
            color = Color.parseColor("#55585E")
            textSize = 7f
            isAntiAlias = true
        }
        for (tick in 0..5) {
            val yVal = yMax * tick / 5f
            val y = plotBottom - plotH * (yVal / yMax)
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            canvas.drawText(yVal.roundToInt().toString(), left, y + 3f, axisPaint)
        }
        canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, gridPaint)
        canvas.drawText(minDate.format(dateTick), plotLeft, plotBottom + 14f, axisPaint)
        canvas.drawText(maxDate.format(dateTick), plotRight - 28f, plotBottom + 14f, axisPaint)

        fun xFor(date: LocalDate): Float {
            val days = ChronoUnit.DAYS.between(minDate, date).toFloat()
            return plotLeft + plotW * (days / daySpan)
        }
        fun yFor(value: Double): Float {
            val clamped = value.coerceIn(0.0, yMax.toDouble())
            return plotBottom - plotH * (clamped / yMax).toFloat()
        }

        seriesList.forEach { series ->
            if (series.points.isEmpty()) return@forEach
            val dotPaint = Paint().apply {
                color = series.color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            series.points.forEach { point ->
                canvas.drawCircle(xFor(point.date), yFor(point.value), 2.2f, dotPaint)
            }
            if (series.smoothed.size >= 2) {
                val path = Path()
                series.smoothed.forEachIndexed { index, point ->
                    val x = xFor(point.date)
                    val y = yFor(point.value)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                val linePaint = Paint().apply {
                    color = series.color
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                    alpha = 220
                }
                canvas.drawPath(path, linePaint)
            }
        }

        var legendX = plotLeft
        val legendY = top + height - 4f
        seriesList.forEach { series ->
            if (series.points.isEmpty()) return@forEach
            val swatch = Paint().apply { color = series.color; style = Paint.Style.FILL }
            canvas.drawCircle(legendX + 4f, legendY - 3f, 3f, swatch)
            canvas.drawText(series.label, legendX + 10f, legendY, axisPaint)
            legendX += axisPaint.measureText(series.label) + 22f
        }
    }
}
