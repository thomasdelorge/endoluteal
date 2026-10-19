package fr.luteal.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

object EndolutealBackup {
    const val FORMAT = "endoluteal/v1"
}

@Serializable
data class LutealBackupPayload(
    @SerialName("backup_format") val backupFormat: String? = null,
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("cycles") val cycles: List<CycleBackupDto> = emptyList(),
    @SerialName("daily_entries") val dailyEntries: List<DailyEntryBackupDto> = emptyList(),
    @SerialName("symptom_logs") val symptomLogs: List<SymptomLogBackupDto> = emptyList(),
    @SerialName("biomarker_observations") val biomarkerObservations: List<BiomarkerBackupDto> = emptyList(),
    @SerialName("journal_events") val journalEvents: List<JournalEventBackupDto> = emptyList(),
    @SerialName("pain_marks") val painMarks: List<PainMarkBackupDto> = emptyList(),
    @SerialName("substances") val substances: List<SubstanceBackupDto> = emptyList(),
    @SerialName("medications") val medications: List<MedicationBackupDto> = emptyList(),
    @SerialName("interaction_rules") val interactionRules: List<InteractionRuleBackupDto> = emptyList(),
    @SerialName("medication_intakes") val medicationIntakes: List<MedicationIntakeBackupDto> = emptyList(),
    @SerialName("treatment_plans") val treatmentPlans: List<TreatmentPlanBackupDto> = emptyList(),
    @SerialName("care_types") val careTypes: List<CareTypeBackupDto> = emptyList(),
    @SerialName("professionals") val professionals: List<ProfessionalBackupDto> = emptyList(),
    @SerialName("appointments") val appointments: List<AppointmentBackupDto> = emptyList(),
    @SerialName("daily_recaps") val dailyRecaps: List<DailyRecapBackupDto> = emptyList(),
    @SerialName("questionnaire_results") val questionnaireResults: List<QuestionnaireResultBackupDto> = emptyList(),
    @SerialName("preferences") val preferences: UserPreferencesBackupDto = UserPreferencesBackupDto()
)

@Serializable
data class QuestionnaireResultBackupDto(
    @SerialName("id") val id: String,
    @SerialName("period") val period: String,
    @SerialName("answers_json") val answersJson: String,
    @SerialName("score") val score: Int,
    @SerialName("completed_at") val completedAt: String
)

@Serializable
data class CycleBackupDto(
    @SerialName("id") val id: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("average_length_days") val averageLengthDays: Int = 28,
    @SerialName("luteal_phase_length_days") val lutealPhaseLengthDays: Int = 14,
    @SerialName("period_days") val periodDays: List<PeriodDayBackupDto> = emptyList(),
    @SerialName("is_excluded_from_estimates") val isExcludedFromEstimates: Boolean = false,
    @SerialName("exclusion_reason") val exclusionReason: String? = null
)

@Serializable
data class PeriodDayBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bleeding_intensity") val bleedingIntensity: String,
    @SerialName("notes") val notes: String = "",
    @SerialName("symptom_ids") val symptomIds: List<String> = emptyList()
)

@Serializable
data class DailyEntryBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bleeding_intensity") val bleedingIntensity: String? = null,
    @SerialName("pain_level") val painLevel: Int? = null,
    @SerialName("mood_level") val moodLevel: Int? = null,
    @SerialName("energy_level") val energyLevel: Int? = null,
    @SerialName("symptom_ids") val symptomIds: List<String> = emptyList(),
    @SerialName("notes") val notes: String = "",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class SymptomLogBackupDto(
    @SerialName("id") val id: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("date") val date: String,
    @SerialName("symptom_id") val symptomId: String,
    @SerialName("severity") val severity: Int,
    @SerialName("notes") val notes: String = ""
)

@Serializable
data class BiomarkerBackupDto(
    @SerialName("date") val date: String,
    @SerialName("bbt_celsius") val bbtCelsius: Double? = null,
    @SerialName("bbt_time") val bbtTime: String? = null,
    @SerialName("bbt_quality") val bbtQuality: String = "normal",
    @SerialName("bbt_disturbances") val bbtDisturbances: List<String> = emptyList(),
    @SerialName("cervical_sensation") val cervicalSensation: String? = null,
    @SerialName("cervical_texture") val cervicalTexture: String? = null,
    @SerialName("lh_test_result") val lhTestResult: String? = null,
    @SerialName("hcg_test_result") val hcgTestResult: String? = null,
    @SerialName("notes") val notes: String = "",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class JournalEventBackupDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("zone_id") val zoneId: String,
    @SerialName("notes") val notes: String = "",
    @SerialName("linked_event_id") val linkedEventId: String? = null,
    @SerialName("payload") val payload: JsonElement,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class PainMarkBackupDto(
    @SerialName("id") val id: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("zone") val zone: String,
    @SerialName("intensity") val intensity: Int,
    @SerialName("pain_types") val painTypes: List<String> = emptyList(),
    @SerialName("pin_side") val pinSide: String? = null,
    @SerialName("pin_x") val pinX: Float? = null,
    @SerialName("pin_y") val pinY: Float? = null
)

@Serializable
data class SubstanceBackupDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("drug_class") val drugClass: String,
    @SerialName("max_daily_mg") val maxDailyMg: Double? = null,
    @SerialName("max_per_intake_mg") val maxPerIntakeMg: Double? = null,
    @SerialName("min_interval_minutes") val minIntervalMinutes: Int? = null,
    @SerialName("validated") val validated: Boolean = false,
    @SerialName("notes") val notes: String = ""
)

