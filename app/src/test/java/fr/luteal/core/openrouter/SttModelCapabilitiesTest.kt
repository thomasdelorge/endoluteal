package fr.luteal.core.openrouter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttModelCapabilitiesTest {
    @Test
    fun parakeetNoDiarization() {
        assertFalse(SttModelCapabilities.supportsDiarization(OpenRouterDefaults.STT_MODEL))
    }

    @Test
    fun whisperSupportsDiarization() {
        assertTrue(SttModelCapabilities.supportsDiarization("openai/whisper-large-v3"))
    }
}
