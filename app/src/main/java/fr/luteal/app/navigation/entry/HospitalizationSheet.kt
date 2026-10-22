package fr.luteal.app.navigation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import fr.luteal.core.designsystem.component.LutealTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.DateTimeField
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.component.PainScale
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.EventPayload
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun HospitalizationSheet(
    initialDate: LocalDate,
    existingEventId: String?,
    viewModel: HospitalizationEntryViewModel,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var admissionAt by remember {
        mutableStateOf(
            if (initialDate == LocalDate.now(zone)) LocalDateTime.now(zone)
            else initialDate.atTime(LocalDateTime.now(zone).toLocalTime())
        )
    }
    var hasDischarge by remember { mutableStateOf(false) }
    var dischargeAt by remember { mutableStateOf(admissionAt.plusDays(1)) }
    var place by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var exams by remember { mutableStateOf(listOf<EventPayload.HospitalExam>()) }
    var meds by remember { mutableStateOf(listOf<EventPayload.HospitalAdministeredMedication>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<IndexedDraft<EventPayload.HospitalExam>?>(null) }
    var editingMed by remember { mutableStateOf<IndexedDraft<EventPayload.HospitalAdministeredMedication>?>(null) }
    var confirmRowRemove by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(existingEventId) {
        val id = existingEventId ?: return@LaunchedEffect
        val event = viewModel.get(id)
        if (event != null) {
            admissionAt = LocalDateTime.ofInstant(event.startAt, zone)
            hasDischarge = event.endAt != null
            if (event.endAt != null) {
                dischargeAt = LocalDateTime.ofInstant(event.endAt, zone)
            }
            notes = event.notes
            val payload = event.payload as? EventPayload.Hospitalization
            if (payload != null) {
                place = payload.place
                reason = payload.reason
                exams = payload.exams
                meds = payload.administeredMedications
            }
        }
    }

    if (confirmDelete && existingEventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.hospital_delete_title)) },
            text = { Text(stringResource(R.string.hospital_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.delete(existingEventId)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.hospital_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }

    EntryScaffold(
        onBack = onDismiss,
        title = stringResource(R.string.hospital_title),
        onConfirm = {
            scope.launch {
                val endInstant = if (hasDischarge) dischargeAt.atZone(zone).toInstant() else null
                viewModel.save(
                    eventId = existingEventId,
                    startAt = admissionAt.atZone(zone).toInstant(),
                    endAt = endInstant,
                    zoneId = zone,
                    notes = notes,
                    payload = EventPayload.Hospitalization(
                        place = place,
                        reason = reason,
                        exams = exams,
                        administeredMedications = meds
                    )
                )
                onDismiss()
            }
        }
    ) {
            Text(stringResource(R.string.hospital_admission))
            DateTimeField(value = admissionAt, onValueChange = { admissionAt = it })
            RowWithSwitch(
                label = stringResource(R.string.hospital_has_discharge),
                checked = hasDischarge,
                onCheckedChange = { hasDischarge = it }
            )
            if (hasDischarge) {
                Text(stringResource(R.string.hospital_discharge))
                DateTimeField(value = dischargeAt, onValueChange = { dischargeAt = it })
            }
            LutealTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(R.string.hospital_place)) },
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.hospital_reason)) },
                modifier = Modifier.fillMaxWidth()
            )
            SectionHeader(stringResource(R.string.hospital_exams)) {
                editingExam = IndexedDraft(null, EventPayload.HospitalExam())
            }
            exams.forEachIndexed { index, exam ->
                LutealCard(onClick = { editingExam = IndexedDraft(index, exam) }) {
                    Text(exam.name.ifBlank { stringResource(R.string.hospital_exam_name) })
                    if (exam.date.isNotBlank()) {
                        Text(exam.date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            SectionHeader(stringResource(R.string.hospital_medications)) {
                editingMed = IndexedDraft(null, EventPayload.HospitalAdministeredMedication())
            }
            meds.forEachIndexed { index, med ->
                LutealCard(onClick = { editingMed = IndexedDraft(index, med) }) {
                    Text(med.freeText.ifBlank { stringResource(R.string.hospital_med_name) })
                    if (med.administeredAt.isNotBlank()) {
                        Text(med.administeredAt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            editingExam?.let { draft ->
                ExamDialog(
                    draft = draft.value,
                    canRemove = draft.index != null,
                    onChange = { editingExam = draft.copy(value = it) },
                    onConfirm = {
                        val index = draft.index
                        exams = if (index == null) exams + draft.value
                        else exams.toMutableList().also { it[index] = draft.value }
                        editingExam = null
                    },
                    onRemove = { confirmRowRemove = true },
                    onDismiss = { editingExam = null }
                )
            }
            editingMed?.let { draft ->
                MedDialog(
                    draft = draft.value,
                    canRemove = draft.index != null,
                    onChange = { editingMed = draft.copy(value = it) },
                    onConfirm = {
                        val index = draft.index
                        meds = if (index == null) meds + draft.value
                        else meds.toMutableList().also { it[index] = draft.value }
                        editingMed = null
                    },
                    onRemove = { confirmRowRemove = true },
                    onDismiss = { editingMed = null }
                )
            }
            if (confirmRowRemove) {
                AlertDialog(
                    onDismissRequest = { confirmRowRemove = false },
                    title = { Text(stringResource(R.string.hospital_remove_row)) },
                    text = { Text(stringResource(R.string.hospital_delete_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            editingExam?.index?.let { index ->
                                exams = exams.filterIndexed { i, _ -> i != index }
                            }
                            editingMed?.index?.let { index ->
                                meds = meds.filterIndexed { i, _ -> i != index }
                            }
                            confirmRowRemove = false
                            editingExam = null
                            editingMed = null
                        }) { Text(stringResource(R.string.hospital_remove_row)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRowRemove = false }) {
                            Text(stringResource(R.string.intake_cancel))
                        }
                    }
                )
            }

            LutealTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.symptom_notes)) },
                modifier = Modifier.fillMaxWidth()
            )
            if (existingEventId != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.hospital_delete))
                }
            }
    }
}

