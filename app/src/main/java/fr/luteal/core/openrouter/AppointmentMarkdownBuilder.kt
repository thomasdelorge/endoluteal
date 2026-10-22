package fr.luteal.core.openrouter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppointmentMarkdownBuilder {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun transcriptHeader(
        sourceName: String,
        model: String,
        language: String,
        diarization: Boolean,
        segmentCount: Int,
        createdAt: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val whenLabel = createdAt.atZone(zoneId).format(formatter)
        return buildString {
            appendLine("# Transcription")
            appendLine()
            appendLine("- Source : $sourceName")
            appendLine("- Modèle : $model")
            appendLine("- Langue : $language")
            appendLine("- Interlocuteurs : ${if (diarization) "oui" else "non"}")
            if (segmentCount > 1) appendLine("- Segments audio : $segmentCount")
            appendLine("- Généré le : $whenLabel")
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    fun summaryHeader(
        sourceName: String,
        model: String,
        createdAt: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val whenLabel = createdAt.atZone(zoneId).format(formatter)
        return buildString {
            appendLine("# Résumé (aide à la rédaction)")
            appendLine()
            appendLine("- Source : $sourceName")
            appendLine("- Modèle : $model")
            appendLine("- Généré le : $whenLabel")
            appendLine()
            appendLine("_Ce texte est une aide à la rédaction, pas un avis médical._")
            appendLine()
            appendLine("---")
            appendLine()
        }
    }
}
