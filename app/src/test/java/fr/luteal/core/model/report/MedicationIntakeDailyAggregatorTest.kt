package fr.luteal.core.model.report

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MedicationIntakeDailyAggregatorTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.parse("2026-06-01")

    @Test
    fun countsIntakesPerDayAndMedication() {
        val samples = listOf(
            TimelineIntakeSample(Instant.parse("2026-06-01T08:00:00Z"), "tramadol", "Tramadol"),
            TimelineIntakeSample(Instant.parse("2026-06-01T10:00:00Z"), "tramadol", "Tramadol"),
            TimelineIntakeSample(Instant.parse("2026-06-01T12:00:00Z"), "tramadol", "Tramadol"),
            TimelineIntakeSample(Instant.parse("2026-06-01T14:00:00Z"), "tramadol", "Tramadol"),
            TimelineIntakeSample(Instant.parse("2026-06-02T10:00:00Z"), "tramadol", "Tramadol")
        )
        val rows = MedicationIntakeDailyAggregator.aggregate(
            intakes = samples,
            zone = zone,
            fromInclusive = null,
            toInclusive = null
        )
        assertEquals(2, rows.size)
        assertEquals(
            DailyMedicationIntakeCount(day, "tramadol", "Tramadol", 4),
            rows.first()
        )
        assertEquals(1, rows.last().count)
    }
}
