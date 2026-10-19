package fr.luteal.app.navigation.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.local.MedicationIntakeRow
import fr.luteal.core.data.repository.AppointmentRepository
import fr.luteal.core.data.repository.DailyRecapRepository
import fr.luteal.core.data.repository.JournalEventRepository
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.data.repository.ProfessionalRepository
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.professional.Appointment
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.report.ClinicalSynthesis
import fr.luteal.core.model.report.ClinicalSynthesisAggregator
import fr.luteal.core.model.report.SinceLastVisitAggregator
import fr.luteal.core.model.report.SinceLastVisitSummary
import fr.luteal.core.model.report.SynthesisBuildRequest
import fr.luteal.core.model.report.SynthesisPdfSection
import fr.luteal.core.model.report.TimelineFilter
import fr.luteal.core.model.report.TimelineIntakeSample
import fr.luteal.core.model.report.TimelinePainSample
import fr.luteal.core.model.report.TimelineVisit
import fr.luteal.core.model.report.TimelineVisitKind
import fr.luteal.core.model.report.TimelineVisitMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class TimelineFilters(
    val professionalIds: Set<String> = emptySet(),
    val kinds: Set<TimelineVisitKind> = TimelineVisitKind.FILTERABLE_ALL,
    val showDetails: Boolean = true,
    val periodDays: Int? = null,
    val sinceLastVisit: Boolean = false,
    val selectedEventId: String? = null
)

data class TimelineUiState(
    val visits: List<TimelineVisit> = emptyList(),
    val allVisits: List<TimelineVisit> = emptyList(),
    val professionals: List<Professional> = emptyList(),
    val summary: SinceLastVisitSummary? = null,
    val filters: TimelineFilters = TimelineFilters(),
    val selected: TimelineVisit? = null,
    val sinceLastVisitAvailable: Boolean = false
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    journalEventRepository: JournalEventRepository,
    appointmentRepository: AppointmentRepository,
    professionalRepository: ProfessionalRepository,
    medicationIntakeRepository: MedicationIntakeRepository,
    medicationRepository: MedicationRepository,
    private val dailyRecapRepository: DailyRecapRepository,
    private val clock: Clock
) : ViewModel() {
    private val filters = MutableStateFlow(TimelineFilters())
    private var cachedSource: TimelineSource? = null

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
        cachedSource = data
        buildState(data, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun toggleProfessional(id: String) {
        val current = filters.value.professionalIds
        val next = if (id in current) current - id else current + id
        filters.value = filters.value.copy(professionalIds = next, selectedEventId = null, sinceLastVisit = false)
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
        filters.value = filters.value.copy(periodDays = days, sinceLastVisit = false, selectedEventId = null)
    }

    fun setSinceLastVisit(enabled: Boolean) {
        filters.value = filters.value.copy(sinceLastVisit = enabled, periodDays = if (enabled) null else filters.value.periodDays)
    }

    fun select(eventId: String?) {
        filters.value = filters.value.copy(selectedEventId = eventId)
    }

    suspend fun buildSynthesis(sections: Set<SynthesisPdfSection>): ClinicalSynthesis = withContext(Dispatchers.Default) {
        val state = uiState.value
        val data = cachedSource ?: TimelineSource(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val now = clock.instant()
        val zone = ZoneId.systemDefault()
        val window = resolveWindow(state.filters, data, now, zone)
        val recaps = dailyRecapRepository.allOnce()
        val (from, to) = synthesisWindow(window, data, recaps, now, zone)
        val filterLines = buildFilterLines(state.filters)
        ClinicalSynthesisAggregator.build(
            SynthesisBuildRequest(
                fromInclusive = from,
                toInclusive = to,
                zone = zone,
                events = data.events,
                recaps = recaps,
                intakes = data.intakes,
                medications = data.medications,
                allVisits = state.allVisits,
                visitKinds = state.filters.kinds,
                professionalIds = state.filters.professionalIds,
                showJournalDetails = state.filters.showDetails,
                sections = sections,
                filterLines = filterLines
            )
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
        val window = resolveWindow(current, data, now, zone)
        val from = window.first
        val to = window.second
        val filtered = TimelineFilter.apply(
            visits = visits,
            kinds = current.kinds,
            professionalIds = current.professionalIds,
            fromInclusive = from,
            toInclusive = to
        )
        val names = data.medications.associate { it.id to it.name }
        val sinceLastVisitAvailable = current.professionalIds.size == 1
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
            selected = filtered.find { it.eventId == current.selectedEventId },
            sinceLastVisitAvailable = sinceLastVisitAvailable
        )
    }

    private fun synthesisWindow(
        window: Pair<Instant?, Instant?>,
        data: TimelineSource,
        recaps: List<fr.luteal.core.model.recap.DailyRecap>,
        now: Instant,
        zone: ZoneId
    ): Pair<Instant, Instant> {
        val to = when (val end = window.second) {
            null, FAR_FUTURE -> now
            else -> end.coerceAtMost(now)
        }
        val from = window.first ?: earliestDataInstant(data, recaps, zone).coerceAtMost(to)
        return from to to
    }

    private fun earliestDataInstant(
        data: TimelineSource,
        recaps: List<fr.luteal.core.model.recap.DailyRecap>,
        zone: ZoneId
    ): Instant {
        val eventMin = data.events.minOfOrNull { it.startAt }
        val recapMin = recaps.minOfOrNull { it.date }?.atStartOfDay(zone)?.toInstant()
        return listOfNotNull(eventMin, recapMin).minOrNull() ?: nowFallback(zone)
    }

    private fun nowFallback(zone: ZoneId): Instant =
        java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()

    private fun resolveWindow(
        filters: TimelineFilters,
        data: TimelineSource,
        now: Instant,
        zone: ZoneId
    ): Pair<Instant?, Instant?> {
        if (filters.sinceLastVisit && filters.professionalIds.size == 1) {
            val professionalId = filters.professionalIds.first()
            val appointmentsByEvent = data.appointments.associateBy { it.eventId }
            val last = data.events
                .filter { it.type == JournalEventType.APPOINTMENT }
                .filter { appointmentsByEvent[it.id]?.professionalId == professionalId }
                .filter { !it.startAt.isAfter(now) }
                .maxByOrNull { it.startAt }
            if (last != null) return last.startAt to now
        }
        val from = filters.periodDays?.let { now.minus(it.toLong(), ChronoUnit.DAYS) }
        return from to if (from == null) null else now
    }

    private fun buildFilterLines(filters: TimelineFilters): List<String> {
        val lines = mutableListOf<String>()
        when {
            filters.sinceLastVisit -> lines += "Période : depuis le dernier rendez-vous (professionnel filtré)"
            filters.periodDays == null -> lines += "Période : tout l’historique"
            else -> lines += "Période : ${filters.periodDays} derniers jours"
        }
        if (filters.professionalIds.isNotEmpty()) {
            lines += "Professionnels : filtre actif (${filters.professionalIds.size})"
        }
        if (filters.kinds != TimelineVisitKind.FILTERABLE_ALL) {
            lines += "Types d’événements : sélection multiple"
        }
        return lines
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
