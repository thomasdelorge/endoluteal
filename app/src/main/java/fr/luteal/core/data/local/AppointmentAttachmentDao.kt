package fr.luteal.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.luteal.core.data.entity.AppointmentAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentAttachmentDao {
    @Query("SELECT * FROM appointment_attachments WHERE eventId = :eventId ORDER BY createdAt ASC")
    fun observeForEvent(eventId: String): Flow<List<AppointmentAttachmentEntity>>

    @Query("SELECT * FROM appointment_attachments WHERE eventId = :eventId ORDER BY createdAt ASC")
    suspend fun forEventOnce(eventId: String): List<AppointmentAttachmentEntity>

    @Query("SELECT * FROM appointment_attachments WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AppointmentAttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppointmentAttachmentEntity)

    @Query("DELETE FROM appointment_attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM appointment_attachments WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)

    @Query("SELECT * FROM appointment_attachments")
    suspend fun allOnce(): List<AppointmentAttachmentEntity>

    @Query("DELETE FROM appointment_attachments")
    suspend fun deleteAll()
}
