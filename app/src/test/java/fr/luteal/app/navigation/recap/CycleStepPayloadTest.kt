package fr.luteal.app.navigation.recap

import fr.luteal.app.navigation.recap.steps.buildRecapCyclePayload
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CervicalMucusTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CycleStepPayloadTest {

    @Test
    fun buildPayloadClearsPhaseOverrideWhenEstimateAccepted() {
        val payload = buildRecapCyclePayload(
            phaseOverride = "LUTEAL",
            acceptEstimate = true,
            hasBleeding = false,
            bleeding = BleedingIntensity.LIGHT.name,
            hasMucus = false,
            mucus = CervicalMucusTexture.STICKY.name
        )
        assertEquals("", payload.phaseOverride)
        assertFalse(payload.hasBleeding)
    }

    @Test
    fun buildPayloadKeepsBleedingAndMucusWhenEnabled() {
        val payload = buildRecapCyclePayload(
            phaseOverride = "MENSTRUAL",
            acceptEstimate = false,
            hasBleeding = true,
            bleeding = BleedingIntensity.HEAVY.name,
            hasMucus = true,
            mucus = CervicalMucusTexture.CREAMY.name
        )
        assertEquals("MENSTRUAL", payload.phaseOverride)
        assertEquals(BleedingIntensity.HEAVY.name, payload.bleedingIntensity)
        assertEquals(CervicalMucusTexture.CREAMY.name, payload.mucusTexture)
    }
}
