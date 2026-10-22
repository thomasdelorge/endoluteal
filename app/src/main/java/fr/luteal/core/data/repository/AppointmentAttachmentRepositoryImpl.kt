package fr.luteal.core.data.repository

import fr.luteal.core.data.appointment.AppointmentAttachmentStorage
import fr.luteal.core.data.appointment.toEntity
import fr.luteal.core.data.appointment.toModel
import fr.luteal.core.data.local.AppointmentAttachmentDao
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import java.io.InputStream
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AppointmentAttachmentRepositoryImpl @Inject constructor(
    private val dao: AppointmentAttachmentDao,
    private val storage: AppointmentAttachmentStorage,
    private val clock: Clock
) : AppointmentAttachmentRepository {

    override fun observeForEvent(eventId: String): Flow<List<AppointmentAttachment>> =
        dao.observeForEvent(eventId).map { list -> list.map { it.toModel() } }

    override suspend fun forEventOnce(eventId: String): List<AppointmentAttachment> =
        dao.forEventOnce(eventId).map { it.toModel() }

    override suspend fun get(id: String): AppointmentAttachment? =
        dao.get(id)?.toModel()

    override suspend fun addFileFromStream(
        eventId: String,
        displayName: String,
        mimeType: String,
        input: InputStream
    ): AppointmentAttachment {
        val stored = storage.copyFromStream(eventId, displayName, mimeType, input)
        val now = clock.instant()
        val attachment = AppointmentAttachment(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            role = AppointmentAttachmentRole.FILE,
            displayName = displayName,
            mimeType = stored.mimeType,
            relativePath = stored.relativePath,
            sizeBytes = stored.sizeBytes,
            createdAt = now,
            sourceId = null
        )
        dao.upsert(attachment.toEntity())
        return attachment
    }

    override suspend fun addDerivedMarkdown(
        eventId: String,
        displayName: String,
        role: AppointmentAttachmentRole,
        markdown: String,
        sourceId: String
    ): AppointmentAttachment {
        val stored = storage.writeText(
            eventId = eventId,
            fileName = displayName,
            mimeType = "text/markdown",
            bytes = markdown.encodeToByteArray()
        )
        val now = clock.instant()
        val attachment = AppointmentAttachment(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            role = role,
            displayName = displayName,
            mimeType = stored.mimeType,
            relativePath = stored.relativePath,
            sizeBytes = stored.sizeBytes,
            createdAt = now,
            sourceId = sourceId
        )
        dao.upsert(attachment.toEntity())
        return attachment
    }

    override suspend fun delete(attachment: AppointmentAttachment) {
        dao.deleteById(attachment.id)
        storage.deleteFile(attachment.relativePath)
    }

    override suspend fun deleteEventFiles(eventId: String) {
        dao.deleteForEvent(eventId)
        storage.deleteEventFiles(eventId)
    }

    override suspend fun allOnce(): List<AppointmentAttachment> =
        dao.allOnce().map { it.toModel() }
}
