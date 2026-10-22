package fr.luteal.app.navigation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.professional.Appointment
import fr.luteal.core.model.professional.AppointmentAttachment
import fr.luteal.core.model.professional.AppointmentAttachmentRole
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.professional.ReimbursementStatus
import fr.luteal.core.model.professional.formatCents
import fr.luteal.core.model.professional.remainingChargeCents
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Read-only view of a medical appointment shown when the user taps an
 * appointment from the day recap. An edit button opens the full editor.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDetailSheet(
    event: JournalEvent,
    appointment: Appointment,
    professional: Professional?,
    attachments: List<AppointmentAttachment> = emptyList(),
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.of(event.zoneId) }
    val whenLabel = LocalDateTime.ofInstant(event.startAt, zone)
        .let(LocalizedDateFormatter::formatNumericDateTime).let { label ->
            event.endAt?.let { end ->
                val endLabel = LocalDateTime.ofInstant(end, zone)
                    .let(LocalizedDateFormatter::formatNumericDateTime)
                "$label → $endLabel"
            } ?: label
        }
    val remaining = remainingChargeCents(
        appointment.feeCents,
        appointment.cpamCents,
        appointment.mutuelleCents
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LutealSpacing.md, vertical = LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            Text(
                text = event.notes.ifBlank { stringResource(R.string.appointment_title) },
                style = MaterialTheme.typography.titleLarge
            )
            DetailLine(stringResource(R.string.appointment_when), whenLabel)
            professional?.let {
                DetailLine(stringResource(R.string.appointment_professional), "${it.name} · ${it.displaySpecialty()}")
            }
            if (appointment.location.isNotBlank()) {
                DetailLine(stringResource(R.string.appointment_location), appointment.location)
            }
            if (appointment.description.isNotBlank()) {
                DetailLine(stringResource(R.string.appointment_description), appointment.description)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.xs))
            appointment.feeCents?.let { DetailLine(stringResource(R.string.appointment_fee), formatCents(it)) }
            if (appointment.reimbursementStatus != ReimbursementStatus.UNSPECIFIED) {
                DetailLine(
                    stringResource(R.string.appointment_reimbursement),
                    reimbursementLabel(appointment.reimbursementStatus)
                )
            }
            appointment.cpamCents?.let { DetailLine(stringResource(R.string.appointment_cpam), formatCents(it)) }
            appointment.mutuelleCents?.let { DetailLine(stringResource(R.string.appointment_mutuelle), formatCents(it)) }
            remaining?.let { DetailLine(stringResource(R.string.appointment_remaining), formatCents(it)) }
            AppointmentAiDerivedSections(attachments = attachments)

            val fileAttachments = attachments.filter { it.role == AppointmentAttachmentRole.FILE }
            if (fileAttachments.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.xs))
                Text(stringResource(R.string.appointment_attachments_title), style = MaterialTheme.typography.titleMedium)
                fileAttachments.forEach { attachment ->
                    AppointmentAttachmentTile(
                        attachment = attachment,
                        job = null,
                        canUseOpenRouter = false,
                        showManageActions = false,
                        onDelete = null,
                        onTranscribe = null,
                        onSummarize = null
                    )
                }
            }
            if (appointment.report.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.xs))
                Text(stringResource(R.string.appointment_section_report), style = MaterialTheme.typography.titleMedium)
                Text(appointment.report, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_close))
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.timeline_edit_appointment))
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        text = "$label : $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun reimbursementLabel(status: ReimbursementStatus): String = when (status) {
    ReimbursementStatus.UNSPECIFIED -> stringResource(R.string.appointment_reimbursement_empty)
    ReimbursementStatus.IN_PROGRESS -> stringResource(R.string.appointment_reimbursement_pending)
    ReimbursementStatus.NO -> stringResource(R.string.appointment_reimbursement_no)
    ReimbursementStatus.DONE -> stringResource(R.string.appointment_reimbursement_done)
}