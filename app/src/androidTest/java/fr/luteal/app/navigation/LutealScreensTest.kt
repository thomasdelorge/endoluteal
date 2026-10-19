package fr.luteal.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import fr.luteal.app.LutealUiState
import fr.luteal.app.R
import fr.luteal.core.designsystem.theme.LutealTheme
import fr.luteal.core.model.DailyEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LutealScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test
    fun calendarViewShowsMonthGridAndInspectsSelectedDay() {
        val today = LocalDate.parse("2026-08-13")
        var openedDate: LocalDate? = null

        composeRule.setContent {
            LutealTheme(darkTheme = false) {
                AgendaScreen(
                    state = LutealUiState(
                        today = today,
                        entries = listOf(
                            DailyEntry(date = today, painLevel = 2)
                        )
                    ),
                    onOpenRecap = { date, _ -> openedDate = date }
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.agenda_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.daily_recap_complete)).performClick()
        assertEquals(today, openedDate)
    }
}
