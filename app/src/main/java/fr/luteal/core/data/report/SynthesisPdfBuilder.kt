package fr.luteal.core.data.report

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import fr.luteal.core.model.report.ClinicalSynthesis
import fr.luteal.core.model.report.SynthesisPdfSection
import fr.luteal.core.model.report.SynthesisTrend
import fr.luteal.core.model.report.TimelineVisitKind
import fr.luteal.core.model.report.WeeklyMetricPoint
import java.io.OutputStream
import java.time.format.DateTimeFormatter

object SynthesisPdfBuilder {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun write(context: Context, synthesis: ClinicalSynthesis, outputStream: OutputStream) {
        val document = PdfDocument()
        val writer = PdfPageWriter(document, PAGE_WIDTH, PAGE_HEIGHT, MARGIN)
        val dates = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        writer.start()
        writer.line("Synthèse Endoluteal", writer.titlePaint)
        writer.line(
            "Période : ${synthesis.periodStart.format(dates)} – ${synthesis.periodEnd.format(dates)}",
            writer.secondaryPaint
        )
        synthesis.filterLines.forEach { writer.line(it, writer.secondaryPaint) }
        writer.y += 8f

        writer.line("En bref", writer.headerPaint)
        synthesis.attentionPoints.forEach { writer.line("• $it") }
        writer.y += 6f

        writer.line("Fiabilité (jours avec données / période)", writer.headerPaint)
        synthesis.reliability.forEach { (section, rel) ->
            writer.line("${section.name}: ${rel.daysWithData}/${rel.daysInPeriod}")
        }
        writer.y += 8f

        if (SynthesisPdfSection.PAIN in synthesis.sections) {
            writer.line("Évolution — douleur et 5D", writer.headerPaint)
            drawSeries(writer, synthesis.weeklyMetrics["pain_marks"], "Douleur (marques)")
            listOf("5d_dysmenorrhea", "5d_dyschezia", "5d_dysuria", "5d_chronic").forEach { key ->
                drawSeries(writer, synthesis.weeklyMetrics[key], metricLabel(key), synthesis.trends[key])
            }
        }
        if (SynthesisPdfSection.PSY in synthesis.sections) {
            writer.line("Évolution — psy", writer.headerPaint)
            synthesis.weeklyMetrics.filterKeys { it.startsWith("psy_") }.forEach { (key, series) ->
                drawSeries(writer, series, metricLabel(key), synthesis.trends[key])
            }
        }
        if (SynthesisPdfSection.GENERAL in synthesis.sections) {
            writer.line("Évolution — général", writer.headerPaint)
            synthesis.weeklyMetrics.filterKeys { it.startsWith("general_") }.forEach { (key, series) ->
                drawSeries(writer, series, metricLabel(key), synthesis.trends[key])
            }
            val impact = synthesis.impactDays
            writer.line(
                "Retentissement : activité réduite ${impact.reducedActivity} j, " +
                    "travail annulé ${impact.cancelledWork} j, repos ${impact.bedrest} j"
            )
        }
        if (SynthesisPdfSection.BOWEL in synthesis.sections) {
            writer.line("Évolution — intestinal", writer.headerPaint)
            drawSeries(writer, synthesis.weeklyMetrics["bowel_bloating"], "Ballonnements", synthesis.trends["bowel_bloating"])
        }

        if (SynthesisPdfSection.TREATMENTS in synthesis.sections && !synthesis.treatmentEffects.isEmpty) {
            writer.line("Médicaments et soins (contrôles douleur appariés)", writer.headerPaint)
            synthesis.treatmentEffects.medications.forEach { row ->
                writer.line(
                    "${row.name} : ${row.pairedCount} paires, Δ moyen ${"%.1f".format(row.meanDrop)} " +
                        "(+${row.improved} / =${row.unchanged} / -${row.worse})"
                )
            }
            synthesis.medicationCounts.forEach { count ->
                writer.line("${count.name} : ${count.count} prise(s) sur la période")
            }
        }

