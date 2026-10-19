package fr.luteal.core.model.recap

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RecapGeneralPayloadJsonTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTripIncludesDizzinessAndVertigo() {
        val payload = RecapGeneralPayload(
            fatigue = 2,
            dizziness = 3,
            vertigo = 4,
            dailyImpact = RecapDailyImpact.REDUCED_ACTIVITY.name
        )
        val decoded = json.decodeFromString<RecapGeneralPayload>(json.encodeToString(payload))
        assertEquals(payload, decoded)
    }
}
