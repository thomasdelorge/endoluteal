package fr.luteal.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.seed.TestDataSeeder
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.TrackingContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import android.content.Context
import android.net.Uri
import fr.luteal.core.data.DataExportManager
import fr.luteal.core.data.backup.MissingBackupPassphrase
import fr.luteal.core.data.LocalDataPurgeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.annotation.StringRes
import fr.luteal.app.BuildConfig
import fr.luteal.app.R
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.openrouter.OpenRouterApiKeyStore
import fr.luteal.core.openrouter.OpenRouterDefaults
import fr.luteal.core.openrouter.OpenRouterModelInfo
import fr.luteal.core.openrouter.OpenRouterModelsRepository
import fr.luteal.core.openrouter.SttModelCapabilities
import fr.luteal.core.data.security.AppLockManager
import fr.luteal.core.data.security.PinCryptoManager
import fr.luteal.core.model.AutoLockTimeout
import fr.luteal.core.data.DataImportManager
import fr.luteal.core.model.DataImportError
import fr.luteal.core.data.ClinicalReportAggregator
import fr.luteal.core.data.report.HtmlReportBuilder
import fr.luteal.core.data.report.PdfReportBuilder
import fr.luteal.core.model.ClinicalReportConfig
import fr.luteal.core.model.ReportFormat
import fr.luteal.app.notification.NotificationScheduler
import fr.luteal.core.model.NotificationVisibility
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.ImportSummary
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.LutealBackupPreview
import java.io.ByteArrayInputStream

