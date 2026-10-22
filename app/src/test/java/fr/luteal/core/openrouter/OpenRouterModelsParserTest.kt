package fr.luteal.core.openrouter

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterModelsParserTest {
    @Test
    fun parsesModelList() {
        val models = OpenRouterModelsParser.parseModels(
            """
            {
              "data": [
                {"id": "nvidia/parakeet-tdt-0.6b-v3", "name": "Parakeet"},
                {"id": "openai/whisper-1", "name": "Whisper"}
              ]
            }
            """.trimIndent()
        )
        assertEquals(2, models.size)
        assertEquals("nvidia/parakeet-tdt-0.6b-v3", models[0].id)
    }
}
