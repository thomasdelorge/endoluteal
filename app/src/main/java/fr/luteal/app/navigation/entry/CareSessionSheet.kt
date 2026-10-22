package fr.luteal.app.navigation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import fr.luteal.core.designsystem.component.LutealTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.DateTimeField
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.component.PainScale
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.EventPayload
import fr.luteal.core.model.care.CareType
import fr.luteal.core.model.care.CareTypeSeed
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareSessionSheet(
    initialDate: LocalDate,
    existingEventId: String?,
    tensOnly: Boolean = false,
    viewModel: CareSessionEntryViewModel,
    onDismiss: () -> Unit
) {
    val careTypes by viewModel.careTypes.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var at by remember {
        mutableStateOf(
            if (initialDate == LocalDate.now(zone)) LocalDateTime.now(zone)
            else initialDate.atTime(LocalDateTime.now(zone).toLocalTime())
        )
    }
    var selectedTypeId by remember { mutableStateOf<String?>(null) }
    var programName by remember { mutableStateOf("") }
    var durationMinutes by remember { mutableStateOf("") }
    var painBefore by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var followup by remember { mutableStateOf<CareSaveResult?>(null) }
    var scheduleReminder by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(careTypes, existingEventId, tensOnly) {
        if (tensOnly) {
            selectedTypeId = CareTypeSeed.TENS_ID
        } else if (existingEventId == null && selectedTypeId == null && careTypes.isNotEmpty()) {
            selectedTypeId = careTypes.first().id
        }
    }

    LaunchedEffect(existingEventId, careTypes) {
        val id = existingEventId ?: return@LaunchedEffect
        val event = viewModel.get(id)
        if (event != null) {
            at = LocalDateTime.ofInstant(event.startAt, zone)
            notes = event.notes
            val payload = event.payload as? EventPayload.CareSession
            if (payload != null) {
                selectedTypeId = payload.careTypeId.ifBlank { careTypes.firstOrNull()?.id }
                programName = payload.programName
                durationMinutes = if (payload.durationMinutes > 0) payload.durationMinutes.toString() else ""
                painBefore = payload.painBefore
            }
        }
    }

    val selectedType = careTypes.find { it.id == selectedTypeId } ?: careTypes.firstOrNull()
    val isTens = selectedType?.id == CareTypeSeed.TENS_ID

    if (confirmDelete && existingEventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.care_delete_title)) },
            text = { Text(stringResource(R.string.care_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.delete(existingEventId)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.care_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }

    val saved = followup
    if (saved != null) {
        val whenLabel = LocalizedDateFormatter.formatNumericDateTime(
            saved.endAt.plus(java.time.Duration.ofHours(1)).atZone(zone).toLocalDateTime()
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.care_followup_title)) },
            text = {
                Text(
                    if (tensOnly) {
                        stringResource(R.string.care_tens_followup_body, whenLabel)
                    } else {
                        stringResource(R.string.care_followup_body, whenLabel)
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.schedulePainFollowup(saved.eventId, saved.endAt)
                    onDismiss()
                }) { Text(stringResource(R.string.care_followup_schedule)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.care_followup_skip)) }
            }
        )
    }

    val duration = durationMinutes.toIntOrNull() ?: 0
    val tensDurations = setOf(30, 60, 90, 120)
    val namedProgram = programName.trim()
    val programOk = when {
        tensOnly && selectedType?.programs?.isNotEmpty() == true -> namedProgram.isNotEmpty()
        isTens -> namedProgram.isNotEmpty()
        else -> true
    }

    EntryScaffold(
        onBack = onDismiss,
        title = stringResource(if (tensOnly) R.string.agenda_action_tens else R.string.care_title),
        confirmEnabled = selectedType != null && programOk &&
            if (isTens) duration in tensDurations else duration > 0,
        onConfirm = {
            val type = selectedType ?: return@EntryScaffold
            scope.launch {
                val saved = viewModel.save(
                    eventId = existingEventId,
                    careType = type,
                    programName = namedProgram,
                    startAt = at.atZone(zone).toInstant(),
                    durationMinutes = duration,
                    painBefore = painBefore,
                    zoneId = zone,
                    notes = notes
                )
                if (tensOnly && !scheduleReminder) {
                    onDismiss()
                } else {
                    followup = saved
                }
            }
        }
    ) {
            if (careTypes.isEmpty()) {
                Text(
                    stringResource(R.string.care_empty_types),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (!tensOnly) {
                    Text(stringResource(R.string.care_type), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                        careTypes.forEach { type ->
                            FilterChip(
                                selected = type.id == (selectedTypeId ?: careTypes.first().id),
                                onClick = {
                                    selectedTypeId = type.id
                                    if (type.programs.none { it.name == programName }) {
                                        programName = ""
                                    }
                                },
                                label = { Text(type.name) }
                            )
                        }
                    }
                }
                val type = selectedType
                if (tensOnly && type?.programs.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.care_tens_configure),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (type != null && type.programs.isNotEmpty()) {
                    Text(stringResource(R.string.care_program), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                        type.programs.forEach { program ->
                            FilterChip(
                                selected = program.name == programName,
                                onClick = { programName = program.name },
                                label = { Text(program.name) }
                            )
                        }
                    }
                }
                if (isTens && !tensOnly) {
                    LutealTextField(
                        value = programName,
                        onValueChange = { programName = it },
                        label = { Text(stringResource(R.string.care_program_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (tensOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.care_tens_reminder))
                    Switch(checked = scheduleReminder, onCheckedChange = { scheduleReminder = it })
                }
            }
            DateTimeField(value = at, onValueChange = { at = it })
            if (isTens) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    listOf(
                        30 to R.string.care_tens_duration_30,
                        60 to R.string.care_tens_duration_60,
                        90 to R.string.care_tens_duration_90,
                        120 to R.string.care_tens_duration_120
                    ).forEach { (minutes, label) ->
                        FilterChip(
                            selected = durationMinutes == minutes.toString(),
                            onClick = { durationMinutes = minutes.toString() },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            } else {
                LutealTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.care_duration)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(stringResource(R.string.care_pain_before))
            PainScale(value = painBefore, onValueChange = { painBefore = it })
            LutealTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.symptom_notes)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (existingEventId != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.care_delete))
                }
            }
    }
}
