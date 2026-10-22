package fr.luteal.app.navigation.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class AppointmentAiContentTest {

    @Test
    fun stripGeneratedMarkdownHeader_keepsBodyAfterSeparator() {
        val raw = """
            # Résumé (aide à la rédaction)

            - Source : audio.wav
            - Modèle : test

            ---

            Contenu utile du résumé.
        """.trimIndent()
        assertEquals("Contenu utile du résumé.", stripGeneratedMarkdownHeader(raw))
    }
}
