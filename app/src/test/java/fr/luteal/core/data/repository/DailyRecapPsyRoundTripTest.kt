package fr.luteal.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapBowelPayload
import fr.luteal.core.model.recap.RecapPsyPayload
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
class DailyRecapPsyRoundTripTest {

    private val clock = Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: LutealDatabase
    private lateinit var repo: DailyRecapRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LutealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = DailyRecapRepositoryImpl(database.dailyRecapDao(), clock, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun psyAndBowelPayloadsRoundTrip() = runTest {
        val date = LocalDate.parse("2026-08-10")
        val psy = RecapPsyPayload(mood = 3, anxiety = 2, libido = 4)
        val bowel = RecapBowelPayload(constipation = true, bristolType = 4, bloating = 2)
        val recap = DailyRecap.empty(date, clock.instant()).copy(psy = psy, bowel = bowel)
        repo.upsert(recap)
        repo.markStepComplete(date, RecapStep.PSY)
        repo.markStepComplete(date, RecapStep.BOWEL)
        val loaded = repo.get(date)
        assertEquals(psy, loaded?.psy)
        assertEquals(bowel, loaded?.bowel)
    }
}
