package fr.luteal.core.model.recap

import fr.luteal.core.model.PainMark
import java.util.UUID

data class ZonePinSeed(
    val pinSide: String,
    val pinX: Float,
    val pinY: Float
)

fun RecapPainMark.toPainMark(): PainMark = PainMark(
    id = id,
    zone = zone,
    intensity = intensity,
    painTypes = painTypes,
    pinSide = pinSide,
    pinX = pinX,
    pinY = pinY
)

fun PainMark.toRecapPainMark(): RecapPainMark = RecapPainMark(
    id = id,
    zone = zone,
    intensity = intensity,
    painTypes = painTypes,
    pinSide = pinSide,
    pinX = pinX,
    pinY = pinY
)

fun RecapPainPayload.withPinsOnly(zoneSeeds: Map<String, ZonePinSeed>): RecapPainPayload {
    if (marks.isNotEmpty()) return copy(zoneRanges = emptyList())
    if (zoneRanges.isEmpty()) return this
    val migrated = zoneRanges.map { range ->
        val intensity = range.maxIntensity.coerceAtLeast(range.minIntensity)
        val seed = zoneSeeds[range.zone]
        if (seed != null) {
            RecapPainMark(
                id = UUID.randomUUID().toString(),
                zone = range.zone,
                intensity = intensity,
                painTypes = emptyList(),
                pinSide = seed.pinSide,
                pinX = seed.pinX,
                pinY = seed.pinY
            )
        } else {
            RecapPainMark(
                id = UUID.randomUUID().toString(),
                zone = range.zone,
                intensity = intensity,
                painTypes = emptyList()
            )
        }
    }
    return copy(marks = migrated, zoneRanges = emptyList())
}