/** Settings tab view model: local prefs, backup/restore, PIN lock, notifications. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val testDataSeeder: TestDataSeeder,
    private val dataExportManager: DataExportManager,
    private val dataImportManager: DataImportManager,
    private val localDataPurgeManager: LocalDataPurgeManager,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val pinCryptoManager: PinCryptoManager,
    private val appLockManager: AppLockManager,
    private val clinicalReportAggregator: ClinicalReportAggregator,
    private val notificationScheduler: NotificationScheduler,
    private val openRouterApiKeyStore: OpenRouterApiKeyStore,
    private val openRouterModelsRepository: OpenRouterModelsRepository
) : ViewModel() {
    private val testDataActionState = MutableStateFlow<TestDataActionState>(TestDataActionState.Idle)

    private val exportState = MutableStateFlow<DataExportState>(DataExportState.Idle)
    private val wipeState = MutableStateFlow<DataWipeState>(DataWipeState.Idle)
    private val importState = MutableStateFlow<DataImportState>(DataImportState.Idle)

    private val reportExportState = MutableStateFlow<DataExportState>(DataExportState.Idle)
    private val openRouterKeyPresent = MutableStateFlow(openRouterApiKeyStore.hasApiKey())
    private val openRouterModelsState = MutableStateFlow(OpenRouterModelsUiState())
    val openRouterModels: StateFlow<OpenRouterModelsUiState> = openRouterModelsState

    private val fileOps: Flow<FileOps> =
        combine(exportState, wipeState, importState, reportExportState) { export, wipe, importOp, report ->
            FileOps(export, wipe, importOp, report)
        }

    val uiState: StateFlow<SettingsSyncUiState> = combine(
        userRepository.getUserPreferences(),
        fileOps,
        testDataActionState,
        openRouterKeyPresent,
    ) { preferences, files, actionState, hasOpenRouterKey ->
        SettingsSyncUiState(
            testDataState = actionState,
            exportState = files.export,
            wipeState = files.wipe,
            importState = files.importOp,
            reportExportState = files.report,
            declaredContexts = preferences.declaredContexts,
            ageBand = AgeBand.fromId(preferences.ageBand),
            isAppLockEnabled = preferences.isAppLockEnabled,
            isBiometricEnabled = preferences.isBiometricEnabled,
            isBiometricHardwareAvailable = appLockManager.isBiometricHardwareAvailable(),
            autoLockTimeout = AutoLockTimeout.fromName(preferences.autoLockTimeout),
            isScreenMaskingEnabled = preferences.isScreenMaskingEnabled,
            hasPinConfigured = pinCryptoManager.hasPinConfigured(),
            isNotificationsEnabled = preferences.isNotificationsEnabled,
            isDailyCheckInEnabled = preferences.isDailyCheckInEnabled,
            dailyCheckInTime = preferences.dailyCheckInTime,
            isPeriodWindowEnabled = preferences.isPeriodWindowEnabled,
            periodWindowLeadDays = preferences.periodWindowLeadDays,
            isLateCycleEnabled = preferences.isLateCycleEnabled,
            lateCycleGraceDays = preferences.lateCycleGraceDays,
            notificationVisibilityMode = NotificationVisibility.fromName(preferences.notificationVisibilityMode),
            notificationCustomTitle = preferences.notificationCustomTitle,
            notificationCustomBody = preferences.notificationCustomBody,
            temperatureUnit = preferences.temperatureUnit,
            themeMode = preferences.themeMode,
            colorPalette = preferences.colorPalette,
            hideDyspareuniaInRecap = preferences.hideDyspareuniaInRecap,
            hideLibidoInRecap = preferences.hideLibidoInRecap,
            openRouterSttModel = preferences.openRouterSttModel,
            openRouterSummaryModel = preferences.openRouterSummaryModel,
            openRouterSttLanguage = preferences.openRouterSttLanguage,
            openRouterSttDiarizationEnabled = preferences.openRouterSttDiarizationEnabled,
            openRouterAiEnabled = preferences.openRouterAiEnabled,
            hasOpenRouterApiKey = hasOpenRouterKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsSyncUiState()
    )

    fun setOpenRouterApiKey(key: String) {
        openRouterApiKeyStore.setApiKey(key)
        openRouterKeyPresent.value = openRouterApiKeyStore.hasApiKey()
        openRouterModelsRepository.clearCache()
        viewModelScope.launch {
            if (userPreferencesDataStore.userPreferencesFlow.first().openRouterAiEnabled) {
                refreshOpenRouterModels(forceRefresh = true)
            }
        }
    }

    fun setOpenRouterAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setOpenRouterAiEnabled(enabled)
            if (enabled) {
                refreshOpenRouterModels(forceRefresh = true)
            } else {
                clearOpenRouterModels()
            }
        }
    }

    fun clearOpenRouterModels() {
        openRouterModelsState.value = OpenRouterModelsUiState()
        openRouterModelsRepository.clearCache()
    }

    fun refreshOpenRouterModels(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!userPreferencesDataStore.userPreferencesFlow.first().openRouterAiEnabled) {
                clearOpenRouterModels()
                return@launch
            }
            openRouterModelsState.value = openRouterModelsState.value.copy(loading = true, error = null)
            runCatching {
                val stt = openRouterModelsRepository.transcriptionModels(forceRefresh)
                val text = openRouterModelsRepository.textModels(forceRefresh)
                openRouterModelsState.value = OpenRouterModelsUiState(
                    loading = false,
                    sttModels = stt,
                    textModels = text,
                    error = null
                )
            }.onFailure { error ->
                openRouterModelsState.value = openRouterModelsState.value.copy(
                    loading = false,
                    error = error.message ?: "Erreur"
                )
            }
        }
    }

    fun setOpenRouterSttModel(model: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setOpenRouterSttModel(model.ifBlank { OpenRouterDefaults.STT_MODEL })
            if (!SttModelCapabilities.supportsDiarization(model)) {
                userPreferencesDataStore.setOpenRouterSttDiarizationEnabled(false)
            }
        }
    }

    fun setOpenRouterSummaryModel(model: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setOpenRouterSummaryModel(
                model.ifBlank { OpenRouterDefaults.SUMMARY_MODEL }
            )
        }
    }

    fun setOpenRouterSttLanguage(language: String) {
        viewModelScope.launch { userPreferencesDataStore.setOpenRouterSttLanguage(language) }
    }

    fun setOpenRouterSttDiarizationEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesDataStore.setOpenRouterSttDiarizationEnabled(enabled) }
    }

    fun setTrackingContext(context: TrackingContext, enabled: Boolean) {
        viewModelScope.launch {
            userRepository.setTrackingContext(context.id, enabled)
        }
    }

    fun setAgeBand(band: AgeBand?) {
        viewModelScope.launch {
            userRepository.setAgeBand(band?.id)
        }
    }

    fun setTemperatureUnit(unit: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setTemperatureUnit(unit)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setThemeMode(mode)
        }
    }

    fun setColorPalette(palette: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setColorPalette(palette)
        }
    }

    fun setHideDyspareuniaInRecap(hidden: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setHideDyspareuniaInRecap(hidden)
        }
    }

    fun setHideLibidoInRecap(hidden: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setHideLibidoInRecap(hidden)
        }
    }

    fun seedMockData() {
        viewModelScope.launch {
            testDataActionState.value = TestDataActionState.Loading
            runCatching {
                testDataSeeder.seedMockData()
            }.onSuccess {
                testDataActionState.value = TestDataActionState.SuccessSeeded
            }.onFailure {
                testDataActionState.value = TestDataActionState.Error(
                    message = it.message,
                    messageResId = if (it.message.isNullOrBlank()) R.string.settings_test_data_error_seed else null
                )
            }
        }
    }

    fun clearTestData() {
        viewModelScope.launch {
            testDataActionState.value = TestDataActionState.Loading
            runCatching {
                testDataSeeder.clearAllData()
            }.onSuccess {
                testDataActionState.value = TestDataActionState.SuccessCleared
            }.onFailure {
                testDataActionState.value = TestDataActionState.Error(
                    message = it.message,
                    messageResId = if (it.message.isNullOrBlank()) R.string.settings_test_data_error_clear else null
                )
            }
        }
    }

    fun clearTestDataMessage() {
        testDataActionState.value = TestDataActionState.Idle
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            exportState.value = DataExportState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        dataExportManager.exportToStream(stream, BuildConfig.VERSION_NAME)
                    } ?: throw IllegalStateException("Cannot open output stream for URI")
                }
            }.onSuccess {
                exportState.value = DataExportState.Success
            }.onFailure { err ->
                exportState.value = DataExportState.Error(
                    if (err is MissingBackupPassphrase) {
                        context.getString(R.string.backup_encrypt_missing)
                    } else {
                        err.message ?: "Export failed"
                    }
                )
            }
        }
    }

    fun purgeAllLocalData(onPurged: () -> Unit = {}) {
        viewModelScope.launch {
            wipeState.value = DataWipeState.Loading
            runCatching {
                if (!localDataPurgeManager.purgeAllLocalData()) {
                    error("local erase did not clear every secret")
                }
            }.onSuccess {
                wipeState.value = DataWipeState.Success
                notificationScheduler.reconcileAllSchedules()
                onPurged()
            }.onFailure { err ->
                wipeState.value = DataWipeState.Error(err.message ?: "Purge failed")
            }
        }
    }

    fun clearExportState() {
        exportState.value = DataExportState.Idle
    }

    fun clearWipeState() {
        wipeState.value = DataWipeState.Idle
    }

    fun inspectBackup(context: Context, uri: Uri, decryptionPassword: CharArray? = null) {
        viewModelScope.launch {
            importState.value = DataImportState.Inspecting
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        dataImportManager.inspectBackup(stream, decryptionPassword)
                    } ?: throw IllegalStateException("Cannot open input stream for URI")
                }
            }
            opened.onSuccess { result ->
                result.fold(
                    onSuccess = { (preview, payload) ->
                        importState.value = DataImportState.PreviewReady(preview, payload)
                    },
                    onFailure = { err -> applyImportFailure(uri, err) }
                )
            }.onFailure { err -> applyImportFailure(uri, err) }
        }
    }

    private fun applyImportFailure(uri: Uri, err: Throwable) {
        importState.value = when (err) {
            is DataImportError.EncryptedBackupNeedsPassword -> DataImportState.NeedsPassword(uri)
            is DataImportError.WrongBackupPassword -> DataImportState.NeedsPassword(uri, wrongPassword = true)
            is DataImportError.InvalidJsonSyntax -> DataImportState.Error(messageResId = R.string.settings_import_error_syntax)
            is DataImportError.UnsupportedSchemaVersion -> DataImportState.Error(messageResId = R.string.settings_import_error_schema)
            else -> DataImportState.Error(message = err.message, messageResId = R.string.settings_import_error_generic)
        }
    }

    fun inspectBackupJson(json: String) {
        viewModelScope.launch {
            importState.value = DataImportState.Inspecting
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    dataImportManager.inspectBackup(
                        ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
                    )
                }
            }
            result.onSuccess { inspection ->
                inspection.fold(
                    onSuccess = { (preview, payload) ->
                        importState.value = DataImportState.PreviewReady(preview, payload)
                    },
                    onFailure = { err ->
                        importState.value = when (err) {
                            is DataImportError.EncryptedBackupNeedsPassword,
                            is DataImportError.WrongBackupPassword -> DataImportState.Error(
                                messageResId = R.string.settings_import_error_generic
                            )
                            is DataImportError.InvalidJsonSyntax -> DataImportState.Error(
                                messageResId = R.string.settings_import_error_syntax
                            )
                            is DataImportError.UnsupportedSchemaVersion -> DataImportState.Error(
                                messageResId = R.string.settings_import_error_schema
                            )
                            else -> DataImportState.Error(
                                message = err.message,
                                messageResId = R.string.settings_import_error_generic
                            )
                        }
                    }
                )
            }.onFailure { err ->
                importState.value = DataImportState.Error(
                    message = err.message,
                    messageResId = R.string.settings_import_error_generic
                )
            }
        }
    }

    fun confirmRestore(payload: LutealBackupPayload, strategy: ImportStrategy, onRestored: () -> Unit = {}) {
        viewModelScope.launch {
            importState.value = DataImportState.Restoring
            val result = dataImportManager.restoreBackup(payload, strategy)
            result.fold(
                onSuccess = { summary ->
                    importState.value = DataImportState.Success(summary)
                    notificationScheduler.reconcileAllSchedules()
                    onRestored()
                },
                onFailure = { err ->
                    importState.value = DataImportState.Error(
                        message = err.message,
                        messageResId = R.string.settings_import_error_generic
                    )
                }
            )
        }
    }

    fun dismissImportPreview() {
        importState.value = DataImportState.Idle
    }

    fun clearImportState() {
        importState.value = DataImportState.Idle
    }

    fun setAppLockEnabledWithPin(pin: String) {
        viewModelScope.launch {
            pinCryptoManager.setPin(pin)
            userPreferencesDataStore.setAppLockEnabled(true)
        }
    }

    suspend fun disableAppLock(currentPin: String): Boolean {
        if (!pinCryptoManager.verifyPin(currentPin)) return false
        userPreferencesDataStore.setAppLockEnabled(false)
        userPreferencesDataStore.setBiometricEnabled(false)
        pinCryptoManager.clearPin()
        return true
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setBiometricEnabled(enabled)
        }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        viewModelScope.launch {
            userPreferencesDataStore.setAutoLockTimeout(timeout.name)
        }
    }

    fun setScreenMaskingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setScreenMaskingEnabled(enabled)
        }
    }

    suspend fun changePin(oldPin: String, newPin: String): Boolean {
        if (!pinCryptoManager.verifyPin(oldPin)) return false
        pinCryptoManager.setPin(newPin)
        return true
    }


    fun exportClinicalReport(context: Context, uri: Uri, config: ClinicalReportConfig) {
        viewModelScope.launch {
            reportExportState.value = DataExportState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    val data = clinicalReportAggregator.aggregate(config)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        if (config.format == ReportFormat.PDF) {
                            PdfReportBuilder.writePdfToStream(data, stream)
                        } else {
                            HtmlReportBuilder.writeHtmlToStream(data, stream)
                        }
                    } ?: throw IllegalStateException("Cannot open output stream for URI")
                }
            }.onSuccess {
                reportExportState.value = DataExportState.Success
            }.onFailure { err ->
                reportExportState.value = DataExportState.Error(err.message ?: "Export failed")
            }
        }
    }

    fun clearReportExportState() {
        reportExportState.value = DataExportState.Idle
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationsEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setDailyCheckInEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setDailyCheckInEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setDailyCheckInTime(time: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setDailyCheckInTime(time)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setPeriodWindowNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setPeriodWindowNotificationEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setPeriodWindowLeadDays(days: Int) {
        viewModelScope.launch {
            userPreferencesDataStore.setPeriodWindowLeadDays(days)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setLateCycleNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setLateCycleNotificationEnabled(enabled)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setLateCycleGraceDays(days: Int) {
        viewModelScope.launch {
            userPreferencesDataStore.setLateCycleGraceDays(days)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationVisibilityMode(mode: NotificationVisibility) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationVisibilityMode(mode.name)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationCustomTitle(title: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationCustomTitle(title)
            notificationScheduler.reconcileAllSchedules()
        }
    }

    fun setNotificationCustomBody(body: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setNotificationCustomBody(body)
            notificationScheduler.reconcileAllSchedules()
        }
    }
    suspend fun verifyPin(pin: String): Boolean = pinCryptoManager.verifyPin(pin)
}

sealed interface DataExportState {
    data object Idle : DataExportState
    data object Loading : DataExportState
    data object Success : DataExportState
    data class Error(val message: String) : DataExportState
}

sealed interface DataWipeState {
    data object Idle : DataWipeState
    data object Loading : DataWipeState
    data object Success : DataWipeState
    data class Error(val message: String) : DataWipeState
}

sealed interface DataImportState {
    data object Idle : DataImportState
    data object Inspecting : DataImportState
    data class NeedsPassword(val uri: Uri, val wrongPassword: Boolean = false) : DataImportState
    data class PreviewReady(val preview: LutealBackupPreview, val payload: LutealBackupPayload) : DataImportState
    data object Restoring : DataImportState
    data class Success(val summary: ImportSummary) : DataImportState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : DataImportState
}

data class OpenRouterModelsUiState(
    val loading: Boolean = false,
    val sttModels: List<OpenRouterModelInfo> = emptyList(),
    val textModels: List<OpenRouterModelInfo> = emptyList(),
    val error: String? = null
)

data class SettingsSyncUiState(
    val testDataState: TestDataActionState = TestDataActionState.Idle,
    val exportState: DataExportState = DataExportState.Idle,
    val wipeState: DataWipeState = DataWipeState.Idle,
    val importState: DataImportState = DataImportState.Idle,
    val reportExportState: DataExportState = DataExportState.Idle,
    /** Contexts the user has declared, editable after onboarding. */
    val declaredContexts: Set<TrackingContext> = emptySet(),
    /** Null means no band declared, which is a valid state. */
    val ageBand: AgeBand? = null,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isBiometricHardwareAvailable: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
    val isScreenMaskingEnabled: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val isDailyCheckInEnabled: Boolean = false,
    val dailyCheckInTime: String = "21:00",
    val isPeriodWindowEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2,
    val isLateCycleEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1,
    val notificationVisibilityMode: NotificationVisibility = NotificationVisibility.CONCEALED,
    val notificationCustomTitle: String = "",
    val notificationCustomBody: String = "",
    val temperatureUnit: String = "CELSIUS",
    val themeMode: String = "SYSTEM",
    val colorPalette: String = "EVERGREEN",
    val hideDyspareuniaInRecap: Boolean = false,
    val hideLibidoInRecap: Boolean = false,
    val openRouterSttModel: String = OpenRouterDefaults.STT_MODEL,
    val openRouterSummaryModel: String = OpenRouterDefaults.SUMMARY_MODEL,
    val openRouterSttLanguage: String = OpenRouterDefaults.STT_LANGUAGE,
    val openRouterSttDiarizationEnabled: Boolean = false,
    val openRouterAiEnabled: Boolean = false,
    val hasOpenRouterApiKey: Boolean = false
)

/** Text drafts held together so [combine] stays within its five-flow limit. */
private data class FileOps(
    val export: DataExportState,
    val wipe: DataWipeState,
    val importOp: DataImportState,
    val report: DataExportState
)

sealed interface TestDataActionState {
    data object Idle : TestDataActionState
    data object Loading : TestDataActionState
    data object SuccessSeeded : TestDataActionState
    data object SuccessCleared : TestDataActionState
    data class Error(val message: String? = null, @param:StringRes val messageResId: Int? = null) : TestDataActionState
}
