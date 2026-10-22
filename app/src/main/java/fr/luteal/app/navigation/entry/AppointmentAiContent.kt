package fr.luteal.app.navigation.entry

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import java.io.File

fun stripGeneratedMarkdownHeader(markdown: String): String {
    val idx = markdown.indexOf("\n---\n")
    return if (idx >= 0) markdown.substring(idx + 5).trim() else markdown.trim()
}

fun readAttachmentBody(context: Context, attachment: AppointmentAttachment): String? {
    val file = File(context.filesDir, attachment.relativePath)
    if (!file.exists()) return null
    return runCatching { stripGeneratedMarkdownHeader(file.readText()) }.getOrNull()
}

@Composable
fun AppointmentAiDerivedSections(
    attachments: List<AppointmentAttachment>,
    canUseOpenRouter: Boolean = false,
    summarizeRunning: Boolean = false,
    onSummarizeTranscript: ((AppointmentAttachment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val transcript = attachments
        .filter { it.role == AppointmentAttachmentRole.TRANSCRIPT }
        .maxByOrNull { it.createdAt }
    val summary = attachments
        .filter { it.role == AppointmentAttachmentRole.SUMMARY }
        .maxByOrNull { it.createdAt }
    if (transcript == null && summary == null) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        transcript?.let { attachment ->
            val body by produceState<String?>(null, attachment.id) {
                value = readAttachmentBody(context, attachment)
            }
            if (!body.isNullOrBlank()) {
                AiTextCard(
                    title = stringResource(R.string.appointment_ai_transcript_section),
                    body = body!!
                )
                if (canUseOpenRouter && onSummarizeTranscript != null) {
                    OutlinedButton(
                        onClick = { onSummarizeTranscript(attachment) },
                        enabled = !summarizeRunning
                    ) {
                        Text(stringResource(R.string.appointment_attachment_summarize))
                    }
                }
            }
        }
        summary?.let { attachment ->
            val body by produceState<String?>(null, attachment.id) {
                value = readAttachmentBody(context, attachment)
            }
            if (!body.isNullOrBlank()) {
                AiTextCard(
                    title = stringResource(R.string.appointment_ai_summary_section),
                    body = body!!
                )
            }
        }
    }
}

@Composable
private fun AiTextCard(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LutealCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(LutealSpacing.md)
            )
        }
        Text(
            stringResource(R.string.appointment_ai_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
