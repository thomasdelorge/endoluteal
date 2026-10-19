package fr.luteal.core.data.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import fr.luteal.core.model.report.TimelinePdfContent
import fr.luteal.core.model.report.TimelineVisitKind
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimelinePdfBuilder {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    fun write(content: TimelinePdfContent, outputStream: OutputStream, zone: ZoneId = ZoneId.systemDefault()) {
        val document = PdfDocument()
        val titlePaint = paint(16f, bold = true, color = "#235B4E")
        val headerPaint = paint(12f, bold = true, color = "#235B4E")
        val textPaint = paint(10f, bold = false, color = "#1A1C1E")
        val barPaint = Paint().apply {
            color = Color.parseColor("#235B4E")
            style = Paint.Style.FILL
        }
        val dates = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun line(text: String, paint: Paint) {
            if (y > PAGE_HEIGHT - MARGIN) newPage()
            canvas.drawText(text, MARGIN, y, paint)
            y += paint.textSize + 6f
        }

        line("Frise Endoluteal", titlePaint)
        y += 8f
        line("Événements", headerPaint)
        content.visits.forEach { visit ->
            val kind = kindLabel(visit.kind)
            val day = visit.at.atZone(zone).toLocalDate().format(dates)
            line("$day · $kind · ${visit.title}", textPaint)
            if (visit.subtitle.isNotBlank()) line(visit.subtitle, textPaint)
            if (content.showDetails && visit.detail.isNotBlank()) {
                visit.detail.lineSequence().forEach { detailLine ->
                    line(detailLine, textPaint)
                }
            }
        }
        content.summary?.let { summary ->
            y += 8f
            line("Depuis le dernier rendez-vous", headerPaint)
            summary.weeklyPain.forEach { week ->
                line(
                    "Semaine du ${week.weekStart} : moyenne ${"%.1f".format(week.average)}, max ${week.max}",
                    textPaint
                )
                val width = CONTENT_WIDTH * (week.max.coerceIn(0, 10) / 10f)
                if (y > PAGE_HEIGHT - MARGIN) newPage()
                canvas.drawRect(MARGIN, y, MARGIN + width, y + 8f, barPaint)
                y += 14f
            }
            summary.intakes.forEach { intake ->
                line("${intake.name} : ${intake.count}", textPaint)
            }
            summary.zonePeaks.forEach { peak ->
                line("${peak.zone} : ${peak.max}", textPaint)
            }
            line("Hospitalisations : ${summary.hospitalizationCount}", textPaint)
            line("Soins : ${summary.careSessionCount}", textPaint)
        }
        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
    }

    private fun kindLabel(kind: TimelineVisitKind): String = when (kind) {
        TimelineVisitKind.APPOINTMENT -> "RDV"
        TimelineVisitKind.HOSPITALIZATION -> "Hospitalisation"
        TimelineVisitKind.MEDICATION_INTAKE -> "Médicament"
        TimelineVisitKind.SYMPTOM -> "Symptôme"
        TimelineVisitKind.CARE_SESSION -> "Soin"
    }

    private fun paint(size: Float, bold: Boolean, color: String) = Paint().apply {
        this.color = Color.parseColor(color)
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isAntiAlias = true
    }
}
