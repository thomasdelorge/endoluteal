package fr.luteal.core.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import fr.luteal.core.designsystem.component.bodymap.ChoirPainMap
import fr.luteal.core.designsystem.component.bodymap.ChoirRegion
import fr.luteal.core.designsystem.component.bodymap.PainPinGeometry
import fr.luteal.core.designsystem.component.painIntensityArgb
import fr.luteal.core.model.report.PainPinSample

object PainHeatmapPdfDrawer {
    fun drawSilhouette(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        front: Boolean,
        marks: List<PainPinSample>
    ) {
        val regions = ChoirPainMap.regions(context).filter { it.front == front }
        val scaleX = width / (ChoirPainMap.IMAGE_WIDTH / 2f)
        val scaleY = height / ChoirPainMap.IMAGE_HEIGHT
        val halfOffset = if (front) 0f else ChoirPainMap.IMAGE_WIDTH / 2f
        val stroke = Paint().apply {
            color = android.graphics.Color.parseColor("#9AA0A6")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            isAntiAlias = true
        }
        regions.forEach { region ->
            canvas.drawPath(region.toPath(scaleX, scaleY, left, top, halfOffset), stroke)
        }

        val sideKey = if (front) PainPinGeometry.PIN_SIDE_FRONT else PainPinGeometry.PIN_SIDE_BACK
        val regionByKey = regions.associateBy { it.key }
        marks.filter { sample ->
            when {
                sample.hasPin -> sample.pinSide == sideKey
                else -> regionByKey.containsKey(sample.zone)
            }
        }.forEach { sample ->
            val (cx, cy) = when {
                sample.hasPin -> {
                    val x = left + sample.pinX!! * width
                    val y = top + sample.pinY!! * height
                    x to y
                }
                else -> {
                    val region = regionByKey[sample.zone] ?: return@forEach
                    region.centroidCanvas(scaleX, scaleY, left, top, halfOffset)
                }
            }
            val core = painIntensityArgb(sample.intensity)
            val radius = 10f + sample.intensity * 1.8f
            val fill = Paint().apply {
                shader = RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(core, android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0.35f, 1f),
                    Shader.TileMode.CLAMP
                )
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, radius, fill)
            val corePaint = Paint().apply {
                color = core
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, 2.5f, corePaint)
        }
    }

    private fun ChoirRegion.centroidCanvas(
        scaleX: Float,
        scaleY: Float,
        left: Float,
        top: Float,
        halfOffset: Float
    ): Pair<Float, Float> {
        val cx = points.map { left + (it.x - halfOffset) * scaleX }.average().toFloat()
        val cy = points.map { top + it.y * scaleY }.average().toFloat()
        return cx to cy
    }

    private fun ChoirRegion.toPath(
        scaleX: Float,
        scaleY: Float,
        left: Float,
        top: Float,
        halfOffset: Float
    ): Path {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + (point.x - halfOffset) * scaleX
            val y = top + point.y * scaleY
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
