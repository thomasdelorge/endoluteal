package fr.luteal.core.model.meds

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationSafetyCheckerTest {

    private val at = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun paracetamolSplitAcrossTwoProductsExceedsDailyMax() {
        val catalog = catalog(
            substance("paracetamol", maxDaily = 4000.0, perIntake = 1000.0, interval = 240),
            listOf(
                medication("doliprane", "paracetamol", 1000.0),
                medication("lamaline", "paracetamol", 300.0)
            )
        )
        val history = (0 until 4).map { index ->
            RecordedIntake("doliprane", 1.0, at.minusSeconds((index + 1) * 5 * 3600L))
        }
        val report = MedicationSafetyChecker.check(
            CandidateIntake("lamaline", 1.0, at),
            history,
            catalog
        )
        val alert = report.alerts.single { it.type == SafetyAlertType.DAILY_MAX_EXCEEDED }
        assertEquals("paracetamol", alert.substanceId)
        assertEquals(4300.0, alert.actualMg!!, 0.01)
    }

    @Test
    fun periodWindowAllowsSplitDosesWithinLimit() {
        val catalog = catalog(
            substance("paracetamol", perIntake = 1000.0, interval = 240),
            listOf(medication("para", "paracetamol", 500.0))
        )
        val history = listOf(RecordedIntake("para", 1.0, at.minusSeconds(2 * 3600)))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at),
            history,
            catalog
        )
        assertTrue(report.alerts.isEmpty())
    }

    @Test
    fun periodWindowAlertsWhenCumulativeExceedsLimit() {
        val catalog = catalog(
            substance("paracetamol", perIntake = 1000.0, interval = 240),
            listOf(medication("para", "paracetamol", 500.0))
        )
        val history = listOf(RecordedIntake("para", 2.0, at.minusSeconds(2 * 3600)))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at),
            history,
            catalog
        )
        assertEquals(SafetyAlertType.PER_INTAKE_MAX_EXCEEDED, report.alerts.single().type)
        assertEquals(1500.0, report.alerts.single().actualMg!!, 0.01)
    }

    @Test
    fun periodWindowIgnoresDosesOutsideWindow() {
        val catalog = catalog(
            substance("paracetamol", perIntake = 1000.0, interval = 240),
            listOf(medication("para", "paracetamol", 500.0))
        )
        val history = listOf(RecordedIntake("para", 2.0, at.minusSeconds(5 * 3600)))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at),
            history,
            catalog
        )
        assertTrue(report.alerts.isEmpty())
    }

    @Test
    fun slidingWindowFreesDailyAtExactHour() {
        val catalog = catalog(
            substance("paracetamol", maxDaily = 4000.0),
            listOf(medication("para", "paracetamol", 1000.0))
        )
        val history = listOf(RecordedIntake("para", 3.0, at))
        val blocked = MedicationSafetyChecker.check(
            CandidateIntake("para", 2.0, at),
            history,
            catalog
        )
        assertTrue(blocked.alerts.any { it.type == SafetyAlertType.DAILY_MAX_EXCEEDED })

        val freed = MedicationSafetyChecker.check(
            CandidateIntake("para", 2.0, at.plusSeconds(24 * 3600)),
            history,
            catalog
        )
        assertTrue(freed.alerts.none { it.type == SafetyAlertType.DAILY_MAX_EXCEEDED })
    }

    @Test
    fun medicationWithoutLimitsRaisesNothing() {
        val catalog = catalog(
            substance("plain"),
            listOf(medication("plain", "plain", 50.0))
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("plain", 3.0, at),
            listOf(RecordedIntake("plain", 3.0, at.minusSeconds(60))),
            catalog
        )
        assertTrue(report.alerts.isEmpty())
    }

    @Test
    fun contraindicatedSubstanceAlertsWithinWindow() {
        val tramadol = substance("tramadol")
        val paracetamol = substance("paracetamol")
        val medA = medication("med-a", "paracetamol", 500.0, contraindicated = listOf("tramadol"))
        val medB = medication("med-b", "tramadol", 50.0)
        val catalog = catalog(listOf(tramadol, paracetamol), listOf(medA, medB))
        val history = listOf(RecordedIntake("med-b", 1.0, at.minus(Duration.ofHours(2))))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("med-a", 1.0, at),
            history,
            catalog
        )
        assertEquals(SafetyAlertType.CONTRAINDICATED_SUBSTANCE, report.alerts.single().type)
    }

    private fun catalog(
        substance: Substance,
        medications: List<Medication>
    ) = MedicationCatalog(listOf(substance), medications, emptyList())

    private fun catalog(
        substances: List<Substance>,
        medications: List<Medication>
    ) = MedicationCatalog(substances, medications, emptyList())

    private fun substance(
        id: String,
        maxDaily: Double? = null,
        perIntake: Double? = null,
        interval: Int? = null
    ) = Substance(id, id, DrugClass.OTHER, maxDaily, perIntake, interval, validated = true, notes = "")

    private fun medication(
        id: String,
        substanceId: String,
        mg: Double,
        contraindicated: List<String> = emptyList()
    ) = Medication(
        id = id,
        name = id,
        form = "",
        contraindications = "",
        archived = false,
        compositions = listOf(Composition(substanceId, mg)),
        contraindicatedSubstanceIds = contraindicated
    )
}
