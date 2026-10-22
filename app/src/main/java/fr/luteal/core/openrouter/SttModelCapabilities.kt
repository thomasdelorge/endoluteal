package fr.luteal.core.openrouter

enum class SttDiarizationMode {
    NONE,
    TOP_LEVEL_DIARIZE,
    AZURE_MAI_TRANSCRIBE_2
}

data class SttModelCapability(
    val supportsDiarization: Boolean,
    val diarizationMode: SttDiarizationMode
)

object SttModelCapabilities {
    private val byModel = mapOf(
        OpenRouterDefaults.STT_MODEL to SttModelCapability(false, SttDiarizationMode.NONE),
        "microsoft/mai-transcribe-2" to SttModelCapability(true, SttDiarizationMode.AZURE_MAI_TRANSCRIBE_2),
        "openai/whisper-large-v3" to SttModelCapability(true, SttDiarizationMode.TOP_LEVEL_DIARIZE)
    )

    fun forModel(modelSlug: String): SttModelCapability =
        byModel[modelSlug.trim()] ?: SttModelCapability(false, SttDiarizationMode.NONE)

    fun supportsDiarization(modelSlug: String): Boolean = forModel(modelSlug).supportsDiarization
}
