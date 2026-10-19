package fr.luteal.core.data.report

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import fr.luteal.core.model.report.ClinicalSynthesis
import fr.luteal.core.model.report.MetricSeriesSmoother
import fr.luteal.core.model.report.SynthesisPdfSection
import fr.luteal.core.model.report.SynthesisTrend
import fr.luteal.core.model.report.TimelineVisitKind
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object SynthesisPdfBuilder {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CHART_HEIGHT = 200f

    private val chartColors = listOf(
        "#C62828",
        "#1565C0",
        "#2E7D32",
        "#6A1B9A",
        "#EF6C00",
        "#00838F"
    )

    fun write(context: Context, synthesis: ClinicalSynthesis, outputStream: OutputStream) {
        val document = PdfDocument()
        val writer = PdfPageWriter(document, PAGE_WIDTH, PAGE_HEIGHT, MARGIN)
        val dates = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val zone = ZoneId.systemDefault()
        val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.FRANCE)

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
            writer.line("${SynthesisPdfLabels.sectionReliabilityLabel(section)} : ${rel.daysWithData}/${rel.daysInPeriod}")
        }
        writer.y += 8f

        if (SynthesisPdfSection.GENERAL in synthesis.sections) {
            val impact = synthesis.impactDays
            writer.line(
                "Retentissement : activité réduite ${impact.reducedActivity} j, " +
                    "travail annulé ${impact.cancelledWork} j, repos ${impact.bedrest} j"
            )
            writer.y += 6f
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
            writer.y += 6f
        }

        drawChartSection(
            writer,
            synthesis,
            title = "Évolution — douleurs 5D",
            keys = listOf("5d_dysmenorrhea", "5d_dyspareunia", "5d_dyschezia", "5d_dysuria", "5d_chronic"),
            enabled = SynthesisPdfSection.PAIN in synthesis.sections
        )
        drawChartSection(
            writer,
            synthesis,
            title = "Évolution — intensité des marques",
            keys = listOf("pain_marks"),
            enabled = SynthesisPdfSection.PAIN in synthesis.sections
        )
        drawChartSection(
            writer,
            synthesis,
            title = "Évolution — psycho",
            keys = listOf("psy_mood", "psy_anxiety"),
            enabled = SynthesisPdfSection.PSY in synthesis.sections
        )
        drawChartSection(
            writer,
            synthesis,
            title = "Évolution — général",
            keys = listOf("general_fatigue", "general_stress", "general_sleep"),
            enabled = SynthesisPdfSection.GENERAL in synthesis.sections
        )
        drawChartSection(
            writer,
            synthesis,
            title = "Évolution — intestinal",
            keys = listOf("bowel_bloating"),
            enabled = SynthesisPdfSection.BOWEL in synthesis.sections
        )

        if (SynthesisPdfSection.PAIN_HEATMAP in synthesis.sections) {
            writer.newPage(landscape = true)
            writer.line("Localisation des douleurs (saisies sur la période)", writer.headerPaint)
            val mapTop = writer.y + 4f
            val halfW = writer.contentWidth / 2f - 4f
            val mapH = writer.contentWidth * 0.42f
            PainHeatmapPdfDrawer.drawSilhouette(
                context, writer.canvas, MARGIN, mapTop, halfW, mapH, front = true, synthesis.painPinSamples
            )
            PainHeatmapPdfDrawer.drawSilhouette(
                context,
                writer.canvas,
                MARGIN + halfW + 8f,
                mapTop,
                halfW,
                mapH,
                front = false,
                synthesis.painPinSamples
            )
            writer.y = mapTop + mapH + 8f
            writer.line("Face avant (gauche) · Face arrière (droite)", writer.secondaryPaint)
            writer.line(
                "Carte CHOIR (Stanford University). Disques = intensité à l’emplacement saisi.",
                writer.secondaryPaint
            )
            if (synthesis.internalZoneHeat.isNotEmpty()) {
                writer.line("Zones internes (intensité max) :", writer.textPaint)
                synthesis.internalZoneHeat.entries
                    .sortedByDescending { it.value }
                    .forEach { (zone, intensity) ->
                        writer.line(
                            "  • ${SynthesisPdfLabels.zoneHumanLabel(context, zone)} : $intensity/10",
                            writer.secondaryPaint
                        )
                    }
            }
            synthesis.zonePeaks.take(6).forEach { peak ->
                writer.line(
                    "Pic : ${SynthesisPdfLabels.zoneHumanLabel(context, peak.zone)} — max ${peak.max}/10",
                    writer.secondaryPaint
                )
            }
            synthesis.phasePain.forEach { row ->
                writer.line("Phase ${row.phase} : douleur moy. ${"%.1f".format(row.average)} (${row.sampleDays} j)")
            }
        }

        if (SynthesisPdfSection.JOURNAL in synthesis.sections) {
            writer.newPage(landscape = false)
            writer.line("Journal détaillé", writer.headerPaint)
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            val byDate = synthesis.journal.visits
                .groupBy { it.at.atZone(zone).toLocalDate() }
                .toSortedMap()
            byDate.forEach { (date, dayVisits) ->
                writer.ensureSpace(24f)
                writer.line(date.format(longDate), writer.headerPaint)
                TimelineVisitKind.entries.forEach { kind ->
                    val items = dayVisits.filter { it.kind == kind }.sortedBy { it.at }
                    if (items.isEmpty()) return@forEach
                    writer.line(SynthesisPdfLabels.visitKindLabel(kind), writer.textPaint)
                    items.forEach { visit ->
                        val time = timeFmt.format(visit.at.atZone(zone))
                        val header = "  $time · ${visit.title}"
                        writer.line(header)
                        if (visit.subtitle.isNotBlank()) {
                            writer.line("    ${visit.subtitle}", writer.secondaryPaint)
                        }
                        if (synthesis.journal.showDetails && visit.detail.isNotBlank()) {
                            visit.detail.lineSequence().take(8).forEach { line ->
                                writer.line("    $line", writer.secondaryPaint)
                            }
                        }
                    }
                }
                writer.y += 4f
            }
        }

        writer.finishDocument()
        document.writeTo(outputStream)
        document.close()
    }

    private fun drawChartSection(
        writer: PdfPageWriter,
        synthesis: ClinicalSynthesis,
        title: String,
        keys: List<String>,
        enabled: Boolean
    ) {
        if (!enabled) return
        val seriesList = keys.mapNotNull { key ->
            val points = synthesis.dailyMetrics[key] ?: return@mapNotNull null
            if (points.isEmpty()) return@mapNotNull null
            key
        }
        if (seriesList.isEmpty()) return

        writer.newPage(landscape = true)
        writer.line(title, writer.headerPaint)
        val trendHints = seriesList.mapNotNull { key ->
            synthesis.trends[key]?.takeIf { it != SynthesisTrend.INSUFFICIENT }?.let {
                "${SynthesisPdfLabels.metricLabel(key)} : ${SynthesisPdfLabels.trendLabel(it)}"
            }
        }
        if (trendHints.isNotEmpty()) {
            writer.line(trendHints.joinToString(" · "), writer.secondaryPaint)
        }
        writer.y += 4f

        val chartSeries = seriesList.mapIndexed { index, key ->
            val points = synthesis.dailyMetrics[key] ?: emptyList()
            PdfChartSeries(
                label = SynthesisPdfLabels.metricLabel(key),
                color = Color.parseColor(chartColors[index % chartColors.size]),
                points = points,
                smoothed = MetricSeriesSmoother.movingAverage(points)
            )
        }
        val hasData = chartSeries.any { it.points.isNotEmpty() }
        if (!hasData) {
            writer.line("Données insuffisantes sur la période.")
            return
        }
        writer.ensureSpace(CHART_HEIGHT + 12f)
        PdfChartDrawer.draw(
            writer.canvas,
            MARGIN,
            writer.y,
            writer.contentWidth,
            CHART_HEIGHT,
            chartSeries
        )
        writer.y += CHART_HEIGHT + 12f
    }
}
