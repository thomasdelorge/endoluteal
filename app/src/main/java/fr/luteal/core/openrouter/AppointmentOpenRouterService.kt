package fr.luteal.core.openrouter

import android.content.Context
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.data.appointment.AppointmentAttachmentStorage
import fr.luteal.core.data.openrouter.OpenRouterApiKeyStore
import fr.luteal.core.data.repository.AppointmentAttachmentRepository
import fr.luteal.core.media.AppointmentAudioChunker
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import java.io.File
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppointmentOpenRouterService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyStore: OpenRouterApiKeyStore,
    private val attachmentRepository: AppointmentAttachmentRepository,
    private val attachmentStorage: AppointmentAttachmentStorage,
    private val clock: Clock
) {
    private val chunker = AppointmentAudioChunker()

    suspend fun transcribe(
        attachment: AppointmentAttachment,
        sttModel: String,
        language: String,
        diarizationEnabled: Boolean,
        onProgress: (segmentIndex: Int, segmentCount: Int) -> Unit = { _, _ -> }
    ): AppointmentAttachment = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getApiKey() ?: throw OpenRouterException("Clé OpenRouter manquante")
        val sourceFile = attachmentStorage.fileFor(attachment.relativePath)
        if (!sourceFile.exists()) throw OpenRouterException("Fichier audio introuvable")

        val durationMs = readDurationMs(sourceFile)
        val plan = AudioTranscriptionPlan.plan(sourceFile.length(), durationMs)
        val capability = SttModelCapabilities.forModel(sttModel)
        val useDiarization = diarizationEnabled && capability.supportsDiarization
        val diarizationRequest = if (useDiarization) {
            SttDiarizationRequest(capability.diarizationMode)
        } else {
            null
        }
        val useVerbose = diarizationRequest != null

        val jobDir = File(context.cacheDir, "appointment-stt/${attachment.id}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val chunks = if (plan.chunkCount <= 1) {
                listOf(sourceFile)
            } else {
                runCatching {
                    chunker.split(sourceFile, jobDir, plan.chunkDurationSec, plan.chunkCount)
                }.getOrElse {
                    throw OpenRouterException(
                        "Fichier trop long ou volumineux pour ce format. Essayez MP3 ou M4A."
                    )
                }
            }

            val client = OpenRouterClient(apiKey)
            val parts = mutableListOf<String>()
            chunks.forEachIndexed { index, chunk ->
                onProgress(index + 1, chunks.size)
                val body = client.transcribeMultipart(
                    audioFile = chunk,
                    mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                    model = sttModel,
                    language = language,
                    diarization = diarizationRequest
                )
                val text = if (useVerbose) {
                    OpenRouterSttParser.parseVerbose(body).text
                } else {
                    OpenRouterSttParser.parseText(body)
                }
                parts.add(text)
            }
            val merged = TranscriptMerge.joinChunkTranscripts(parts)
            val header = AppointmentMarkdownBuilder.transcriptHeader(
                sourceName = attachment.displayName,
                model = sttModel,
                language = language,
                diarization = useDiarization,
                segmentCount = chunks.size,
                createdAt = clock.instant()
            )
            val fileName = transcriptFileName(attachment.displayName)
            return@withContext attachmentRepository.addDerivedMarkdown(
                eventId = attachment.eventId,
                displayName = fileName,
                role = AppointmentAttachmentRole.TRANSCRIPT,
                markdown = header + merged.trim() + "\n",
                sourceId = attachment.id
            )
        } finally {
            jobDir.deleteRecursively()
        }
    }

    suspend fun summarize(
        transcript: AppointmentAttachment,
        summaryModel: String
    ): AppointmentAttachment = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.getApiKey() ?: throw OpenRouterException("Clé OpenRouter manquante")
        val file = attachmentStorage.fileFor(transcript.relativePath)
        val transcriptBody = file.readText()
        val client = OpenRouterClient(apiKey)
        val system = """
            Tu aides à rédiger un brouillon de compte-rendu de consultation en français.
            Ne invente aucun fait absent du transcript.
            Ce n'est pas un avis médical.
            Si le transcript distingue des interlocuteurs, structure le brouillon en séparant
            ce qui relève du professionnel de santé et de la patiente uniquement quand c'est évident.
            Sinon reste neutre.
        """.trimIndent()
        val user = "Transcript :\n\n$transcriptBody"
        val body = client.summarize(summaryModel, system, user)
        val summaryText = OpenRouterChatParser.parseAssistantText(body)
        val header = AppointmentMarkdownBuilder.summaryHeader(
            sourceName = transcript.displayName,
            model = summaryModel,
            createdAt = clock.instant()
        )
        val fileName = summaryFileName(transcript.displayName)
        return@withContext attachmentRepository.addDerivedMarkdown(
            eventId = transcript.eventId,
            displayName = fileName,
            role = AppointmentAttachmentRole.SUMMARY,
            markdown = header + summaryText.trim() + "\n",
            sourceId = transcript.id
        )
    }

    private fun readDurationMs(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun transcriptFileName(sourceDisplayName: String): String {
        val base = sourceDisplayName.substringBeforeLast('.').ifBlank { "audio" }
        return "$base-transcript.md"
    }

    private fun summaryFileName(transcriptName: String): String {
        val base = transcriptName.removeSuffix(".md").substringBeforeLast('-')
        return "$base-resume.md"
    }
}
