package fr.luteal.core.model.meds

import java.time.Duration
import java.time.Instant

enum class SafetyAlertType {
    DAILY_MAX_EXCEEDED,
    PER_INTAKE_MAX_EXCEEDED,
    MIN_INTERVAL_NOT_ELAPSED,
    CLASS_DUPLICATE,
    INTERACTION_RULE,
    CONTRAINDICATED_SUBSTANCE
}

data class SafetyAlert(
    val type: SafetyAlertType,
    val severity: AlertSeverity,
    val substanceId: String? = null,
    val ruleId: String? = null,
    val unvalidated: Boolean = false,
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
    private val window: Duration = Duration.ofHours(24)

    fun check(
        candidate: CandidateIntake,
        history: List<RecordedIntake>,
        catalog: MedicationCatalog
    ): SafetyReport {
        val substances = catalog.substances.associateBy { it.id }
        val windowStart = candidate.at.minus(window)
        val recent = history.filter { intake ->
            intake.at.isAfter(windowStart) && !intake.at.isAfter(candidate.at)
        }
        val candidateDoses = dosesOf(candidate.medicationId, candidate.units, catalog)
        val historyDoses = recent.flatMap { intake ->
            dosesOf(intake.medicationId, intake.units, catalog).map { dose -> intake.at to dose }
        }
        val alerts = mutableListOf<SafetyAlert>()
        candidateDoses.forEach { dose ->
            val substance = substances[dose.substanceId] ?: return@forEach
            val sameHistory = historyDoses.filter { it.second.substanceId == substance.id }
            val perIntake = substance.maxPerIntakeMg
            if (perIntake != null && dose.mg > perIntake) {
                alerts += SafetyAlert(
                    type = SafetyAlertType.PER_INTAKE_MAX_EXCEEDED,
                    severity = AlertSeverity.DANGER,
                    substanceId = substance.id,
                    unvalidated = !substance.validated,
                    actualMg = dose.mg,
                    limitMg = perIntake
                )
            }
            val daily = substance.maxDailyMg
            if (daily != null) {
                val used = sameHistory.sumOf { it.second.mg }
                if (used + dose.mg > daily) {
                    alerts += SafetyAlert(
                        type = SafetyAlertType.DAILY_MAX_EXCEEDED,
                        severity = AlertSeverity.DANGER,
                        substanceId = substance.id,
                        unvalidated = !substance.validated,
                        actualMg = used + dose.mg,
                        limitMg = daily
                    )
                }
            }
            val interval = substance.minIntervalMinutes
            val lastAt = sameHistory.maxOfOrNull { it.first }
            if (interval != null && lastAt != null) {
                val allowedAt = lastAt.plus(Duration.ofMinutes(interval.toLong()))
                if (candidate.at.isBefore(allowedAt)) {
                    alerts += SafetyAlert(
                        type = SafetyAlertType.MIN_INTERVAL_NOT_ELAPSED,
                        severity = AlertSeverity.DANGER,
                        substanceId = substance.id,
                        unvalidated = !substance.validated,
                        limitMg = null
                    )
                }
            }
        }
        alerts += classDuplicates(candidate.at, candidateDoses, historyDoses, substances, catalog.rules)
        alerts += interactionRules(candidateDoses, historyDoses, substances, catalog.rules)
        alerts += contraindicatedSubstances(candidate, recent, catalog)
        return SafetyReport(
            alerts = alerts,
            nextPossibleIntakeAt = nextPossible(candidate, candidateDoses, historyDoses, substances)
        )
    }

    private fun classDuplicates(
        at: Instant,
        candidateDoses: List<Dose>,
        historyDoses: List<Pair<Instant, Dose>>,
        substances: Map<String, Substance>,
        rules: List<InteractionRule>
    ): List<SafetyAlert> {
        val alerts = mutableListOf<SafetyAlert>()
        candidateDoses.forEach { dose ->
            val substance = substances[dose.substanceId] ?: return@forEach
            val interval = substance.minIntervalMinutes ?: return@forEach
            val others = historyDoses.filter { (takenAt, other) ->
                val previous = substances[other.substanceId] ?: return@filter false
                previous.id != substance.id &&
                    previous.drugClass == substance.drugClass &&
                    at.isBefore(takenAt.plus(Duration.ofMinutes(interval.toLong()))) &&
                    !ruleCovers(substance, previous, rules)
            }
            if (others.isNotEmpty()) {
                alerts += SafetyAlert(
                    type = SafetyAlertType.CLASS_DUPLICATE,
                    severity = AlertSeverity.WARNING,
                    substanceId = substance.id,
                    unvalidated = !substance.validated
                )
            }
        }
        return alerts
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
        val otherIntakes = recent.filter { it.medicationId != candidate.medicationId }
        val alerts = mutableListOf<SafetyAlert>()
        otherIntakes.forEach { other ->
            val otherMed = medsById[other.medicationId] ?: return@forEach
            val otherSubstances = substanceIds(other.medicationId)
            val hit = candidateMed.contraindicatedSubstanceIds.any { it in otherSubstances } ||
                otherMed.contraindicatedSubstanceIds.any { it in candidateSubstances }
            if (hit) {
                alerts += SafetyAlert(
                    type = SafetyAlertType.CONTRAINDICATED_SUBSTANCE,
                    severity = AlertSeverity.DANGER,
                    unvalidated = false
                )
            }
        }
        return alerts.distinctBy { it.type }
    }

    private fun interactionRules(
        candidateDoses: List<Dose>,
        historyDoses: List<Pair<Instant, Dose>>,
        substances: Map<String, Substance>,
        rules: List<InteractionRule>
    ): List<SafetyAlert> {
        val historySubstances = historyDoses.mapNotNull { substances[it.second.substanceId] }.distinctBy { it.id }
        val candidateSubstances = candidateDoses.mapNotNull { substances[it.substanceId] }
        return rules.mapNotNull { rule ->
            val hit = candidateSubstances.any { left ->
                historySubstances.any { right ->
                    left.id != right.id &&
                        ((matches(rule.targetAType, rule.targetAId, left) && matches(rule.targetBType, rule.targetBId, right)) ||
                            (matches(rule.targetBType, rule.targetBId, left) && matches(rule.targetAType, rule.targetAId, right)))
                }
            }
            if (!hit) return@mapNotNull null
            val involved = candidateSubstances + historySubstances
            SafetyAlert(
                type = SafetyAlertType.INTERACTION_RULE,
                severity = rule.severity,
                ruleId = rule.id,
                unvalidated = involved.any { !it.validated }
            )
        }
    }

    private fun nextPossible(
        candidate: CandidateIntake,
        candidateDoses: List<Dose>,
        historyDoses: List<Pair<Instant, Dose>>,
        substances: Map<String, Substance>
    ): Instant {
        var latest = candidate.at
        candidateDoses.forEach { dose ->
            val substance = substances[dose.substanceId] ?: return@forEach
            val same = historyDoses.filter { it.second.substanceId == substance.id }
            val interval = substance.minIntervalMinutes
            val lastAt = same.maxOfOrNull { it.first }
            if (interval != null && lastAt != null) {
                val allowed = lastAt.plus(Duration.ofMinutes(interval.toLong()))
                if (allowed.isAfter(latest)) latest = allowed
            }
            val daily = substance.maxDailyMg
            if (daily != null) {
                val freed = firstInstantWindowAllows(same, dose.mg, daily)
                if (freed != null && freed.isAfter(latest)) latest = freed
            }
        }
        return latest
    }

    private fun firstInstantWindowAllows(
        history: List<Pair<Instant, Dose>>,
        doseMg: Double,
        maxDailyMg: Double
    ): Instant? {
        if (doseMg > maxDailyMg) return null
        if (history.isEmpty()) return null
        val used = history.sumOf { it.second.mg }
        if (used + doseMg <= maxDailyMg) return null
        return history.map { it.first.plus(window) }
            .sorted()
            .firstOrNull { candidate ->
                val start = candidate.minus(window)
                val stillIn = history.filter { it.first > start && !it.first.isAfter(candidate) }.sumOf { it.second.mg }
                stillIn + doseMg <= maxDailyMg
            }
    }

    private fun ruleCovers(left: Substance, right: Substance, rules: List<InteractionRule>): Boolean =
        rules.any { rule ->
            (matches(rule.targetAType, rule.targetAId, left) && matches(rule.targetBType, rule.targetBId, right)) ||
                (matches(rule.targetBType, rule.targetBId, left) && matches(rule.targetAType, rule.targetAId, right))
        }

    private fun matches(type: InteractionTargetType, id: String, substance: Substance): Boolean =
        when (type) {
            InteractionTargetType.SUBSTANCE -> id == substance.id
            InteractionTargetType.CLASS -> id == substance.drugClass.name
        }

    private fun dosesOf(medicationId: String, units: Double, catalog: MedicationCatalog): List<Dose> {
        val medication = catalog.medications.find { it.id == medicationId } ?: return emptyList()
        return medication.compositions.map { composition ->
            Dose(composition.substanceId, composition.amountMgPerUnit * units)
        }
    }

    private data class Dose(val substanceId: String, val mg: Double)
}
