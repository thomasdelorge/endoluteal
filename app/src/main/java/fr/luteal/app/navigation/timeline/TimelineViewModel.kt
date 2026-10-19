package fr.luteal.app.navigation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.local.MedicationIntakeRow
import fr.luteal.core.data.repository.AppointmentRepository
import fr.luteal.core.data.repository.JournalEventRepository
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.data.repository.ProfessionalRepository
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.professional.Appointment
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.report.SinceLastVisitAggregator
import fr.luteal.core.model.report.SinceLastVisitSummary
import fr.luteal.core.model.report.TimelineFilter
import fr.luteal.core.model.report.TimelineIntakeSample
import fr.luteal.core.model.report.TimelinePainSample
import fr.luteal.core.model.report.TimelinePdfContent
import fr.luteal.core.model.report.TimelineVisit
import fr.luteal.core.model.report.TimelineVisitKind
import fr.luteal.core.model.report.TimelineVisitMapper
import fr.luteal.core.model.report.buildTimelinePdfContent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TimelineFilters(
    val professionalIds: Set<String> = emptySet(),
    val kinds: Set<TimelineVisitKind> = TimelineVisitKind.FILTERABLE_ALL,
    val showDetails: Boolean = true,
    val periodDays: Int? = null,
    val selectedEventId: String? = null
)

data class TimelineUiState(
    val visits: List<TimelineVisit> = emptyList(),
    val allVisits: List<TimelineVisit> = emptyList(),
    val professionals: List<Professional> = emptyList(),
    val summary: SinceLastVisitSummary? = null,
    val filters: TimelineFilters = TimelineFilters(),
    val selected: TimelineVisit? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    journalEventRepository: JournalEventRepository,
    appointmentRepository: AppointmentRepository,
    professionalRepository: ProfessionalRepository,
    medicationIntakeRepository: MedicationIntakeRepository,
    medicationRepository: MedicationRepository,
    private val clock: Clock
) : ViewModel() {
    private val filters = MutableStateFlow(TimelineFilters())

    private val source = combine(
        journalEventRepository.observeBetween(Instant.EPOCH, FAR_FUTURE),
        appointmentRepository.observeAll(),
        professionalRepository.observeAll(),
        medicationIntakeRepository.observeRows(),
        medicationRepository.observeCatalog()
    ) { events, appointments, professionals, intakes, catalog ->
        TimelineSource(events, appointments, professionals, intakes, catalog.medications)
    }

    val uiState: StateFlow<TimelineUiState> = combine(source, filters) { data, current ->
        buildState(data, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun toggleProfessional(id: String) {
        val current = filters.value.professionalIds
        val next = if (id in current) current - id else current + id
        filters.value = filters.value.copy(professionalIds = next, selectedEventId = null)
    }

    fun toggleKind(kind: TimelineVisitKind) {
        val current = filters.value.kinds
        val next = if (kind in current) current - kind else current + kind
        filters.value = filters.value.copy(kinds = next, selectedEventId = null)
    }

    fun setShowDetails(enabled: Boolean) {
        filters.value = filters.value.copy(showDetails = enabled)
    }

    fun setPeriodDays(days: Int?) {
        filters.value = filters.value.copy(periodDays = days, selectedEventId = null)
    }

    fun select(eventId: String?) {
        filters.value = filters.value.copy(selectedEventId = eventId)
    }

    fun pdfContent(): TimelinePdfContent {
        val state = uiState.value
        val now = clock.instant()
        val from = state.filters.periodDays?.let { now.minus(it.toLong(), ChronoUnit.DAYS) }
        return buildTimelinePdfContent(
            visits = state.allVisits,
            kinds = state.filters.kinds,
            professionalIds = state.filters.professionalIds,
            fromInclusive = from,
            toInclusive = if (from == null) null else now,
            summary = state.summary,
            showDetails = state.filters.showDetails
        )
    }

    private fun buildState(data: TimelineSource, current: TimelineFilters): TimelineUiState {
        val now = clock.instant()
        val zone = ZoneId.systemDefault()
        val professionalsById = data.professionals.associateBy { it.id }
        val appointmentsByEvent = data.appointments.associateBy { it.eventId }
        val intakeLabels = intakeLabels(data.intakes, data.medications)
        val children = TimelineVisitMapper.childrenByParent(data.events)
        val visits = TimelineVisitMapper.rootEvents(data.events).mapNotNull { event ->
            TimelineVisitMapper.toVisit(
                event = event,
                appointment = appointmentsByEvent[event.id],
                professionals = professionalsById,
                intakeLabel = intakeLabels[event.id],
                childEvents = children[event.id].orEmpty(),
                zone = zone
            )
        }
        val from = current.periodDays?.let { now.minus(it.toLong(), ChronoUnit.DAYS) }
        val filtered = TimelineFilter.apply(
            visits = visits,
            kinds = current.kinds,
            professionalIds = current.professionalIds,
            fromInclusive = from,
            toInclusive = if (from == null) null else now
        )
        val names = data.medications.associate { it.id to it.name }
        val summary = current.professionalIds.singleOrNull()?.let { professionalId ->
            val last = data.events
                .filter { it.type == JournalEventType.APPOINTMENT }
                .filter { appointmentsByEvent[it.id]?.professionalId == professionalId }
                .filter { !it.startAt.isAfter(now) }
                .maxByOrNull { it.startAt }
                ?: return@let null
            SinceLastVisitAggregator.summarize(
                fromInclusive = last.startAt,
                toInclusive = now,
                zone = zone,
                pain = data.events.flatMap { event ->
                    event.marks.map { mark ->
                        TimelinePainSample(event.startAt, mark.zone, mark.intensity)
                    }
                },
                intakes = data.intakes.map { row ->
                    TimelineIntakeSample(
                        at = Instant.ofEpochMilli(row.startAt),
                        medicationId = row.medicationId,
                        medicationName = names[row.medicationId] ?: row.medicationId
                    )
                },
                hospitalizations = data.events
                    .filter { it.type == JournalEventType.HOSPITALIZATION }
                    .map { it.startAt },
                careSessions = data.events
                    .filter { it.type == JournalEventType.CARE_SESSION }
                    .map { it.startAt }
            )
        }
        return TimelineUiState(
            visits = filtered,
            allVisits = visits,
            professionals = data.professionals.sortedBy { it.name },
            summary = summary,
            filters = current,
            selected = filtered.find { it.eventId == current.selectedEventId }
        )
    }

    private fun intakeLabels(
        intakes: List<MedicationIntakeRow>,
        medications: List<Medication>
    ): Map<String, String> = intakes.associate { row ->
        val name = medications.find { it.id == row.medicationId }?.name ?: row.medicationId
        val units = if (row.units % 1.0 == 0.0) row.units.toInt().toString() else row.units.toString()
        row.eventId to "$name · $units"
    }

    private data class TimelineSource(
        val events: List<JournalEvent>,
        val appointments: List<Appointment>,
        val professionals: List<Professional>,
        val intakes: List<MedicationIntakeRow>,
        val medications: List<Medication>
    )

    private companion object {
        val FAR_FUTURE: Instant = Instant.parse("2100-01-01T00:00:00Z")
    }
}
