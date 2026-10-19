package fr.luteal.core.model.report

import fr.luteal.core.data.local.MedicationIntakeRow
import fr.luteal.core.designsystem.component.bodymap.PainHeatmap
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapDailyImpact
import fr.luteal.core.model.recap.RecapStep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

data class SynthesisBuildRequest(
    val fromInclusive: Instant,
    val toInclusive: Instant,
    val zone: ZoneId,
    val events: List<JournalEvent>,
    val recaps: List<DailyRecap>,
    val intakes: List<MedicationIntakeRow>,
    val medications: List<Medication>,
    val allVisits: List<TimelineVisit>,
    val visitKinds: Set<TimelineVisitKind>,
    val professionalIds: Set<String>,
    val showJournalDetails: Boolean,
    val sections: Set<SynthesisPdfSection>,
    val filterLines: List<String>
)

object ClinicalSynthesisAggregator {
    private const val TREND_MIN_POINTS = 4
    private const val TREND_THRESHOLD = 0.35

    fun build(request: SynthesisBuildRequest): ClinicalSynthesis {
        val fromDate = request.fromInclusive.atZone(request.zone).toLocalDate()
        val toDate = request.toInclusive.atZone(request.zone).toLocalDate()
        val periodDays = ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1
        val recapsInPeriod = request.recaps.filter { it.date in fromDate..toDate }
        val marks = PainHeatmap.marksFromEvents(request.events, request.fromInclusive, request.toInclusive) +
            PainHeatmap.marksFromRecaps(request.recaps, fromDate, toDate)
        val zoneHeat = PainHeatmap.zoneIntensityMax(marks)
        val weeklyPain = weeklyPainFromSources(
            request.events,
            recapsInPeriod,
            request.zone,
            request.fromInclusive,
            request.toInclusive
        )
        val weeklyMetrics = buildWeeklyRecapMetrics(recapsInPeriod) + mapOf("pain_marks" to weeklyPain)
        val trends = weeklyMetrics.mapValues { (_, points) -> trend(points.map { it.average }) }
        val reliability = sectionReliability(recapsInPeriod, periodDays, marks, request.events, request.fromInclusive, request.toInclusive)
        val treatmentUses = if (SynthesisPdfSection.TREATMENTS in request.sections) {
            buildTreatmentUses(
                request.events,
                request.intakes,
                request.medications,
                request.recaps,
                request.zone,
                request.fromInclusive,
                request.toInclusive
            )
        } else {
            emptyList()
        }
        val treatmentEffects = TreatmentEffectAggregator.summarize(treatmentUses)
        val medCounts = intakeCounts(request.intakes, request.medications, request.fromInclusive, request.toInclusive)
        val journal = buildTimelinePdfContent(
            visits = request.allVisits,
            kinds = request.visitKinds,
            professionalIds = request.professionalIds,
            fromInclusive = request.fromInclusive,
            toInclusive = request.toInclusive,
            summary = null,
            showDetails = request.showJournalDetails
        )
        return ClinicalSynthesis(
            periodStart = fromDate,
            periodEnd = toDate,
            filterLines = request.filterLines,
            reliability = reliability,
            attentionPoints = attentionPoints(weeklyPain, treatmentEffects, recapsInPeriod, zoneHeat),
            weeklyMetrics = weeklyMetrics.filterKeys { metricIncluded(it, request.sections) },
            trends = trends.filterKeys { metricIncluded(it, request.sections) },
            impactDays = impactDays(recapsInPeriod),
            zonePeaks = zoneHeat.entries
                .sortedByDescending { it.value }
                .take(12)
                .map { ZonePeak(it.key, it.value) },
            painHeatmap = PainHeatmap.silhouetteHeat(zoneHeat),
            internalZoneHeat = PainHeatmap.internalZoneHeat(zoneHeat),
            phasePain = phasePainAverages(recapsInPeriod),
            treatmentEffects = treatmentEffects,
            medicationCounts = medCounts,
            journal = journal,
            sections = request.sections
        )
    }

    private fun metricIncluded(key: String, sections: Set<SynthesisPdfSection>): Boolean = when {
        key == "pain_marks" || key.startsWith("5d_") -> SynthesisPdfSection.PAIN in sections
        key.startsWith("psy_") -> SynthesisPdfSection.PSY in sections
        key.startsWith("general_") -> SynthesisPdfSection.GENERAL in sections
        key.startsWith("bowel_") -> SynthesisPdfSection.BOWEL in sections
        else -> true
    }

