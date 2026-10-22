package fr.luteal.app.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.LutealViewModel
import fr.luteal.app.R
import fr.luteal.app.navigation.entry.MedicationIntakeSheet
import fr.luteal.app.navigation.entry.MedicationIntakeViewModel
import fr.luteal.app.navigation.entry.CareSessionEntryViewModel
import fr.luteal.app.navigation.entry.CareSessionSheet
import fr.luteal.app.navigation.entry.AppointmentEntryViewModel
import fr.luteal.app.navigation.entry.AppointmentReportSheet
import fr.luteal.app.navigation.entry.AppointmentSheet
import fr.luteal.app.navigation.entry.HospitalizationEntryViewModel
import fr.luteal.app.navigation.entry.HospitalizationSheet
import fr.luteal.app.navigation.entry.SymptomEntryViewModel
import fr.luteal.app.navigation.entry.SymptomSheet
import fr.luteal.app.navigation.recap.MonthlyQuestionnaireSheet
import fr.luteal.app.navigation.recap.MonthlyQuestionnaireViewModel
import fr.luteal.app.navigation.recap.RecapWizardSheet
import fr.luteal.app.navigation.recap.RecapWizardViewModel
import fr.luteal.app.navigation.timeline.TimelineScreen
import fr.luteal.core.model.JournalEventType
import fr.luteal.app.navigation.meds.MedsScreen
import fr.luteal.core.designsystem.theme.LutealSpacing
import java.time.LocalDate

