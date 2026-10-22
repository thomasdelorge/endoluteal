package fr.luteal.app.navigation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.DateTimeField
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.component.bodymap.BodyMap
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.JournalEvent
import fr.luteal.core.model.PainMark
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun SymptomSheet(
    initialDate: LocalDate,
    existingEventId: String?,
    viewModel: SymptomEntryViewModel,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    var loaded by remember { mutableStateOf<JournalEvent?>(null) }
    var at by remember {
        mutableStateOf(
            if (initialDate == LocalDate.now(zone)) LocalDateTime.now(zone)
            else initialDate.atTime(LocalDateTime.now(zone).toLocalTime())
        )
    }
    var notes by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf<List<PainMark>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(existingEventId) {
        val id = existingEventId ?: return@LaunchedEffect
        val event = viewModel.get(id)
        loaded = event
        if (event != null) {
            at = LocalDateTime.ofInstant(event.startAt, zone)
            notes = event.notes
            marks = event.marks
        }
    }

    if (confirmDelete && existingEventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.symptom_delete_title)) },
            text = { Text(stringResource(R.string.symptom_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.delete(existingEventId)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.symptom_delete_confirm)) }
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
        title = stringResource(R.string.symptom_title),
        confirmEnabled = marks.isNotEmpty(),
        onConfirm = {
            scope.launch {
                viewModel.save(
                    eventId = existingEventId,
                    at = at.atZone(zone).toInstant(),
                    zoneId = zone,
                    notes = notes,
                    marks = marks
                )
                onDismiss()
            }
        }
    ) {
        DateTimeField(value = at, onValueChange = { at = it })
        BodyMap(marks = marks, onMarksChange = { marks = it })
        LutealTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.symptom_notes)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (existingEventId != null) {
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.symptom_delete))
            }
        }
    }
}
