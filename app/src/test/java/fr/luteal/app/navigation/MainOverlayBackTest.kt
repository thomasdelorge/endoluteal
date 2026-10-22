package fr.luteal.app.navigation

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainOverlayBackTest {

    @Test
    fun resolvesTopmostOverlayFirst() {
        val state = MainOverlayBackState(
            intakeDate = LocalDate.parse("2026-01-01"),
            appointmentDate = LocalDate.parse("2026-01-02")
        )
        assertEquals(MainOverlayBackTarget.APPOINTMENT, resolveMainOverlayBackTarget(state))
    }

    @Test
    fun appointmentDetailBeforeEditor() {
        val state = MainOverlayBackState(
            appointmentDetailEventId = "evt-1",
            appointmentDate = LocalDate.parse("2026-01-02")
        )
        assertEquals(MainOverlayBackTarget.APPOINTMENT_DETAIL, resolveMainOverlayBackTarget(state))
    }

    @Test
    fun returnsNullWhenNoOverlay() {
        assertNull(resolveMainOverlayBackTarget(MainOverlayBackState()))
    }

    @Test
    fun backfillIsLowestPriority() {
        val state = MainOverlayBackState(showBackfillDialog = true)
        assertEquals(MainOverlayBackTarget.BACKFILL_CYCLE, resolveMainOverlayBackTarget(state))
    }

    @Test
    fun monthlyQuestionnaireBeforeHospital() {
        val state = MainOverlayBackState(
            hospitalDate = LocalDate.parse("2026-01-01"),
            monthlyPeriod = YearMonth.parse("2026-02")
        )
        assertEquals(MainOverlayBackTarget.MONTHLY_QUESTIONNAIRE, resolveMainOverlayBackTarget(state))
    }
}