@Serializable
data class MedicationBackupDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("form") val form: String = "",
    @SerialName("contraindications") val contraindications: String = "",
    @SerialName("archived") val archived: Boolean = false,
    @SerialName("compositions") val compositions: List<CompositionBackupDto> = emptyList(),
    @SerialName("contraindicated_substance_ids") val contraindicatedSubstanceIds: List<String> = emptyList(),
    @SerialName("repeat_every_hours") val repeatEveryHours: Int? = null
)

@Serializable
data class CompositionBackupDto(
    @SerialName("substance_id") val substanceId: String,
    @SerialName("amount_mg_per_unit") val amountMgPerUnit: Double
)

@Serializable
data class InteractionRuleBackupDto(
    @SerialName("id") val id: String,
    @SerialName("target_a_type") val targetAType: String,
    @SerialName("target_a_id") val targetAId: String,
    @SerialName("target_b_type") val targetBType: String,
    @SerialName("target_b_id") val targetBId: String,
    @SerialName("severity") val severity: String,
    @SerialName("message") val message: String
)

@Serializable
data class TreatmentPlanBackupDto(
    @SerialName("id") val id: String,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("units_per_dose") val unitsPerDose: Double,
    @SerialName("times_of_day") val timesOfDay: List<String> = emptyList(),
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("active") val active: Boolean = true,
    @SerialName("continuous_hormonal") val continuousHormonal: Boolean = false
)

@Serializable
data class CareTypeBackupDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("programs") val programs: List<CareProgramBackupDto> = emptyList(),
    @SerialName("archived") val archived: Boolean = false
)

@Serializable
data class CareProgramBackupDto(
    @SerialName("name") val name: String,
    @SerialName("default_duration_minutes") val defaultDurationMinutes: Int
)

@Serializable
data class DailyRecapBackupDto(
    @SerialName("date") val date: String,
    @SerialName("cycle") val cycle: String,
    @SerialName("pain") val pain: String,
    @SerialName("psy") val psy: String,
    @SerialName("general") val general: String,
    @SerialName("bowel") val bowel: String,
    @SerialName("completed_steps") val completedSteps: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ProfessionalBackupDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("specialty") val specialty: String,
    @SerialName("custom_specialty") val customSpecialty: String = "",
    @SerialName("place") val place: String = "",
    @SerialName("phone") val phone: String = "",
    @SerialName("notes") val notes: String = ""
)

@Serializable
data class AppointmentBackupDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("professional_id") val professionalId: String,
    @SerialName("calendar_event_key") val calendarEventKey: String? = null,
    @SerialName("location") val location: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("report") val report: String = "",
    @SerialName("report_status") val reportStatus: String = "PENDING",
    @SerialName("title_edited") val titleEdited: Boolean = false,
    @SerialName("location_edited") val locationEdited: Boolean = false,
    @SerialName("fee_cents") val feeCents: Int? = null,
    @SerialName("reimbursement_status") val reimbursementStatus: String = "",
    @SerialName("cpam_cents") val cpamCents: Int? = null,
    @SerialName("mutuelle_cents") val mutuelleCents: Int? = null
)

@Serializable
data class MedicationIntakeBackupDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("units") val units: Double
)

@Serializable
data class UserPreferencesBackupDto(
    @SerialName("user_role") val userRole: String = "PRIMARY_TRACKER",
    @SerialName("locale") val locale: String = "fr",
    @SerialName("track_pmdd") val trackPmdd: Boolean = false,
    @SerialName("track_pms") val trackPms: Boolean = false,
    @SerialName("track_endometriosis") val trackEndometriosis: Boolean = false,
    @SerialName("track_pcos") val trackPcos: Boolean = false,
    @SerialName("track_perimenopause") val trackPerimenopause: Boolean = false,
    @SerialName("track_thyroid") val trackThyroid: Boolean = false,
    @SerialName("age_band") val ageBand: String? = null,
    @SerialName("temperature_unit") val temperatureUnit: String = "CELSIUS",
    @SerialName("theme_mode") val themeMode: String = "SYSTEM",
    @SerialName("color_palette") val colorPalette: String = "EVERGREEN"
)

enum class ImportStrategy {
    MERGE_UPSERT,
    REPLACE_ALL
}

data class LutealBackupPreview(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val cycleCount: Int,
    val earliestCycleDate: String?,
    val latestCycleDate: String?,
    val dailyEntryCount: Int,
    val earliestEntryDate: String?,
    val latestEntryDate: String?,
    val symptomLogCount: Int,
    val biomarkerCount: Int = 0,
    val journalEventCount: Int = 0,
    val painMarkCount: Int = 0,
    val substanceCount: Int = 0,
    val medicationCount: Int = 0,
    val interactionRuleCount: Int = 0,
    val medicationIntakeCount: Int = 0,
    val preferences: UserPreferencesBackupDto
)

data class ImportSummary(
    val cyclesImported: Int,
    val dailyEntriesImported: Int,
    val symptomLogsImported: Int,
    val biomarkersImported: Int = 0,
    val journalEventsImported: Int = 0,
    val painMarksImported: Int = 0,
    val substancesImported: Int = 0,
    val medicationsImported: Int = 0,
    val interactionRulesImported: Int = 0,
    val medicationIntakesImported: Int = 0,
    val preferencesRestored: Boolean
)

sealed class DataImportError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidJsonSyntax : DataImportError("Invalid JSON syntax")
    data object EncryptedBackupNeedsPassword : DataImportError("Encrypted backup needs password")
    data object WrongBackupPassword : DataImportError("Wrong backup password")
    data class UnsupportedSchemaVersion(val version: Int) : DataImportError("Unsupported schema version: $version")
    data class CorruptedPayload(val detail: String) : DataImportError("Corrupted payload: $detail")
    data class IoError(val detail: String) : DataImportError("I/O error: $detail")
}
