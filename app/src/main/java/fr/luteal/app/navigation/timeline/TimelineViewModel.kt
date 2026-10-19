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
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.professional.Appointment
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.professional.ReportStatus
import fr.luteal.core.model.professional.Specialty
import fr.luteal.core.model.report.DailyMedicationIntakeCount
import fr.luteal.core.model.report.MedicationIntakeDailyAggregator
import fr.luteal.core.model.report.SinceLastVisitAggregator
import fr.luteal.core.model.report.SinceLastVisitSummary
import fr.luteal.core.model.report.TimelineFilter
import fr.luteal.core.model.report.TimelineIntakeSample
import fr.luteal.core.model.report.TimelinePainSample
import fr.luteal.core.model.report.TimelinePdfContent
import fr.luteal.core.model.report.TimelineVisit
import fr.luteal.core.model.report.TimelineVisitKind
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
    val professionalId: String? = null,
    val specialties: Set<Specialty> = emptySet(),
    val periodDays: Int? = null,
    val selectedEventId: String? = null
)

data class TimelineUiState(
    val visits: List<TimelineVisit> = emptyList(),
    val professionals: List<Professional> = emptyList(),
    val summary: SinceLastVisitSummary? = null,
    val dailyIntakes: List<DailyMedicationIntakeCount> = emptyList(),
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

    fun setProfessional(id: String?) {
        filters.value = filters.value.copy(professionalId = id, selectedEventId = null)
    }

    fun toggleSpecialty(specialty: Specialty) {
        val current = filters.value.specialties
        val next = if (specialty in current) current - specialty else current + specialty
        filters.value = filters.value.copy(specialties = next, selectedEventId = null)
    }

    fun setPeriodDays(days: Int?) {
        filters.value = filters.value.copy(periodDays = days, selectedEventId = null)
    }

    fun select(eventId: String?) {
        filters.value = filters.value.copy(selectedEventId = eventId)
    }

    fun pdfContent(): TimelinePdfContent {
        val state = uiState.value
        return TimelinePdfContent(
            visits = state.visits,
            summary = state.summary,
            dailyIntakes = state.dailyIntakes
        )
    }

    private fun buildState(data: TimelineSource, current: TimelineFilters): TimelineUiState {
        val now = clock.instant()
        val zone = ZoneId.systemDefault()
        val professionalsById = data.professionals.associateBy { it.id }
        val appointmentsByEvent = data.appointments.associateBy { it.eventId }
        val visits = data.events.mapNotNull { event ->
            toVisit(event, appointmentsByEvent[event.id], professionalsById)
        }
        val from = current.periodDays?.let { now.minus(it.toLong(), ChronoUnit.DAYS) }
        val filtered = TimelineFilter.apply(
            visits = visits,
            professionalId = current.professionalId,
            specialties = current.specialties,
            fromInclusive = from,
            toInclusive = if (from == null) null else now
        )
        val names = data.medications.associate { it.id to it.name }
        val intakeSamples = data.intakes.map { row ->
            TimelineIntakeSample(
                at = Instant.ofEpochMilli(row.startAt),
                medicationId = row.medicationId,
                medicationName = names[row.medicationId] ?: row.medicationId
            )
        }
        val dailyIntakes = MedicationIntakeDailyAggregator.aggregate(
            intakes = intakeSamples,
            zone = zone,
            fromInclusive = from,
            toInclusive = if (from == null) null else now
        )
        val summary = current.professionalId?.let { professionalId ->
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
            professionals = data.professionals.sortedBy { it.name },
            summary = summary,
            dailyIntakes = dailyIntakes,
            filters = current,
            selected = filtered.find { it.eventId == current.selectedEventId }
        )
    }

    private fun toVisit(
        event: JournalEvent,
        appointment: Appointment?,
        professionals: Map<String, Professional>
    ): TimelineVisit? {
        return when (event.type) {
            JournalEventType.APPOINTMENT -> {
                val professional = appointment?.professionalId?.let { professionals[it] }
                val status = appointment?.reportStatus
                val statusLabel = if (status == ReportStatus.CANCELLED) " · annulé" else ""
                TimelineVisit(
                    eventId = event.id,
                    kind = TimelineVisitKind.APPOINTMENT,
                    professionalId = appointment?.professionalId,
                    specialty = professional?.specialty,
                    at = event.startAt,
                    title = professional?.name ?: event.notes.ifBlank { "Rendez-vous" },
                    subtitle = (professional?.displaySpecialty() ?: "") + statusLabel,
                    detail = appointment?.report.orEmpty()
                )
            }
            JournalEventType.HOSPITALIZATION -> {
                val payload = event.payload as? EventPayload.Hospitalization
                TimelineVisit(
                    eventId = event.id,
                    kind = TimelineVisitKind.HOSPITALIZATION,
                    professionalId = null,
                    specialty = null,
                    at = event.startAt,
                    title = payload?.place?.ifBlank { null } ?: "Hospitalisation",
                    subtitle = payload?.reason.orEmpty(),
                    detail = event.notes
                )
            }
            else -> null
        }
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
