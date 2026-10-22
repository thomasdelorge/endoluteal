package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.DataExportManager
import fr.luteal.core.data.DataImportManager
import fr.luteal.core.data.backup.AutoBackupPassphraseStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.ImportStrategy
import fr.luteal.core.model.meds.AlertSeverity
import fr.luteal.core.model.meds.Composition
import fr.luteal.core.model.meds.DrugClass
import fr.luteal.core.model.meds.InteractionRule
import fr.luteal.core.model.meds.InteractionTargetType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.meds.MedicationSeed
import fr.luteal.core.model.meds.Substance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicationCatalogTest {

    private lateinit var context: Context
    private lateinit var database: LutealDatabase
    private lateinit var repository: MedicationRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MedicationRepositoryImpl(database.medicationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createTwoSubstanceMedicationThenEditAndArchive() = runTest {
        repository.upsertSubstance(substance("para"))
        repository.upsertSubstance(substance("caf"))
        repository.upsertMedication(
            Medication(
                id = "combo",
                name = "Combo",
                form = "gélule",
                contraindications = "",
                archived = false,
                compositions = listOf(Composition("para", 300.0), Composition("caf", 30.0))
            )
        )
        repository.upsertMedication(
            Medication(
                id = "combo",
                name = "Combo modifié",
                form = "gélule",
                contraindications = "foie",
                archived = true,
                compositions = listOf(Composition("para", 250.0), Composition("caf", 30.0))
            )
        )

        val saved = repository.catalogOnce().medications.single()
        assertEquals("Combo modifié", saved.name)
        assertTrue(saved.archived)
        assertEquals(250.0, saved.compositions.first { it.substanceId == "para" }.amountMgPerUnit, 0.01)
    }

    @Test
    fun mergeMissingSeedFillsNullCeilingsAndKeepsEditedOnes() = runTest {
        val raw = context.assets.open("medications_seed.json").bufferedReader().use { it.readText() }
        val seed = MedicationSeed.parse(raw)
        repository.upsertSubstance(
            substance("paracetamol").copy(
                name = "Paracétamol perso",
                maxDailyMg = 3000.0
            )
        )
        repository.mergeMissingSeed(seed)
        repository.mergeMissingSeed(seed)

        val catalog = repository.catalogOnce()
        val paracetamol = catalog.substances.single { it.id == "paracetamol" }
        assertEquals("Paracétamol perso", paracetamol.name)
        assertEquals(3000.0, paracetamol.maxDailyMg!!, 0.01)
        assertEquals(1000.0, paracetamol.maxPerIntakeMg!!, 0.01)
        assertEquals(240, paracetamol.minIntervalMinutes)
        assertEquals(seed.medications.size, catalog.medications.size)
        assertEquals(seed.rules.size, catalog.rules.size)
        assertEquals(seed.substances.size, catalog.substances.size)
    }

    @Test
    fun seedCreatesEightUnvalidatedMedications() = runTest {
        val raw = context.assets.open("medications_seed.json").bufferedReader().use { it.readText() }
        repository.applySeed(MedicationSeed.parse(raw))
        val catalog = repository.observeCatalog().first()
        assertEquals(8, catalog.medications.size)
        assertTrue(catalog.substances.all { !it.validated })
        assertEquals(2, catalog.rules.size)
        assertFalse(catalog.medications.single { it.id == "med-lamaline" }.compositions.size < 2)
    }

    @Test
    fun exportThenImportRestoresCatalog() = runTest {
        repository.upsertSubstance(substance("para"))
        repository.upsertSubstance(substance("caf"))
        repository.upsertMedication(
            Medication(
                id = "combo",
                name = "Combo",
                form = "gélule",
                contraindications = "foie",
                archived = false,
                compositions = listOf(Composition("para", 300.0), Composition("caf", 30.0))
            )
        )
        repository.upsertRule(
            InteractionRule(
                id = "rule",
                targetAType = InteractionTargetType.SUBSTANCE,
                targetAId = "para",
                targetBType = InteractionTargetType.SUBSTANCE,
                targetBId = "caf",
                severity = AlertSeverity.WARNING,
                message = "café"
            )
        )
        val preferences = UserPreferencesDataStore(context)
        val exportManager = DataExportManager(
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
            fr.luteal.core.data.appointment.AppointmentAttachmentStorage(context),
            database.dailyRecapDao(),
            database.questionnaireResultDao(),
            preferences,
            AutoBackupPassphraseStore(context)
        )
        val json = exportManager.exportToJsonString()
        database.medicationDao().deleteAllCompositions()
        database.medicationDao().deleteAllContraindications()
        database.medicationDao().deleteAllRules()
        database.medicationDao().deleteAllMedications()
        database.medicationDao().deleteAllSubstances()
        val importManager = DataImportManager(
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
            fr.luteal.core.data.appointment.AppointmentAttachmentStorage(context),
            database.dailyRecapDao(),
            database.questionnaireResultDao(),
            preferences
        )
        val payload = importManager.inspectBackup(json.byteInputStream()).getOrThrow().second
        val restored = importManager.restoreBackup(payload, ImportStrategy.REPLACE_ALL)
        assertTrue(restored.isSuccess)
        val catalog = repository.catalogOnce()
        assertEquals("Combo", catalog.medications.single().name)
        assertEquals(2, catalog.medications.single().compositions.size)
        assertEquals(2, catalog.substances.size)
        assertEquals("café", catalog.rules.single().message)
    }

    private fun substance(id: String) = Substance(
        id = id,
        name = id,
        drugClass = DrugClass.OTHER,
        maxDailyMg = null,
        maxPerIntakeMg = null,
        minIntervalMinutes = null,
        validated = false,
        notes = ""
    )
}
