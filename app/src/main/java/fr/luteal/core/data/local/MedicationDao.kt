package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.luteal.core.data.entity.InteractionRuleEntity
import fr.luteal.core.data.entity.MedicationEntity
import fr.luteal.core.data.entity.MedicationContraindicationEntity
import fr.luteal.core.data.entity.MedicationSubstanceEntity
import fr.luteal.core.data.entity.SubstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM substances ORDER BY name COLLATE NOCASE")
    fun observeSubstances(): Flow<List<SubstanceEntity>>

    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medication_substances")
    fun observeCompositions(): Flow<List<MedicationSubstanceEntity>>

    @Query("SELECT * FROM interaction_rules ORDER BY id")
    fun observeRules(): Flow<List<InteractionRuleEntity>>

    @Query("SELECT * FROM medication_contraindications")
    fun observeContraindications(): Flow<List<MedicationContraindicationEntity>>

    @Query("SELECT * FROM substances")
    suspend fun substancesOnce(): List<SubstanceEntity>

    @Query("SELECT * FROM medications")
    suspend fun medicationsOnce(): List<MedicationEntity>

    @Query("SELECT * FROM medication_substances")
    suspend fun compositionsOnce(): List<MedicationSubstanceEntity>

    @Query("SELECT * FROM interaction_rules")
    suspend fun rulesOnce(): List<InteractionRuleEntity>

    @Query("SELECT * FROM medication_contraindications")
    suspend fun contraindicationsOnce(): List<MedicationContraindicationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubstance(entity: SubstanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(entity: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompositions(rows: List<MedicationSubstanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(entity: InteractionRuleEntity)

    @Query("DELETE FROM medication_substances WHERE medicationId = :medicationId")
    suspend fun deleteCompositions(medicationId: String)

    @Query("DELETE FROM medication_contraindications WHERE medicationId = :medicationId")
    suspend fun deleteContraindications(medicationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContraindications(rows: List<MedicationContraindicationEntity>)

    @Query("DELETE FROM medication_contraindications")
    suspend fun deleteAllContraindications()

    @Query("DELETE FROM interaction_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Query("DELETE FROM medication_substances")
    suspend fun deleteAllCompositions()

    @Query("DELETE FROM interaction_rules")
    suspend fun deleteAllRules()

    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()

    @Query("DELETE FROM substances")
    suspend fun deleteAllSubstances()

    @Transaction
    suspend fun replaceMedication(
        medication: MedicationEntity,
        compositions: List<MedicationSubstanceEntity>,
        contraindications: List<MedicationContraindicationEntity> = emptyList()
    ) {
        upsertMedication(medication)
        deleteCompositions(medication.id)
        if (compositions.isNotEmpty()) upsertCompositions(compositions)
        deleteContraindications(medication.id)
        if (contraindications.isNotEmpty()) upsertContraindications(contraindications)
    }
}
