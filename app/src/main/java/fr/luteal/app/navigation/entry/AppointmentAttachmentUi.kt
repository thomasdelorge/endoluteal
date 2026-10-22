package fr.luteal.app.navigation.entry

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import java.text.DecimalFormat

fun openAppointmentAttachment(context: Context, attachment: AppointmentAttachment) {
    val file = context.filesDir.resolve(attachment.relativePath)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, attachment.displayName))
}

@Composable
fun AppointmentAttachmentTile(
    attachment: AppointmentAttachment,
    job: AttachmentJobState?,
    canUseOpenRouter: Boolean,
    showManageActions: Boolean,
    onDelete: (() -> Unit)?,
    onTranscribe: (() -> Unit)?,
    onSummarize: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val isAudio = attachment.mimeType.startsWith("audio/")
    val isTranscript = attachment.role == AppointmentAttachmentRole.TRANSCRIPT
    val running = job is AttachmentJobState.Running && job.attachmentId == attachment.id
    val error = job is AttachmentJobState.Error && job.attachmentId == attachment.id
    val subtitle = buildList {
        add(formatAttachmentSize(attachment.sizeBytes))
        if (attachment.role == AppointmentAttachmentRole.TRANSCRIPT) {
            add(stringResource(R.string.appointment_attachment_role_transcript))
        } else if (attachment.role == AppointmentAttachmentRole.SUMMARY) {
            add(stringResource(R.string.appointment_attachment_role_summary))
        }
    }.joinToString(" · ")

    LutealCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { openAppointmentAttachment(context, attachment) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LutealSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LutealSpacing.xxs)) {
                Text(attachment.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (running && job is AttachmentJobState.Running) {
                    val label = if (job.segmentCount > 1) {
                        stringResource(
                            R.string.appointment_attachment_progress_segments,
                            job.segmentIndex,
                            job.segmentCount
                        )
                    } else {
                        stringResource(R.string.appointment_attachment_progress)
                    }
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                if (error && job is AttachmentJobState.Error) {
                    Text(
                        job.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (showManageActions) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.appointment_attachment_more_actions)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.appointment_attachment_open)) },
                            onClick = {
                                menuOpen = false
                                openAppointmentAttachment(context, attachment)
                            }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appointment_attachment_delete)) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                        if (isAudio && canUseOpenRouter && onTranscribe != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appointment_attachment_transcribe)) },
                                onClick = {
                                    menuOpen = false
                                    onTranscribe()
                                },
                                enabled = !running
                            )
                        }
                        if (isTranscript && canUseOpenRouter && onSummarize != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.appointment_attachment_summarize)) },
                                onClick = {
                                    menuOpen = false
                                    onSummarize()
                                },
                                enabled = !running
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatAttachmentSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes o"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalFormat("#.#").format(kb)} Ko"
    val mb = kb / 1024.0
    return "${DecimalFormat("#.#").format(mb)} Mo"
}