@Composable
private fun RowWithSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class IndexedDraft<T>(val index: Int?, val value: T)

@Composable
private fun SectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAdd) { Text("+") }
    }
}

@Composable
private fun ExamDialog(
    draft: EventPayload.HospitalExam,
    canRemove: Boolean,
    onChange: (EventPayload.HospitalExam) -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hospital_exams)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                LutealTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text(stringResource(R.string.hospital_exam_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LutealTextField(
                    value = draft.date,
                    onValueChange = { onChange(draft.copy(date = it)) },
                    label = { Text(stringResource(R.string.hospital_exam_date)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LutealTextField(
                    value = draft.result,
                    onValueChange = { onChange(draft.copy(result = it)) },
                    label = { Text(stringResource(R.string.hospital_exam_result)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (canRemove) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.hospital_remove_row)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.meds_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_cancel)) }
        }
    )
}

@Composable
private fun MedDialog(
    draft: EventPayload.HospitalAdministeredMedication,
    canRemove: Boolean,
    onChange: (EventPayload.HospitalAdministeredMedication) -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hospital_medications)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                LutealTextField(
                    value = draft.freeText,
                    onValueChange = { onChange(draft.copy(freeText = it)) },
                    label = { Text(stringResource(R.string.hospital_med_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LutealTextField(
                    value = draft.administeredAt,
                    onValueChange = { onChange(draft.copy(administeredAt = it)) },
                    label = { Text(stringResource(R.string.hospital_med_time)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.hospital_med_pain_after))
                PainScale(value = draft.painAfter, onValueChange = { onChange(draft.copy(painAfter = it)) })
                if (canRemove) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.hospital_remove_row)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.meds_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_cancel)) }
        }
    )
}
