package fr.luteal.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.appointment.AppointmentAttachmentStorage
import fr.luteal.core.data.backup.AutoBackupPassphraseStore
import fr.luteal.core.data.openrouter.OpenRouterApiKeyStore
import fr.luteal.core.data.backup.MissingBackupPassphrase
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.LutealBackupPayload
import fr.luteal.core.data.security.InMemoryPinSecretStore
import fr.luteal.core.data.security.PinCryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class DataExportManagerTest {

    private lateinit var context: Context
    private lateinit var database: LutealDatabase
    private lateinit var userPreferencesDataStore: UserPreferencesDataStore
    private lateinit var exportManager: DataExportManager
    private lateinit var purgeManager: LocalDataPurgeManager

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
        runBlocking { userPreferencesDataStore.setBackupEncryptionEnabled(false) }

        purgeManager = LocalDataPurgeManager(
            database = database,
            userPreferencesDataStore = userPreferencesDataStore,
            pinCryptoManager = PinCryptoManager(InMemoryPinSecretStore()),
            appointmentAttachmentStorage = AppointmentAttachmentStorage(context),
            openRouterApiKeyStore = OpenRouterApiKeyStore(context)
        )
    }

    @After
    fun tearDown() {
        runBlocking { userPreferencesDataStore.setBackupEncryptionEnabled(false) }
        database.close()
    }

    @Test
    fun exportIncludesSeededData() = runTest {
        database.cycleDao().insertCycle(
            CycleEntity(
                id = "cycle-100",
                startDate = "2026-08-01",
                endDate = null,
                periodDaysJson = "[]",
                averageLengthDays = 28,
                lutealPhaseLengthDays = 14
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-08-02",
                bleedingIntensity = null,
                painLevel = 1,
                moodLevel = 5,
                energyLevel = 5,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = 1785600000000L
            )
        )

        val output = ByteArrayOutputStream()
        exportManager.exportToStream(output)
        val payload = Json.decodeFromString<LutealBackupPayload>(output.toString(Charsets.UTF_8))

        assertEquals(1, payload.cycles.size)
        assertEquals(1, payload.dailyEntries.size)
    }

    @Test
    fun encryptedExportWithoutPassphraseFails() = runTest {
        userPreferencesDataStore.setBackupEncryptionEnabled(true)
        val output = ByteArrayOutputStream()
        val error = runCatching { exportManager.exportToStream(output) }.exceptionOrNull()
        assertTrue(error is MissingBackupPassphrase)
    }

    @Test
    fun localDataPurgeClearsAllDataAndSecrets() = runTest {
        database.cycleDao().insertCycle(
            CycleEntity(
                id = "cycle-200",
                startDate = "2026-08-01",
                endDate = null,
                periodDaysJson = "[]",
                averageLengthDays = 28,
                lutealPhaseLengthDays = 14
            )
        )
        database.dailyEntryDao().upsert(
            DailyEntryEntity(
                date = "2026-08-02",
                bleedingIntensity = null,
                painLevel = 1,
                moodLevel = 5,
                energyLevel = 5,
                symptomIdsJson = "[]",
                notes = "",
                updatedAtEpochMillis = 1785600000000L
            )
        )

        userPreferencesDataStore.setCompletedOnboarding(true)
        purgeManager.purgeAllLocalData()

        assertEquals(0, database.cycleDao().getAllCyclesOnce().size)
        assertEquals(0, database.dailyEntryDao().getAllEntriesOnce().size)
        assertEquals(0, database.symptomDao().getAllSymptomLogsOnce().size)

        val prefs = userPreferencesDataStore.userPreferencesFlow.first()
        assertFalse(prefs.hasCompletedOnboarding)
    }
}
