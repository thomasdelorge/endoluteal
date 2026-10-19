package fr.luteal.core.model.report

import java.time.Instant

data class TimelinePdfContent(
    val visits: List<TimelineVisit>,
    val summary: SinceLastVisitSummary?,
    val showDetails: Boolean = true
)

fun buildTimelinePdfContent(
    visits: List<TimelineVisit>,
    kinds: Set<TimelineVisitKind>,
    professionalIds: Set<String>,
    fromInclusive: Instant?,
    toInclusive: Instant?,
    summary: SinceLastVisitSummary?,
    showDetails: Boolean = true
): TimelinePdfContent = TimelinePdfContent(
    visits = TimelineFilter.apply(
        visits = visits,
        kinds = kinds,
        professionalIds = professionalIds,
        fromInclusive = fromInclusive,
        toInclusive = toInclusive
    ),
    summary = if (professionalIds.size == 1) summary else null,
    showDetails = showDetails
)
