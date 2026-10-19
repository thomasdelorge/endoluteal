package fr.luteal.core.model.report

import java.time.LocalDate

enum class SynthesisTrend {
    UP,
    DOWN,
    STABLE,
    INSUFFICIENT
}

enum class SynthesisPdfSection {
    PAIN,
    PAIN_HEATMAP,
    PSY,
    GENERAL,
    BOWEL,
    TREATMENTS,
    JOURNAL;

    companion object {
        val DEFAULT: Set<SynthesisPdfSection> = entries.toSet()
    }
}

data class SectionReliability(
    val daysWithData: Int,
    val daysInPeriod: Int
)

data class WeeklyMetricPoint(
    val weekStart: LocalDate,
    val average: Double,
    val max: Int
)

data class ImpactDayCount(
    val reducedActivity: Int = 0,
    val cancelledWork: Int = 0,
    val bedrest: Int = 0
)

data class PhasePainAverage(
    val phase: String,
    val average: Double,
    val sampleDays: Int
)

data class ClinicalSynthesis(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val filterLines: List<String>,
    val reliability: Map<SynthesisPdfSection, SectionReliability>,
    val attentionPoints: List<String>,
    val weeklyMetrics: Map<String, List<WeeklyMetricPoint>>,
    val trends: Map<String, SynthesisTrend>,
    val impactDays: ImpactDayCount,
    val zonePeaks: List<ZonePeak>,
    val painHeatmap: Map<String, Int>,
    val internalZoneHeat: Map<String, Int>,
    val phasePain: List<PhasePainAverage>,
    val treatmentEffects: TreatmentEffectSummary,
    val medicationCounts: List<MedicationCount>,
    val journal: TimelinePdfContent,
    val sections: Set<SynthesisPdfSection>
)