@Composable
fun LutealMainScaffold(
    pendingImportJson: String? = null,
    pendingPainEventId: String? = null,
    pendingMedicationId: String? = null,
    pendingRecapDate: String? = null,
    pendingMonthlyPeriod: String? = null,
    pendingAppointmentReportEventId: String? = null,
    onPendingImportConsumed: () -> Unit = {},
    onPendingReminderConsumed: () -> Unit = {},
    viewModel: LutealViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val agendaViewModel: AgendaViewModel = hiltViewModel()
    val intakeViewModel: MedicationIntakeViewModel = hiltViewModel()
    val symptomViewModel: SymptomEntryViewModel = hiltViewModel()
    val careViewModel: CareSessionEntryViewModel = hiltViewModel()
    val hospitalViewModel: HospitalizationEntryViewModel = hiltViewModel()
    val appointmentViewModel: AppointmentEntryViewModel = hiltViewModel()
    val recapViewModel: RecapWizardViewModel = hiltViewModel()
    val monthlyQuestionnaireViewModel: MonthlyQuestionnaireViewModel = hiltViewModel()
    val events by agendaViewModel.events.collectAsStateWithLifecycle()
    val intakeLabels by agendaViewModel.intakeLabels.collectAsStateWithLifecycle()
    val careLabels by agendaViewModel.careLabels.collectAsStateWithLifecycle()
    val appointmentLabels by agendaViewModel.appointmentLabels.collectAsStateWithLifecycle()
    val dailyRecaps by agendaViewModel.recapsByDate.collectAsStateWithLifecycle()
    val appointments by agendaViewModel.appointments.collectAsStateWithLifecycle()
    val professionals by agendaViewModel.professionals.collectAsStateWithLifecycle()
    val eventDetailLabels = intakeLabels + careLabels + appointmentLabels
    var selectedDestination by rememberSaveable { mutableStateOf(LutealDestination.AGENDA) }
    var intakeDate by remember { mutableStateOf<LocalDate?>(null) }
    var intakeMedicationId by remember { mutableStateOf<String?>(null) }
    var intakeEventId by remember { mutableStateOf<String?>(null) }
    var symptomDate by remember { mutableStateOf<LocalDate?>(null) }
    var symptomEventId by remember { mutableStateOf<String?>(null) }
    var careDate by remember { mutableStateOf<LocalDate?>(null) }
    var careEventId by remember { mutableStateOf<String?>(null) }
    var careTensOnly by remember { mutableStateOf(false) }
    var hospitalDate by remember { mutableStateOf<LocalDate?>(null) }
    var hospitalEventId by remember { mutableStateOf<String?>(null) }
    var appointmentDate by remember { mutableStateOf<LocalDate?>(null) }
    var appointmentEventId by remember { mutableStateOf<String?>(null) }
    var recapDate by remember { mutableStateOf<LocalDate?>(null) }
    var recapStartAtFirstPage by remember { mutableStateOf(false) }
    var monthlyPeriod by remember { mutableStateOf<java.time.YearMonth?>(null) }
    var appointmentReportEventId by remember { mutableStateOf<String?>(null) }
    var showBackfillDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val saveError = stringResource(R.string.save_error)

    LaunchedEffect(pendingImportJson, uiState.preferences.hasCompletedOnboarding) {
        val json = pendingImportJson ?: return@LaunchedEffect
        if (!uiState.preferences.hasCompletedOnboarding) return@LaunchedEffect

        selectedDestination = LutealDestination.SETTINGS
        settingsViewModel.inspectBackupJson(json)
        onPendingImportConsumed()
    }

    LaunchedEffect(
        pendingPainEventId,
        pendingMedicationId,
        pendingRecapDate,
        pendingMonthlyPeriod,
        pendingAppointmentReportEventId
    ) {
        if (pendingPainEventId != null) {
            // The pain follow-up opens the intake's own sheet so the after-pain
            // is recorded where the dose was taken, not in a detached check.
            selectedDestination = LutealDestination.AGENDA
            intakeEventId = pendingPainEventId
            intakeMedicationId = null
            intakeDate = uiState.today
            onPendingReminderConsumed()
        } else if (pendingMedicationId != null) {
            selectedDestination = LutealDestination.AGENDA
            intakeMedicationId = pendingMedicationId
            intakeDate = uiState.today
            onPendingReminderConsumed()
        } else if (pendingRecapDate != null) {
            selectedDestination = LutealDestination.AGENDA
            recapDate = runCatching { LocalDate.parse(pendingRecapDate) }.getOrDefault(uiState.today)
            recapStartAtFirstPage = false
            onPendingReminderConsumed()
        } else if (pendingMonthlyPeriod != null) {
            selectedDestination = LutealDestination.AGENDA
            monthlyPeriod = runCatching { java.time.YearMonth.parse(pendingMonthlyPeriod) }
                .getOrDefault(java.time.YearMonth.from(uiState.today))
            onPendingReminderConsumed()
        } else if (pendingAppointmentReportEventId != null) {
            selectedDestination = LutealDestination.AGENDA
            appointmentReportEventId = pendingAppointmentReportEventId
            onPendingReminderConsumed()
        }
    }

    LaunchedEffect(uiState.operationFailed) {
        if (uiState.operationFailed) {
            snackbarHostState.showSnackbar(saveError)
            viewModel.clearOperationError()
        }
    }

    if (uiState.isInitializing) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
    } else if (!uiState.preferences.hasCompletedOnboarding) {
        OnboardingScreen(
            onComplete = { role, focusMap, ageBandId ->
                viewModel.completeOnboarding(role, focusMap, ageBandId)
            }
        )
    } else {
        val overlayBackState = MainOverlayBackState(
            appointmentDate = appointmentDate,
            appointmentReportEventId = appointmentReportEventId,
            recapDate = recapDate,
            monthlyPeriod = monthlyPeriod,
            hospitalDate = hospitalDate,
            careDate = careDate,
            symptomDate = symptomDate,
            intakeDate = intakeDate,
            showBackfillDialog = showBackfillDialog
        )
        val overlayBackTarget = resolveMainOverlayBackTarget(overlayBackState)
        BackHandler(enabled = overlayBackTarget != null) {
            when (overlayBackTarget) {
                MainOverlayBackTarget.APPOINTMENT -> {
                    appointmentDate = null
                    appointmentEventId = null
                }
                MainOverlayBackTarget.APPOINTMENT_REPORT -> appointmentReportEventId = null
                MainOverlayBackTarget.RECAP -> recapDate = null
                MainOverlayBackTarget.MONTHLY_QUESTIONNAIRE -> monthlyPeriod = null
                MainOverlayBackTarget.HOSPITALIZATION -> {
                    hospitalDate = null
                    hospitalEventId = null
                }
                MainOverlayBackTarget.CARE_SESSION -> {
                    careDate = null
                    careEventId = null
                    careTensOnly = false
                }
                MainOverlayBackTarget.SYMPTOM -> {
                    symptomDate = null
                    symptomEventId = null
                }
                MainOverlayBackTarget.MEDICATION_INTAKE -> {
                    intakeDate = null
                    intakeMedicationId = null
                    intakeEventId = null
                }
                MainOverlayBackTarget.BACKFILL_CYCLE -> showBackfillDialog = false
                null -> Unit
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        LutealDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == selectedDestination,
                                onClick = { selectedDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(destination.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.fillMaxSize().widthIn(max = 760.dp)) {
                        when (selectedDestination) {
                            LutealDestination.AGENDA -> AgendaScreen(
                                state = uiState,
                                events = events,
                                intakeLabels = eventDetailLabels,
                                dailyRecaps = dailyRecaps,
                                appointments = appointments,
                                professionals = professionals,
                                onOpenRecap = { date, startAtFirstPage ->
                                    recapDate = date
                                    recapStartAtFirstPage = startAtFirstPage
                                },
                                onRecordMedication = { date ->
                                    intakeMedicationId = null
                                    intakeEventId = null
                                    intakeDate = date
                                },
                                onRecordSymptom = { date ->
                                    symptomEventId = null
                                    symptomDate = date
                                },
                                onRecordCare = { date ->
                                    careEventId = null
                                    careTensOnly = true
                                    careDate = date
                                },
                                onRecordHospitalization = { date ->
                                    hospitalEventId = null
                                    hospitalDate = date
                                },
                                onRecordAppointment = { date ->
                                    appointmentEventId = null
                                    appointmentDate = date
                                },
                                onOpenEvent = { event ->
                                    val zone = java.time.ZoneId.of(event.zoneId)
                                    when (event.type) {
                                        JournalEventType.SYMPTOM -> {
                                            symptomDate = event.startAt.atZone(zone).toLocalDate()
                                            symptomEventId = event.id
                                        }
                                        JournalEventType.CARE_SESSION -> {
                                            careDate = event.startAt.atZone(zone).toLocalDate()
                                            careEventId = event.id
                                            careTensOnly = false
                                        }
                                        JournalEventType.HOSPITALIZATION -> {
                                            hospitalDate = event.startAt.atZone(zone).toLocalDate()
                                            hospitalEventId = event.id
                                        }
                                        JournalEventType.APPOINTMENT -> {
                                            appointmentEventId = event.id
                                            appointmentDate = event.startAt.atZone(zone).toLocalDate()
                                        }
                                        JournalEventType.MEDICATION_INTAKE -> {
                                            intakeDate = event.startAt.atZone(zone).toLocalDate()
                                            intakeEventId = event.id
                                            intakeMedicationId = null
                                        }
                                        else -> Unit
                                    }
                                },
                                onOpenAppointmentDetail = { event ->
                                    val zone = java.time.ZoneId.of(event.zoneId)
                                    appointmentEventId = event.id
                                    appointmentDate = event.startAt.atZone(zone).toLocalDate()
                                },
                                onEditCycle = { cycleId, newStartDate ->
                                    viewModel.editCycleStartDate(cycleId, newStartDate)
                                },
                                onDeleteCycle = { cycleId ->
                                    viewModel.deleteCycle(cycleId)
                                },
                                onBackfillCycle = { showBackfillDialog = true }
                            )
                            LutealDestination.TIMELINE -> TimelineScreen(
                                onOpenAppointmentDetail = { eventId ->
                                    val event = events.firstOrNull { it.id == eventId } ?: return@TimelineScreen
                                    val zone = java.time.ZoneId.of(event.zoneId)
                                    appointmentEventId = event.id
                                    appointmentDate = event.startAt.atZone(zone).toLocalDate()
                                }
                            )
                            LutealDestination.MEDICATIONS -> MedsScreen(
                                onLogIntake = {
                                    intakeEventId = null
                                    intakeMedicationId = null
                                    intakeDate = uiState.today
                                },
                                onOpenIntake = { eventId, date ->
                                    intakeMedicationId = null
                                    intakeEventId = eventId
                                    intakeDate = date
                                }
                            )
                            LutealDestination.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                }
            }
        }
    }

    if (showBackfillDialog) {
        BackfillCycleDialog(
            existingCycleStarts = uiState.cycles.map { it.startDate }.toSet(),
            onConfirm = { startDate ->
                viewModel.addBackfilledCycle(startDate)
                showBackfillDialog = false
            },
            onDismiss = { showBackfillDialog = false }
        )
    }

    intakeDate?.let { date ->
        MedicationIntakeSheet(
            initialDate = date,
            initialMedicationId = intakeMedicationId,
            existingEventId = intakeEventId,
            viewModel = intakeViewModel,
            onDismiss = {
                intakeDate = null
                intakeMedicationId = null
                intakeEventId = null
            }
        )
    }

    symptomDate?.let { date ->
        SymptomSheet(
            initialDate = date,
            existingEventId = symptomEventId,
            viewModel = symptomViewModel,
            onDismiss = {
                symptomDate = null
                symptomEventId = null
            }
        )
    }

    careDate?.let { date ->
        CareSessionSheet(
            initialDate = date,
            existingEventId = careEventId,
            tensOnly = careTensOnly,
            viewModel = careViewModel,
            onDismiss = {
                careDate = null
                careEventId = null
                careTensOnly = false
            }
        )
    }

    hospitalDate?.let { date ->
        HospitalizationSheet(
            initialDate = date,
            existingEventId = hospitalEventId,
            viewModel = hospitalViewModel,
            onDismiss = {
                hospitalDate = null
                hospitalEventId = null
            }
        )
    }

    recapDate?.let { date ->
        RecapWizardSheet(
            date = date,
            viewModel = recapViewModel,
            startAtFirstPage = recapStartAtFirstPage,
            onDismiss = {
                recapDate = null
                recapStartAtFirstPage = false
            }
        )
    }

    monthlyPeriod?.let { period ->
        MonthlyQuestionnaireSheet(
            period = period,
            viewModel = monthlyQuestionnaireViewModel,
            onDismiss = { monthlyPeriod = null }
        )
    }

    appointmentReportEventId?.let { eventId ->
        AppointmentReportSheet(
            eventId = eventId,
            viewModel = appointmentViewModel,
            onDismiss = { appointmentReportEventId = null }
        )
    }

    appointmentDate?.let { date ->
        AppointmentSheet(
            initialDate = date,
            existingEventId = appointmentEventId,
            viewModel = appointmentViewModel,
            onDismiss = {
                appointmentDate = null
                appointmentEventId = null
            }
        )
    }
}

private enum class LutealDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    AGENDA(R.string.nav_agenda, Icons.Rounded.CalendarMonth),
    TIMELINE(R.string.nav_timeline, Icons.Rounded.Timeline),
    MEDICATIONS(R.string.nav_medications, Icons.Rounded.Medication),
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings)
}
