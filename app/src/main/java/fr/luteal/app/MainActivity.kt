package fr.luteal.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.luteal.app.navigation.AppLockResolvingBarrier
import fr.luteal.app.navigation.AppLockScreen
import fr.luteal.app.navigation.LutealMainScaffold
import fr.luteal.app.calendar.CalendarImportScheduler
import fr.luteal.app.notification.AppointmentReportScheduler
import fr.luteal.app.notification.ReminderAlarmReceiver
import fr.luteal.app.notification.ReminderScheduler
import fr.luteal.core.data.datastore.UserPreferences
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.security.AppLockManager
import fr.luteal.core.designsystem.theme.LutealTheme
import fr.luteal.core.designsystem.theme.resolveDarkTheme
import fr.luteal.core.model.AppLockState
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var userPreferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var calendarImportScheduler: CalendarImportScheduler

    @Inject
    lateinit var appointmentReportScheduler: AppointmentReportScheduler

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private var bufferedImportJson by mutableStateOf<String?>(null)
    private var pendingPainEventId by mutableStateOf<String?>(null)
    private var pendingMedicationId by mutableStateOf<String?>(null)
    private var pendingRecapDate by mutableStateOf<String?>(null)
    private var pendingMonthlyPeriod by mutableStateOf<String?>(null)
    private var pendingAppointmentReportEventId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleBackupImport(intent)
        handleReminderIntent(intent)
        lifecycleScope.launch {
            calendarImportScheduler.enqueueNow()
            calendarImportScheduler.schedulePeriodic()
            appointmentReportScheduler.reconcileAll()
        }
        enableEdgeToEdge()

        lifecycle.addObserver(appLockManager)

        // Observe screen masking preference and apply/clear FLAG_SECURE
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userPreferencesDataStore.userPreferencesFlow.collect { prefs ->
                    if (prefs.isScreenMaskingEnabled) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            val preferences by userPreferencesDataStore.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            LutealTheme(
                darkTheme = resolveDarkTheme(preferences.themeMode, isSystemInDarkTheme()),
                palette = preferences.colorPalette
            ) {
                val lockState by appLockManager.lockState.collectAsStateWithLifecycle()
                val barrierUp = lockState is AppLockState.Resolving || lockState is AppLockState.Locked
                // PIN length comes from a Keystore read: produceState keeps
                // that suspend call out of composition.
                @SuppressLint("ProduceStateDoesNotAssignValue")
                val expectedPinLength by produceState<Int?>(initialValue = null, lockState) {
                    value = appLockManager.pinLength()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = if (barrierUp) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier
                        }
                    ) {
                        LutealMainScaffold(
                            pendingImportJson = if (!barrierUp) bufferedImportJson else null,
                            pendingPainEventId = if (!barrierUp) pendingPainEventId else null,
                            pendingMedicationId = if (!barrierUp) pendingMedicationId else null,
                            pendingRecapDate = if (!barrierUp) pendingRecapDate else null,
                            pendingMonthlyPeriod = if (!barrierUp) pendingMonthlyPeriod else null,
                            pendingAppointmentReportEventId = if (!barrierUp) pendingAppointmentReportEventId else null,
                            onPendingImportConsumed = { bufferedImportJson = null },
                            onPendingReminderConsumed = {
                                pendingPainEventId = null
                                pendingMedicationId = null
                                pendingRecapDate = null
                                pendingMonthlyPeriod = null
                                pendingAppointmentReportEventId = null
                            }
                        )
                    }
                    if (lockState is AppLockState.Resolving) {
                        AppLockResolvingBarrier()
                    }
                    val locked = lockState as? AppLockState.Locked
                    if (locked != null) {
                        AppLockScreen(
                            isBiometricAvailable = locked.isBiometricAvailable,
                            remainingLockoutSeconds = locked.remainingLockoutSeconds,
                            expectedPinLength = expectedPinLength,
                            onVerifyPin = { pin -> appLockManager.verifyAndUnlockPin(pin) },
                            onRequestBiometricPrompt = ::showBiometricPrompt
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBackupImport(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_COMPLETE_REMINDER_ID)?.let { reminderId ->
            lifecycleScope.launch {
                reminderScheduler.complete(reminderId)
            }
            intent.removeExtra(ReminderAlarmReceiver.EXTRA_COMPLETE_REMINDER_ID)
        }
        pendingPainEventId = intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_OPEN_PAIN_EVENT)
        pendingMedicationId = intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_OPEN_MEDICATION)
        pendingRecapDate = intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_OPEN_RECAP_DATE)
        pendingMonthlyPeriod = intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_OPEN_MONTHLY_PERIOD)
        pendingAppointmentReportEventId = intent?.getStringExtra(ReminderAlarmReceiver.EXTRA_OPEN_APPOINTMENT_REPORT)
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                lifecycleScope.launch {
                    appLockManager.unlockWithBiometric()
                }
            }
        }
        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_biometric_title))
            .setSubtitle(getString(R.string.lock_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.lock_biometric_negative))
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun handleBackupImport(intent: Intent) {
        if (intent.action == ACTION_IMPORT_BACKUP && !intent.hasExtra(EXTRA_IMPORT_JSON)) {
            bufferedImportJson = PendingBackupStore.consume()
        }
        intent.removeExtra(EXTRA_IMPORT_JSON)
    }
    companion object {
        const val ACTION_IMPORT_BACKUP = "fr.luteal.app.action.IMPORT_BACKUP"
        const val EXTRA_IMPORT_JSON = "fr.luteal.app.extra.IMPORT_JSON"
        const val EXTRA_IMPORT_JSON_BASE64 = "fr.luteal.app.extra.IMPORT_JSON_BASE64"
        /** Device-local path (e.g. /data/local/tmp/…) set by adb push; avoids shell arg size limits. */
        const val EXTRA_IMPORT_JSON_PATH = "fr.luteal.app.extra.IMPORT_JSON_PATH"
    }
}
