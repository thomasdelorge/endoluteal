package fr.luteal.core.model.report

import fr.luteal.core.model.professional.Specialty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SinceLastVisitAggregatorTest {
    private val zone = ZoneOffset.UTC
    private val monday = Instant.parse("2026-06-01T10:00:00Z")
    private val laterSameWeek = Instant.parse("2026-06-03T10:00:00Z")
    private val outside = Instant.parse("2026-05-01T10:00:00Z")

    @Test
    fun summarizesFixedWindow() {
        val summary = SinceLastVisitAggregator.summarize(
            fromInclusive = Instant.parse("2026-06-01T00:00:00Z"),
            toInclusive = Instant.parse("2026-06-30T00:00:00Z"),
            zone = zone,
            pain = listOf(
                TimelinePainSample(monday, "pelvis", 2),
                TimelinePainSample(laterSameWeek, "pelvis", 4),
                TimelinePainSample(laterSameWeek, "back", 1),
                TimelinePainSample(outside, "pelvis", 10)
            ),
            intakes = listOf(
                TimelineIntakeSample(monday, "med-1", "Ibuprofène"),
                TimelineIntakeSample(laterSameWeek, "med-1", "Ibuprofène"),
                TimelineIntakeSample(outside, "med-2", "Autre")
            ),
            hospitalizations = listOf(monday, outside),
            careSessions = listOf(laterSameWeek, outside)
        )
        assertEquals(1, summary.weeklyPain.size)
        assertEquals(7.0 / 3.0, summary.weeklyPain.single().average, 0.001)
        assertEquals(4, summary.weeklyPain.single().max)
        assertEquals(listOf(ZonePeak("back", 1), ZonePeak("pelvis", 4)), summary.zonePeaks)
        assertEquals(1, summary.intakes.size)
        assertEquals(2, summary.intakes.single().count)
        assertEquals(1, summary.hospitalizationCount)
        assertEquals(1, summary.careSessionCount)
    }
}

class TimelineFilterTest {
    @Test
    fun professionalFilterKeepsMatchingAppointmentsAndOtherKinds() {
        val visits = listOf(
            visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"),
            visit("b", TimelineVisitKind.APPOINTMENT, "pro-2"),
            visit("h", TimelineVisitKind.HOSPITALIZATION, null)
        )
        val filtered = TimelineFilter.apply(
            visits,
            kinds = TimelineVisitKind.FILTERABLE_ALL,
            professionalIds = setOf("pro-1"),
            fromInclusive = null,
            toInclusive = null
        )
        assertEquals(setOf("a", "h"), filtered.map { it.eventId }.toSet())
    }

    @Test
    fun severalProfessionalsKeepEachAppointment() {
        val visits = listOf(
            visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"),
            visit("b", TimelineVisitKind.APPOINTMENT, "pro-2"),
            visit("c", TimelineVisitKind.APPOINTMENT, "pro-3")
        )
        val filtered = TimelineFilter.apply(
            visits,
            kinds = TimelineVisitKind.FILTERABLE_ALL,
            professionalIds = setOf("pro-1", "pro-3"),
            fromInclusive = null,
            toInclusive = null
        )
        assertEquals(setOf("a", "c"), filtered.map { it.eventId }.toSet())
    }

    @Test
    fun emptyProfessionalSetKeepsEveryVisit() {
        val visits = listOf(
            visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"),
            visit("b", TimelineVisitKind.APPOINTMENT, "pro-2")
        )
        val filtered = TimelineFilter.apply(
            visits,
            kinds = TimelineVisitKind.FILTERABLE_ALL,
            professionalIds = emptySet(),
            fromInclusive = null,
            toInclusive = null
        )
        assertEquals(listOf("a", "b"), filtered.map { it.eventId })
    }

    @Test
    fun emptyKindsFilterShowsNothing() {
        val visits = listOf(visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"))
        val filtered = TimelineFilter.apply(
            visits,
            kinds = emptySet(),
            professionalIds = emptySet(),
            fromInclusive = null,
            toInclusive = null
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun kindFilterSubset() {
        val visits = listOf(
            visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"),
            visit("m", TimelineVisitKind.MEDICATION_INTAKE, null, null)
        )
        val filtered = TimelineFilter.apply(
            visits,
            kinds = setOf(TimelineVisitKind.MEDICATION_INTAKE),
            professionalIds = emptySet(),
            fromInclusive = null,
            toInclusive = null
        )
        assertEquals(listOf("m"), filtered.map { it.eventId })
    }

    @Test
    fun pdfContentKeepsOnlyTheSelectedProfessionals() {
        val visits = listOf(
            visit("a", TimelineVisitKind.APPOINTMENT, "pro-1"),
            visit("b", TimelineVisitKind.APPOINTMENT, "pro-2"),
            visit("h", TimelineVisitKind.HOSPITALIZATION, null)
        )
        val content = buildTimelinePdfContent(
            visits = visits,
            kinds = TimelineVisitKind.FILTERABLE_ALL,
            professionalIds = setOf("pro-1"),
            fromInclusive = null,
            toInclusive = null,
            summary = null
        )
        assertEquals(setOf("a", "h"), content.visits.map { it.eventId }.toSet())
    }

    private fun visit(
        id: String,
        kind: TimelineVisitKind,
        professionalId: String?,
        specialty: Specialty? = Specialty.GYNECOLOGIST
    ) = TimelineVisit(
        eventId = id,
        kind = kind,
        professionalId = professionalId,
        specialty = specialty,
        at = Instant.parse("2026-06-01T10:00:00Z"),
        title = id,
        subtitle = "",
        detail = ""
    )
}
