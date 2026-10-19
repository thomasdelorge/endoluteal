package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.DailyRecapEntity
import fr.luteal.core.data.local.DailyRecapDao
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapBowelPayload
import fr.luteal.core.model.recap.RecapCyclePayload
import fr.luteal.core.model.recap.RecapGeneralPayload
import fr.luteal.core.model.recap.RecapPainPayload
import fr.luteal.core.model.recap.RecapPsyPayload
import fr.luteal.core.model.recap.RecapStep
import fr.luteal.core.model.recap.withPinsOnly
import fr.luteal.core.designsystem.component.bodymap.ChoirPainMap
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray

interface DailyRecapRepository {
    fun observe(date: LocalDate): Flow<DailyRecap?>
    suspend fun get(date: LocalDate): DailyRecap?
    suspend fun upsert(recap: DailyRecap)
    suspend fun markStepComplete(date: LocalDate, step: RecapStep)
    suspend fun isFullyComplete(date: LocalDate): Boolean
    suspend fun allOnce(): List<DailyRecap>
    fun observeAll(): Flow<List<DailyRecap>>
}

@Singleton
class DailyRecapRepositoryImpl @Inject constructor(
    private val dailyRecapDao: DailyRecapDao,
    private val clock: Clock,
    @ApplicationContext private val context: Context
) : DailyRecapRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val zonePinSeeds by lazy { ChoirPainMap.zonePinSeeds(context) }

    override fun observe(date: LocalDate): Flow<DailyRecap?> =
        dailyRecapDao.observe(date.toString()).map { entity -> entity?.toModel() }

    override suspend fun get(date: LocalDate): DailyRecap? =
        dailyRecapDao.get(date.toString())?.toModel()

    override suspend fun upsert(recap: DailyRecap) {
        dailyRecapDao.upsert(recap.toEntity())
    }

    override suspend fun markStepComplete(date: LocalDate, step: RecapStep) {
        val now = clock.instant()
        val current = get(date) ?: DailyRecap.empty(date, now)
        upsert(
            current.copy(
                completedSteps = current.completedSteps + step,
                updatedAt = now
            )
        )
    }

    override suspend fun isFullyComplete(date: LocalDate): Boolean =
        get(date)?.isFullyComplete() == true

    override suspend fun allOnce(): List<DailyRecap> =
        dailyRecapDao.allOnce().map { it.toModel() }

    override fun observeAll(): Flow<List<DailyRecap>> =
        dailyRecapDao.observeAll().map { rows -> rows.map { it.toModel() } }

    private fun DailyRecapEntity.toModel(): DailyRecap = DailyRecap(
        date = LocalDate.parse(date),
        cycle = json.decodeFromString(cycleJson),
        pain = json.decodeFromString<RecapPainPayload>(painJson).withPinsOnly(zonePinSeeds),
        psy = json.decodeFromString(psyJson),
        general = json.decodeFromString(generalJson),
        bowel = json.decodeFromString(bowelJson),
        completedSteps = completedStepsJson.toSteps(),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
    )

    private fun DailyRecap.toEntity(): DailyRecapEntity = DailyRecapEntity(
        date = date.toString(),
        cycleJson = json.encodeToString(cycle),
        painJson = json.encodeToString(pain),
        psyJson = json.encodeToString(psy),
        generalJson = json.encodeToString(general),
        bowelJson = json.encodeToString(bowel),
        completedStepsJson = completedSteps.toJson(),
        updatedAtEpochMillis = updatedAt.toEpochMilli()
    )

    companion object {
        fun Set<RecapStep>.toJson(): String {
            val array = JSONArray()
            forEach { array.put(it.name) }
            return array.toString()
        }

        fun String.toSteps(): Set<RecapStep> = runCatching {
            val array = JSONArray(this)
            buildSet {
                for (index in 0 until array.length()) {
                    add(RecapStep.valueOf(array.getString(index)))
                }
            }
        }.getOrDefault(emptySet())
    }
}
