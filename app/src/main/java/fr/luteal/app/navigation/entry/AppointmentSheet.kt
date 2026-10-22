package fr.luteal.app.navigation.entry

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import fr.luteal.core.designsystem.component.LutealTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.DateTimeField
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.ReimbursementStatus
import fr.luteal.core.model.professional.Specialty
import fr.luteal.core.model.professional.formatCents
import fr.luteal.core.model.professional.parseEurosToCents
import fr.luteal.core.model.professional.remainingChargeCents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentSheet(
    initialDate: LocalDate,
    existingEventId: String?,
    viewModel: AppointmentEntryViewModel,
    onDismiss: () -> Unit
) {
    val professionals by viewModel.professionals.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val attachmentJob by viewModel.attachmentJob.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.openRouterAiEnabled.collectAsStateWithLifecycle()
    val canUseOpenRouter = viewModel.hasOpenRouterKey && aiEnabled
    val zone = remember { ZoneId.systemDefault() }
    var startAt by remember {
        mutableStateOf(
            if (initialDate == LocalDate.now(zone)) LocalDateTime.now(zone)
            else initialDate.atTime(9, 0)
        )
    }
    var endAt by remember { mutableStateOf(startAt.plusHours(1)) }
    var selectedProfessionalId by remember { mutableStateOf<String?>(null) }
    var importedTitle by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var reimbursementStatus by remember { mutableStateOf(ReimbursementStatus.UNSPECIFIED) }
    var cpamText by remember { mutableStateOf("") }
    var mutuelleText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showNewProfessional by remember { mutableStateOf(false) }
    var mapsUnavailable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var persistedEventId by remember { mutableStateOf(existingEventId) }

    LaunchedEffect(existingEventId) {
        persistedEventId = existingEventId
    }
    LaunchedEffect(persistedEventId) {
        viewModel.watchAttachments(persistedEventId)
    }

    suspend fun ensureSavedForAttachments(): String? {
        val professionalId = selectedProfessionalId ?: return null
        val id = viewModel.save(
            eventId = persistedEventId,
            professionalId = professionalId,
            startAt = startAt.atZone(zone).toInstant(),
            endAt = endAt.atZone(zone).toInstant(),
            zoneId = zone,
            title = importedTitle,
            location = location,
            description = description,
            report = report,
            feeCents = parseEurosToCents(feeText),
            reimbursementStatus = reimbursementStatus,
            cpamCents = parseEurosToCents(cpamText),
            mutuelleCents = parseEurosToCents(mutuelleText)
        )
        persistedEventId = id
        return id
    }

    LaunchedEffect(existingEventId, professionals) {
        val id = existingEventId ?: return@LaunchedEffect
        val event = viewModel.getEvent(id)
        val appointment = viewModel.getAppointment(id)
        if (event != null) {
            startAt = LocalDateTime.ofInstant(event.startAt, zone)
            importedTitle = event.notes
            if (event.endAt != null) {
                endAt = LocalDateTime.ofInstant(event.endAt, zone)
            }
        }
        if (appointment != null) {
            selectedProfessionalId = appointment.professionalId.takeIf { it.isNotBlank() }
            location = appointment.location
            description = appointment.description
            report = appointment.report
            feeText = appointment.feeCents?.let(::formatCents).orEmpty()
            reimbursementStatus = appointment.reimbursementStatus
            cpamText = appointment.cpamCents?.let(::formatCents).orEmpty()
            mutuelleText = appointment.mutuelleCents?.let(::formatCents).orEmpty()
        }
    }

    if (confirmDelete && persistedEventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.appointment_delete_title)) },
            text = { Text(stringResource(R.string.appointment_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.delete(persistedEventId!!)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.appointment_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }

    if (showNewProfessional) {
        NewProfessionalDialog(
            onDismiss = { showNewProfessional = false },
            onSave = { name, specialty, custom ->
                scope.launch {
                    selectedProfessionalId = viewModel.createProfessional(name, specialty, custom)
                    showNewProfessional = false
                }
            }
        )
    }

    EntryScaffold(
        onBack = onDismiss,
        confirmEnabled = selectedProfessionalId != null,
        onDelete = if (persistedEventId != null) {
            { confirmDelete = true }
        } else {
            null
        },
        onConfirm = {
            val professionalId = selectedProfessionalId ?: return@EntryScaffold
            scope.launch {
                viewModel.save(
                    eventId = persistedEventId,
                    professionalId = professionalId,
                    startAt = startAt.atZone(zone).toInstant(),
                    endAt = endAt.atZone(zone).toInstant(),
                    zoneId = zone,
                    title = importedTitle,
                    location = location,
                    description = description,
                    report = report,
                    feeCents = parseEurosToCents(feeText),
                    reimbursementStatus = reimbursementStatus,
                    cpamCents = parseEurosToCents(cpamText),
                    mutuelleCents = parseEurosToCents(mutuelleText)
                )
                onDismiss()
            }
        },
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.appointment_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) {
            // Header — title, professional, location and description can all be
            // edited locally and override the calendar on the next sync.
            Text(stringResource(R.string.appointment_section_header), style = MaterialTheme.typography.titleMedium)
            LutealTextField(
                value = importedTitle,
                onValueChange = { importedTitle = it },
                label = { Text(stringResource(R.string.appointment_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            ProfessionalPickerField(
                professionals = professionals,
                selectedId = selectedProfessionalId,
                onSelected = { selectedProfessionalId = it }
            )
            TextButton(onClick = { showNewProfessional = true }) {
                Text(stringResource(R.string.appointment_add_professional))
            }
            val mapsChooserTitle = stringResource(R.string.appointment_open_maps)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LutealTextField(
                    value = location,
                    onValueChange = {
                        location = it
                        mapsUnavailable = false
                    },
                    label = { Text(stringResource(R.string.appointment_location)) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val query = Uri.encode(location.trim())
                        val view = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
                        try {
                            context.startActivity(Intent.createChooser(view, mapsChooserTitle))
                        } catch (_: ActivityNotFoundException) {
                            mapsUnavailable = true
                        }
                    },
                    enabled = location.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Place, contentDescription = stringResource(R.string.appointment_open_maps))
                }
            }
            if (mapsUnavailable) {
                Text(stringResource(R.string.appointment_maps_unavailable))
            }
            LutealTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.appointment_description)) },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.sm))

            // Quand — date and time.
            Text(stringResource(R.string.appointment_section_when), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.appointment_start))
            DateTimeField(value = startAt, onValueChange = { startAt = it })
            Text(stringResource(R.string.appointment_end))
            DateTimeField(value = endAt, onValueChange = { endAt = it })

            HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.sm))

            // Remboursement — cost and reimbursements.
            Text(stringResource(R.string.appointment_section_reimbursement), style = MaterialTheme.typography.titleMedium)
            LutealTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text(stringResource(R.string.appointment_fee)) },
                modifier = Modifier.fillMaxWidth()
            )
            SettingsDropdownField(
                label = stringResource(R.string.appointment_reimbursement),
                selectedLabel = reimbursementLabel(reimbursementStatus),
                options = ReimbursementStatus.entries.map { status ->
                    status.name to reimbursementLabel(status)
                },
                onSelect = { reimbursementStatus = ReimbursementStatus.fromStored(it) }
            )
            LutealTextField(
                value = cpamText,
                onValueChange = { cpamText = it },
                label = { Text(stringResource(R.string.appointment_cpam)) },
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = mutuelleText,
                onValueChange = { mutuelleText = it },
                label = { Text(stringResource(R.string.appointment_mutuelle)) },
                modifier = Modifier.fillMaxWidth()
            )
            val remaining = remainingChargeCents(
                parseEurosToCents(feeText),
                parseEurosToCents(cpamText),
                parseEurosToCents(mutuelleText)
            )
            if (remaining != null) {
                Text(stringResource(R.string.appointment_remaining, formatCents(remaining)))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.sm))

            AppointmentAttachmentsSection(
                eventId = persistedEventId,
                selectedProfessionalId = selectedProfessionalId,
                viewModel = viewModel,
                onEnsureSaved = { ensureSavedForAttachments() }
            )

            AppointmentAiDerivedSections(
                attachments = attachments,
                canUseOpenRouter = canUseOpenRouter,
                summarizeRunning = attachmentJob is AttachmentJobState.Running,
                onSummarizeTranscript = { viewModel.summarize(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = LutealSpacing.sm))

            // CR — the clinical report, free-form multiline.
            Text(stringResource(R.string.appointment_section_report), style = MaterialTheme.typography.titleMedium)
            LutealTextField(
                value = report,
                onValueChange = { report = it },
                label = { Text(stringResource(R.string.appointment_report)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
    }
}

@Composable
private fun reimbursementLabel(status: ReimbursementStatus): String = when (status) {
    ReimbursementStatus.UNSPECIFIED -> stringResource(R.string.appointment_reimbursement_empty)
    ReimbursementStatus.IN_PROGRESS -> stringResource(R.string.appointment_reimbursement_pending)
    ReimbursementStatus.NO -> stringResource(R.string.appointment_reimbursement_no)
    ReimbursementStatus.DONE -> stringResource(R.string.appointment_reimbursement_done)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewProfessionalDialog(
    onDismiss: () -> Unit,
    onSave: (String, Specialty, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf(Specialty.GYNECOLOGIST) }
    var customSpecialty by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appointment_new_professional_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.professional_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    Specialty.entries.forEach { item ->
                        FilterChip(
                            selected = specialty == item,
                            onClick = { specialty = item },
                            label = { Text(item.label) }
                        )
                    }
                }
                if (specialty == Specialty.OTHER) {
                    LutealTextField(
                        value = customSpecialty,
                        onValueChange = { customSpecialty = it },
                        label = { Text(stringResource(R.string.professional_custom_specialty)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(name, specialty, customSpecialty)
            }) { Text(stringResource(R.string.meds_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_cancel)) }
        }
    )
}
