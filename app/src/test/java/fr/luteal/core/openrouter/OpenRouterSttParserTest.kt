package fr.luteal.core.openrouter

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterSttParserTest {
    @Test
    fun parsesText() {
        val text = OpenRouterSttParser.parseText("""{"text":"Bonjour"}""")
        assertEquals("Bonjour", text)
    }

    @Test
    fun parsesVerbose() {
        val parsed = OpenRouterSttParser.parseVerbose(
            """
            {
              "text": "Hi",
              "segments": [
                {"text": "Hi", "speaker": 0}
              ]
            }
            """.trimIndent()
        )
        assertEquals("Hi", parsed.text)
        assertEquals(1, parsed.segments.size)
    }
}
