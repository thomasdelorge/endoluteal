package fr.luteal.app.navigation.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.notification.ReminderScheduler
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.local.ReminderDao
import fr.luteal.core.data.repository.JournalEventRepository
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.MedsAlarmPayload
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.ReminderStatus
import fr.luteal.core.model.ReminderType
import fr.luteal.core.model.meds.CandidateIntake
import fr.luteal.core.model.meds.MedicationCatalog
import fr.luteal.core.model.meds.MedicationSafetyChecker
import fr.luteal.core.model.meds.SafetyReport
import fr.luteal.core.model.meds.medicationNextReminderId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltViewModel
class MedicationIntakeViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val intakeRepository: MedicationIntakeRepository,
    private val journalEventRepository: JournalEventRepository,
    private val reminderScheduler: ReminderScheduler,
    private val reminderDao: ReminderDao,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val clock: Clock
) : ViewModel() {
    val catalog: StateFlow<MedicationCatalog> = medicationRepository.observeCatalog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationCatalog(emptyList(), emptyList(), emptyList()))

    private val json = Json { encodeDefaults = true }

    suspend fun evaluate(medicationId: String, units: Double, at: Instant): SafetyReport {
        val current = medicationRepository.catalogOnce()
        val history = intakeRepository.historyUntil(at, at.minus(Duration.ofHours(24)))
        return MedicationSafetyChecker.check(
            CandidateIntake(medicationId, units, at),
            history,
            current
        )
    }

    suspend fun getEvent(eventId: String): JournalEvent? = journalEventRepository.get(eventId)

    suspend fun intakeForEvent(eventId: String) = intakeRepository.intakeForEvent(eventId)

    suspend fun isNextReminderScheduled(eventId: String): Boolean {
        val reminder = reminderDao.get(medicationNextReminderId(eventId)) ?: return false
        return reminder.status == ReminderStatus.SCHEDULED.name
    }

    suspend fun deleteIntake(eventId: String) {
        intakeRepository.delete(eventId)
        reminderScheduler.cancel(medicationNextReminderId(eventId))
    }

    suspend fun saveIntake(
        medicationId: String,
        units: Double,
        at: Instant,
        marks: List<PainMark>,
        dangerAcknowledged: Boolean,
        eventId: String? = null,
        scheduleNextReminder: Boolean = true
    ): SafetyReport {
        val report = evaluate(medicationId, units, at)
        val now = clock.instant()
        val existing = eventId?.let { journalEventRepository.get(it) }
        val event = JournalEvent(
            id = eventId ?: UUID.randomUUID().toString(),
            type = JournalEventType.MEDICATION_INTAKE,
            startAt = at,
            endAt = null,
            zoneId = ZoneId.systemDefault().id,
            notes = "",
            linkedEventId = null,
            payload = EventPayload.MedicationIntake(dangerAcknowledged),
            marks = marks,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        intakeRepository.record(event, medicationId, units)
        val delay = userPreferencesDataStore.userPreferencesFlow.first().painFollowupDelayMinutes
        if (delay > 0 && existing == null) {
            reminderScheduler.schedule(
                id = UUID.randomUUID().toString(),
                type = ReminderType.PAIN_FOLLOWUP,
                triggerAt = at.plus(Duration.ofMinutes(delay.toLong())),
                eventId = event.id
            )
        }
        syncNextReminder(event.id, medicationId, units, at, scheduleNextReminder)
        return report
    }

    private suspend fun syncNextReminder(
        eventId: String,
        medicationId: String,
        units: Double,
        at: Instant,
        enabled: Boolean
    ) {
        val medication = medicationRepository.catalogOnce().medications.find { it.id == medicationId }
        val repeatHours = medication?.repeatEveryHours
        val reminderId = medicationNextReminderId(eventId)
        if (enabled && repeatHours != null && repeatHours > 0) {
            reminderScheduler.schedule(
                id = reminderId,
                type = ReminderType.MEDS,
                triggerAt = at.plus(Duration.ofHours(repeatHours.toLong())),
                payloadJson = json.encodeToString(
                    MedsAlarmPayload(medicationId, units, medication.name)
                ),
                eventId = eventId
            )
        } else {
            reminderScheduler.cancel(reminderId)
        }
    }

    suspend fun painCheckFor(linkedEventId: String?): JournalEvent? =
        linkedEventId?.let { journalEventRepository.getLinkedPainCheck(it) }

    suspend fun savePainCheck(linkedEventId: String?, marks: List<PainMark>) {
        val now = clock.instant()
        val existingId = linkedEventId?.let { journalEventRepository.getLinkedPainCheck(it)?.id }
        journalEventRepository.upsert(
            JournalEvent(
                id = existingId ?: UUID.randomUUID().toString(),
                type = JournalEventType.PAIN_CHECK,
                startAt = existingId?.let { runCatching { journalEventRepository.get(it)?.createdAt }.getOrNull() } ?: now,
                endAt = null,
                zoneId = ZoneId.systemDefault().id,
                notes = "",
                linkedEventId = linkedEventId,
                payload = EventPayload.PainCheck,
                marks = marks,
                createdAt = existingId?.let { runCatching { journalEventRepository.get(it)?.createdAt }.getOrNull() } ?: now,
                updatedAt = now
            )
        )
    }
}
