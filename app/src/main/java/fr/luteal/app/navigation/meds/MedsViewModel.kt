package fr.luteal.app.navigation.meds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.notification.TreatmentPlanScheduler
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.data.repository.TreatmentPlanRepository
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

data class MedsRecentIntake(
    val eventId: String,
    val name: String,
    val takenAt: Instant,
    val nextReminderAt: Instant?
)

data class MedsUi(
    val plans: List<MedsPlanRow> = emptyList(),
    val recentIntakes: List<MedsRecentIntake> = emptyList(),
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
        val medsById = catalog.medications.associateBy { it.id }
        val planRows = plans.filter { it.active }.map { plan ->
            MedsPlanRow(plan, names[plan.medicationId] ?: plan.medicationId, nextGroupedIntake(listOf(plan), now)?.at)
        }
        val recentCutoff = clock.instant().minus(Duration.ofHours(24))
        val recentIntakes = rows
            .filter { Instant.ofEpochMilli(it.startAt).isAfter(recentCutoff) }
            .sortedByDescending { it.startAt }
            .map { row ->
                val takenAt = Instant.ofEpochMilli(row.startAt)
                val repeatHours = medsById[row.medicationId]?.repeatEveryHours
                MedsRecentIntake(
                    eventId = row.eventId,
                    name = names[row.medicationId] ?: row.medicationId,
                    takenAt = takenAt,
                    nextReminderAt = repeatHours?.let { takenAt.plus(Duration.ofHours(it.toLong())) }
                )
            }
        MedsUi(
            plans = planRows,
            recentIntakes = recentIntakes,
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
}
