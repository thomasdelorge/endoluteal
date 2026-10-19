package fr.luteal.core.designsystem.component.bodymap

import fr.luteal.core.model.BodySide
import fr.luteal.core.model.BodyZone
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapStep
import fr.luteal.core.model.recap.toPainMark
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PainHeatmap {
    fun zoneIntensityMax(marks: List<PainMark>): Map<String, Int> =
        marks.groupBy { it.zone }.mapValues { (_, zoneMarks) -> zoneMarks.maxOf { it.intensity } }

    fun mergeMax(vararg maps: Map<String, Int>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        maps.forEach { map ->
            map.forEach { (zone, intensity) ->
                out[zone] = maxOf(out.getOrDefault(zone, 0), intensity)
            }
        }
        return out
    }

    fun silhouetteHeat(zoneHeat: Map<String, Int>): Map<String, Int> =
        zoneHeat.filterKeys { zone ->
            BodyZone.fromId(zone)?.side != BodySide.INTERNAL
        }

    fun internalZoneHeat(zoneHeat: Map<String, Int>): Map<String, Int> =
        zoneHeat.filterKeys { zone ->
            BodyZone.fromId(zone)?.side == BodySide.INTERNAL
        }

    fun marksFromEvents(
        events: List<fr.luteal.core.model.JournalEvent>,
        fromInclusive: Instant,
        toInclusive: Instant
    ): List<PainMark> = events
        .filter { !it.startAt.isBefore(fromInclusive) && !it.startAt.isAfter(toInclusive) }
        .flatMap { it.marks }

    fun marksFromRecaps(
        recaps: List<DailyRecap>,
        fromDate: LocalDate,
        toDate: LocalDate
    ): List<PainMark> = recaps
        .filter { it.date in fromDate..toDate && RecapStep.PAIN in it.completedSteps }
        .flatMap { recap -> recap.pain.marks.map { it.toPainMark() } }
}
