package fr.luteal.core.model.meds

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationSafetyCheckerTest {

    private val at = Instant.parse("2026-08-01T10:00:00Z")

    @Test
    fun paracetamolSplitAcrossTwoProductsExceedsDailyMax() {
        val catalog = catalog(
            substance("paracetamol", DrugClass.PARACETAMOL, maxDaily = 4000.0, perIntake = 1000.0, interval = 240),
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
        assertEquals(AlertSeverity.DANGER, alert.severity)
        assertTrue(alert.unvalidated)
        assertEquals(4300.0, alert.actualMg!!, 0.01)
    }

    @Test
    fun ibuprofenThenKetoprofenHitsInteractionRule() {
        val catalog = catalog(
            listOf(
                substance("ibuprofen", DrugClass.NSAID, maxDaily = 1200.0, perIntake = 400.0, interval = 360),
                substance("ketoprofen", DrugClass.NSAID, maxDaily = 200.0, perIntake = 100.0, interval = 480)
            ),
            listOf(
                medication("ibuprofen", "ibuprofen", 400.0),
                medication("ketoprofen", "ketoprofen", 100.0)
            ),
            listOf(nsaidRule())
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("ketoprofen", 1.0, at),
            listOf(RecordedIntake("ibuprofen", 1.0, at.minusSeconds(3600))),
            catalog
        )
        assertEquals(SafetyAlertType.INTERACTION_RULE, report.alerts.single().type)
        assertEquals("rule-nsaid", report.alerts.single().ruleId)
        assertTrue(report.alerts.none { it.type == SafetyAlertType.CLASS_DUPLICATE })
    }

    @Test
    fun classDuplicateWithoutExplicitRule() {
        val catalog = catalog(
            listOf(
                substance("a", DrugClass.NSAID, interval = 360),
                substance("b", DrugClass.NSAID, interval = 360)
            ),
            listOf(medication("med-a", "a", 10.0), medication("med-b", "b", 10.0))
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("med-b", 1.0, at),
            listOf(RecordedIntake("med-a", 1.0, at.minusSeconds(60))),
            catalog
        )
        assertEquals(SafetyAlertType.CLASS_DUPLICATE, report.alerts.single().type)
    }

    @Test
    fun minIntervalNotElapsed() {
        val catalog = catalog(
            substance("paracetamol", DrugClass.PARACETAMOL, interval = 240),
            listOf(medication("para", "paracetamol", 500.0))
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at),
            listOf(RecordedIntake("para", 1.0, at.minusSeconds(3600))),
            catalog
        )
        assertEquals(SafetyAlertType.MIN_INTERVAL_NOT_ELAPSED, report.alerts.single().type)
        assertEquals(at.plusSeconds(3 * 3600), report.nextPossibleIntakeAt)
    }

    @Test
    fun slidingWindowFreesAtTheExactHour() {
        val catalog = catalog(
            substance("paracetamol", DrugClass.PARACETAMOL, maxDaily = 4000.0),
            listOf(medication("para", "paracetamol", 1000.0))
        )
        val history = listOf(RecordedIntake("para", 3.0, at))
        val blocked = MedicationSafetyChecker.check(
            CandidateIntake("para", 2.0, at),
            history,
            catalog
        )
        assertTrue(blocked.alerts.any { it.type == SafetyAlertType.DAILY_MAX_EXCEEDED })
        assertEquals(at.plusSeconds(24 * 3600), blocked.nextPossibleIntakeAt)

        val freed = MedicationSafetyChecker.check(
            CandidateIntake("para", 2.0, at.plusSeconds(24 * 3600)),
            history,
            catalog
        )
        assertTrue(freed.alerts.none { it.type == SafetyAlertType.DAILY_MAX_EXCEEDED })
    }

    @Test
    fun perIntakeMaxExceeded() {
        val catalog = catalog(
            substance("ibuprofen", DrugClass.NSAID, perIntake = 400.0),
            listOf(medication("ibu", "ibuprofen", 400.0))
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("ibu", 2.0, at),
            emptyList(),
            catalog
        )
        assertEquals(SafetyAlertType.PER_INTAKE_MAX_EXCEEDED, report.alerts.single().type)
    }

    @Test
    fun medicationWithoutLimitsRaisesNothing() {
        val catalog = catalog(
            substance("plain", DrugClass.OTHER),
            listOf(medication("plain", "plain", 50.0))
        )
        val report = MedicationSafetyChecker.check(
            CandidateIntake("plain", 3.0, at),
            listOf(RecordedIntake("plain", 3.0, at.minusSeconds(60))),
            catalog
        )
        assertTrue(report.alerts.isEmpty())
        assertEquals(at, report.nextPossibleIntakeAt)
    }

    @Test
    fun nextPossibleIsTheLaterOfIntervalAndWindow() {
        val catalog = catalog(
            substance("paracetamol", DrugClass.PARACETAMOL, maxDaily = 1000.0, interval = 240),
            listOf(medication("para", "paracetamol", 500.0))
        )
        val history = listOf(RecordedIntake("para", 2.0, at))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at.plusSeconds(60)),
            history,
            catalog
        )
        assertEquals(at.plusSeconds(24 * 3600), report.nextPossibleIntakeAt)
    }

    @Test
    fun nextPossibleIntervalOnlyAndWindowOnly() {
        val intervalOnly = catalog(
            substance("para", DrugClass.PARACETAMOL, interval = 240),
            listOf(medication("para", "para", 500.0))
        )
        val intervalReport = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at.plusSeconds(60)),
            listOf(RecordedIntake("para", 1.0, at)),
            intervalOnly
        )
        assertEquals(at.plusSeconds(240 * 60), intervalReport.nextPossibleIntakeAt)

        val windowOnly = catalog(
            substance("para", DrugClass.PARACETAMOL, maxDaily = 500.0),
            listOf(medication("para", "para", 500.0))
        )
        val windowReport = MedicationSafetyChecker.check(
            CandidateIntake("para", 1.0, at.plusSeconds(60)),
            listOf(RecordedIntake("para", 1.0, at)),
            windowOnly
        )
        assertEquals(at.plusSeconds(24 * 3600), windowReport.nextPossibleIntakeAt)
        assertNull(windowReport.alerts.find { it.type == SafetyAlertType.MIN_INTERVAL_NOT_ELAPSED })
    }

    private fun nsaidRule() = InteractionRule(
        id = "rule-nsaid",
        targetAType = InteractionTargetType.CLASS,
        targetAId = "NSAID",
        targetBType = InteractionTargetType.CLASS,
        targetBId = "NSAID",
        severity = AlertSeverity.DANGER,
        message = "Deux AINS"
    )

    private fun catalog(
        substance: Substance,
        medications: List<Medication>,
        rules: List<InteractionRule> = emptyList()
    ) = MedicationCatalog(listOf(substance), medications, rules)

    private fun catalog(
        substances: List<Substance>,
        medications: List<Medication>,
        rules: List<InteractionRule> = emptyList()
    ) = MedicationCatalog(substances, medications, rules)

    private fun substance(
        id: String,
        drugClass: DrugClass,
        maxDaily: Double? = null,
        perIntake: Double? = null,
        interval: Int? = null
    ) = Substance(id, id, drugClass, maxDaily, perIntake, interval, validated = false, notes = "")

    @Test
    fun contraindicatedSubstanceAlertsWithinWindow() {
        val tramadol = substance("tramadol", DrugClass.OPIOID)
        val paracetamol = substance("paracetamol", DrugClass.PARACETAMOL)
        val medA = medication("med-a", "paracetamol", 500.0, contraindicated = listOf("tramadol"))
        val medB = medication("med-b", "tramadol", 50.0)
        val catalog = catalog(listOf(tramadol, paracetamol), listOf(medA, medB))
        val at = Instant.parse("2026-06-01T12:00:00Z")
        val history = listOf(RecordedIntake("med-b", 1.0, at.minus(Duration.ofHours(2))))
        val report = MedicationSafetyChecker.check(
            CandidateIntake("med-a", 1.0, at),
            history,
            catalog
        )
        assertEquals(SafetyAlertType.CONTRAINDICATED_SUBSTANCE, report.alerts.single().type)
        val outside = MedicationSafetyChecker.check(
            CandidateIntake("med-a", 1.0, at),
            emptyList(),
            catalog
        )
        assertTrue(outside.alerts.none { it.type == SafetyAlertType.CONTRAINDICATED_SUBSTANCE })
    }

    private fun medication(
        id: String,
        substanceId: String,
        mg: Double,
        contraindicated: List<String> = emptyList()
    ) = Medication(
        id = id,
        name = id,
        form = "comprimé",
        contraindications = "",
        archived = false,
        compositions = listOf(Composition(substanceId, mg)),
        contraindicatedSubstanceIds = contraindicated
    )
}