    private fun weeklyPainFromSources(
        events: List<JournalEvent>,
        recaps: List<DailyRecap>,
        zone: ZoneId,
        from: Instant,
        to: Instant
    ): List<WeeklyMetricPoint> {
        val weeks = WeekFields.of(Locale.FRANCE)
        val samples = mutableListOf<Pair<LocalDate, Int>>()
        events.filter { !it.startAt.isBefore(from) && !it.startAt.isAfter(to) }
            .forEach { event ->
                val day = event.startAt.atZone(zone).toLocalDate()
                event.marks.maxOfOrNull { it.intensity }?.let { samples += day to it }
            }
        recaps.forEach { recap ->
            recap.pain.marks.maxOfOrNull { it.intensity }?.let { samples += recap.date to it }
        }
        return samples.groupBy { (day, _) -> day.with(weeks.dayOfWeek(), 1) }
            .map { (weekStart, rows) ->
                val values = rows.map { it.second }
                WeeklyMetricPoint(weekStart, values.average(), values.max())
            }
            .sortedBy { it.weekStart }
    }

    private fun buildWeeklyRecapMetrics(recaps: List<DailyRecap>): Map<String, List<WeeklyMetricPoint>> {
        val weeks = WeekFields.of(Locale.FRANCE)
        fun series(step: RecapStep, values: (DailyRecap) -> List<Int?>): List<WeeklyMetricPoint> {
            val points = recaps.filter { step in it.completedSteps }.flatMap { recap ->
                values(recap).filterNotNull().map { recap.date to it }
            }
            return points.groupBy { (day, _) -> day.with(weeks.dayOfWeek(), 1) }
                .map { (weekStart, rows) ->
                    val ints = rows.map { it.second }
                    WeeklyMetricPoint(weekStart, ints.average(), ints.max())
                }
                .sortedBy { it.weekStart }
        }
        return buildMap {
            put("5d_dysmenorrhea", series(RecapStep.PAIN) { listOf(it.pain.dysmenorrhea) })
            put("5d_dyspareunia", series(RecapStep.PAIN) { listOf(it.pain.dyspareunia) })
            put("5d_dyschezia", series(RecapStep.PAIN) { listOf(it.pain.dyschezia) })
            put("5d_dysuria", series(RecapStep.PAIN) { listOf(it.pain.dysuria) })
            put("5d_chronic", series(RecapStep.PAIN) { listOf(it.pain.chronicPelvicPain) })
            put("psy_mood", series(RecapStep.PSY) { listOf(it.psy.mood) })
            put("psy_anxiety", series(RecapStep.PSY) { listOf(it.psy.anxiety) })
            put("general_fatigue", series(RecapStep.GENERAL) { listOf(it.general.fatigue) })
            put("general_stress", series(RecapStep.GENERAL) { listOf(it.general.stress) })
            put("general_sleep", series(RecapStep.GENERAL) { listOf(it.general.sleepQuality) })
            put("bowel_bloating", series(RecapStep.BOWEL) { listOf(it.bowel.bloating) })
        }
    }

    fun weeklyPainSeries(
        events: List<JournalEvent>,
        recaps: List<DailyRecap>,
        zone: ZoneId,
        from: Instant,
        to: Instant
    ): List<WeeklyMetricPoint> = weeklyPainFromSources(events, recaps, zone, from, to)

    private fun trend(values: List<Double>): SynthesisTrend {
        if (values.size < TREND_MIN_POINTS) return SynthesisTrend.INSUFFICIENT
        val half = values.size / 2
        if (half == 0) return SynthesisTrend.INSUFFICIENT
        val first = values.take(half).average()
        val second = values.drop(half).average()
        val delta = second - first
        return when {
            abs(delta) < TREND_THRESHOLD -> SynthesisTrend.STABLE
            delta > 0 -> SynthesisTrend.UP
            else -> SynthesisTrend.DOWN
        }
    }

