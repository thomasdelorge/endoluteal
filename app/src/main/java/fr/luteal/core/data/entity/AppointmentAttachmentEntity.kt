package fr.luteal.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointment_attachments",
    foreignKeys = [
        ForeignKey(
            entity = JournalEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AppointmentAttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("eventId"),
        Index("sourceId")
    ]
)
data class AppointmentAttachmentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val role: String,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val sourceId: String?
)
