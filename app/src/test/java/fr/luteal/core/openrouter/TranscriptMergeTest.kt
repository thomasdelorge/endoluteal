package fr.luteal.core.openrouter

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptMergeTest {
    @Test
    fun joinsWithSpaceWhenNoSentenceEnd() {
        val merged = TranscriptMerge.joinPlainSegments(listOf("Bonjour", "docteur"))
        assertEquals("Bonjour docteur", merged)
    }

    @Test
    fun joinsWithNewlineAfterSentence() {
        val merged = TranscriptMerge.joinPlainSegments(listOf("Bonjour.", "Suite"))
        assertEquals("Bonjour.\nSuite", merged)
    }

    @Test
    fun formatsSpeakers() {
        val text = TranscriptMerge.formatVerboseSegments(
            listOf(
                SttVerboseSegment("Bonjour", speaker = 0),
                SttVerboseSegment("Bonjour madame", speaker = 1)
            )
        )
        assert(text.contains("[Interlocuteur 0]"))
        assert(text.contains("[Interlocuteur 1]"))
    }
}
