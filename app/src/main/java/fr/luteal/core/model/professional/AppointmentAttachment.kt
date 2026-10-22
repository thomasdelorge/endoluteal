package fr.luteal.core.model.professional

import java.time.Instant

data class AppointmentAttachment(
    val id: String,
    val eventId: String,
    val role: AppointmentAttachmentRole,
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val sizeBytes: Long,
    val createdAt: Instant,
    val sourceId: String? = null
)
