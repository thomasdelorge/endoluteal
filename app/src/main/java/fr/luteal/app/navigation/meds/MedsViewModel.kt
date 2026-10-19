package fr.luteal.app.navigation.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.notification.TreatmentPlanScheduler
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.data.repository.TreatmentPlanRepository
import fr.luteal.core.model.meds.CandidateIntake
import fr.luteal.core.model.meds.MedicationSafetyChecker
import fr.luteal.core.model.meds.SafetyAlert
import fr.luteal.core.model.meds.TreatmentPlan
import fr.luteal.core.model.meds.nextGroupedIntake
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MedsPlanRow(val plan: TreatmentPlan, val name: String, val nextAt: LocalDateTime?)
data class MedsIntakeLine(val eventId: String, val units: Double, val at: Instant)

data class MedsHistoryRow(
    val medicationId: String,
    val name: String,
    val lastAt: Instant?,
    val nextAt: Instant?,
    val alert: SafetyAlert? = null,
    val ruleMessage: String? = null,
    val intakes: List<MedsIntakeLine> = emptyList()
)

data class MedsUi(
    val plans: List<MedsPlanRow> = emptyList(),
    val history: List<MedsHistoryRow> = emptyList(),
    val medications: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class MedsViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val intakeRepository: MedicationIntakeRepository,
    private val treatmentPlanRepository: TreatmentPlanRepository,
    private val treatmentPlanScheduler: TreatmentPlanScheduler,
    private val clock: Clock
) : ViewModel() {
    val state: StateFlow<MedsUi> = combine(
        medicationRepository.observeCatalog(),
        intakeRepository.observeRows(),
        treatmentPlanRepository.observe()
    ) { catalog, rows, plans ->
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(clock.instant(), zone)
        val names = catalog.medications.associate { it.id to it.name }
        val planRows = plans.filter { it.active }.map { plan ->
            MedsPlanRow(plan, names[plan.medicationId] ?: plan.medicationId, nextGroupedIntake(listOf(plan), now)?.at)
        }
        val at = clock.instant()
        val recentCutoff = at.minus(Duration.ofHours(24))
        val recentRows = rows.filter { Instant.ofEpochMilli(it.startAt).isAfter(recentCutoff) }
        val history = recentRows.groupBy { it.medicationId }.map { (medicationId, intakes) ->
            val last = intakes.maxBy { it.startAt }
            val report = MedicationSafetyChecker.check(
                CandidateIntake(medicationId, last.units, at),
                intakes.filter { Instant.ofEpochMilli(it.startAt).isAfter(recentCutoff) }
                    .map { fr.luteal.core.model.meds.RecordedIntake(it.medicationId, it.units, Instant.ofEpochMilli(it.startAt)) },
                catalog
            )
            val alert = report.alerts.maxByOrNull { it.severity }
            val ruleMessage = alert?.ruleId?.let { ruleId -> catalog.rules.find { it.id == ruleId }?.message }
            MedsHistoryRow(
                medicationId,
                names[medicationId] ?: medicationId,
                Instant.ofEpochMilli(last.startAt),
                report.nextPossibleIntakeAt,
                alert,
                ruleMessage,
                intakes.sortedByDescending { it.startAt }.map {
                    MedsIntakeLine(it.eventId, it.units, Instant.ofEpochMilli(it.startAt))
                }
            )
        }.sortedByDescending { it.lastAt }
        MedsUi(
            plans = planRows,
            history = history,
            medications = catalog.medications.filter { !it.archived }.map { it.id to it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedsUi())

    fun savePlan(medicationId: String, units: Double, times: List<LocalTime>, start: LocalDate, end: LocalDate?, hormonal: Boolean) {
        viewModelScope.launch {
            treatmentPlanRepository.upsert(
                TreatmentPlan(
                    id = UUID.randomUUID().toString(),
                    medicationId = medicationId,
                    unitsPerDose = units,
                    timesOfDay = times,
                    startDate = start,
                    endDate = end,
                    active = true,
                    continuousHormonal = hormonal
                )
            )
            treatmentPlanScheduler.sync()
        }
    }

    fun deleteIntake(eventId: String) {
        viewModelScope.launch { intakeRepository.delete(eventId) }
    }
}
