package fr.luteal.core.openrouter

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OpenRouterModelsClient(
    private val apiKey: String? = null
) {
    fun listModels(outputModality: String): List<OpenRouterModelInfo> {
        val all = mutableListOf<OpenRouterModelInfo>()
        var offset = 0
        val limit = 500
        while (true) {
            val page = fetchPage(outputModality, offset, limit)
            all.addAll(page)
            if (page.size < limit) break
            offset += limit
            if (offset > 10_000) break // ponytail: safety cap
        }
        return all.distinctBy { it.id }
    }

    private fun fetchPage(outputModality: String, offset: Int, limit: Int): List<OpenRouterModelInfo> {
        val encodedModality = URLEncoder.encode(outputModality, "UTF-8")
        val url = URL(
            "https://openrouter.ai/api/v1/models" +
                "?output_modalities=$encodedModality&limit=$limit&offset=$offset"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("HTTP-Referer", "https://endoluteal.local")
            setRequestProperty("X-Title", "Endoluteal")
            if (!apiKey.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw OpenRouterException("Impossible de charger les modèles (HTTP $code)")
        }
        return OpenRouterModelsParser.parseModels(body)
    }
}
