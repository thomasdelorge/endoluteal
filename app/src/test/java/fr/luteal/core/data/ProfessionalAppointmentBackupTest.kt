package fr.luteal.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.appointment.AppointmentAttachmentStorage
import fr.luteal.core.data.backup.AutoBackupPassphraseStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.entity.AppointmentEntity
import fr.luteal.core.data.entity.JournalEventEntity
import fr.luteal.core.data.entity.ProfessionalEntity
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.EventPayloadCodec
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.professional.Specialty
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ProfessionalAppointmentBackupTest {

    private lateinit var database: LutealDatabase
    private lateinit var exportManager: DataExportManager
    private lateinit var importManager: DataImportManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val preferences = UserPreferencesDataStore(context)
        exportManager = DataExportManager(
            database.cycleDao(),
            database.dailyEntryDao(),
            database.symptomDao(),
            database.biomarkerDao(),
            database.journalEventDao(),
            database.medicationDao(),
            database.medicationIntakeDao(),
            database.treatmentPlanDao(),
            database.careTypeDao(),
            database.professionalDao(),
            database.appointmentDao(),
            database.appointmentAttachmentDao(),
            AppointmentAttachmentStorage(context),
            database.dailyRecapDao(),
            database.questionnaireResultDao(),
            preferences,
            AutoBackupPassphraseStore(context)
        )
        importManager = DataImportManager(
            database,
            database.cycleDao(),
            database.dailyEntryDao(),
            database.symptomDao(),
            database.biomarkerDao(),
            database.journalEventDao(),
            database.medicationDao(),
            database.medicationIntakeDao(),
            database.treatmentPlanDao(),
            database.careTypeDao(),
            database.professionalDao(),
            database.appointmentDao(),
            database.appointmentAttachmentDao(),
            AppointmentAttachmentStorage(context),
            database.dailyRecapDao(),
            database.questionnaireResultDao(),
            preferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportImportRestoresProfessionalAndAppointment() = runTest {
        val now = Instant.parse("2026-03-01T10:00:00Z")
        database.professionalDao().upsert(
            ProfessionalEntity(
                id = "pro-1",
                name = "Dr Martin",
                specialty = Specialty.GYNECOLOGIST.name,
                customSpecialty = "",
                place = "CHU",
                phone = "",
                notes = ""
            )
        )
        database.journalEventDao().upsert(
            JournalEventEntity(
                id = "evt-rdv",
                type = "APPOINTMENT",
                startAt = now.toEpochMilli(),
                endAt = now.plusSeconds(3600).toEpochMilli(),
                zoneId = "Europe/Paris",
                notes = "",
                linkedEventId = null,
                payloadJson = EventPayloadCodec.encode(EventPayload.Appointment),
                createdAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli()
            ),
            emptyList()
        )
        database.appointmentDao().upsert(
            AppointmentEntity(
                eventId = "evt-rdv",
                professionalId = "pro-1",
                calendarEventKey = null,
                location = "Consultation",
                description = "Première visite",
                report = "Suivi",
                reportStatus = "DONE"
            )
        )

        val json = exportManager.exportToJsonString(appVersion = "0.9.0")
        assertTrue(json.contains("\"app_version\": \"0.9.0\""))
        database.appointmentDao().deleteAll()
        database.professionalDao().deleteAll()
        database.journalEventDao().deleteAllEvents()

        val payload = importManager.inspectBackup(json.byteInputStream()).getOrThrow().second
        importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL).getOrThrow()

        val pro = database.professionalDao().allOnce().single()
        assertEquals("Dr Martin", pro.name)
        val appt = database.appointmentDao().allOnce().single()
        assertEquals("pro-1", appt.professionalId)
        assertEquals("Consultation", appt.location)
        assertEquals("Première visite", appt.description)
    }
}
