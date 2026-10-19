package fr.luteal.core.model.report

import fr.luteal.core.model.professional.Specialty
import java.time.Instant

data class TimelinePdfContent(
    val visits: List<TimelineVisit>,
    val summary: SinceLastVisitSummary?,
    val showDetails: Boolean = true
)

fun buildTimelinePdfContent(
    visits: List<TimelineVisit>,
    kinds: Set<TimelineVisitKind>,
    professionalId: String?,
    specialties: Set<Specialty>,
    fromInclusive: Instant?,
    toInclusive: Instant?,
    summary: SinceLastVisitSummary?,
    showDetails: Boolean = true
): TimelinePdfContent = TimelinePdfContent(
    visits = TimelineFilter.apply(
        visits = visits,
        kinds = kinds,
        professionalId = professionalId,
        specialties = specialties,
        fromInclusive = fromInclusive,
        toInclusive = toInclusive
    ),
    summary = if (professionalId == null) null else summary,
    showDetails = showDetails
)
