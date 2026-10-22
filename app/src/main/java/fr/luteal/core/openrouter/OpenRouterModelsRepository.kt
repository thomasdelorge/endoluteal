package fr.luteal.core.openrouter

import fr.luteal.core.data.openrouter.OpenRouterApiKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class OpenRouterModelsRepository @Inject constructor(
    private val apiKeyStore: OpenRouterApiKeyStore
) {
    private val mutex = Mutex()
    private var cachedStt: List<OpenRouterModelInfo>? = null
    private var cachedText: List<OpenRouterModelInfo>? = null

    suspend fun transcriptionModels(forceRefresh: Boolean = false): List<OpenRouterModelInfo> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!forceRefresh && cachedStt != null) return@withContext cachedStt!!
                val client = OpenRouterModelsClient(apiKeyStore.getApiKey())
                cachedStt = client.listModels("transcription")
                cachedStt!!
            }
        }

    suspend fun textModels(forceRefresh: Boolean = false): List<OpenRouterModelInfo> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!forceRefresh && cachedText != null) return@withContext cachedText!!
                val client = OpenRouterModelsClient(apiKeyStore.getApiKey())
                cachedText = client.listModels("text")
                cachedText!!
            }
        }

    fun clearCache() {
        cachedStt = null
        cachedText = null
    }

    fun ensureSelectionInList(
        models: List<OpenRouterModelInfo>,
        selectedId: String
    ): List<OpenRouterModelInfo> {
        if (selectedId.isBlank()) return models
        if (models.any { it.id == selectedId }) return models
        return listOf(OpenRouterModelInfo(id = selectedId, name = selectedId)) + models
    }
}
