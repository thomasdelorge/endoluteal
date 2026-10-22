package fr.luteal.app.navigation.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.BuildConfig
import fr.luteal.app.R
import fr.luteal.app.debug.AppointmentDemoAudio
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import kotlinx.coroutines.launch

@Composable
fun AppointmentAttachmentsSection(
    eventId: String?,
    selectedProfessionalId: String?,
    viewModel: AppointmentEntryViewModel,
    onEnsureSaved: suspend () -> String?
) {
    val context = LocalContext.current
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val job by viewModel.attachmentJob.collectAsStateWithLifecycle()
    val hasKey = viewModel.hasOpenRouterKey
    val aiEnabled by viewModel.openRouterAiEnabled.collectAsStateWithLifecycle()
    val canUseOpenRouter = hasKey && aiEnabled
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "piece_jointe"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        scope.launch {
            val id = eventId ?: onEnsureSaved() ?: return@launch
            resolver.openInputStream(uri)?.use { stream ->
                viewModel.addAttachment(id, name, mime, stream)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(
            stringResource(R.string.appointment_attachments_title),
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = selectedProfessionalId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.appointment_attachment_add))
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val id = eventId ?: onEnsureSaved() ?: return@launch
                        AppointmentDemoAudio.openDemoInputStream(context)?.use { stream ->
                            viewModel.addAttachment(
                                eventId = id,
                                displayName = AppointmentDemoAudio.DISPLAY_NAME,
                                mimeType = "audio/wav",
                                input = stream
                            )
                        }
                    }
                },
                enabled = selectedProfessionalId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.appointment_attachment_add_demo))
            }
            Text(
                stringResource(R.string.appointment_attachment_add_demo_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selectedProfessionalId == null) {
            Text(
                stringResource(R.string.appointment_attachment_need_professional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        attachments.filter { it.role == AppointmentAttachmentRole.FILE }.forEach { attachment ->
            AppointmentAttachmentTile(
                attachment = attachment,
                job = job,
                canUseOpenRouter = canUseOpenRouter,
                showManageActions = true,
                onDelete = { viewModel.deleteAttachment(attachment) },
                onTranscribe = { viewModel.transcribe(attachment) },
                onSummarize = { viewModel.summarize(attachment) }
            )
        }
    }
}
