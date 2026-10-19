package fr.luteal.core.model.recap

import fr.luteal.core.designsystem.component.bodymap.PainPinGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapPainMigrationTest {

    @Test
    fun zoneRangesMigrateToPinsWithSeed() {
        val seeds = mapOf(
            "f08" to ZonePinSeed(PainPinGeometry.PIN_SIDE_FRONT, 0.4f, 0.6f)
        )
        val payload = RecapPainPayload(
            zoneRanges = listOf(
                RecapZoneRange(zone = "f08", minIntensity = 2, maxIntensity = 7)
            )
        )
        val migrated = payload.withPinsOnly(seeds)
        assertTrue(migrated.zoneRanges.isEmpty())
        assertEquals(1, migrated.marks.size)
        val mark = migrated.marks.single()
        assertEquals("f08", mark.zone)
        assertEquals(7, mark.intensity)
        assertEquals(PainPinGeometry.PIN_SIDE_FRONT, mark.pinSide)
        assertEquals(0.4f, mark.pinX)
        assertEquals(0.6f, mark.pinY)
    }

    @Test
    fun existingMarksAreNotMigratedAgain() {
        val existing = RecapPainMark(
            id = "keep",
            zone = "f01",
            intensity = 3,
            pinSide = PainPinGeometry.PIN_SIDE_FRONT,
            pinX = 0.1f,
            pinY = 0.2f
        )
        val payload = RecapPainPayload(
            marks = listOf(existing),
            zoneRanges = listOf(RecapZoneRange(zone = "f08", minIntensity = 1, maxIntensity = 5))
        )
        val result = payload.withPinsOnly(emptyMap())
        assertEquals(listOf(existing), result.marks)
        assertTrue(result.zoneRanges.isEmpty())
    }

    @Test
    fun unknownZoneKeepsMarkWithoutPin() {
        val payload = RecapPainPayload(
            zoneRanges = listOf(RecapZoneRange(zone = "deep_vagina", minIntensity = 4, maxIntensity = 6))
        )
        val migrated = payload.withPinsOnly(emptyMap())
        val mark = migrated.marks.single()
        assertEquals("deep_vagina", mark.zone)
        assertEquals(6, mark.intensity)
        assertEquals(null, mark.pinSide)
    }
}
