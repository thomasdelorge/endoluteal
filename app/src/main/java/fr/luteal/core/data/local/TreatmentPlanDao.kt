package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.luteal.core.data.entity.TreatmentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentPlanDao {
    @Query("SELECT * FROM treatment_plans ORDER BY startDate")
    fun observe(): Flow<List<TreatmentPlanEntity>>

    @Query("SELECT * FROM treatment_plans")
    suspend fun allOnce(): List<TreatmentPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TreatmentPlanEntity)

    @Query("DELETE FROM treatment_plans WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM treatment_plans")
    suspend fun deleteAll()
}
