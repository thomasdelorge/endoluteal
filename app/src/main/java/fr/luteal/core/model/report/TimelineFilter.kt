package fr.luteal.core.model.report

import fr.luteal.core.model.JournalEventType
import fr.luteal.core.model.professional.Specialty
import java.time.Instant

data class TimelineVisit(
    val eventId: String,
    val kind: TimelineVisitKind,
    val professionalId: String?,
    val specialty: Specialty?,
    val at: Instant,
    val title: String,
    val subtitle: String,
    val detail: String
)

enum class TimelineVisitKind {
    APPOINTMENT,
    HOSPITALIZATION,
    MEDICATION_INTAKE,
    SYMPTOM,
    CARE_SESSION;

    companion object {
        val FILTERABLE_ALL: Set<TimelineVisitKind> = entries.toSet()

        fun fromJournalType(type: JournalEventType): TimelineVisitKind? = when (type) {
            JournalEventType.APPOINTMENT -> APPOINTMENT
            JournalEventType.HOSPITALIZATION -> HOSPITALIZATION
            JournalEventType.MEDICATION_INTAKE -> MEDICATION_INTAKE
            JournalEventType.SYMPTOM -> SYMPTOM
            JournalEventType.CARE_SESSION -> CARE_SESSION
            JournalEventType.PAIN_CHECK -> null
        }
    }
}

object TimelineFilter {
    fun apply(
        visits: List<TimelineVisit>,
        kinds: Set<TimelineVisitKind>,
        professionalIds: Set<String>,
        fromInclusive: Instant?,
        toInclusive: Instant?
    ): List<TimelineVisit> {
        return visits.filter { visit ->
            val kindOk = visit.kind in kinds
            val professionalOk = professionalIds.isEmpty() ||
                visit.kind != TimelineVisitKind.APPOINTMENT ||
                visit.professionalId in professionalIds
            val after = fromInclusive == null || !visit.at.isBefore(fromInclusive)
            val before = toInclusive == null || !visit.at.isAfter(toInclusive)
            kindOk && professionalOk && after && before
        }.sortedByDescending { it.at }
    }
}
