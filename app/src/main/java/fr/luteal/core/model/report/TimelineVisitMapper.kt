package fr.luteal.core.model.report

import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.professional.Appointment
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.professional.ReportStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimelineVisitMapper {
    fun rootEvents(events: List<JournalEvent>): List<JournalEvent> =
        events.filter { it.linkedEventId.isNullOrBlank() }

    fun childrenByParent(events: List<JournalEvent>): Map<String, List<JournalEvent>> =
        events.filter { !it.linkedEventId.isNullOrBlank() }.groupBy { it.linkedEventId!! }

    fun toVisit(
        event: JournalEvent,
        appointment: Appointment?,
        professionals: Map<String, Professional>,
        intakeLabel: String?,
        childEvents: List<JournalEvent>,
        zone: ZoneId,
        timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    ): TimelineVisit? {
        if (!event.linkedEventId.isNullOrBlank()) return null
        val timeLabel = timeFormatter.format(event.startAt.atZone(zone))
        return when (event.type) {
            JournalEventType.APPOINTMENT -> appointmentVisit(event, appointment, professionals)
            JournalEventType.HOSPITALIZATION -> hospitalizationVisit(event)
            JournalEventType.MEDICATION_INTAKE -> medicationVisit(event, intakeLabel, timeLabel, childEvents)
            JournalEventType.SYMPTOM -> symptomVisit(event)
            JournalEventType.CARE_SESSION -> careSessionVisit(event, timeLabel)
            JournalEventType.PAIN_CHECK -> null
        }
    }

    private fun appointmentVisit(
        event: JournalEvent,
        appointment: Appointment?,
        professionals: Map<String, Professional>
    ): TimelineVisit {
        val professional = appointment?.professionalId?.let { professionals[it] }
        val status = appointment?.reportStatus
        val statusLabel = if (status == ReportStatus.CANCELLED) " · annulé" else ""
        return TimelineVisit(
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

    private fun hospitalizationVisit(event: JournalEvent): TimelineVisit {
        val payload = event.payload as? EventPayload.Hospitalization
        val detail = buildString {
            if (event.notes.isNotBlank()) {
                append(event.notes)
            }
            payload?.exams?.filter { it.name.isNotBlank() }?.forEach { exam ->
                if (isNotEmpty()) appendLine()
                append("Examen : ${exam.name}")
                if (exam.result.isNotBlank()) append(" — ${exam.result}")
            }
            payload?.administeredMedications?.filter {
                it.freeText.isNotBlank() || !it.medicationId.isNullOrBlank()
            }?.forEach { med ->
                if (isNotEmpty()) appendLine()
                append("Médicament : ${med.freeText.ifBlank { med.medicationId ?: "" }}")
            }
        }
        return TimelineVisit(
            eventId = event.id,
            kind = TimelineVisitKind.HOSPITALIZATION,
            professionalId = null,
            specialty = null,
            at = event.startAt,
            title = payload?.place?.ifBlank { null } ?: "Hospitalisation",
            subtitle = payload?.reason.orEmpty(),
            detail = detail.trim()
        )
    }

    private fun medicationVisit(
        event: JournalEvent,
        intakeLabel: String?,
        timeLabel: String,
        childEvents: List<JournalEvent>
    ): TimelineVisit {
        val subtitle = listOfNotNull(intakeLabel, timeLabel).joinToString(" · ")
        val before = event.marks.maxOfOrNull { it.intensity }
        val after = childEvents
            .filter { it.type == JournalEventType.PAIN_CHECK }
            .flatMap { it.marks }
            .maxOfOrNull { it.intensity }
        val detail = buildString {
            if (event.notes.isNotBlank()) append(event.notes)
            before?.let {
                if (isNotEmpty()) appendLine()
                append("Douleur avant : $it")
            }
            after?.let {
                if (isNotEmpty()) appendLine()
                append("Douleur après : $it")
            }
        }
        return TimelineVisit(
            eventId = event.id,
            kind = TimelineVisitKind.MEDICATION_INTAKE,
            professionalId = null,
            specialty = null,
            at = event.startAt,
            title = intakeLabel?.substringBefore(" · ") ?: "Prise de médicament",
            subtitle = subtitle,
            detail = detail.trim()
        )
    }

    private fun symptomVisit(event: JournalEvent): TimelineVisit {
        val marks = event.marks
        val subtitle = when {
            marks.isEmpty() -> event.notes.take(80)
            marks.size == 1 -> "Intensité max : ${marks.maxOf { it.intensity }}"
            else -> "${marks.size} zones · max ${marks.maxOf { it.intensity }}"
        }
        return TimelineVisit(
            eventId = event.id,
            kind = TimelineVisitKind.SYMPTOM,
            professionalId = null,
            specialty = null,
            at = event.startAt,
            title = "Symptôme",
            subtitle = subtitle,
            detail = formatMarksDetail(event.notes, marks)
        )
    }

    private fun careSessionVisit(event: JournalEvent, timeLabel: String): TimelineVisit {
        val payload = event.payload as? EventPayload.CareSession
        val title = payload?.careTypeName?.ifBlank { null } ?: "Soin"
        val subtitle = buildList {
            if (!payload?.programName.isNullOrBlank()) add(payload.programName)
            if (payload != null && payload.durationMinutes > 0) add("${payload.durationMinutes} min")
            add(timeLabel)
        }.joinToString(" · ")
        val detail = buildString {
            payload?.painBefore?.let { append("Douleur avant : $it") }
            if (event.notes.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(event.notes)
            }
        }
        return TimelineVisit(
            eventId = event.id,
            kind = TimelineVisitKind.CARE_SESSION,
            professionalId = null,
            specialty = null,
            at = event.startAt,
            title = title,
            subtitle = subtitle,
            detail = detail.trim()
        )
    }

    private fun formatMarksDetail(notes: String, marks: List<PainMark>): String = buildString {
        if (notes.isNotBlank()) append(notes)
        marks.forEach { mark ->
            if (isNotEmpty()) appendLine()
            val types = mark.painTypes.takeIf { it.isNotEmpty() }?.joinToString(", ")
            append("${mark.zone} : ${mark.intensity}")
            if (!types.isNullOrBlank()) append(" ($types)")
        }
    }.trim()
}
