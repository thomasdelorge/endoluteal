package fr.luteal.core.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import fr.luteal.core.designsystem.component.bodymap.ChoirPainMap
import fr.luteal.core.designsystem.component.bodymap.ChoirRegion
import fr.luteal.core.designsystem.component.painIntensityArgb

object PainHeatmapPdfDrawer {
    fun drawSilhouette(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        front: Boolean,
        zoneHeat: Map<String, Int>
    ) {
        val regions = ChoirPainMap.regions(context).filter { it.front == front }
        val scaleX = width / (ChoirPainMap.IMAGE_WIDTH / 2f)
        val scaleY = height / ChoirPainMap.IMAGE_HEIGHT
        val offsetX = if (front) left else left
        val halfOffset = if (front) 0f else ChoirPainMap.IMAGE_WIDTH / 2f
        val stroke = Paint().apply {
            color = android.graphics.Color.parseColor("#1A1C1E")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            isAntiAlias = true
        }
        regions.forEach { region ->
            val path = region.toPath(scaleX, scaleY, offsetX, top, halfOffset)
            val intensity = zoneHeat[region.key] ?: 0
            if (intensity > 0) {
                val fill = Paint().apply {
                    color = painIntensityArgb(intensity)
                    alpha = 115
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawPath(path, fill)
            }
            canvas.drawPath(path, stroke)
        }
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
