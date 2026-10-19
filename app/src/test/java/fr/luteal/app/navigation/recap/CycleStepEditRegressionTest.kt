package fr.luteal.app.navigation.recap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import fr.luteal.app.navigation.recap.steps.CycleStep
import fr.luteal.core.model.recap.RecapCyclePayload
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CycleStepEditRegressionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveUsesLatestInitialAfterRecapReload() {
        var initial by mutableStateOf(
            RecapCyclePayload(hasBleeding = true, bleedingIntensity = "HEAVY")
        )
        var saved: RecapCyclePayload? = null
        var triggerSave: () -> Unit = {}
        composeRule.setContent {
            CycleStep(
                ui = null,
                initial = initial,
                active = true,
                onBindSave = { triggerSave = it },
                onContinue = { saved = it }
            )
        }
        composeRule.runOnIdle { triggerSave() }
        assertEquals(true, saved?.hasBleeding)
        assertEquals("HEAVY", saved?.bleedingIntensity)

        initial = RecapCyclePayload(hasBleeding = false)
        composeRule.runOnIdle { triggerSave() }
        assertEquals(false, saved?.hasBleeding)
        assertEquals("", saved?.bleedingIntensity)
    }
}
