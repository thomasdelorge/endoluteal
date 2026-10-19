package fr.luteal.core.data

import androidx.room.withTransaction
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.AppointmentEntity
import fr.luteal.core.data.entity.CareTypeEntity
import fr.luteal.core.data.backup.BackupCrypto
import fr.luteal.core.data.entity.DailyRecapEntity
import fr.luteal.core.data.entity.QuestionnaireResultEntity
import fr.luteal.core.data.entity.ProfessionalEntity
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.InteractionRuleEntity
import fr.luteal.core.data.entity.JournalEventEntity
import fr.luteal.core.data.entity.MedicationEntity
import fr.luteal.core.data.entity.MedicationIntakeEntity
import fr.luteal.core.data.entity.MedicationSubstanceEntity
import fr.luteal.core.data.entity.PainMarkEntity
import fr.luteal.core.data.entity.SubstanceEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.entity.TreatmentPlanEntity
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.AppointmentDao
import fr.luteal.core.data.local.DailyRecapDao
import fr.luteal.core.data.local.QuestionnaireResultDao
import fr.luteal.core.data.local.CareTypeDao
import fr.luteal.core.data.local.ProfessionalDao
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.JournalEventDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.MedicationDao
import fr.luteal.core.data.local.MedicationIntakeDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.local.TreatmentPlanDao
import fr.luteal.core.model.DataImportError
import fr.luteal.core.model.EndolutealBackup
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.ImportSummary
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.LutealBackupPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataImportManager @Inject constructor(
    private val database: LutealDatabase,
    private val cycleDao: CycleDao,
    private val dailyEntryDao: DailyEntryDao,
    private val symptomDao: SymptomDao,
    private val biomarkerDao: BiomarkerDao,
    private val journalEventDao: JournalEventDao,
    private val medicationDao: MedicationDao,
    private val medicationIntakeDao: MedicationIntakeDao,
    private val treatmentPlanDao: TreatmentPlanDao,
    private val careTypeDao: CareTypeDao,
    private val professionalDao: ProfessionalDao,
    private val appointmentDao: AppointmentDao,
    private val dailyRecapDao: DailyRecapDao,
    private val questionnaireResultDao: QuestionnaireResultDao,
    private val userPreferencesDataStore: UserPreferencesDataStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun inspectBackup(
        inputStream: InputStream,
        decryptionPassword: CharArray? = null
    ): Result<Pair<LutealBackupPreview, LutealBackupPayload>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = inputStream.readBytes()
                val jsonString = decodeBackupText(raw, decryptionPassword)

                val payload = try {
                    json.decodeFromString<LutealBackupPayload>(jsonString)
                } catch (e: SerializationException) {
                    throw DataImportError.InvalidJsonSyntax
                } catch (e: IllegalArgumentException) {
                    throw DataImportError.InvalidJsonSyntax
                }

                validateBackup(payload)
                validateDates(payload)

                val cycleDates = payload.cycles.mapNotNull { it.startDate.takeIf { d -> d.isNotBlank() } }.sorted()
                val entryDates = payload.dailyEntries.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }.sorted()

                val preview = LutealBackupPreview(
                    schemaVersion = payload.schemaVersion,
                    exportedAt = payload.exportedAt,
                    appVersion = payload.appVersion,
                    cycleCount = payload.cycles.size,
                    earliestCycleDate = cycleDates.firstOrNull(),
                    latestCycleDate = cycleDates.lastOrNull(),
                    dailyEntryCount = payload.dailyEntries.size,
                    earliestEntryDate = entryDates.firstOrNull(),
                    latestEntryDate = entryDates.lastOrNull(),
                    symptomLogCount = payload.symptomLogs.size,
                    biomarkerCount = payload.biomarkerObservations.size,
                    journalEventCount = payload.journalEvents.size,
                    painMarkCount = payload.painMarks.size,
                    substanceCount = payload.substances.size,
                    medicationCount = payload.medications.size,
                    interactionRuleCount = payload.interactionRules.size,
                    medicationIntakeCount = payload.medicationIntakes.size,
                    preferences = payload.preferences
                )

                Pair(preview, payload)
            }
        }
    }

    suspend fun restoreBackup(payload: LutealBackupPayload, strategy: ImportStrategy): Result<ImportSummary> {
        return withContext(Dispatchers.IO) {
            runCatching {
                validateBackup(payload)
                validateDates(payload)

                var cyclesCount = 0
                var entriesCount = 0
                var symptomsCount = 0
                var biomarkersCount = 0
                var journalEventsCount = 0
                var painMarksCount = 0
                var substancesCount = 0
                var medicationsCount = 0
                var rulesCount = 0
                var intakesCount = 0

                database.withTransaction {
                    if (strategy == ImportStrategy.REPLACE_ALL) {
                        cycleDao.deleteAllCycles()
                        dailyEntryDao.deleteAllEntries()
                        symptomDao.deleteAllSymptomLogs()
                        biomarkerDao.deleteAll()
                        journalEventDao.deleteAllMarks()
                        journalEventDao.deleteAllEvents()
                        medicationDao.deleteAllCompositions()
                        medicationDao.deleteAllContraindications()
                        medicationDao.deleteAllRules()
                        medicationDao.deleteAllMedications()
                        medicationDao.deleteAllSubstances()
                        medicationIntakeDao.deleteAll()
                        treatmentPlanDao.deleteAll()
                        careTypeDao.deleteAll()
                        appointmentDao.deleteAll()
                        professionalDao.deleteAll()
                        dailyRecapDao.deleteAll()
                    }

                    val now = System.currentTimeMillis()

                    for (cycleDto in payload.cycles) {
                        if (cycleDto.id.isBlank() || cycleDto.startDate.isBlank()) continue

                        val periodDaysJsonArr = JSONArray()
                        for (pd in cycleDto.periodDays) {
                            val obj = JSONObject()
                            obj.put("date", pd.date)
                            obj.put("bleedingIntensity", pd.bleedingIntensity)
                            obj.put("notes", pd.notes)
                            val symptomsArr = JSONArray()
                            for (s in pd.symptomIds) {
                                symptomsArr.put(s)
                            }
                            obj.put("symptomIds", symptomsArr)
                            periodDaysJsonArr.put(obj)
                        }

                        cycleDao.insertCycle(
                            CycleEntity(
                                id = cycleDto.id,
                                startDate = cycleDto.startDate,
                                endDate = cycleDto.endDate,
                                averageLengthDays = cycleDto.averageLengthDays,
                                lutealPhaseLengthDays = cycleDto.lutealPhaseLengthDays,
                                periodDaysJson = periodDaysJsonArr.toString(),
                                isExcludedFromEstimates = cycleDto.isExcludedFromEstimates,
                                exclusionReason = cycleDto.exclusionReason
                            )
                        )
                        cyclesCount++
                    }

                    for (entryDto in payload.dailyEntries) {
                        if (entryDto.date.isBlank()) continue

                        val symptomsArr = JSONArray()
                        for (s in entryDto.symptomIds) {
                            symptomsArr.put(s)
                        }

                        val updatedAtMillis = runCatching {
                            Instant.parse(entryDto.updatedAt).toEpochMilli()
                        }.getOrDefault(now)

                        val entity = DailyEntryEntity(
                            date = entryDto.date,
                            bleedingIntensity = entryDto.bleedingIntensity,
                            painLevel = entryDto.painLevel?.coerceIn(1, 5),
                            moodLevel = entryDto.moodLevel?.coerceIn(1, 5),
                            energyLevel = entryDto.energyLevel?.coerceIn(1, 5),
                            symptomIdsJson = symptomsArr.toString(),
                            notes = entryDto.notes,
                            updatedAtEpochMillis = updatedAtMillis
                        )

                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = dailyEntryDao.getEntryOnce(entryDto.date)
                            if (existing == null || updatedAtMillis >= existing.updatedAtEpochMillis) {
                                dailyEntryDao.upsert(entity)
                                entriesCount++
                            }
                        } else {
                            dailyEntryDao.upsert(entity)
                            entriesCount++
                        }
                    }

                    for (logDto in payload.symptomLogs) {
                        if (logDto.id.isBlank() || logDto.date.isBlank()) continue

                        val timestampMillis = runCatching {
                            Instant.parse(logDto.timestamp).toEpochMilli()
                        }.getOrDefault(now)

                        val entity = SymptomLogEntity(
                            id = logDto.id,
                            timestampEpochMillis = timestampMillis,
                            date = logDto.date,
                            symptomId = logDto.symptomId,
                            severity = logDto.severity.coerceIn(0, 5),
                            notes = logDto.notes
                        )

                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = symptomDao.getSymptomLogOnce(logDto.id)
                            if (existing != null && timestampMillis < existing.timestampEpochMillis) {
                                continue
                            }
                        }

                        symptomDao.insertSymptomLog(entity)
                        symptomsCount++
                    }

                    for (biomarkerDto in payload.biomarkerObservations) {
                        if (biomarkerDto.date.isBlank()) continue
                        val updatedAtMillis = runCatching {
                            Instant.parse(biomarkerDto.updatedAt).toEpochMilli()
                        }.getOrDefault(now)
                        val disturbances = JSONArray()
                        for (item in biomarkerDto.bbtDisturbances) {
                            disturbances.put(item)
                        }
                        val entity = BiomarkerObservationEntity(
                            date = biomarkerDto.date,
                            bbtCelsius = biomarkerDto.bbtCelsius,
                            bbtTime = biomarkerDto.bbtTime,
                            bbtQuality = biomarkerDto.bbtQuality,
                            bbtDisturbancesJson = disturbances.toString(),
                            cervicalSensation = biomarkerDto.cervicalSensation,
                            cervicalTexture = biomarkerDto.cervicalTexture,
                            lhTestResult = biomarkerDto.lhTestResult,
                            hcgTestResult = biomarkerDto.hcgTestResult,
                            notes = biomarkerDto.notes,
                            updatedAtEpochMillis = updatedAtMillis
                        )
                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = biomarkerDao.getObservationOnce(biomarkerDto.date)
                            if (existing == null || updatedAtMillis >= existing.updatedAtEpochMillis) {
                                biomarkerDao.upsert(entity)
                                biomarkersCount++
                            }
                        } else {
                            biomarkerDao.upsert(entity)
                            biomarkersCount++
                        }
                    }

                    val marksByEvent = payload.painMarks.groupBy { it.eventId }
                    for (eventDto in payload.journalEvents) {
                        if (eventDto.id.isBlank()) continue
                        val updatedAtMillis = runCatching {
                            Instant.parse(eventDto.updatedAt).toEpochMilli()
                        }.getOrDefault(now)
                        val createdAtMillis = runCatching {
                            Instant.parse(eventDto.createdAt).toEpochMilli()
                        }.getOrDefault(updatedAtMillis)
                        val entity = JournalEventEntity(
                            id = eventDto.id,
                            type = eventDto.type,
                            startAt = Instant.parse(eventDto.startAt).toEpochMilli(),
                            endAt = eventDto.endAt?.let { Instant.parse(it).toEpochMilli() },
                            zoneId = eventDto.zoneId,
                            notes = eventDto.notes,
                            linkedEventId = eventDto.linkedEventId,
                            payloadJson = eventDto.payload.toPayloadJson(),
                            createdAt = createdAtMillis,
                            updatedAt = updatedAtMillis
                        )
                        if (strategy == ImportStrategy.MERGE_UPSERT) {
                            val existing = journalEventDao.getEventOnce(eventDto.id)
                            if (existing != null && updatedAtMillis < existing.updatedAt) continue
                        }
                        val marks = marksByEvent[eventDto.id].orEmpty().mapNotNull { mark ->
                            if (mark.id.isBlank() || mark.intensity !in 0..10) return@mapNotNull null
                            val types = JSONArray()
                            mark.painTypes.forEach(types::put)
                            PainMarkEntity(
                                id = mark.id,
                                eventId = eventDto.id,
                                zone = mark.zone,
                                intensity = mark.intensity,
                                painTypes = types.toString(),
                                pinSide = mark.pinSide,
                                pinX = mark.pinX,
                                pinY = mark.pinY
                            )
                        }
                        journalEventDao.upsert(entity, marks)
                        journalEventsCount++
                        painMarksCount += marks.size
                    }

                    for (substance in payload.substances) {
                        if (substance.id.isBlank()) continue
                        medicationDao.upsertSubstance(
                            SubstanceEntity(
                                id = substance.id,
                                name = substance.name,
                                drugClass = substance.drugClass,
                                maxDailyMg = substance.maxDailyMg,
                                maxPerIntakeMg = substance.maxPerIntakeMg,
                                minIntervalMinutes = substance.minIntervalMinutes,
                                validated = substance.validated,
                                notes = substance.notes
                            )
                        )
                        substancesCount++
                    }
                    for (medication in payload.medications) {
                        if (medication.id.isBlank()) continue
                        medicationDao.replaceMedication(
                            MedicationEntity(
                                id = medication.id,
                                name = medication.name,
                                form = medication.form,
                                contraindications = medication.contraindications,
                                archived = medication.archived
                            ),
                            medication.compositions.map { composition ->
                                MedicationSubstanceEntity(
                                    medicationId = medication.id,
                                    substanceId = composition.substanceId,
                                    amountMgPerUnit = composition.amountMgPerUnit
                                )
                            },
                            medication.contraindicatedSubstanceIds.map { substanceId ->
                                fr.luteal.core.data.entity.MedicationContraindicationEntity(
                                    medication.id,
                                    substanceId
                                )
                            }
                        )
                        medicationsCount++
                    }
                    for (rule in payload.interactionRules) {
                        if (rule.id.isBlank()) continue
                        medicationDao.upsertRule(
                            InteractionRuleEntity(
                                id = rule.id,
                                targetAType = rule.targetAType,
                                targetAId = rule.targetAId,
                                targetBType = rule.targetBType,
                                targetBId = rule.targetBId,
                                severity = rule.severity,
                                message = rule.message
                            )
                        )
                        rulesCount++
                    }
                    for (intake in payload.medicationIntakes) {
                        if (intake.eventId.isBlank() || intake.medicationId.isBlank()) continue
                        medicationIntakeDao.upsert(
                            MedicationIntakeEntity(intake.eventId, intake.medicationId, intake.units)
                        )
                        intakesCount++
                    }
                    for (plan in payload.treatmentPlans) {
                        if (plan.id.isBlank() || plan.medicationId.isBlank()) continue
                        val times = JSONArray()
                        plan.timesOfDay.forEach(times::put)
                        treatmentPlanDao.upsert(
                            TreatmentPlanEntity(
                                id = plan.id,
                                medicationId = plan.medicationId,
                                unitsPerDose = plan.unitsPerDose,
                                timesOfDay = times.toString(),
                                startDate = plan.startDate,
                                endDate = plan.endDate,
                                active = plan.active,
                                continuousHormonal = plan.continuousHormonal
                            )
                        )
                    }
                    for (careType in payload.careTypes) {
                        if (careType.id.isBlank()) continue
                        val programs = JSONArray()
                        careType.programs.forEach { program ->
                            programs.put(
                                JSONObject()
                                    .put("name", program.name)
                                    .put("defaultDurationMinutes", program.defaultDurationMinutes)
                            )
                        }
                        careTypeDao.upsert(
                            CareTypeEntity(
                                id = careType.id,
                                name = careType.name,
                                programsJson = programs.toString(),
                                archived = careType.archived
                            )
                        )
                    }
                    for (professional in payload.professionals) {
                        if (professional.id.isBlank()) continue
                        professionalDao.upsert(
                            ProfessionalEntity(
                                id = professional.id,
                                name = professional.name,
                                specialty = professional.specialty,
                                customSpecialty = professional.customSpecialty,
                                place = professional.place,
                                phone = professional.phone,
                                notes = professional.notes
                            )
                        )
                    }
                    for (appointment in payload.appointments) {
                        if (appointment.eventId.isBlank() || appointment.professionalId.isBlank()) continue
                        appointmentDao.upsert(
                            AppointmentEntity(
                                eventId = appointment.eventId,
                                professionalId = appointment.professionalId,
                                calendarEventKey = appointment.calendarEventKey,
                                location = appointment.location,
                                description = appointment.description,
                                report = appointment.report,
                                reportStatus = appointment.reportStatus,
                                titleEdited = appointment.titleEdited,
                                locationEdited = appointment.locationEdited,
                                feeCents = appointment.feeCents,
                                reimbursementStatus = appointment.reimbursementStatus,
                                cpamCents = appointment.cpamCents,
                                mutuelleCents = appointment.mutuelleCents
                            )
                        )
                    }
                    for (result in payload.questionnaireResults) {
                        if (result.period.isBlank()) continue
                        questionnaireResultDao.upsert(
                            QuestionnaireResultEntity(
                                id = result.id,
                                period = result.period,
                                answersJson = result.answersJson,
                                score = result.score,
                                completedAtEpochMillis = runCatching {
                                    Instant.parse(result.completedAt).toEpochMilli()
                                }.getOrDefault(System.currentTimeMillis())
                            )
                        )
                    }
                    for (recap in payload.dailyRecaps) {
                        if (recap.date.isBlank()) continue
                        val steps = JSONArray()
                        recap.completedSteps.forEach(steps::put)
                        dailyRecapDao.upsert(
                            DailyRecapEntity(
                                date = recap.date,
                                cycleJson = recap.cycle,
                                painJson = recap.pain,
                                psyJson = recap.psy,
                                generalJson = recap.general,
                                bowelJson = recap.bowel,
                                completedStepsJson = steps.toString(),
                                updatedAtEpochMillis = runCatching {
                                    Instant.parse(recap.updatedAt).toEpochMilli()
                                }.getOrDefault(System.currentTimeMillis())
                            )
                        )
                    }
                }

                var preferencesRestored = false
                if (strategy == ImportStrategy.REPLACE_ALL) {
                    val prefs = payload.preferences
                    userPreferencesDataStore.setUserRole(prefs.userRole)
                    userPreferencesDataStore.setLocale(prefs.locale)
                    userPreferencesDataStore.setDisorderTracking("pmdd", prefs.trackPmdd)
                    userPreferencesDataStore.setDisorderTracking("pms", prefs.trackPms)
                    userPreferencesDataStore.setDisorderTracking("endometriosis", prefs.trackEndometriosis)
                    userPreferencesDataStore.setDisorderTracking("pcos", prefs.trackPcos)
                    userPreferencesDataStore.setDisorderTracking("perimenopause", prefs.trackPerimenopause)
                    userPreferencesDataStore.setDisorderTracking("thyroid", prefs.trackThyroid)
                    userPreferencesDataStore.setAgeBand(prefs.ageBand)
                    userPreferencesDataStore.setTemperatureUnit(prefs.temperatureUnit)
                    userPreferencesDataStore.setThemeMode(prefs.themeMode)
                    userPreferencesDataStore.setColorPalette(prefs.colorPalette)
                    userPreferencesDataStore.setCompletedOnboarding(true)
                    preferencesRestored = true
                }

                ImportSummary(
                    cyclesImported = cyclesCount,
                    dailyEntriesImported = entriesCount,
                    symptomLogsImported = symptomsCount,
                    biomarkersImported = biomarkersCount,
                    journalEventsImported = journalEventsCount,
                    painMarksImported = painMarksCount,
                    substancesImported = substancesCount,
                    medicationsImported = medicationsCount,
                    interactionRulesImported = rulesCount,
                    medicationIntakesImported = intakesCount,
                    preferencesRestored = preferencesRestored
                )
            }
        }
    }

    private fun decodeBackupText(raw: ByteArray, decryptionPassword: CharArray?): String {
        if (raw.isEmpty()) throw DataImportError.InvalidJsonSyntax
        if (BackupCrypto.isEncrypted(raw)) {
            if (decryptionPassword == null) throw DataImportError.EncryptedBackupNeedsPassword
            return try {
                BackupCrypto.decrypt(raw, decryptionPassword).decodeToString()
            } catch (_: Exception) {
                throw DataImportError.WrongBackupPassword
            }
        }
        return raw.decodeToString()
    }

    private fun validateBackup(payload: LutealBackupPayload) {
        if (payload.backupFormat != EndolutealBackup.FORMAT) {
            throw DataImportError.UnsupportedSchemaVersion(payload.schemaVersion)
        }
        if (payload.schemaVersion != 1) {
            throw DataImportError.UnsupportedSchemaVersion(payload.schemaVersion)
        }
    }

    private fun validateDates(payload: LutealBackupPayload) {
        fun requireDate(raw: String?, field: String) {
            if (raw.isNullOrBlank()) return
            runCatching { LocalDate.parse(raw) }.getOrElse {
                throw DataImportError.CorruptedPayload("unparseable date '$raw' in $field")
            }
        }
        payload.cycles.forEach { cycle ->
            requireDate(cycle.startDate, "cycle ${cycle.id} startDate")
            requireDate(cycle.endDate, "cycle ${cycle.id} endDate")
            cycle.periodDays.forEach { requireDate(it.date, "cycle ${cycle.id} periodDay") }
        }
        payload.dailyEntries.forEach { requireDate(it.date, "daily entry ${it.date}") }
        payload.symptomLogs.forEach { requireDate(it.date, "symptom log ${it.id}") }
        payload.biomarkerObservations.forEach { requireDate(it.date, "biomarker ${it.date}") }
        payload.journalEvents.forEach { event ->
            runCatching { Instant.parse(event.startAt) }.getOrElse {
                throw DataImportError.CorruptedPayload("unparseable instant '${event.startAt}' in event ${event.id}")
            }
            event.endAt?.let { endAt ->
                runCatching { Instant.parse(endAt) }.getOrElse {
                    throw DataImportError.CorruptedPayload("unparseable instant '$endAt' in event ${event.id}")
                }
            }
        }
    }

    private fun JsonElement.toPayloadJson(): String = json.encodeToString(JsonElement.serializer(), this)
}
