package fr.luteal.core.openrouter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenRouterSttParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseText(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "Erreur transcription"
            throw OpenRouterException(msg)
        }
        return root["text"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw OpenRouterException("Réponse transcription invalide")
    }

    fun parseVerbose(body: String): ParsedVerboseTranscript {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "Erreur transcription"
            throw OpenRouterException(msg)
        }
        val segments = root["segments"]?.jsonArray?.mapNotNull { el -> parseSegment(el.jsonObject) }
            ?: emptyList()
        val text = root["text"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: TranscriptMerge.formatVerboseSegments(segments).ifBlank {
                segments.map { it.text }.joinToString(" ")
            }
        return ParsedVerboseTranscript(text = text.trim(), segments = segments)
    }

    private fun parseSegment(obj: JsonObject): SttVerboseSegment? {
        val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim() ?: return null
        val speaker = obj["speaker"]?.jsonPrimitive?.intOrNull
        val speakerLabel = obj["speaker_label"]?.jsonPrimitive?.contentOrNull
        return SttVerboseSegment(text = text, speaker = speaker, speakerLabel = speakerLabel)
    }
}

data class ParsedVerboseTranscript(
    val text: String,
    val segments: List<SttVerboseSegment>
)

class OpenRouterException(message: String) : Exception(message)
