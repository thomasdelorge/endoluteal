package fr.luteal.app.navigation.recap

import fr.luteal.app.navigation.recap.steps.applyPhaseDropdown
import fr.luteal.app.navigation.recap.steps.applyBleedingDropdown
import fr.luteal.app.navigation.recap.steps.phaseDropdownId
import fr.luteal.app.navigation.recap.steps.RECAP_PHASE_USE_ESTIMATE
import fr.luteal.app.navigation.recap.steps.applyMucusDropdown
import fr.luteal.app.navigation.recap.steps.bleedingDropdownId
import fr.luteal.app.navigation.recap.steps.buildRecapCyclePayload
import fr.luteal.app.navigation.recap.steps.mucusDropdownId
import fr.luteal.app.navigation.recap.steps.RECAP_BLEEDING_NONE
import fr.luteal.app.navigation.recap.steps.RECAP_MUCUS_NONE
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

    @Test
    fun bleedingDropdownMapsNoneAndIntensities() {
        assertEquals(RECAP_BLEEDING_NONE, bleedingDropdownId(hasBleeding = false, intensity = ""))
        assertEquals(BleedingIntensity.LIGHT.name, bleedingDropdownId(hasBleeding = true, intensity = "LIGHT"))
        assertEquals(false, applyBleedingDropdown(RECAP_BLEEDING_NONE).first)
        assertEquals(BleedingIntensity.MEDIUM.name, applyBleedingDropdown(BleedingIntensity.MEDIUM.name).second)
    }

    @Test
    fun phaseDropdownDefaultsToEstimateWhenUnknown() {
        assertEquals(RECAP_PHASE_USE_ESTIMATE, phaseDropdownId(acceptEstimatedPhase = true, phaseOverride = ""))
        assertEquals(RECAP_PHASE_USE_ESTIMATE, phaseDropdownId(acceptEstimatedPhase = false, phaseOverride = ""))
        assertEquals("LUTEAL", phaseDropdownId(acceptEstimatedPhase = false, phaseOverride = "LUTEAL"))
        assertEquals(true, applyPhaseDropdown(RECAP_PHASE_USE_ESTIMATE).first)
        assertEquals("", applyPhaseDropdown(RECAP_PHASE_USE_ESTIMATE).second)
        assertEquals(false, applyPhaseDropdown("MENSTRUAL").first)
        assertEquals("MENSTRUAL", applyPhaseDropdown("MENSTRUAL").second)
    }

    @Test
    fun mucusDropdownMapsNoneAndTextures() {
        assertEquals(RECAP_MUCUS_NONE, mucusDropdownId(hasMucus = false, texture = ""))
        assertEquals(CervicalMucusTexture.EGG_WHITE.name, mucusDropdownId(hasMucus = true, texture = "EGG_WHITE"))
        assertEquals(false, applyMucusDropdown(RECAP_MUCUS_NONE).first)
        assertEquals(CervicalMucusTexture.STICKY.name, applyMucusDropdown(CervicalMucusTexture.STICKY.name).second)
    }
}
