package fr.luteal.core.data.appointment

import fr.luteal.core.data.entity.AppointmentAttachmentEntity
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import java.time.Instant

fun AppointmentAttachmentEntity.toModel(): AppointmentAttachment = AppointmentAttachment(
    id = id,
    eventId = eventId,
    role = AppointmentAttachmentRole.fromStored(role),
    displayName = displayName,
    mimeType = mimeType,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    createdAt = Instant.ofEpochMilli(createdAt),
    sourceId = sourceId
)

fun AppointmentAttachment.toEntity(): AppointmentAttachmentEntity = AppointmentAttachmentEntity(
    id = id,
    eventId = eventId,
    role = role.toStored(),
    displayName = displayName,
    mimeType = mimeType,
    relativePath = relativePath,
    sizeBytes = sizeBytes,
    createdAt = createdAt.toEpochMilli(),
    sourceId = sourceId
)
