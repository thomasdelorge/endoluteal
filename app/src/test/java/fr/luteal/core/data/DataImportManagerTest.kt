package fr.luteal.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.appointment.AppointmentAttachmentStorage
import fr.luteal.core.data.backup.AutoBackupPassphraseStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.JournalEventEntity
import fr.luteal.core.data.entity.PainMarkEntity
import fr.luteal.core.model.BiomarkerBackupDto
import fr.luteal.core.model.CycleBackupDto
import fr.luteal.core.model.DailyEntryBackupDto
import fr.luteal.core.model.DataImportError
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.EndolutealBackup
import fr.luteal.core.model.JournalEventBackupDto
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.model.PeriodDayBackupDto
import fr.luteal.core.model.SymptomLogBackupDto
import fr.luteal.core.model.UserPreferencesBackupDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONArray
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DataImportManagerTest {

    private lateinit var context: Context
    private lateinit var database: LutealDatabase
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var exportManager: DataExportManager
    private lateinit var importManager: DataImportManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPreferencesDataStore = UserPreferencesDataStore(context)

        exportManager = DataExportManager(
            cycleDao = database.cycleDao(),
            dailyEntryDao = database.dailyEntryDao(),
            symptomDao = database.symptomDao(),
            biomarkerDao = database.biomarkerDao(),
            journalEventDao = database.journalEventDao(),
            medicationDao = database.medicationDao(),
            medicationIntakeDao = database.medicationIntakeDao(),
            treatmentPlanDao = database.treatmentPlanDao(),
            careTypeDao = database.careTypeDao(),
            professionalDao = database.professionalDao(),
            appointmentDao = database.appointmentDao(),
            appointmentAttachmentDao = database.appointmentAttachmentDao(),
            appointmentAttachmentStorage = AppointmentAttachmentStorage(context),
            dailyRecapDao = database.dailyRecapDao(),
            questionnaireResultDao = database.questionnaireResultDao(),
            userPreferencesDataStore = userPreferencesDataStore,
            autoBackupPassphraseStore = AutoBackupPassphraseStore(context)
        )

        importManager = DataImportManager(
            database = database,
            cycleDao = database.cycleDao(),
            dailyEntryDao = database.dailyEntryDao(),
            symptomDao = database.symptomDao(),
            biomarkerDao = database.biomarkerDao(),
            journalEventDao = database.journalEventDao(),
            medicationDao = database.medicationDao(),
            medicationIntakeDao = database.medicationIntakeDao(),
            treatmentPlanDao = database.treatmentPlanDao(),
            careTypeDao = database.careTypeDao(),
            professionalDao = database.professionalDao(),
            appointmentDao = database.appointmentDao(),
            appointmentAttachmentDao = database.appointmentAttachmentDao(),
            appointmentAttachmentStorage = AppointmentAttachmentStorage(context),
            dailyRecapDao = database.dailyRecapDao(),
            questionnaireResultDao = database.questionnaireResultDao(),
            userPreferencesDataStore = userPreferencesDataStore
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inspectValidBackupGeneratesAccuratePreview() = runTest {
        val payload = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = "2026-08-15T12:00:00Z",
            appVersion = "1.2.0",
            cycles = listOf(
                CycleBackupDto(
                    id = "cycle_1",
                    startDate = "2026-06-01",
                    endDate = "2026-06-29",
                    periodDays = listOf(
                        PeriodDayBackupDto(date = "2026-06-01", bleedingIntensity = "MEDIUM")
                    )
                ),
                CycleBackupDto(
                    id = "cycle_2",
                    startDate = "2026-06-30",
                    endDate = null
                )
            ),
            dailyEntries = listOf(
                DailyEntryBackupDto(
                    date = "2026-06-01",
                    bleedingIntensity = "MEDIUM",
                    painLevel = 3,
                    moodLevel = 4,
                    energyLevel = 2,
                    symptomIds = listOf("cramps"),
                    notes = "First day",
                    updatedAt = "2026-06-01T20:00:00Z"
                )
            ),
            symptomLogs = listOf(
                SymptomLogBackupDto(
                    id = "symptom_1",
                    timestamp = "2026-06-01T10:00:00Z",
                    date = "2026-06-01",
                    symptomId = "cramps",
                    severity = 3,
                    notes = "Severe cramps"
                )
            ),
            preferences = UserPreferencesBackupDto(
                userRole = "PRIMARY_TRACKER",
                locale = "fr",
                trackPmdd = true,
                ageBand = "AGE_25_29"
            )
        )

        val jsonString = exportManager.exportToJsonString()
        val inputStream = ByteArrayInputStream(exportManager.exportToJsonString().toByteArray())

        val customStream = ByteArrayInputStream(
            kotlinx.serialization.json.Json.encodeToString(LutealBackupPayload.serializer(), payload).toByteArray()
        )

        val result = importManager.inspectBackup(customStream)
        assertTrue(result.isSuccess)
        val (preview, parsedPayload) = result.getOrThrow()

        assertEquals(1, preview.schemaVersion)
        assertEquals(2, preview.cycleCount)
        assertEquals("2026-06-01", preview.earliestCycleDate)
        assertEquals("2026-06-30", preview.latestCycleDate)
        assertEquals(1, preview.dailyEntryCount)
        assertEquals("2026-06-01", preview.earliestEntryDate)
        assertEquals(1, preview.symptomLogCount)
        assertTrue(preview.preferences.trackPmdd)
        assertEquals("AGE_25_29", preview.preferences.ageBand)
    }

    @Test
    fun inspectInvalidJsonFailsWithSyntaxError() = runTest {
        val stream = ByteArrayInputStream("{ malformed json }".toByteArray())
        val result = importManager.inspectBackup(stream)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DataImportError.InvalidJsonSyntax)
    }

    @Test
    fun inspectUnsupportedSchemaVersionFails() = runTest {
        val json = """
            {
              "schema_version": 2,
              "exported_at": "2026-08-15T12:00:00Z",
              "app_version": "2.0.0",
              "cycles": [],
              "daily_entries": [],
              "symptom_logs": [],
              "preferences": {}
            }
        """.trimIndent()
        val stream = ByteArrayInputStream(json.toByteArray())
        val result = importManager.inspectBackup(stream)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as? DataImportError.UnsupportedSchemaVersion
        assertNotNull(error)
        assertEquals(2, error?.version)
    }

    @Test
    fun restoreWithReplaceAllWipesAndRestoresCleanly() = runTest {
        // Seed initial local data
        database.cycleDao().insertCycle(
            CycleEntity(id = "old_cycle", startDate = "2026-01-01", endDate = "2026-01-28", averageLengthDays = 28, lutealPhaseLengthDays = 14, periodDaysJson = "[]")
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-01-01", bleedingIntensity = "LIGHT", painLevel = 1, moodLevel = 1, energyLevel = 1, symptomIdsJson = "[]", notes = "Old", updatedAtEpochMillis = 1000L)
        )

        val payload = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = "2026-08-15T12:00:00Z",
            appVersion = "1.2.0",
            cycles = listOf(
                CycleBackupDto(id = "new_cycle", startDate = "2026-07-01", endDate = null)
            ),
            dailyEntries = listOf(
                DailyEntryBackupDto(
                    date = "2026-07-01",
                    bleedingIntensity = "HEAVY",
                    painLevel = 4,
                    moodLevel = 2,
                    energyLevel = 3,
                    symptomIds = listOf("cramps", "fatigue"),
                    notes = "New entry",
                    updatedAt = "2026-07-01T12:00:00Z"
                )
            ),
            symptomLogs = listOf(
                SymptomLogBackupDto(
                    id = "symptom_new",
                    timestamp = "2026-07-01T08:00:00Z",
                    date = "2026-07-01",
                    symptomId = "cramps",
                    severity = 4
                )
            ),
            preferences = UserPreferencesBackupDto(
                trackEndometriosis = true,
                trackPms = true
            )
        )

        val result = importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()

        assertEquals(1, summary.cyclesImported)
        assertEquals(1, summary.dailyEntriesImported)
        assertEquals(1, summary.symptomLogsImported)

        // Verify old records removed, new records present
        val cycles = database.cycleDao().getAllCyclesOnce()
        assertEquals(1, cycles.size)
        assertEquals("new_cycle", cycles[0].id)

        val entries = database.dailyEntryDao().getAllEntriesOnce()
        assertEquals(1, entries.size)
        assertEquals("2026-07-01", entries[0].date)
        assertEquals("HEAVY", entries[0].bleedingIntensity)

        val symptoms = database.symptomDao().getAllSymptomLogsOnce()
        assertEquals(1, symptoms.size)
        assertEquals("symptom_new", symptoms[0].id)

        // Verify preferences updated
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertTrue(prefs.trackEndometriosis)
        assertTrue(prefs.trackPms)
        assertTrue(prefs.hasCompletedOnboarding)
    }

    @Test
    fun restoreWithMergeUpsertMergesAndPreservesLocalNonConflictingData() = runTest {
        // Local existing record
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-06-01", bleedingIntensity = "LIGHT", painLevel = 2, moodLevel = 3, energyLevel = 3, symptomIdsJson = "[]", notes = "Local", updatedAtEpochMillis = 2000L)
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-06-02", bleedingIntensity = "NONE", painLevel = 1, moodLevel = 5, energyLevel = 5, symptomIdsJson = "[]", notes = "Local untouched", updatedAtEpochMillis = 2000L)
        )

        val payload = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = "2026-08-15T12:00:00Z",
            appVersion = "1.2.0",
            cycles = emptyList(),
            dailyEntries = listOf(
                // 2026-06-01 with newer timestamp -> should update
                DailyEntryBackupDto(
                    date = "2026-06-01",
                    bleedingIntensity = "HEAVY",
                    painLevel = 5,
                    moodLevel = 1,
                    energyLevel = 1,
                    symptomIds = listOf("cramps"),
                    notes = "Updated from backup",
                    updatedAt = "2026-06-01T20:00:00Z"
                ),
                // 2026-06-03 new date -> should insert
                DailyEntryBackupDto(
                    date = "2026-06-03",
                    bleedingIntensity = "LIGHT",
                    painLevel = 2,
                    moodLevel = 3,
                    energyLevel = 4,
                    symptomIds = emptyList(),
                    notes = "Inserted from backup",
                    updatedAt = "2026-06-03T12:00:00Z"
                )
            ),
            symptomLogs = emptyList()
        )

        val result = importManager.restoreBackup(payload, ImportStrategy.MERGE_UPSERT)
        assertTrue(result.isSuccess)

        val entries = database.dailyEntryDao().getAllEntriesOnce().associateBy { it.date }
        assertEquals(3, entries.size)
        assertEquals("HEAVY", entries["2026-06-01"]?.bleedingIntensity)
        assertEquals("Updated from backup", entries["2026-06-01"]?.notes)
        assertEquals("Local untouched", entries["2026-06-02"]?.notes)
        assertEquals("Inserted from backup", entries["2026-06-03"]?.notes)
        assertEquals(false, result.getOrThrow().preferencesRestored)
    }

    @Test
    fun mergeKeepsNewerLocalSymptomLogAndLeavesPreferences() = runTest {
        userPreferencesDataStore.clear()
        userPreferencesDataStore.setDisorderTracking("endometriosis", true)
        userPreferencesDataStore.setDisorderTracking("pms", false)
        userPreferencesDataStore.setTemperatureUnit("FAHRENHEIT")
        database.symptomDao().insertSymptomLog(
            SymptomLogEntity(
                id = "same",
                timestampEpochMillis = Instant.parse("2026-08-01T12:00:00Z").toEpochMilli(),
                date = "2026-08-01",
                symptomId = "cramps",
                severity = 4,
                notes = "local newer"
            )
        )
        val payload = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = "2026-08-15T12:00:00Z",
            appVersion = "1.2.0",
            symptomLogs = listOf(
                SymptomLogBackupDto(
                    id = "same",
                    timestamp = "2026-07-01T12:00:00Z",
                    date = "2026-07-01",
                    symptomId = "cramps",
                    severity = 1,
                    notes = "older backup"
                )
            ),
            preferences = UserPreferencesBackupDto(trackPms = true, temperatureUnit = "CELSIUS")
        )
        val result = importManager.restoreBackup(payload, ImportStrategy.MERGE_UPSERT)
        assertTrue(result.isSuccess)
        val log = database.symptomDao().getSymptomLogOnce("same")
        assertEquals("local newer", log?.notes)
        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertTrue(prefs.trackEndometriosis)
        assertEquals("FAHRENHEIT", prefs.temperatureUnit)
        assertEquals(false, prefs.trackPms)
    }

    @Test
    fun exportAndImportRoundTripFidelity() = runTest {
        // Seed full database
        database.cycleDao().insertCycle(
            CycleEntity(id = "cycle_roundtrip", startDate = "2026-05-01", endDate = "2026-05-28", averageLengthDays = 28, lutealPhaseLengthDays = 14, periodDaysJson = "[{\"date\":\"2026-05-01\",\"bleedingIntensity\":\"MEDIUM\",\"notes\":\"Day 1\",\"symptomIds\":[\"cramps\"]}]")
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(date = "2026-05-01", bleedingIntensity = "MEDIUM", painLevel = 3, moodLevel = 2, energyLevel = 4, symptomIdsJson = "[\"cramps\"]", notes = "Round trip entry", updatedAtEpochMillis = 1714560000000L)
        )
        database.symptomDao().insertSymptomLog(
            SymptomLogEntity(id = "symp_roundtrip", timestampEpochMillis = 1714560000000L, date = "2026-05-01", symptomId = "cramps", severity = 3, notes = "Note")
        )
        userPreferencesDataStore.setDisorderTracking("pmdd", true)
        userPreferencesDataStore.setAgeBand("AGE_30_34")
        userPreferencesDataStore.setThemeMode("DARK")
        userPreferencesDataStore.setColorPalette("PLUM")

        // Export to stream
        val outStream = ByteArrayOutputStream()
        exportManager.exportToStream(outStream)

        // Clear local database
        database.cycleDao().deleteAllCycles()
        database.dailyEntryDao().deleteAllEntries()
        database.symptomDao().deleteAllSymptomLogs()

        // Inspect and restore from stream
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val inspectResult = importManager.inspectBackup(inStream)
        assertTrue(inspectResult.isSuccess)
        val (_, payload) = inspectResult.getOrThrow()

        val restoreResult = importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL)
        assertTrue(restoreResult.isSuccess)

        // Assert data is restored faithfully
        val cycles = database.cycleDao().getAllCyclesOnce()
        assertEquals(1, cycles.size)
        assertEquals("cycle_roundtrip", cycles[0].id)
        assertEquals("2026-05-01", cycles[0].startDate)

        val entries = database.dailyEntryDao().getAllEntriesOnce()
        assertEquals(1, entries.size)
        assertEquals("2026-05-01", entries[0].date)
        assertEquals("MEDIUM", entries[0].bleedingIntensity)
        assertEquals(3, entries[0].painLevel)

        val symptoms = database.symptomDao().getAllSymptomLogsOnce()
        assertEquals(1, symptoms.size)
        assertEquals("symp_roundtrip", symptoms[0].id)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertTrue(prefs.trackPmdd)
        assertEquals("AGE_30_34", prefs.ageBand)
        assertEquals("DARK", prefs.themeMode)
        assertEquals("PLUM", prefs.colorPalette)
    }

    @Test
    fun restoreRejectsUnparseableCycleDatesInsteadOfPersistingThem() = runTest {
        // "2026-13-45" passes the old blank-only check but explodes
        // LocalDate.parse on every later read. Import must reject it up front.
        val payload = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = "2026-08-15T12:00:00Z",
            appVersion = "1.2.0",
            cycles = listOf(
                CycleBackupDto(
                    id = "cycle_bad",
                    startDate = "2026-13-45",
                    endDate = null,
                    periodDays = emptyList()
                )
            ),
            dailyEntries = emptyList(),
            symptomLogs = emptyList(),
            biomarkerObservations = emptyList(),
            preferences = UserPreferencesBackupDto(
                userRole = "PRIMARY_TRACKER",
                locale = "fr"
            )
        )

        val restoreResult = importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL)

        assertTrue(restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull() is DataImportError.CorruptedPayload)
        // Nothing was persisted: the database stays untouched.
        assertTrue(database.cycleDao().getAllCyclesOnce().isEmpty())
    }

    @Test
    fun inspectRejectsLegacyLutealBackupWithoutEndolutealFormat() = runTest {
        val json = """
            {
              "schema_version": 1,
              "exported_at": "2026-08-15T12:00:00Z",
              "app_version": "1.3.0",
              "cycles": [],
              "daily_entries": [],
              "symptom_logs": [],
              "preferences": {}
            }
        """.trimIndent()
        val result = importManager.inspectBackup(ByteArrayInputStream(json.toByteArray()))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DataImportError.UnsupportedSchemaVersion)
    }

    @Test
    fun exportThenImportRestoresJournalEventsAndMarks() = runTest {
        val start = Instant.parse("2026-08-01T10:00:00Z")
        database.journalEventDao().upsert(
            JournalEventEntity(
                id = "evt-1",
                type = "PAIN_CHECK",
                startAt = start.toEpochMilli(),
                endAt = null,
                zoneId = "Europe/Paris",
                notes = "matin",
                linkedEventId = null,
                payloadJson = """{"type":"PAIN_CHECK"}""",
                createdAt = start.toEpochMilli(),
                updatedAt = start.toEpochMilli()
            ),
            listOf(
                PainMarkEntity("m1", "evt-1", "hypogastre", 6, """["crampe"]"""),
                PainMarkEntity("m2", "evt-1", "lombaire_g", 4, "[]"),
                PainMarkEntity("m3", "evt-1", "sacrum", 2, "[]")
            )
        )

        val exported = exportManager.exportToJsonString()
        database.journalEventDao().deleteAllMarks()
        database.journalEventDao().deleteAllEvents()

        val inspected = importManager.inspectBackup(ByteArrayInputStream(exported.toByteArray()))
        assertTrue(inspected.isSuccess)
        val (preview, payload) = inspected.getOrThrow()
        assertEquals(1, preview.journalEventCount)
        assertEquals(3, preview.painMarkCount)

        val restored = importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL)
        assertTrue(restored.isSuccess)
        assertEquals(1, restored.getOrThrow().journalEventsImported)
        assertEquals(3, restored.getOrThrow().painMarksImported)

        val event = database.journalEventDao().get("evt-1")
        assertNotNull(event)
        assertEquals("PAIN_CHECK", event!!.event.type)
        assertEquals("matin", event.event.notes)
        assertEquals(3, event.marks.size)
        assertEquals(listOf("crampe"), JSONArray(event.marks.first { it.id == "m1" }.painTypes).let { array ->
            List(array.length()) { array.getString(it) }
        })
    }

    @Test
    fun mergeKeepsNewerJournalEvent() = runTest {
        val start = Instant.parse("2026-08-01T10:00:00Z")
        val newer = start.plusSeconds(3600)
        database.journalEventDao().upsert(
            JournalEventEntity(
                id = "evt-1",
                type = "SYMPTOM",
                startAt = start.toEpochMilli(),
                endAt = null,
                zoneId = "Europe/Paris",
                notes = "local",
                linkedEventId = null,
                payloadJson = """{"type":"SYMPTOM"}""",
                createdAt = start.toEpochMilli(),
                updatedAt = newer.toEpochMilli()
            ),
            emptyList()
        )
        val older = LutealBackupPayload(
            backupFormat = EndolutealBackup.FORMAT,
            schemaVersion = 1,
            exportedAt = start.toString(),
            appVersion = "0.1.0-dev",
            journalEvents = listOf(
                JournalEventBackupDto(
                    id = "evt-1",
                    type = "SYMPTOM",
                    startAt = start.toString(),
                    zoneId = "Europe/Paris",
                    notes = "backup",
                    payload = Json.parseToJsonElement("""{"type":"SYMPTOM"}"""),
                    createdAt = start.toString(),
                    updatedAt = start.toString()
                )
            )
        )

        val restored = importManager.restoreBackup(older, ImportStrategy.MERGE_UPSERT)
        assertTrue(restored.isSuccess)
        assertEquals(0, restored.getOrThrow().journalEventsImported)
        assertEquals("local", database.journalEventDao().getEventOnce("evt-1")?.notes)
    }
}
