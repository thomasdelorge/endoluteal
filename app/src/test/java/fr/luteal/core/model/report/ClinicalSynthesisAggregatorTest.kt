package fr.luteal.core.model.report

import fr.luteal.core.designsystem.component.bodymap.PainHeatmap
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapPainMark
import fr.luteal.core.model.recap.RecapStep
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import fr.luteal.core.model.report.SynthesisTrend
import org.junit.Test

class ClinicalSynthesisAggregatorTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun painHeatmapMergesMaxPerZone() {
        val marks = listOf(
            PainMark("1", "f08", 3, emptyList()),
            PainMark("2", "f08", 7, emptyList()),
            PainMark("3", "b02", 2, emptyList())
        )
        val heat = PainHeatmap.zoneIntensityMax(marks)
        assertEquals(7, heat["f08"])
        assertEquals(2, heat["b02"])
    }

    @Test
    fun weeklyPainSeriesGroupsByWeek() {
        val day = LocalDate.of(2026, 6, 3)
        val recap = DailyRecap.empty(day, Instant.EPOCH).copy(
            pain = DailyRecap.empty(day, Instant.EPOCH).pain.copy(
                marks = listOf(RecapPainMark("a", "f08", 5, emptyList()))
            ),
            completedSteps = setOf(RecapStep.PAIN)
        )
        val series = ClinicalSynthesisAggregator.weeklyPainSeries(
            events = emptyList(),
            recaps = listOf(recap),
            zone = zone,
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-06-30T00:00:00Z")
        )
        assertEquals(1, series.size)
        assertEquals(5, series.single().max)
    }

    @Test
    fun buildIncludesAttentionWhenHighPain() {
        val synthesis = ClinicalSynthesisAggregator.build(
            SynthesisBuildRequest(
                fromInclusive = Instant.parse("2026-06-01T00:00:00Z"),
                toInclusive = Instant.parse("2026-06-30T00:00:00Z"),
                zone = zone,
                events = emptyList(),
                recaps = listOf(
                    DailyRecap.empty(LocalDate.of(2026, 6, 5), Instant.EPOCH).copy(
                        pain = DailyRecap.empty(LocalDate.of(2026, 6, 5), Instant.EPOCH).pain.copy(
                            marks = listOf(RecapPainMark("a", "f08", 9, emptyList()))
                        ),
                        completedSteps = setOf(RecapStep.PAIN)
                    )
                ),
                intakes = emptyList(),
                medications = emptyList(),
                allVisits = emptyList(),
                visitKinds = TimelineVisitKind.FILTERABLE_ALL,
                professionalIds = emptySet(),
                showJournalDetails = true,
                sections = SynthesisPdfSection.DEFAULT,
                filterLines = emptyList()
            )
        )
        assertTrue(synthesis.attentionPoints.any { it.contains("9") })
    }

    @Test
    fun trendInsufficientWithFewWeeklyPoints() {
        val synthesis = ClinicalSynthesisAggregator.build(
            SynthesisBuildRequest(
                fromInclusive = Instant.parse("2026-06-01T00:00:00Z"),
                toInclusive = Instant.parse("2026-06-30T00:00:00Z"),
                zone = zone,
                events = emptyList(),
                recaps = listOf(
                    DailyRecap.empty(LocalDate.of(2026, 6, 2), Instant.EPOCH).copy(
                        pain = DailyRecap.empty(LocalDate.of(2026, 6, 2), Instant.EPOCH).pain.copy(dysmenorrhea = 3),
                        completedSteps = setOf(RecapStep.PAIN)
                    )
                ),
                intakes = emptyList(),
                medications = emptyList(),
                allVisits = emptyList(),
                visitKinds = TimelineVisitKind.FILTERABLE_ALL,
                professionalIds = emptySet(),
                showJournalDetails = true,
                sections = setOf(SynthesisPdfSection.PAIN),
                filterLines = emptyList()
            )
        )
        assertEquals(SynthesisTrend.INSUFFICIENT, synthesis.trends["5d_dysmenorrhea"])
    }
}
