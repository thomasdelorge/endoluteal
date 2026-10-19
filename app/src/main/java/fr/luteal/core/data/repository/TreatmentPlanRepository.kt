package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.TreatmentPlanEntity
import fr.luteal.core.data.local.TreatmentPlanDao
import fr.luteal.core.model.meds.TreatmentPlan
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

interface TreatmentPlanRepository {
    fun observe(): Flow<List<TreatmentPlan>>
    suspend fun allOnce(): List<TreatmentPlan>
    suspend fun upsert(plan: TreatmentPlan)
    suspend fun delete(id: String)
}

@Singleton
class TreatmentPlanRepositoryImpl @Inject constructor(
    private val treatmentPlanDao: TreatmentPlanDao
) : TreatmentPlanRepository {
    override fun observe(): Flow<List<TreatmentPlan>> =
        treatmentPlanDao.observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun allOnce(): List<TreatmentPlan> =
        treatmentPlanDao.allOnce().map { it.toDomain() }

    override suspend fun upsert(plan: TreatmentPlan) {
        require(plan.timesOfDay.isNotEmpty()) { "A plan needs at least one time" }
        require(plan.unitsPerDose > 0.0) { "Units per dose must be positive" }
        treatmentPlanDao.upsert(plan.toEntity())
    }

    override suspend fun delete(id: String) {
        treatmentPlanDao.delete(id)
    }
}

private fun TreatmentPlanEntity.toDomain() = TreatmentPlan(
    id = id,
    medicationId = medicationId,
    unitsPerDose = unitsPerDose,
    timesOfDay = timesOfDay.toTimes(),
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    active = active,
    continuousHormonal = continuousHormonal
)

private fun TreatmentPlan.toEntity() = TreatmentPlanEntity(
    id = id,
    medicationId = medicationId,
    unitsPerDose = unitsPerDose,
    timesOfDay = timesOfDay.toJson(),
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    active = active,
    continuousHormonal = continuousHormonal
)

private fun String.toTimes(): List<LocalTime> = runCatching {
    val array = JSONArray(this)
    List(array.length()) { index -> LocalTime.parse(array.getString(index)) }
}.getOrDefault(emptyList())

private fun List<LocalTime>.toJson(): String {
    val array = JSONArray()
    forEach { array.put(it.toString()) }
    return array.toString()
}
