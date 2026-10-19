package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapCyclePayload
import fr.luteal.core.model.recap.RecapStep
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyRecapRepositoryTest {

    private lateinit var database: LutealDatabase
    private lateinit var repository: DailyRecapRepository
    private val clock = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DailyRecapRepositoryImpl(database.dailyRecapDao(), clock, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cyclePayloadSurvivesEditRoundTrip() = runTest {
        val date = LocalDate.of(2026, 10, 19)
        val first = RecapCyclePayload(
            hasBleeding = true,
            bleedingIntensity = "LIGHT",
            hasCervicalMucus = true,
            mucusTexture = "CREAMY"
        )
        repository.upsert(DailyRecap.empty(date, clock.instant()).copy(cycle = first))
        val updated = first.copy(bleedingIntensity = "HEAVY", hasCervicalMucus = false, mucusTexture = "")
        repository.upsert(
            (repository.get(date) ?: DailyRecap.empty(date, clock.instant())).copy(cycle = updated)
        )
        assertEquals(updated, repository.get(date)?.cycle)
    }

    @Test
    fun resumeAtFirstIncompleteStep() = runTest {
        val date = LocalDate.of(2026, 4, 1)
        repository.markStepComplete(date, RecapStep.CYCLE)
        repository.markStepComplete(date, RecapStep.PAIN)
        val recap = repository.get(date)
        assertEquals(RecapStep.PSY, recap?.firstIncompleteStep())
    }
}
