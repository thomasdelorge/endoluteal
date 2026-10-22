package fr.luteal.core.openrouter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenRouterModelsParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseModels(body: String): List<OpenRouterModelInfo> {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.jsonObject?.let { err ->
            val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "Erreur modèles"
            throw OpenRouterException(msg)
        }
        val data = root["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            OpenRouterModelInfo(id = id, name = name)
        }.sortedBy { it.id.lowercase() }
    }
}
