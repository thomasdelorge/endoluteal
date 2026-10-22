package fr.luteal.core.openrouter

object TranscriptMerge {
    fun joinPlainSegments(parts: List<String>): String {
        if (parts.isEmpty()) return ""
        val builder = StringBuilder()
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (builder.isEmpty()) {
                builder.append(trimmed)
                continue
            }
            val last = builder.last()
            val endsSentence = last == '.' || last == '!' || last == '?' || last == '…'
            if (endsSentence) builder.append('\n')
            else builder.append(' ')
            builder.append(trimmed)
        }
        return builder.toString()
    }

    fun formatVerboseSegments(segments: List<SttVerboseSegment>): String {
        if (segments.isEmpty()) return ""
        val lines = mutableListOf<String>()
        var lastSpeaker: String? = null
        val buffer = StringBuilder()
        for (segment in segments) {
            val speaker = segment.speakerLabel ?: segment.speaker?.let { "Interlocuteur $it" } ?: ""
            val text = segment.text.trim()
            if (text.isEmpty()) continue
            if (speaker.isNotEmpty() && speaker != lastSpeaker) {
                if (buffer.isNotEmpty()) {
                    lines.add(buffer.toString().trim())
                    buffer.clear()
                }
                buffer.append("[$speaker] ").append(text)
                lastSpeaker = speaker
            } else {
                if (buffer.isNotEmpty()) buffer.append(' ')
                buffer.append(text)
            }
        }
        if (buffer.isNotEmpty()) lines.add(buffer.toString().trim())
        return lines.joinToString("\n\n")
    }

    fun joinChunkTranscripts(chunks: List<String>): String = joinPlainSegments(chunks)
}

data class SttVerboseSegment(
    val text: String,
    val speaker: Int? = null,
    val speakerLabel: String? = null
)
