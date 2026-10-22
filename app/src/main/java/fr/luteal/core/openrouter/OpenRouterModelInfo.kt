package fr.luteal.core.openrouter

data class OpenRouterModelInfo(
    val id: String,
    val name: String
) {
    fun displayLabel(): String = if (name.isBlank() || name == id) id else "$name — $id"
}
