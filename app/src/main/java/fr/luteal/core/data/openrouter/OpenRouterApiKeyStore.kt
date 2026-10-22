package fr.luteal.core.data.openrouter

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.luteal.core.data.security.KeystoreSecretStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterApiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val store = KeystoreSecretStore(
        context = context,
        fileName = "openrouter_secrets",
        keyAlias = "openrouter_api_key"
    )

    fun getApiKey(): String? = store.get(KEY)

    fun setApiKey(value: String?) {
        if (value.isNullOrBlank()) store.remove(KEY)
        else store.put(KEY, value.trim())
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    fun clear() {
        store.remove(KEY)
    }

    private companion object {
        const val KEY = "api_key"
    }
}
