package fr.luteal.app.navigation

import android.os.Build
import androidx.core.content.ContextCompat
import fr.luteal.core.model.NotificationVisibility
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Storage
import fr.luteal.app.navigation.settings.OpenRouterSettingsCard
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.luteal.app.BuildConfig
import fr.luteal.app.R
import fr.luteal.app.backup.AutoBackupCard
import fr.luteal.app.calendar.CalendarImportSettingsCard
import fr.luteal.app.navigation.recap.MonthlyScoreHistoryCard
import fr.luteal.app.navigation.settings.care.CareSettingsScreen
import fr.luteal.app.navigation.settings.professionals.ProfessionalsSettingsScreen
import fr.luteal.app.navigation.settings.meds.MedsSettingsScreen
import fr.luteal.app.navigation.settings.meds.TreatmentPlansSettingsScreen
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.ScreenBack
import fr.luteal.core.designsystem.component.LutealRadioRow
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.component.TimeField
import fr.luteal.core.designsystem.theme.splitAppearanceChoice
import java.time.LocalTime
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.AutoLockTimeout
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.LutealBackupPreview
import fr.luteal.core.model.ReportDateRangePreset
import fr.luteal.core.model.ReportFormat
import fr.luteal.core.model.ReportLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val syncState by viewModel.uiState.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
        }
    }

    val onToggleNotificationsMaster: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.setNotificationsEnabled(true)
                } else {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                viewModel.setNotificationsEnabled(true)
            }
        } else {
            viewModel.setNotificationsEnabled(false)
        }
    }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showMeds by remember { mutableStateOf(false) }
    var showPlans by remember { mutableStateOf(false) }
    var showCare by remember { mutableStateOf(false) }
    var showProfessionals by remember { mutableStateOf(false) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showClinicalReportDialog by remember { mutableStateOf(false) }
    var pendingReportConfig by remember { mutableStateOf<ClinicalReportConfig?>(null) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportData(context, uri)
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackup(context, uri)
        }
    }

    val reportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            viewModel.exportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }
    val reportHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        val config = pendingReportConfig
        if (uri != null && config != null) {
            viewModel.exportClinicalReport(context, uri, config)
        }
        pendingReportConfig = null
    }

    val currentImportState = syncState.importState
    if (currentImportState is DataImportState.PreviewReady) {
        ImportBackupDialog(
            preview = currentImportState.preview,
            onConfirm = { strategy ->
                viewModel.confirmRestore(currentImportState.payload, strategy)
            },
            onDismiss = viewModel::dismissImportPreview
        )
    }

    if (currentImportState is DataImportState.NeedsPassword) {
        ImportEncryptedBackupDialog(
            wrongPassword = currentImportState.wrongPassword,
            onConfirm = { password ->
                viewModel.inspectBackup(context, currentImportState.uri, password)
            },
            onDismiss = viewModel::dismissImportPreview
        )
    }

    if (showClinicalReportDialog) {
        ClinicalReportDialog(
            onDismiss = { showClinicalReportDialog = false },
            onExport = { config ->
                showClinicalReportDialog = false
                pendingReportConfig = config
                val ext = if (config.format == ReportFormat.HTML) "html" else "pdf"
                val name = "luteal_recapitulatif_medical_${LocalDate.now()}.$ext"
                if (config.format == ReportFormat.HTML) {
                    reportHtmlLauncher.launch(name)
                } else {
                    reportPdfLauncher.launch(name)
                }
            }
        )
    }

    if (showWipeConfirmDialog) {
        ClearAllDataDialog(
            onConfirm = {
                showWipeConfirmDialog = false
                viewModel.purgeAllLocalData()
            },
            onDismiss = { showWipeConfirmDialog = false }
        )
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onConfirm = { pin ->
                showSetPinDialog = false
                viewModel.setAppLockEnabledWithPin(pin)
            },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showChangePinDialog) {
        ChangePinDialog(
            onVerifyCurrent = viewModel::verifyPin,
            onConfirmNew = { newPin ->
                showChangePinDialog = false
                viewModel.setAppLockEnabledWithPin(newPin)
            },
            onDismiss = { showChangePinDialog = false }
        )
    }

    if (showDisableLockDialog) {
        DisableLockDialog(
            onConfirm = { currentPin ->
                val success = viewModel.disableAppLock(currentPin)
                if (success) {
                    showDisableLockDialog = false
                }
                success
            },
            onDismiss = { showDisableLockDialog = false }
        )
    }

    if (showTimeoutDialog) {
        AutoLockTimeoutDialog(
            currentTimeout = syncState.autoLockTimeout,
            onSelect = viewModel::setAutoLockTimeout,
            onDismiss = { showTimeoutDialog = false }
        )
    }

    val settingsSubScreenOpen = showMeds || showPlans || showCare || showProfessionals || settingsSection != null
    BackHandler(enabled = settingsSubScreenOpen) {
        when {
            showMeds -> showMeds = false
            showPlans -> showPlans = false
            showCare -> showCare = false
            showProfessionals -> showProfessionals = false
            settingsSection != null -> settingsSection = null
        }
    }

    if (showMeds) {
        MedsSettingsScreen(onBack = { showMeds = false })
        return
    }

    if (showPlans) {
        TreatmentPlansSettingsScreen(onBack = { showPlans = false })
        return
    }

    if (showCare) {
        CareSettingsScreen(onBack = { showCare = false })
        return
    }

    if (showProfessionals) {
        ProfessionalsSettingsScreen(onBack = { showProfessionals = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.md)
    ) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle)
        )
        if (settingsSection == null) {
            SettingsHubRow(Icons.Rounded.Medication, stringResource(R.string.settings_hub_health)) {
                settingsSection = SettingsSection.HEALTH
            }
            SettingsHubRow(Icons.Rounded.Notifications, stringResource(R.string.settings_hub_reminders)) {
                settingsSection = SettingsSection.REMINDERS
            }
            SettingsHubRow(Icons.Rounded.Palette, stringResource(R.string.settings_hub_appearance)) {
                settingsSection = SettingsSection.APPEARANCE
            }
            SettingsHubRow(Icons.Rounded.Storage, stringResource(R.string.settings_hub_data)) {
                settingsSection = SettingsSection.DATA
            }
            SettingsHubRow(Icons.Rounded.AutoAwesome, stringResource(R.string.settings_hub_ai)) {
                settingsSection = SettingsSection.AI
            }
            SettingsHubRow(Icons.Rounded.Shield, stringResource(R.string.settings_hub_security)) {
                settingsSection = SettingsSection.SECURITY
            }
        Text(
            text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LutealSpacing.sm)
        )
        } else {
            ScreenBack(onClick = { settingsSection = null })
            when (settingsSection) {
                SettingsSection.HEALTH -> {
        SettingsSectionHeader(title = stringResource(R.string.settings_meds_title))
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMeds = true }
                    .padding(vertical = LutealSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Medication, contentDescription = null)
                Column {
                    Text(stringResource(R.string.settings_meds_open), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_meds_open_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPlans = true }
                    .padding(vertical = LutealSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null)
                Column {
                    Text(stringResource(R.string.settings_plans_open), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_plans_open_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSectionHeader(title = stringResource(R.string.settings_care_title))
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCare = true }
                    .padding(vertical = LutealSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Spa, contentDescription = null)
                Column {
                    Text(stringResource(R.string.settings_care_open), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_care_open_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSectionHeader(title = stringResource(R.string.settings_professionals_title))
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfessionals = true }
                    .padding(vertical = LutealSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
                Column {
                    Text(stringResource(R.string.settings_professionals_open), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_professionals_open_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

                }
                SettingsSection.REMINDERS -> {
        NotificationSettingsCard(
            state = syncState,
            onToggleMaster = onToggleNotificationsMaster,
            onToggleDaily = viewModel::setDailyCheckInEnabled,
            onDailyTimeChange = viewModel::setDailyCheckInTime,
            onToggleWindow = viewModel::setPeriodWindowNotificationEnabled,
            onWindowLeadDaysChange = viewModel::setPeriodWindowLeadDays,
            onToggleLate = viewModel::setLateCycleNotificationEnabled,
            onVisibilityChange = viewModel::setNotificationVisibilityMode,
            onCustomTitleChange = viewModel::setNotificationCustomTitle,
            onCustomBodyChange = viewModel::setNotificationCustomBody
        )
        CalendarImportSettingsCard()
                }
                SettingsSection.APPEARANCE -> {
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            LutealToggleRow(
                title = stringResource(R.string.settings_hide_dyspareunia_recap_title),
                description = stringResource(R.string.settings_hide_dyspareunia_recap_body),
                checked = syncState.hideDyspareuniaInRecap,
                onCheckedChange = viewModel::setHideDyspareuniaInRecap
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LutealToggleRow(
                title = stringResource(R.string.settings_hide_libido_recap_title),
                description = stringResource(R.string.settings_hide_libido_recap_body),
                checked = syncState.hideLibidoInRecap,
                onCheckedChange = viewModel::setHideLibidoInRecap
            )
        }
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_appearance_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val appearanceOptions = appearanceMenuOptions()
            val selectedAppearance = "${syncState.themeMode}|${syncState.colorPalette}"
            SettingsDropdownField(
                label = stringResource(R.string.settings_theme_label),
                selectedLabel = appearanceOptions.find { it.first == selectedAppearance }?.second
                    ?: appearanceOptions.first().second,
                options = appearanceOptions,
                onSelect = { value ->
                    val (mode, palette) = splitAppearanceChoice(value)
                    viewModel.setThemeMode(mode)
                    viewModel.setColorPalette(palette)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsInformationRow(
                icon = Icons.Rounded.HealthAndSafety,
                title = stringResource(R.string.settings_about_title),
                body = stringResource(R.string.settings_about_body)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.settings_temperature_unit_title),
                style = MaterialTheme.typography.titleMedium
            )
            LutealRadioRow(
                title = stringResource(R.string.bbt_unit_celsius),
                selected = syncState.temperatureUnit == "CELSIUS",
                onClick = { viewModel.setTemperatureUnit("CELSIUS") }
            )
            LutealRadioRow(
                title = stringResource(R.string.bbt_unit_fahrenheit),
                selected = syncState.temperatureUnit == "FAHRENHEIT",
                onClick = { viewModel.setTemperatureUnit("FAHRENHEIT") }
            )
        }
                }
                SettingsSection.DATA -> {
        SettingsSectionHeader(title = stringResource(R.string.settings_data_management_title))

        DataManagementCard(
            exportState = syncState.exportState,
            wipeState = syncState.wipeState,
            importState = syncState.importState,
            reportExportState = syncState.reportExportState,
            onExportRequested = {
                exportDocumentLauncher.launch("luteal_backup_${LocalDate.now()}.json")
            },
            onImportRequested = {
                importDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onClinicalReportRequested = { showClinicalReportDialog = true },
            onWipeRequested = { showWipeConfirmDialog = true },
            onDismissExportState = viewModel::clearExportState,
            onDismissWipeState = viewModel::clearWipeState,
            onDismissImportState = viewModel::clearImportState,
            onDismissReportExportState = viewModel::clearReportExportState
        )
        AutoBackupCard()
        MonthlyScoreHistoryCard()
                }
                SettingsSection.AI -> {
                    LaunchedEffect(syncState.openRouterAiEnabled) {
                        if (syncState.openRouterAiEnabled) {
                            viewModel.refreshOpenRouterModels()
                        } else {
                            viewModel.clearOpenRouterModels()
                        }
                    }
                    SettingsSectionHeader(title = stringResource(R.string.settings_ai_section_title))
                    Text(
                        text = stringResource(R.string.settings_ai_section_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OpenRouterSettingsCard(
                        aiEnabled = syncState.openRouterAiEnabled,
                        onAiEnabledChange = viewModel::setOpenRouterAiEnabled,
                        sttModel = syncState.openRouterSttModel,
                        summaryModel = syncState.openRouterSummaryModel,
                        sttLanguage = syncState.openRouterSttLanguage,
                        diarizationEnabled = syncState.openRouterSttDiarizationEnabled,
                        hasApiKey = syncState.hasOpenRouterApiKey,
                        modelsState = openRouterModels,
                        onRefreshModels = { viewModel.refreshOpenRouterModels(forceRefresh = true) },
                        onSttModelChange = viewModel::setOpenRouterSttModel,
                        onSummaryModelChange = viewModel::setOpenRouterSummaryModel,
                        onLanguageChange = viewModel::setOpenRouterSttLanguage,
                        onDiarizationChange = viewModel::setOpenRouterSttDiarizationEnabled,
                        onApiKeyChange = viewModel::setOpenRouterApiKey
                    )
                }
                SettingsSection.SECURITY -> {
        SecurityCard(
            isAppLockEnabled = syncState.isAppLockEnabled,
            isBiometricEnabled = syncState.isBiometricEnabled,
            isBiometricHardwareAvailable = syncState.isBiometricHardwareAvailable,
            autoLockTimeout = syncState.autoLockTimeout,
            isScreenMaskingEnabled = syncState.isScreenMaskingEnabled,
            onToggleAppLock = { enable ->
                if (enable) {
                    showSetPinDialog = true
                } else {
                    showDisableLockDialog = true
                }
            },
            onToggleBiometric = viewModel::setBiometricEnabled,
            onOpenTimeoutSelector = { showTimeoutDialog = true },
            onOpenChangePin = { showChangePinDialog = true },
            onToggleScreenMasking = viewModel::setScreenMaskingEnabled
        )
                }
                null -> Unit
            }
        }
        Spacer(Modifier.height(LutealSpacing.md))
    }
}


@Composable
private fun appearanceMenuOptions(): List<Pair<String, String>> {
    val modes = listOf(
        "LIGHT" to stringResource(R.string.settings_theme_light),
        "DARK" to stringResource(R.string.settings_theme_dark),
        "SYSTEM" to stringResource(R.string.settings_theme_system)
    )
    val palettes = listOf(
        "EVERGREEN" to stringResource(R.string.settings_palette_evergreen),
        "BLUE" to stringResource(R.string.settings_palette_blue),
        "PLUM" to stringResource(R.string.settings_palette_plum),
        "SAND" to stringResource(R.string.settings_palette_sand),
        "SOLARIZED" to stringResource(R.string.settings_palette_solarized),
        "CATPPUCCIN" to stringResource(R.string.settings_palette_catppuccin)
    )
    val themed = palettes.flatMap { (paletteId, paletteLabel) ->
        modes.map { (modeId, modeLabel) -> "$modeId|$paletteId" to "$modeLabel - $paletteLabel" }
    }
    return themed + ("DARK|DRACULA" to stringResource(R.string.settings_palette_dracula))
}

private enum class SettingsSection {
    HEALTH,
    REMINDERS,
    APPEARANCE,
    DATA,
    AI,
    SECURITY
}

@Composable
private fun SettingsHubRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    LutealCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LutealSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SecurityCard(
    isAppLockEnabled: Boolean,
    isBiometricEnabled: Boolean,
    isBiometricHardwareAvailable: Boolean,
    autoLockTimeout: AutoLockTimeout,
    isScreenMaskingEnabled: Boolean,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenTimeoutSelector: () -> Unit,
    onOpenChangePin: () -> Unit,
    onToggleScreenMasking: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_security_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealToggleRow(
                    title = stringResource(R.string.settings_app_lock_title),
                    description = stringResource(R.string.settings_app_lock_desc),
                    checked = isAppLockEnabled,
                    onCheckedChange = onToggleAppLock
                )

                if (isAppLockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LutealToggleRow(
                        title = stringResource(R.string.settings_biometric_title),
                        description = if (isBiometricHardwareAvailable) {
                            stringResource(R.string.settings_biometric_desc)
                        } else {
                            stringResource(R.string.settings_biometric_unavailable)
                        },
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        modifier = Modifier.let {
                            if (!isBiometricHardwareAvailable) it.alpha(0.5f) else it
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTimeoutSelector)
                            .padding(vertical = LutealSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_timeout_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (autoLockTimeout) {
                                    AutoLockTimeout.IMMEDIATE -> stringResource(R.string.settings_timeout_immediate)
                                    AutoLockTimeout.ONE_MINUTE -> stringResource(R.string.settings_timeout_1min)
                                    AutoLockTimeout.FIVE_MINUTES -> stringResource(R.string.settings_timeout_5min)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LutealSecondaryButton(
                        text = stringResource(R.string.settings_change_pin_action),
                        onClick = onOpenChangePin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LutealToggleRow(
                    title = stringResource(R.string.settings_screen_masking_title),
                    description = stringResource(R.string.settings_screen_masking_desc),
                    checked = isScreenMaskingEnabled,
                    onCheckedChange = onToggleScreenMasking
                )
            }
        }
    }
}

@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val errorLength = stringResource(R.string.dialog_set_pin_error_length)
    val errorMatch = stringResource(R.string.dialog_set_pin_error_match)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_set_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.dialog_set_pin_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val pinLengthError = stringResource(R.string.dialog_set_pin_error_length)
            val pinMatchError = stringResource(R.string.dialog_set_pin_error_match)
            TextButton(
                onClick = {
                    if (pin.length < 4) {
                        error = pinLengthError
                    } else if (pin != confirmPin) {
                        error = pinMatchError
                    } else {
                        onConfirm(pin)
                    }
                }
            ) {
                Text(stringResource(R.string.dialog_set_pin_action), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettingsCard(
    state: SettingsSyncUiState,
    onToggleMaster: (Boolean) -> Unit,
    onToggleDaily: (Boolean) -> Unit,
    onDailyTimeChange: (String) -> Unit,
    onToggleWindow: (Boolean) -> Unit,
    onWindowLeadDaysChange: (Int) -> Unit,
    onToggleLate: (Boolean) -> Unit,
    onVisibilityChange: (NotificationVisibility) -> Unit,
    onCustomTitleChange: (String) -> Unit,
    onCustomBodyChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_notifications_title))

        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealToggleRow(
                    title = stringResource(R.string.settings_notifications_master_title),
                    description = stringResource(R.string.settings_notifications_master_desc),
                    checked = state.isNotificationsEnabled,
                    onCheckedChange = onToggleMaster
                )

                if (state.isNotificationsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Daily Check-in
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_daily_title),
                        description = stringResource(R.string.settings_notif_daily_desc),
                        checked = state.isDailyCheckInEnabled,
                        onCheckedChange = onToggleDaily
                    )

                    if (state.isDailyCheckInEnabled) {
                        val checkInTime = remember(state.dailyCheckInTime) {
                            runCatching { LocalTime.parse(state.dailyCheckInTime) }
                                .getOrDefault(LocalTime.of(21, 0))
                        }
                        TimeField(
                            value = checkInTime,
                            onValueChange = {
                                onDailyTimeChange(String.format("%02d:%02d", it.hour, it.minute))
                            },
                            label = stringResource(R.string.settings_notif_time_picker_label),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Period Window Reminder
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_window_title),
                        description = stringResource(R.string.settings_notif_window_desc),
                        checked = state.isPeriodWindowEnabled,
                        onCheckedChange = onToggleWindow
                    )

                    if (state.isPeriodWindowEnabled) {
                        Text(
                            text = stringResource(R.string.settings_notif_lead_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                            listOf(
                                1 to stringResource(R.string.settings_notif_lead_1d),
                                2 to stringResource(R.string.settings_notif_lead_2d),
                                3 to stringResource(R.string.settings_notif_lead_3d)
                            ).forEach { (days, label) ->
                                FilterChip(
                                    selected = state.periodWindowLeadDays == days,
                                    onClick = { onWindowLeadDaysChange(days) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Late Cycle Prompt
                    LutealToggleRow(
                        title = stringResource(R.string.settings_notif_late_title),
                        description = stringResource(R.string.settings_notif_late_desc),
                        checked = state.isLateCycleEnabled,
                        onCheckedChange = onToggleLate
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Visibility selector
                    Text(
                        text = stringResource(R.string.settings_notif_visibility_title),
                        style = MaterialTheme.typography.titleSmall
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_concealed),
                        description = stringResource(R.string.settings_notif_visibility_concealed_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.CONCEALED,
                        onClick = { onVisibilityChange(NotificationVisibility.CONCEALED) }
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_descriptive),
                        description = stringResource(R.string.settings_notif_visibility_descriptive_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.DESCRIPTIVE,
                        onClick = { onVisibilityChange(NotificationVisibility.DESCRIPTIVE) }
                    )

                    LutealRadioRow(
                        title = stringResource(R.string.settings_notif_visibility_custom),
                        description = stringResource(R.string.settings_notif_visibility_custom_desc),
                        selected = state.notificationVisibilityMode == NotificationVisibility.CUSTOM,
                        onClick = { onVisibilityChange(NotificationVisibility.CUSTOM) }
                    )

                    if (state.notificationVisibilityMode == NotificationVisibility.CUSTOM) {
                        OutlinedTextField(
                            value = state.notificationCustomTitle,
                            onValueChange = onCustomTitleChange,
                            label = { Text(stringResource(R.string.settings_notif_custom_title_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.notificationCustomBody,
                            onValueChange = onCustomBodyChange,
                            label = { Text(stringResource(R.string.settings_notif_custom_body_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun ChangePinDialog(
    onVerifyCurrent: suspend (String) -> Boolean,
    onConfirmNew: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val errorCurrent = stringResource(R.string.dialog_change_pin_error_current)
    val errorLength = stringResource(R.string.dialog_set_pin_error_length)
    val errorMatch = stringResource(R.string.dialog_set_pin_error_match)
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_change_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) currentPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_current_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_new_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmNewPin = it },
                    label = { Text(stringResource(R.string.dialog_change_pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
        val currentPinError = stringResource(R.string.dialog_change_pin_error_current)
        val pinLengthError = stringResource(R.string.dialog_set_pin_error_length)
        val pinMatchError = stringResource(R.string.dialog_set_pin_error_match)
        TextButton(
            enabled = !verifying,
            onClick = {
                if (verifying) return@TextButton
                verifying = true
                scope.launch {
                    val currentOk = onVerifyCurrent(currentPin)
                    when {
                        !currentOk ->
                            error = currentPinError
                        newPin.length < 4 ->
                            error = pinLengthError
                        newPin != confirmNewPin ->
                            error = pinMatchError
                        else -> {
                            verifying = false
                            onConfirmNew(newPin)
                            return@launch
                        }
                    }
                    verifying = false
                }
            }
        ) {
            Text(stringResource(R.string.dialog_change_pin_action), fontWeight = FontWeight.Bold)
        }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DisableLockDialog(
    onConfirm: suspend (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val errorDisable = stringResource(R.string.dialog_disable_lock_error)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_disable_lock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(
                    text = stringResource(R.string.dialog_disable_lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text(stringResource(R.string.dialog_set_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val disableLockError = stringResource(R.string.dialog_disable_lock_error)
            TextButton(
                enabled = !verifying,
                onClick = {
                    if (verifying) return@TextButton
                    verifying = true
                    scope.launch {
                        val success = onConfirm(pin)
                        verifying = false
                        if (!success) {
                            error = disableLockError
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.dialog_disable_lock_action), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun AutoLockTimeoutDialog(
    currentTimeout: AutoLockTimeout,
    onSelect: (AutoLockTimeout) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timeout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                AutoLockTimeout.entries.forEach { timeout ->
                    LutealRadioRow(
                        title = when (timeout) {
                            AutoLockTimeout.IMMEDIATE -> stringResource(R.string.settings_timeout_immediate)
                            AutoLockTimeout.ONE_MINUTE -> stringResource(R.string.settings_timeout_1min)
                            AutoLockTimeout.FIVE_MINUTES -> stringResource(R.string.settings_timeout_5min)
                        },
                        selected = timeout == currentTimeout,
                        onClick = {
                            onSelect(timeout)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DataManagementCard(
    exportState: DataExportState,
    wipeState: DataWipeState,
    importState: DataImportState,
    reportExportState: DataExportState,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onClinicalReportRequested: () -> Unit,
    onWipeRequested: () -> Unit,
    onDismissExportState: () -> Unit,
    onDismissWipeState: () -> Unit,
    onDismissImportState: () -> Unit,
    onDismissReportExportState: () -> Unit
) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            Text(
                text = stringResource(R.string.settings_report_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_report_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (reportExportState) {
                    is DataExportState.Loading -> stringResource(R.string.settings_export_in_progress)
                    else -> stringResource(R.string.settings_report_action)
                },
                onClick = onClinicalReportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = reportExportState !is DataExportState.Loading
            )
            when (reportExportState) {
                is DataExportState.Success -> Text(
                    text = stringResource(R.string.settings_report_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataExportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_report_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissReportExportState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.settings_export_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_export_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (exportState) {
                    is DataExportState.Loading -> stringResource(R.string.settings_export_in_progress)
                    else -> stringResource(R.string.settings_export_action)
                },
                onClick = onExportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = exportState !is DataExportState.Loading
            )
            when (exportState) {
                is DataExportState.Success -> Text(
                    text = stringResource(R.string.settings_export_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataExportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_export_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissExportState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.settings_import_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_import_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (importState) {
                    is DataImportState.Inspecting -> stringResource(R.string.settings_import_inspecting)
                    is DataImportState.Restoring -> stringResource(R.string.settings_import_in_progress)
                    else -> stringResource(R.string.settings_import_action)
                },
                onClick = onImportRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = importState !is DataImportState.Inspecting && importState !is DataImportState.Restoring
            )
            when (importState) {
                is DataImportState.Success -> Text(
                    text = stringResource(
                        R.string.settings_import_success,
                        importState.summary.cyclesImported,
                        importState.summary.dailyEntriesImported
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataImportState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    val errorMsg = importState.messageResId?.let { stringResource(it) } ?: importState.message.orEmpty()
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissImportState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.settings_wipe_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_wipe_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LutealSecondaryButton(
                text = when (wipeState) {
                    is DataWipeState.Loading -> stringResource(R.string.settings_wipe_in_progress)
                    else -> stringResource(R.string.settings_wipe_action)
                },
                onClick = onWipeRequested,
                modifier = Modifier.fillMaxWidth(),
                enabled = wipeState !is DataWipeState.Loading
            )
            when (wipeState) {
                is DataWipeState.Success -> Text(
                    text = stringResource(R.string.settings_wipe_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is DataWipeState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)
                ) {
                    Text(
                        text = stringResource(R.string.settings_wipe_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onDismissWipeState) {
                        Text(text = stringResource(R.string.action_close))
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ImportEncryptedBackupDialog(
    wrongPassword: Boolean,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(stringResource(R.string.settings_import_password_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_encrypt_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrongPassword) {
                    Text(
                        text = stringResource(R.string.settings_import_error_wrong_password),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password.toCharArray()) },
                enabled = password.isNotEmpty()
            ) { Text(stringResource(R.string.backup_encrypt_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ImportBackupDialog(
    preview: LutealBackupPreview,
    onConfirm: (ImportStrategy) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.MERGE_UPSERT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_import_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val cycleText = if (preview.earliestCycleDate != null && preview.latestCycleDate != null) {
                    stringResource(
                        R.string.settings_import_dialog_cycles,
                        preview.cycleCount,
                        preview.earliestCycleDate,
                        preview.latestCycleDate
                    )
                } else {
                    stringResource(R.string.settings_import_dialog_cycles_no_dates, preview.cycleCount)
                }
                Text(text = cycleText, style = MaterialTheme.typography.bodyMedium)

                Text(
                    text = stringResource(R.string.settings_import_dialog_entries, preview.dailyEntryCount),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.settings_import_dialog_symptoms, preview.symptomLogCount),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.settings_import_dialog_events, preview.journalEventCount),
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.settings_import_dialog_strategy_title),
                    style = MaterialTheme.typography.titleSmall
                )

                LutealRadioRow(
                    selected = selectedStrategy == ImportStrategy.MERGE_UPSERT,
                    onClick = { selectedStrategy = ImportStrategy.MERGE_UPSERT },
                    title = stringResource(R.string.settings_import_strategy_merge),
                    description = stringResource(R.string.settings_import_strategy_merge_desc)
                )

                LutealRadioRow(
                    selected = selectedStrategy == ImportStrategy.REPLACE_ALL,
                    onClick = { selectedStrategy = ImportStrategy.REPLACE_ALL },
                    title = stringResource(R.string.settings_import_strategy_replace),
                    description = stringResource(R.string.settings_import_strategy_replace_desc)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy) }) {
                Text(
                    text = stringResource(R.string.settings_import_dialog_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ClearAllDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_wipe_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_wipe_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_wipe_dialog_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = LutealSpacing.xs)
    )
}


@Composable
private fun SettingsInformationRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LutealSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(LutealSpacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

