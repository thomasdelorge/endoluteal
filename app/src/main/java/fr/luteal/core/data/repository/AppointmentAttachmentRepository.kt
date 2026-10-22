package fr.luteal.core.data.repository

import fr.luteal.core.model.professional.AppointmentAttachment
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

interface AppointmentAttachmentRepository {
    fun observeForEvent(eventId: String): Flow<List<AppointmentAttachment>>
    suspend fun forEventOnce(eventId: String): List<AppointmentAttachment>
    suspend fun get(id: String): AppointmentAttachment?
    suspend fun addFileFromStream(
        eventId: String,
        displayName: String,
        mimeType: String,
        input: InputStream
    ): AppointmentAttachment
    suspend fun addDerivedMarkdown(
        eventId: String,
        displayName: String,
        role: fr.luteal.core.model.professional.AppointmentAttachmentRole,
        markdown: String,
        sourceId: String
    ): AppointmentAttachment
    suspend fun delete(attachment: AppointmentAttachment)
    suspend fun deleteEventFiles(eventId: String)
    suspend fun allOnce(): List<AppointmentAttachment>
}
