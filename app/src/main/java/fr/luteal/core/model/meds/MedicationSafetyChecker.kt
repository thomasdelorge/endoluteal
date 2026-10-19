package fr.luteal.core.model.meds

import java.time.Duration
import java.time.Instant

enum class SafetyAlertType {
    DAILY_MAX_EXCEEDED,
    PER_INTAKE_MAX_EXCEEDED,
    CONTRAINDICATED_SUBSTANCE
}

data class SafetyAlert(
    val type: SafetyAlertType,
    val severity: AlertSeverity,
    val substanceId: String? = null,
    val actualMg: Double? = null,
    val limitMg: Double? = null
)

data class RecordedIntake(
    val medicationId: String,
    val units: Double,
    val at: Instant
)

data class CandidateIntake(
    val medicationId: String,
    val units: Double,
    val at: Instant
)

data class SafetyReport(
    val alerts: List<SafetyAlert>,
    val nextPossibleIntakeAt: Instant
)

object MedicationSafetyChecker {
    private val dailyWindow: Duration = Duration.ofHours(24)

    fun check(
        candidate: CandidateIntake,
        history: List<RecordedIntake>,
        catalog: MedicationCatalog
    ): SafetyReport {
        val substances = catalog.substances.associateBy { it.id }
        val dailyStart = candidate.at.minus(dailyWindow)
        val recent = history.filter { intake ->
            intake.at.isAfter(dailyStart) && !intake.at.isAfter(candidate.at)
        }
        val candidateDoses = dosesOf(candidate.medicationId, candidate.units, catalog)
        val historyDoses = recent.flatMap { intake ->
            dosesOf(intake.medicationId, intake.units, catalog).map { dose -> intake.at to dose }
        }
        val alerts = mutableListOf<SafetyAlert>()
        candidateDoses.forEach { dose ->
            val substance = substances[dose.substanceId] ?: return@forEach
            val sameHistory = historyDoses.filter { it.second.substanceId == substance.id }
            val periodMax = substance.maxPerIntakeMg
            val periodMinutes = substance.minIntervalMinutes
            if (periodMax != null && periodMinutes != null) {
                val periodWindow = Duration.ofMinutes(periodMinutes.toLong())
                val periodStart = candidate.at.minus(periodWindow)
                val usedInPeriod = sameHistory
                    .filter { it.first.isAfter(periodStart) && !it.first.isAfter(candidate.at) }
                    .sumOf { it.second.mg }
                if (usedInPeriod + dose.mg > periodMax) {
                    alerts += SafetyAlert(
                        type = SafetyAlertType.PER_INTAKE_MAX_EXCEEDED,
                        severity = AlertSeverity.DANGER,
                        substanceId = substance.id,
                        actualMg = usedInPeriod + dose.mg,
                        limitMg = periodMax
                    )
                }
            }
            val daily = substance.maxDailyMg
            if (daily != null) {
                val usedDaily = sameHistory.sumOf { it.second.mg }
                if (usedDaily + dose.mg > daily) {
                    alerts += SafetyAlert(
                        type = SafetyAlertType.DAILY_MAX_EXCEEDED,
                        severity = AlertSeverity.DANGER,
                        substanceId = substance.id,
                        actualMg = usedDaily + dose.mg,
                        limitMg = daily
                    )
                }
            }
        }
        alerts += contraindicatedSubstances(candidate, recent, catalog)
        return SafetyReport(
            alerts = alerts,
            nextPossibleIntakeAt = candidate.at
        )
    }

    private fun contraindicatedSubstances(
        candidate: CandidateIntake,
        recent: List<RecordedIntake>,
        catalog: MedicationCatalog
    ): List<SafetyAlert> {
        val medsById = catalog.medications.associateBy { it.id }
        fun substanceIds(medicationId: String): Set<String> =
            medsById[medicationId]?.compositions?.map { it.substanceId }?.toSet() ?: emptySet()
        val candidateMed = medsById[candidate.medicationId] ?: return emptyList()
        val candidateSubstances = substanceIds(candidate.medicationId)
        val alerts = mutableListOf<SafetyAlert>()
        recent.filter { it.medicationId != candidate.medicationId }.forEach { other ->
            val otherMed = medsById[other.medicationId] ?: return@forEach
            val otherSubstances = substanceIds(other.medicationId)
            val hit = candidateMed.contraindicatedSubstanceIds.any { it in otherSubstances } ||
                otherMed.contraindicatedSubstanceIds.any { it in candidateSubstances }
            if (hit) {
                alerts += SafetyAlert(
                    type = SafetyAlertType.CONTRAINDICATED_SUBSTANCE,
                    severity = AlertSeverity.DANGER
                )
            }
        }
        return alerts.distinctBy { it.type }
    }

    private fun dosesOf(medicationId: String, units: Double, catalog: MedicationCatalog): List<Dose> {
        val medication = catalog.medications.find { it.id == medicationId } ?: return emptyList()
        return medication.compositions.map { composition ->
            Dose(composition.substanceId, composition.amountMgPerUnit * units)
        }
    }

    private data class Dose(val substanceId: String, val mg: Double)
}
