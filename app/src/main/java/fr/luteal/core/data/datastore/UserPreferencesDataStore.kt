package fr.luteal.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.model.ContextGroup
import fr.luteal.core.model.DuoSharingField
import fr.luteal.core.model.DuoSharingPreferences
import fr.luteal.core.model.TrackingContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val syncMode: String = "OFFLINE_LOCAL",
    val userRole: String = "PRIMARY_TRACKER",
    val locale: String = "fr",
    val hasCompletedOnboarding: Boolean = false,
    val trackPmdd: Boolean = false,
    val trackPms: Boolean = false,
    val trackEndometriosis: Boolean = false,
    val trackPcos: Boolean = false,
    val trackPerimenopause: Boolean = false,
    val trackThyroid: Boolean = false,
    /** Optional; null means the user did not declare one. */
    val ageBand: String? = null,
    val couplePairingCode: String? = null,
    val duoSharing: DuoSharingPreferences = DuoSharingPreferences(),
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeout: String = "IMMEDIATE",
    val isScreenMaskingEnabled: Boolean = false,
    val consecutivePinFailures: Int = 0,
    val lockoutUntilEpochMillis: Long = 0L,
    /** Monotonic-clock twin of [lockoutUntilEpochMillis]; see AppLockManager. */
    val lockoutUntilElapsedRealtimeMillis: Long = 0L,
    val isNotificationsEnabled: Boolean = false,
    val isDailyCheckInEnabled: Boolean = false,
    val dailyCheckInTime: String = "21:00",
    val isPeriodWindowEnabled: Boolean = false,
    val periodWindowLeadDays: Int = 2,
    val isLateCycleEnabled: Boolean = false,
    val lateCycleGraceDays: Int = 1,
    val notificationVisibilityMode: String = "CONCEALED",
    val notificationCustomTitle: String = "",
    val notificationCustomBody: String = "",
    val temperatureUnit: String = "CELSIUS",
    val medicationsSeedOffered: Boolean = false,
    val careTypesSeedOffered: Boolean = false,
    val painFollowupDelayMinutes: Int = 45,
    val autoBackupEnabled: Boolean = false,
    val autoBackupUri: String = "",
    val autoBackupHour: String = "03:00",
    val autoBackupKeep: Int = 14,
    val autoBackupLastSuccess: String = "",
    val autoBackupLastError: String = "",
    val hideDyspareuniaInRecap: Boolean = false,
    val hideLibidoInRecap: Boolean = false,
    val backupEncryptionEnabled: Boolean = false,
    val themeMode: String = "SYSTEM",
    val colorPalette: String = "EVERGREEN",
    val calendarImportEnabled: Boolean = false,
    val calendarImportKeywords: String = "",
    val calendarSelectedIds: Set<String> = emptySet(),
    val openRouterSttModel: String = fr.luteal.core.openrouter.OpenRouterDefaults.STT_MODEL,
    val openRouterSummaryModel: String = fr.luteal.core.openrouter.OpenRouterDefaults.SUMMARY_MODEL,
    val openRouterSttLanguage: String = fr.luteal.core.openrouter.OpenRouterDefaults.STT_LANGUAGE,
    val openRouterSttDiarizationEnabled: Boolean = false,
    val openRouterAiEnabled: Boolean = false
) {
    /** Contexts the user declared during onboarding. */
    val declaredContexts: Set<TrackingContext>
        get() = buildSet {
            if (trackPms) add(TrackingContext.PMS)
            if (trackPmdd) add(TrackingContext.PMDD)
            if (trackEndometriosis) add(TrackingContext.ENDOMETRIOSIS)
            if (trackPcos) add(TrackingContext.PCOS)
            if (trackPerimenopause) add(TrackingContext.PERIMENOPAUSE)
            if (trackThyroid) add(TrackingContext.THYROID)
        }

    /**
     * Whether any declared context belongs to [ContextGroup.TIMING].
     *
     * Endometriosis, SPM, and TDPM are deliberately excluded: they are
     * OBSERVATION contexts and must not influence estimation. See
     * [TrackingContext] and docs/research/CONDITION_CYCLE_IMPACTS.md.
     */
    val hasTimingContext: Boolean
        get() = declaredContexts.any { it.group == ContextGroup.TIMING }
}

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val SYNC_MODE = stringPreferencesKey("sync_mode")
        val USER_ROLE = stringPreferencesKey("user_role")
        val LOCALE = stringPreferencesKey("locale")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        val TRACK_PMDD = booleanPreferencesKey("track_pmdd")
        val TRACK_PMS = booleanPreferencesKey("track_pms")
        val TRACK_ENDOMETRIOSIS = booleanPreferencesKey("track_endometriosis")
        val TRACK_PCOS = booleanPreferencesKey("track_pcos")
        val TRACK_PERIMENOPAUSE = booleanPreferencesKey("track_perimenopause")
        val TRACK_THYROID = booleanPreferencesKey("track_thyroid")
        val AGE_BAND = stringPreferencesKey("age_band")
        val COUPLE_PAIRING_CODE = stringPreferencesKey("couple_pairing_code")
        val SHARE_CYCLE_DAY = booleanPreferencesKey("share_cycle_day")
        val SHARE_PERIOD_ESTIMATE = booleanPreferencesKey("share_period_estimate")
        val SHARE_MOOD = booleanPreferencesKey("share_mood")
        val SHARE_ENERGY = booleanPreferencesKey("share_energy")
        val SHARE_SUPPORT_REQUESTS = booleanPreferencesKey("share_support_requests")
        val IS_APP_LOCK_ENABLED = booleanPreferencesKey("is_app_lock_enabled")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val AUTO_LOCK_TIMEOUT = stringPreferencesKey("auto_lock_timeout")
        val IS_SCREEN_MASKING_ENABLED = booleanPreferencesKey("is_screen_masking_enabled")
        val CONSECUTIVE_PIN_FAILURES = androidx.datastore.preferences.core.intPreferencesKey("consecutive_pin_failures")
        val LOCKOUT_UNTIL_EPOCH_MILLIS = androidx.datastore.preferences.core.longPreferencesKey("lockout_until_epoch_millis")
        val LOCKOUT_UNTIL_ELAPSED_REALTIME_MILLIS = androidx.datastore.preferences.core.longPreferencesKey("lockout_until_elapsed_realtime_millis")
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_DAILY_ENABLED = booleanPreferencesKey("notif_daily_enabled")
        val NOTIF_DAILY_TIME = stringPreferencesKey("notif_daily_time")
        val NOTIF_WINDOW_ENABLED = booleanPreferencesKey("notif_window_enabled")
        val NOTIF_WINDOW_LEAD_DAYS = androidx.datastore.preferences.core.intPreferencesKey("notif_window_lead_days")
        val NOTIF_LATE_ENABLED = booleanPreferencesKey("notif_late_enabled")
        val NOTIF_LATE_GRACE_DAYS = androidx.datastore.preferences.core.intPreferencesKey("notif_late_grace_days")
        val NOTIF_VISIBILITY_MODE = stringPreferencesKey("notif_visibility_mode")
        val NOTIF_CUSTOM_TITLE = stringPreferencesKey("notif_custom_title")
        val NOTIF_CUSTOM_BODY = stringPreferencesKey("notif_custom_body")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val MEDICATIONS_SEED_OFFERED = booleanPreferencesKey("medications_seed_offered")
        val CARE_TYPES_SEED_OFFERED = booleanPreferencesKey("care_types_seed_offered")
        val PAIN_FOLLOWUP_DELAY_MINUTES = androidx.datastore.preferences.core.intPreferencesKey("pain_followup_delay_minutes")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val AUTO_BACKUP_HOUR = stringPreferencesKey("auto_backup_hour")
        val AUTO_BACKUP_KEEP = androidx.datastore.preferences.core.intPreferencesKey("auto_backup_keep")
        val AUTO_BACKUP_LAST_SUCCESS = stringPreferencesKey("auto_backup_last_success")
        val AUTO_BACKUP_LAST_ERROR = stringPreferencesKey("auto_backup_last_error")
        val HIDE_DYSPAREUNIA_IN_RECAP = booleanPreferencesKey("hide_dyspareunia_in_recap")
        val HIDE_LIBIDO_IN_RECAP = booleanPreferencesKey("hide_libido_in_recap")
        val BACKUP_ENCRYPTION_ENABLED = booleanPreferencesKey("backup_encryption_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val CALENDAR_IMPORT_ENABLED = booleanPreferencesKey("calendar_import_enabled")
        val CALENDAR_IMPORT_KEYWORDS = stringPreferencesKey("calendar_import_keywords")
        val CALENDAR_SELECTED_IDS = stringPreferencesKey("calendar_selected_ids")
        val OPENROUTER_STT_MODEL = stringPreferencesKey("openrouter_stt_model")
        val OPENROUTER_SUMMARY_MODEL = stringPreferencesKey("openrouter_summary_model")
        val OPENROUTER_STT_LANGUAGE = stringPreferencesKey("openrouter_stt_language")
        val OPENROUTER_STT_DIARIZATION = booleanPreferencesKey("openrouter_stt_diarization")
        val OPENROUTER_AI_ENABLED = booleanPreferencesKey("openrouter_ai_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserPreferences(
                syncMode = preferences[SYNC_MODE] ?: "OFFLINE_LOCAL",
                userRole = preferences[USER_ROLE] ?: "PRIMARY_TRACKER",
                locale = preferences[LOCALE] ?: "fr",
                hasCompletedOnboarding = preferences[COMPLETED_ONBOARDING] ?: false,
                trackPmdd = preferences[TRACK_PMDD] ?: false,
                trackPms = preferences[TRACK_PMS] ?: false,
                trackEndometriosis = preferences[TRACK_ENDOMETRIOSIS] ?: false,
                trackPcos = preferences[TRACK_PCOS] ?: false,
                trackPerimenopause = preferences[TRACK_PERIMENOPAUSE] ?: false,
                trackThyroid = preferences[TRACK_THYROID] ?: false,
                ageBand = preferences[AGE_BAND],
                couplePairingCode = preferences[COUPLE_PAIRING_CODE],
                duoSharing = DuoSharingPreferences(
                    // All off by default: sharing is opt-in and the server is
                    // the source of truth for grants. These keys mirror the
                    // last confirmed choices and are written only from the
                    // server's duoView response or a successful toggle.
                    shareCycleDay = preferences[SHARE_CYCLE_DAY] ?: false,
                    sharePeriodEstimate = preferences[SHARE_PERIOD_ESTIMATE] ?: false,
                    shareMood = preferences[SHARE_MOOD] ?: false,
                    shareEnergy = preferences[SHARE_ENERGY] ?: false,
                    shareSupportRequests = preferences[SHARE_SUPPORT_REQUESTS] ?: false
                ),
                isAppLockEnabled = preferences[IS_APP_LOCK_ENABLED] ?: false,
                isBiometricEnabled = preferences[IS_BIOMETRIC_ENABLED] ?: false,
                autoLockTimeout = preferences[AUTO_LOCK_TIMEOUT] ?: "IMMEDIATE",
                isScreenMaskingEnabled = preferences[IS_SCREEN_MASKING_ENABLED] ?: false,
                consecutivePinFailures = preferences[CONSECUTIVE_PIN_FAILURES] ?: 0,
                lockoutUntilEpochMillis = preferences[LOCKOUT_UNTIL_EPOCH_MILLIS] ?: 0L,
                lockoutUntilElapsedRealtimeMillis = preferences[LOCKOUT_UNTIL_ELAPSED_REALTIME_MILLIS] ?: 0L,
                isNotificationsEnabled = preferences[NOTIF_ENABLED] ?: false,
                isDailyCheckInEnabled = preferences[NOTIF_DAILY_ENABLED] ?: false,
                dailyCheckInTime = preferences[NOTIF_DAILY_TIME] ?: "21:00",
                isPeriodWindowEnabled = preferences[NOTIF_WINDOW_ENABLED] ?: false,
                periodWindowLeadDays = preferences[NOTIF_WINDOW_LEAD_DAYS] ?: 2,
                isLateCycleEnabled = preferences[NOTIF_LATE_ENABLED] ?: false,
                lateCycleGraceDays = preferences[NOTIF_LATE_GRACE_DAYS] ?: 1,
                notificationVisibilityMode = preferences[NOTIF_VISIBILITY_MODE] ?: "CONCEALED",
                notificationCustomTitle = preferences[NOTIF_CUSTOM_TITLE] ?: "",
                notificationCustomBody = preferences[NOTIF_CUSTOM_BODY] ?: "",
                temperatureUnit = preferences[TEMPERATURE_UNIT] ?: "CELSIUS",
                medicationsSeedOffered = preferences[MEDICATIONS_SEED_OFFERED] ?: false,
                careTypesSeedOffered = preferences[CARE_TYPES_SEED_OFFERED] ?: false,
                painFollowupDelayMinutes = preferences[PAIN_FOLLOWUP_DELAY_MINUTES] ?: 45,
                autoBackupEnabled = preferences[AUTO_BACKUP_ENABLED] ?: false,
                autoBackupUri = preferences[AUTO_BACKUP_URI] ?: "",
                autoBackupHour = preferences[AUTO_BACKUP_HOUR] ?: "03:00",
                autoBackupKeep = preferences[AUTO_BACKUP_KEEP] ?: 14,
                autoBackupLastSuccess = preferences[AUTO_BACKUP_LAST_SUCCESS] ?: "",
                autoBackupLastError = preferences[AUTO_BACKUP_LAST_ERROR] ?: "",
                hideDyspareuniaInRecap = preferences[HIDE_DYSPAREUNIA_IN_RECAP] ?: false,
                hideLibidoInRecap = preferences[HIDE_LIBIDO_IN_RECAP] ?: false,
                backupEncryptionEnabled = preferences[BACKUP_ENCRYPTION_ENABLED] ?: false,
                themeMode = preferences[THEME_MODE] ?: "SYSTEM",
                colorPalette = preferences[COLOR_PALETTE] ?: "EVERGREEN",
                calendarImportEnabled = preferences[CALENDAR_IMPORT_ENABLED] ?: false,
                calendarImportKeywords = preferences[CALENDAR_IMPORT_KEYWORDS] ?: "",
                calendarSelectedIds = preferences[CALENDAR_SELECTED_IDS]
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: emptySet(),
                openRouterSttModel = preferences[OPENROUTER_STT_MODEL]
                    ?: fr.luteal.core.openrouter.OpenRouterDefaults.STT_MODEL,
                openRouterSummaryModel = preferences[OPENROUTER_SUMMARY_MODEL]
                    ?: fr.luteal.core.openrouter.OpenRouterDefaults.SUMMARY_MODEL,
                openRouterSttLanguage = preferences[OPENROUTER_STT_LANGUAGE]
                    ?: fr.luteal.core.openrouter.OpenRouterDefaults.STT_LANGUAGE,
                openRouterSttDiarizationEnabled = preferences[OPENROUTER_STT_DIARIZATION] ?: false,
                openRouterAiEnabled = preferences[OPENROUTER_AI_ENABLED] ?: false
            )
        }

    suspend fun setSyncMode(mode: String) = edit { it[SYNC_MODE] = mode }

    suspend fun setUserRole(role: String) = edit { it[USER_ROLE] = role }

    suspend fun setLocale(locale: String) = edit { it[LOCALE] = locale }
    suspend fun setCompletedOnboarding(completed: Boolean) = edit { it[COMPLETED_ONBOARDING] = completed }

    suspend fun setDisorderTracking(disorderId: String, enabled: Boolean) = edit { preferences ->
        when (disorderId.lowercase().removePrefix("track_")) {
            "pmdd" -> preferences[TRACK_PMDD] = enabled
            "pms" -> preferences[TRACK_PMS] = enabled
            "endometriosis" -> preferences[TRACK_ENDOMETRIOSIS] = enabled
            "pcos" -> preferences[TRACK_PCOS] = enabled
            "perimenopause" -> preferences[TRACK_PERIMENOPAUSE] = enabled
            "thyroid" -> preferences[TRACK_THYROID] = enabled
        }
    }

    /** Null clears a previously declared band. */
    suspend fun setAgeBand(ageBandId: String?) = edit { preferences ->
        if (ageBandId == null) preferences.remove(AGE_BAND)
        else preferences[AGE_BAND] = ageBandId
    }

    suspend fun setDuoSharing(field: DuoSharingField, enabled: Boolean) = edit { preferences ->
        preferences[field.preferenceKey()] = enabled
    }

    suspend fun setCouplePairingCode(code: String?) = edit { preferences ->
        if (code == null) preferences.remove(COUPLE_PAIRING_CODE)
        else preferences[COUPLE_PAIRING_CODE] = code
    }


    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[IS_APP_LOCK_ENABLED] = enabled }
    suspend fun setBiometricEnabled(enabled: Boolean) = edit { it[IS_BIOMETRIC_ENABLED] = enabled }
    suspend fun setAutoLockTimeout(timeout: String) = edit { it[AUTO_LOCK_TIMEOUT] = timeout }
    suspend fun setScreenMaskingEnabled(enabled: Boolean) = edit { it[IS_SCREEN_MASKING_ENABLED] = enabled }
    suspend fun setConsecutivePinFailures(count: Int) = edit { it[CONSECUTIVE_PIN_FAILURES] = count }
    suspend fun setLockoutUntilEpochMillis(timestampMillis: Long) = edit { it[LOCKOUT_UNTIL_EPOCH_MILLIS] = timestampMillis }

    suspend fun setLockoutUntilElapsedRealtimeMillis(elapsedMillis: Long) =
        edit { it[LOCKOUT_UNTIL_ELAPSED_REALTIME_MILLIS] = elapsedMillis }
    suspend fun resetPinFailures() = edit {
        it[CONSECUTIVE_PIN_FAILURES] = 0
        it[LOCKOUT_UNTIL_EPOCH_MILLIS] = 0L
        it[LOCKOUT_UNTIL_ELAPSED_REALTIME_MILLIS] = 0L
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[NOTIF_ENABLED] = enabled }
    suspend fun setDailyCheckInEnabled(enabled: Boolean) = edit { it[NOTIF_DAILY_ENABLED] = enabled }
    suspend fun setDailyCheckInTime(time: String) = edit { it[NOTIF_DAILY_TIME] = time }
    suspend fun setPeriodWindowNotificationEnabled(enabled: Boolean) = edit { it[NOTIF_WINDOW_ENABLED] = enabled }
    suspend fun setPeriodWindowLeadDays(days: Int) = edit { it[NOTIF_WINDOW_LEAD_DAYS] = days }
    suspend fun setLateCycleNotificationEnabled(enabled: Boolean) = edit { it[NOTIF_LATE_ENABLED] = enabled }
    suspend fun setLateCycleGraceDays(days: Int) = edit { it[NOTIF_LATE_GRACE_DAYS] = days }
    suspend fun setNotificationVisibilityMode(mode: String) = edit { it[NOTIF_VISIBILITY_MODE] = mode }
    suspend fun setNotificationCustomTitle(title: String) = edit { it[NOTIF_CUSTOM_TITLE] = title }
    suspend fun setNotificationCustomBody(body: String) = edit { it[NOTIF_CUSTOM_BODY] = body }
    suspend fun setTemperatureUnit(unit: String) = edit { it[TEMPERATURE_UNIT] = unit }
    suspend fun setThemeMode(mode: String) = edit { it[THEME_MODE] = mode }
    suspend fun setColorPalette(palette: String) = edit { it[COLOR_PALETTE] = palette }
    suspend fun setMedicationsSeedOffered(offered: Boolean) = edit { it[MEDICATIONS_SEED_OFFERED] = offered }
    suspend fun setCareTypesSeedOffered(offered: Boolean) = edit { it[CARE_TYPES_SEED_OFFERED] = offered }
    suspend fun setPainFollowupDelayMinutes(minutes: Int) = edit { it[PAIN_FOLLOWUP_DELAY_MINUTES] = minutes }
    suspend fun setAutoBackupEnabled(enabled: Boolean) = edit { it[AUTO_BACKUP_ENABLED] = enabled }
    suspend fun setAutoBackupUri(uri: String) = edit { it[AUTO_BACKUP_URI] = uri }
    suspend fun setAutoBackupHour(hour: String) = edit { it[AUTO_BACKUP_HOUR] = hour }
    suspend fun setAutoBackupKeep(keep: Int) = edit { it[AUTO_BACKUP_KEEP] = keep }
    suspend fun setAutoBackupStatus(success: String, error: String) = edit {
        it[AUTO_BACKUP_LAST_SUCCESS] = success
        it[AUTO_BACKUP_LAST_ERROR] = error
    }
    suspend fun setHideDyspareuniaInRecap(hidden: Boolean) = edit { it[HIDE_DYSPAREUNIA_IN_RECAP] = hidden }
    suspend fun setHideLibidoInRecap(hidden: Boolean) = edit { it[HIDE_LIBIDO_IN_RECAP] = hidden }
    suspend fun setBackupEncryptionEnabled(enabled: Boolean) = edit { it[BACKUP_ENCRYPTION_ENABLED] = enabled }
    suspend fun setCalendarImportEnabled(enabled: Boolean) = edit { it[CALENDAR_IMPORT_ENABLED] = enabled }
    suspend fun setCalendarImportKeywords(keywords: String) = edit { it[CALENDAR_IMPORT_KEYWORDS] = keywords }
    suspend fun setCalendarSelectedIds(ids: Set<String>) = edit {
        it[CALENDAR_SELECTED_IDS] = ids.joinToString(",")
    }
    suspend fun setOpenRouterSttModel(model: String) = edit { it[OPENROUTER_STT_MODEL] = model }
    suspend fun setOpenRouterSummaryModel(model: String) = edit { it[OPENROUTER_SUMMARY_MODEL] = model }
    suspend fun setOpenRouterSttLanguage(language: String) = edit { it[OPENROUTER_STT_LANGUAGE] = language }
    suspend fun setOpenRouterSttDiarizationEnabled(enabled: Boolean) =
        edit { it[OPENROUTER_STT_DIARIZATION] = enabled }
    suspend fun setOpenRouterAiEnabled(enabled: Boolean) = edit { it[OPENROUTER_AI_ENABLED] = enabled }
    suspend fun clear() = edit { it.clear() }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun DuoSharingField.preferenceKey() = when (this) {
        DuoSharingField.CYCLE_DAY -> SHARE_CYCLE_DAY
        DuoSharingField.PERIOD_ESTIMATE -> SHARE_PERIOD_ESTIMATE
        DuoSharingField.MOOD -> SHARE_MOOD
        DuoSharingField.ENERGY -> SHARE_ENERGY
        DuoSharingField.SUPPORT_REQUESTS -> SHARE_SUPPORT_REQUESTS
    }
}
