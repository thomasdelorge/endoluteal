package fr.luteal.app.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.luteal.app.LutealUiState
import fr.luteal.core.designsystem.theme.LutealTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgendaSpeedDialTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fabOpensAndClosesTheDailyRecapAction() {
        var recapDate: LocalDate? = null
        val today = LocalDate.parse("2026-08-13")
        composeRule.setContent {
            LutealTheme {
                AgendaScreen(
                    state = LutealUiState(today = today),
                    onOpenRecap = { date, _ -> recapDate = date }
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("agenda_action_daily_recap").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("agenda_fab").performClick()
        composeRule.onNodeWithText("Récap du jour").assertIsDisplayed()
        composeRule.onNodeWithText("Médicament").assertIsDisplayed()
        composeRule.onNodeWithText("Symptôme").assertIsDisplayed()
        composeRule.onNodeWithText("TENS").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda_action_daily_recap").performClick()
        composeRule.runOnIdle { assertEquals(today, recapDate) }
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("agenda_action_daily_recap").fetchSemanticsNodes().size
        )
    }

    @Test
    fun scrimTapClosesFabMenu() {
        composeRule.setContent {
            LutealTheme {
                AgendaScreen(state = LutealUiState(today = LocalDate.parse("2026-08-13")))
            }
        }
        composeRule.onNodeWithTag("agenda_fab").performClick()
        composeRule.onNodeWithText("Récap du jour").assertIsDisplayed()
        composeRule.onNodeWithTag("agenda_fab_scrim").performClick()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("agenda_action_daily_recap").fetchSemanticsNodes().size
        )
    }
}
