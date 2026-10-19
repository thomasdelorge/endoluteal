package fr.luteal.core.model.report

import fr.luteal.core.data.local.MedicationIntakeRow
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.recap.DailyRecap
import java.time.ZoneId

fun buildTreatmentUses(
    events: List<JournalEvent>,
    intakes: List<MedicationIntakeRow>,
    medications: List<Medication>,
    recaps: List<DailyRecap>,
    zone: ZoneId,
    fromInclusive: java.time.Instant,
    toInclusive: java.time.Instant
): List<TreatmentUse> {
    val inWindow = { at: java.time.Instant -> !at.isBefore(fromInclusive) && !at.isAfter(toInclusive) }
    val filteredEvents = events.filter { inWindow(it.startAt) }
    val names = medications.associate { it.id to it.name }
    val recapByDay = recaps.associateBy { it.date }
    val checks = filteredEvents.filter { it.type == JournalEventType.PAIN_CHECK }
    val intakeByEvent = intakes.associateBy { it.eventId }
    return filteredEvents.mapNotNull { event ->
        val day = event.startAt.atZone(zone).toLocalDate()
        val recap = recapByDay[day]
        val phase = recap?.cycle?.phaseOverride?.takeIf { it.isNotBlank() }
        val sleep = recap?.general?.sleepQuality
        val stress = recap?.general?.stress
        val after = checks.filter { it.linkedEventId == event.id }.flatMap { it.marks.map { mark -> mark.intensity } }
        when (event.type) {
            JournalEventType.MEDICATION_INTAKE -> {
                val intake = intakeByEvent[event.id] ?: return@mapNotNull null
                TreatmentUse(
                    kind = TreatmentKind.MEDICATION,
                    key = intake.medicationId,
                    name = names[intake.medicationId] ?: intake.medicationId,
                    beforeMarks = event.marks.map { it.intensity },
                    afterMarks = after,
                    phase = phase,
                    sleep = sleep,
                    stress = stress
                )
            }
            JournalEventType.CARE_SESSION -> {
                val payload = event.payload as? EventPayload.CareSession ?: return@mapNotNull null
                val name = payload.careTypeName.ifBlank { payload.programName }.ifBlank { "Soin" }
                TreatmentUse(
                    kind = TreatmentKind.CARE,
                    key = payload.careTypeId.ifBlank { name },
                    name = name,
                    beforeMarks = event.marks.map { it.intensity },
                    painBefore = payload.painBefore,
                    afterMarks = after,
                    phase = phase,
                    sleep = sleep,
                    stress = stress
                )
            }
            else -> null
        }
    }
}
