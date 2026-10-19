package fr.luteal.core.model.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyMedicationIntakeCount(
    val date: LocalDate,
    val medicationId: String,
    val name: String,
    val count: Int
)

object MedicationIntakeDailyAggregator {
    fun aggregate(
        intakes: List<TimelineIntakeSample>,
        zone: ZoneId,
        fromInclusive: Instant?,
        toInclusive: Instant?
    ): List<DailyMedicationIntakeCount> {
        fun inWindow(at: Instant): Boolean {
            if (fromInclusive != null && at.isBefore(fromInclusive)) return false
            if (toInclusive != null && at.isAfter(toInclusive)) return false
            return true
        }
        return intakes
            .filter { inWindow(it.at) }
            .groupBy { sample ->
                sample.at.atZone(zone).toLocalDate() to sample.medicationId
            }
            .map { (key, rows) ->
                DailyMedicationIntakeCount(
                    date = key.first,
                    medicationId = key.second,
                    name = rows.first().medicationName,
                    count = rows.size
                )
            }
            .sortedWith(compareBy({ it.date }, { it.name }))
    }
}
