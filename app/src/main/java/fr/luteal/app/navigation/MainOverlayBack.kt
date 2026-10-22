package fr.luteal.app.navigation

import java.time.LocalDate
import java.time.YearMonth

enum class MainOverlayBackTarget {
    APPOINTMENT,
    APPOINTMENT_REPORT,
    RECAP,
    MONTHLY_QUESTIONNAIRE,
    HOSPITALIZATION,
    CARE_SESSION,
    SYMPTOM,
    MEDICATION_INTAKE,
    BACKFILL_CYCLE
}

data class MainOverlayBackState(
    val appointmentDate: LocalDate? = null,
    val appointmentReportEventId: String? = null,
    val recapDate: LocalDate? = null,
    val monthlyPeriod: YearMonth? = null,
    val hospitalDate: LocalDate? = null,
    val careDate: LocalDate? = null,
    val symptomDate: LocalDate? = null,
    val intakeDate: LocalDate? = null,
    val showBackfillDialog: Boolean = false
)

fun resolveMainOverlayBackTarget(state: MainOverlayBackState): MainOverlayBackTarget? = when {
    state.appointmentDate != null -> MainOverlayBackTarget.APPOINTMENT
    state.appointmentReportEventId != null -> MainOverlayBackTarget.APPOINTMENT_REPORT
    state.recapDate != null -> MainOverlayBackTarget.RECAP
    state.monthlyPeriod != null -> MainOverlayBackTarget.MONTHLY_QUESTIONNAIRE
    state.hospitalDate != null -> MainOverlayBackTarget.HOSPITALIZATION
    state.careDate != null -> MainOverlayBackTarget.CARE_SESSION
    state.symptomDate != null -> MainOverlayBackTarget.SYMPTOM
    state.intakeDate != null -> MainOverlayBackTarget.MEDICATION_INTAKE
    state.showBackfillDialog -> MainOverlayBackTarget.BACKFILL_CYCLE
    else -> null
}