        if (SynthesisPdfSection.PAIN_HEATMAP in synthesis.sections) {
            writer.ensureSpace(200f)
            writer.line("Localisation des douleurs (max sur la période)", writer.headerPaint)
            val mapTop = writer.y + 4f
            val halfW = writer.contentWidth / 2f - 4f
            val mapH = 180f
            PainHeatmapPdfDrawer.drawSilhouette(
                context, writer.canvas, MARGIN, mapTop, halfW, mapH, front = true, synthesis.painHeatmap
            )
            PainHeatmapPdfDrawer.drawSilhouette(
                context,
                writer.canvas,
                MARGIN + halfW + 8f,
                mapTop,
                halfW,
                mapH,
                front = false,
                synthesis.painHeatmap
            )
            writer.y = mapTop + mapH + 12f
            writer.line("Face avant (gauche) · Face arrière (droite)", writer.secondaryPaint)
            writer.line(
                "Carte CHOIR (Stanford University). Données : intensité max par zone sur la période.",
                writer.secondaryPaint
            )
            synthesis.internalZoneHeat.forEach { (zone, intensity) ->
                writer.line("$zone : $intensity/10")
            }
            synthesis.zonePeaks.take(8).forEach { peak ->
                writer.line("${peak.zone} : max ${peak.max}/10")
            }
            synthesis.phasePain.forEach { row ->
                writer.line("Phase ${row.phase} : douleur moy. ${"%.1f".format(row.average)} (${row.sampleDays} j)")
            }
        }

        if (SynthesisPdfSection.JOURNAL in synthesis.sections) {
            writer.line("Journal détaillé", writer.headerPaint)
            synthesis.journal.visits.forEach { visit ->
                val day = visit.at.atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(dates)
                writer.line("$day · ${kindLabel(visit.kind)} · ${visit.title}")
                if (visit.subtitle.isNotBlank()) writer.line(visit.subtitle, writer.secondaryPaint)
                if (synthesis.journal.showDetails && visit.detail.isNotBlank()) {
                    visit.detail.lineSequence().forEach { writer.line(it, writer.secondaryPaint) }
                }
            }
        }

        writer.finishDocument()
        document.writeTo(outputStream)
        document.close()
    }

    private fun drawSeries(
        writer: PdfPageWriter,
        series: List<WeeklyMetricPoint>?,
        label: String,
        trend: SynthesisTrend? = null
    ) {
        if (series.isNullOrEmpty()) {
            writer.line("$label : données insuffisantes")
            return
        }
        val trendLabel = trend?.let { " · tendance ${it.name}" } ?: ""
        writer.line("$label$trendLabel", writer.textPaint)
        val barPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#235B4E")
            style = Paint.Style.FILL
        }
        series.takeLast(12).forEach { point ->
            writer.ensureSpace(20f)
            writer.line(
                "  Sem. ${point.weekStart}: moy ${"%.1f".format(point.average)}, max ${point.max}",
                writer.secondaryPaint
            )
            val width = writer.contentWidth * 0.6f * (point.max.coerceIn(0, 10) / 10f)
            writer.canvas.drawRect(MARGIN + 12f, writer.y, MARGIN + 12f + width, writer.y + 6f, barPaint)
            writer.y += 12f
        }
    }

    private fun metricLabel(key: String): String = when (key) {
        "5d_dysmenorrhea" -> "Dysménorrhée"
        "5d_dyschezia" -> "Dyschezie"
        "5d_dysuria" -> "Dysurie"
        "5d_chronic" -> "Douleur pelvienne chronique"
        "psy_mood" -> "Humeur"
        "psy_anxiety" -> "Anxiété"
        "general_fatigue" -> "Fatigue"
        "general_stress" -> "Stress"
        "general_sleep" -> "Sommeil"
        "bowel_bloating" -> "Ballonnements"
        else -> key
    }

    private fun kindLabel(kind: TimelineVisitKind): String = when (kind) {
        TimelineVisitKind.APPOINTMENT -> "RDV"
        TimelineVisitKind.HOSPITALIZATION -> "Hospitalisation"
        TimelineVisitKind.MEDICATION_INTAKE -> "Médicament"
        TimelineVisitKind.SYMPTOM -> "Symptôme"
        TimelineVisitKind.CARE_SESSION -> "Soin"
    }
}
