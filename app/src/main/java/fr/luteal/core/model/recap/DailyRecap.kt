package fr.luteal.core.model.recap

import java.time.Instant
import java.time.LocalDate

data class DailyRecap(
    val date: LocalDate,
    val cycle: RecapCyclePayload,
    val pain: RecapPainPayload,
    val psy: RecapPsyPayload,
    val general: RecapGeneralPayload,
    val bowel: RecapBowelPayload,
    val completedSteps: Set<RecapStep>,
    val updatedAt: Instant
) {
    fun firstIncompleteStep(): RecapStep =
        RecapStep.ordered.firstOrNull { it !in completedSteps } ?: RecapStep.BOWEL

    fun wizardStartPage(startAtFirstPage: Boolean): Int =
        if (startAtFirstPage) 0 else RecapStep.ordered.indexOf(firstIncompleteStep()).coerceAtLeast(0)

    fun isFullyComplete(): Boolean = completedSteps.containsAll(RecapStep.ordered)

    companion object {
        fun empty(date: LocalDate, now: Instant): DailyRecap = DailyRecap(
            date = date,
            cycle = RecapCyclePayload(),
            pain = RecapPainPayload(),
            psy = RecapPsyPayload(),
            general = RecapGeneralPayload(),
            bowel = RecapBowelPayload(),
            completedSteps = emptySet(),
            updatedAt = now
        )
    }
}
