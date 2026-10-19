package fr.luteal.core.model.report

import fr.luteal.core.model.professional.Specialty
import java.time.Instant

data class TimelinePdfContent(
    val visits: List<TimelineVisit>,
    val summary: SinceLastVisitSummary?,
    val dailyIntakes: List<DailyMedicationIntakeCount> = emptyList()
)

fun buildTimelinePdfContent(
    visits: List<TimelineVisit>,
    professionalId: String?,
    specialties: Set<Specialty>,
    fromInclusive: Instant?,
    toInclusive: Instant?,
    summary: SinceLastVisitSummary?,
    dailyIntakes: List<DailyMedicationIntakeCount> = emptyList()
): TimelinePdfContent = TimelinePdfContent(
    visits = TimelineFilter.apply(visits, professionalId, specialties, fromInclusive, toInclusive),
    summary = if (professionalId == null) null else summary,
    dailyIntakes = dailyIntakes
)
