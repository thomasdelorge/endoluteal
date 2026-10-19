package fr.luteal.core.model.recap

import fr.luteal.core.designsystem.component.bodymap.PainPinGeometry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapPainPayloadJsonTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTripMarksWithPinFields() {
        val payload = RecapPainPayload(
            marks = listOf(
                RecapPainMark(
                    id = "m1",
                    zone = "f08",
                    intensity = 5,
                    painTypes = listOf("cramp"),
                    pinSide = PainPinGeometry.PIN_SIDE_FRONT,
                    pinX = 0.33f,
                    pinY = 0.66f
                )
            ),
            dysmenorrhea = 2
        )
        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<RecapPainPayload>(encoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun legacyZoneRangesStillDecode() {
        val legacy = """{"zoneRanges":[{"zone":"f08","minIntensity":1,"maxIntensity":4}]}"""
        val decoded = json.decodeFromString<RecapPainPayload>(legacy)
        assertEquals(1, decoded.zoneRanges.size)
        assertTrue(decoded.marks.isEmpty())
    }
}