    private fun sectionReliability(
        recaps: List<DailyRecap>,
        periodDays: Int,
        marks: List<fr.luteal.core.model.PainMark>,
        events: List<JournalEvent>,
        from: Instant,
        to: Instant
    ): Map<SynthesisPdfSection, SectionReliability> {
        val painDays = recaps.count { RecapStep.PAIN in it.completedSteps } +
            if (marks.isNotEmpty()) 1 else 0
        val eventPainDays = events.count { it.marks.isNotEmpty() && !it.startAt.isBefore(from) && !it.startAt.isAfter(to) }
        return mapOf(
            SynthesisPdfSection.PAIN to SectionReliability((painDays + eventPainDays).coerceAtMost(periodDays), periodDays),
            SynthesisPdfSection.PSY to SectionReliability(recaps.count { RecapStep.PSY in it.completedSteps }, periodDays),
            SynthesisPdfSection.GENERAL to SectionReliability(recaps.count { RecapStep.GENERAL in it.completedSteps }, periodDays),
            SynthesisPdfSection.BOWEL to SectionReliability(recaps.count { RecapStep.BOWEL in it.completedSteps }, periodDays)
        )
    }

    private fun impactDays(recaps: List<DailyRecap>): ImpactDayCount {
        var reduced = 0
        var cancelled = 0
        var bed = 0
        recaps.filter { RecapStep.GENERAL in it.completedSteps }.forEach { recap ->
            when (recap.general.dailyImpact) {
                RecapDailyImpact.REDUCED_ACTIVITY.name -> reduced++
                RecapDailyImpact.CANCELLED_WORK.name -> cancelled++
                RecapDailyImpact.BEDREST.name -> bed++
            }
        }
        return ImpactDayCount(reduced, cancelled, bed)
    }

    private fun phasePainAverages(recaps: List<DailyRecap>): List<PhasePainAverage> =
        recaps.filter { RecapStep.PAIN in it.completedSteps && RecapStep.CYCLE in it.completedSteps }
            .mapNotNull { recap ->
                val phase = recap.cycle.phaseOverride.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val values = listOfNotNull(
                    recap.pain.dysmenorrhea,
                    recap.pain.dyschezia,
                    recap.pain.dysuria,
                    recap.pain.chronicPelvicPain
                )
                if (values.isEmpty()) return@mapNotNull null
                phase to values.average()
            }
            .groupBy { it.first }
            .map { (phase, rows) ->
                PhasePainAverage(phase, rows.map { it.second }.average(), rows.size)
            }
            .sortedBy { it.phase }

    private fun intakeCounts(
        intakes: List<MedicationIntakeRow>,
        medications: List<Medication>,
        from: Instant,
        to: Instant
    ): List<MedicationCount> {
        val names = medications.associate { it.id to it.name }
        return intakes.filter { row ->
            val at = Instant.ofEpochMilli(row.startAt)
            !at.isBefore(from) && !at.isAfter(to)
        }.groupBy { it.medicationId }
            .map { (id, rows) ->
                MedicationCount(id, names[id] ?: id, rows.size)
            }
            .sortedBy { it.name }
    }

    private fun attentionPoints(
        weeklyPain: List<WeeklyMetricPoint>,
        treatment: TreatmentEffectSummary,
        recaps: List<DailyRecap>,
        zoneHeat: Map<String, Int>
    ): List<String> {
        val points = mutableListOf<String>()
        val maxPain = weeklyPain.maxOfOrNull { it.max } ?: zoneHeat.values.maxOrNull()
        if (maxPain != null && maxPain >= 8) {
            points += "Douleur enregistrée jusqu’à $maxPain/10 sur la période (saisies utilisatrice)."
        }
        val impact = impactDays(recaps)
        val impactTotal = impact.reducedActivity + impact.cancelledWork + impact.bedrest
        if (impactTotal >= 3) {
            points += "Retentissement sur la journée signalé $impactTotal jour(s) (activité réduite, arrêt ou repos)."
        }
        treatment.medications.filter { it.pairedCount >= 5 && it.meanDrop <= 0 }.take(2).forEach { row ->
            points += "${row.name} : $row.pairedCount prises avec contrôle douleur, baisse moyenne ${"%.1f".format(row.meanDrop)} (descriptif, pas efficacité prouvée)."
        }
        if (points.isEmpty()) {
            points += "Aucun signal automatique fort ; voir séries et journal détaillé."
        }
        return points.take(8)
    }
}
