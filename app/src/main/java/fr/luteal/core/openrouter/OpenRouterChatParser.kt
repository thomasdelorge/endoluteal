package fr.luteal.core.openrouter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenRouterChatParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseAssistantText(body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "Erreur résumé"
            throw OpenRouterException(msg)
        }
        val choices = root["choices"]?.jsonArray ?: throw OpenRouterException("Réponse résumé invalide")
        val first = choices.firstOrNull()?.jsonObject ?: throw OpenRouterException("Réponse résumé vide")
        val message = first["message"]?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
            ?: throw OpenRouterException("Réponse résumé vide")
        return content
    }
}
