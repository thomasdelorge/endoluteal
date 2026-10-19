package fr.luteal.app.navigation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.DateTimeField
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.component.PainScale
import fr.luteal.core.designsystem.component.bodymap.BodyMap
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.PainMark
import fr.luteal.core.model.meds.AlertSeverity
import fr.luteal.core.model.meds.SafetyAlert
import fr.luteal.core.model.meds.SafetyAlertType
import fr.luteal.core.model.meds.SafetyReport
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun MedicationIntakeSheet(
    initialDate: LocalDate,
    initialMedicationId: String?,
    existingEventId: String? = null,
    viewModel: MedicationIntakeViewModel,
    onDismiss: () -> Unit
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var medicationId by remember { mutableStateOf(initialMedicationId) }
    var units by remember { mutableStateOf("1") }
    var at by remember {
        mutableStateOf(
            if (initialDate == LocalDate.now(zone)) LocalDateTime.now(zone)
            else initialDate.atTime(LocalDateTime.now(zone).toLocalTime())
        )
    }
    var intensity by remember { mutableStateOf<Int?>(null) }
    var marks by remember { mutableStateOf<List<PainMark>>(emptyList()) }
    var showMap by remember { mutableStateOf(false) }
    var afterIntensity by remember { mutableStateOf<Int?>(null) }
    var afterMarks by remember { mutableStateOf<List<PainMark>>(emptyList()) }
    var showAfterMap by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SafetyReport?>(null) }
    var askDanger by remember { mutableStateOf(false) }
    var doneAt by remember { mutableStateOf<Instant?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(existingEventId) {
        val id = existingEventId ?: return@LaunchedEffect
        val event = viewModel.getEvent(id) ?: return@LaunchedEffect
        val intake = viewModel.intakeForEvent(id) ?: return@LaunchedEffect
        at = LocalDateTime.ofInstant(event.startAt, zone)
        medicationId = intake.medicationId
        units = intake.units.toString()
        marks = event.marks
        intensity = event.marks.maxOfOrNull { it.intensity }
        val after = viewModel.painCheckFor(id)
        afterMarks = after?.marks.orEmpty()
        afterIntensity = after?.marks?.maxOfOrNull { it.intensity }
    }

    if (confirmDelete && existingEventId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.intake_delete_title)) },
            text = { Text(stringResource(R.string.intake_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.deleteIntake(existingEventId)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.intake_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }
    val selected = catalog.medications.find { it.id == medicationId }
    val unitCount = units.toDoubleOrNull()
    val filtering = selected == null || query != selected.name
    val medicationOptions = catalog.medications.filter { medication ->
        !medication.archived && (
            !filtering || query.isBlank() || medication.name.contains(query, ignoreCase = true)
        )
    }

    LaunchedEffect(medicationId, catalog.medications) {
        val name = catalog.medications.find { it.id == medicationId }?.name ?: return@LaunchedEffect
        if (query.isBlank()) query = name
    }

    fun saveAfterPain(eventId: String?) {
        if (eventId == null) return
        val marks = marksWith(afterIntensity, afterMarks)
        scope.launch {
            if (marks.isNotEmpty()) {
                viewModel.savePainCheck(eventId, marks)
            }
        }
    }

    LaunchedEffect(medicationId, units, at, catalog.medications.size) {
        val id = medicationId
        val count = unitCount
        if (id == null || count == null || count <= 0.0) {
            report = null
        } else {
            report = viewModel.evaluate(id, count, at.atZone(zone).toInstant())
        }
    }

    EntryScaffold(
        onBack = onDismiss,
        title = stringResource(R.string.intake_title),
        confirmEnabled = medicationId != null && unitCount != null && unitCount > 0.0,
        onConfirm = {
            val id = medicationId ?: return@EntryScaffold
            val count = unitCount ?: return@EntryScaffold
            val alerts = report?.alerts.orEmpty()
            if (alerts.any { it.severity == AlertSeverity.DANGER }) {
                askDanger = true
            } else {
                scope.launch {
                    val saved = viewModel.saveIntake(
                        id,
                        count,
                        at.atZone(zone).toInstant(),
                        marksWith(intensity, marks),
                        false,
                        existingEventId
                    )
                    saveAfterPain(existingEventId)
                    if (existingEventId == null) {
                        doneAt = saved.nextPossibleIntakeAt
                    } else {
                        onDismiss()
                    }
                }
            }
        }
    ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        menuOpen = true
                        if (selected?.name != it) medicationId = null
                    },
                    label = { Text(stringResource(R.string.intake_search)) },
                    trailingIcon = {
                        IconButton(onClick = { menuOpen = !menuOpen }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    medicationOptions.forEach { medication ->
                        DropdownMenuItem(
                            text = { Text(medication.name) },
                            onClick = {
                                medicationId = medication.id
                                query = medication.name
                                menuOpen = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = units,
                onValueChange = { units = it },
                label = { Text(stringResource(R.string.intake_units)) },
                modifier = Modifier.fillMaxWidth()
            )
            DateTimeField(value = at, onValueChange = { at = it })
            report?.alerts.orEmpty().forEach { alert ->
                Text(alertText(alert, catalog.rules.find { it.id == alert.ruleId }?.message))
            }
            Text(stringResource(R.string.intake_pain_before))
            PainScale(value = intensity, onValueChange = { intensity = it })
            TextButton(onClick = { showMap = !showMap }) {
                Text(stringResource(R.string.intake_body_map))
            }
            if (showMap) {
                BodyMap(marks = marks, onMarksChange = { marks = it })
            }
            if (existingEventId != null) {
                Text(stringResource(R.string.intake_pain_after))
                PainScale(value = afterIntensity, onValueChange = { afterIntensity = it })
                TextButton(onClick = { showAfterMap = !showAfterMap }) {
                    Text(stringResource(R.string.intake_body_map))
                }
                if (showAfterMap) {
                    BodyMap(marks = afterMarks, onMarksChange = { afterMarks = it })
                }
            }
            if (existingEventId != null) {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.intake_delete))
                }
            }
    }

    if (askDanger) {
        AlertDialog(
            onDismissRequest = { askDanger = false },
            title = { Text(stringResource(R.string.intake_danger_title)) },
            text = { Text(stringResource(R.string.intake_danger_body)) },
            confirmButton = {
                Button(onClick = {
                    askDanger = false
                    val id = medicationId ?: return@Button
                    val count = unitCount ?: return@Button
                    scope.launch {
                        val saved = viewModel.saveIntake(
                            id,
                            count,
                            at.atZone(zone).toInstant(),
                            marksWith(intensity, marks),
                            true,
                            existingEventId
                        )
                        saveAfterPain(existingEventId)
                        if (existingEventId == null) {
                            doneAt = saved.nextPossibleIntakeAt
                        } else {
                            onDismiss()
                        }
                    }
                }) { Text(stringResource(R.string.intake_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { askDanger = false }) { Text(stringResource(R.string.intake_cancel)) }
            }
        )
    }

    val finished = doneAt
    if (finished != null && selected != null && unitCount != null) {
        val whenLabel = LocalizedDateFormatter.formatNumericDateTime(finished.atZone(zone).toLocalDateTime())
        val now = Instant.now()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.intake_next_title)) },
            text = {
                Text(
                    if (finished.isAfter(now)) {
                        stringResource(R.string.intake_next_possible, whenLabel)
                    } else {
                        stringResource(R.string.intake_next_now)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (finished.isAfter(now)) {
                        viewModel.scheduleNextDose(selected.id, unitCount, finished, selected.name)
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.intake_schedule)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_ok)) }
            }
        )
    }
}

private fun marksWith(intensity: Int?, marks: List<PainMark>): List<PainMark> {
    if (intensity == null || marks.isNotEmpty()) return marks
    return listOf(PainMark(UUID.randomUUID().toString(), "non_localisee", intensity, emptyList()))
}

@Composable
private fun alertText(alert: SafetyAlert, ruleMessage: String?): String {
    val base = when (alert.type) {
        SafetyAlertType.DAILY_MAX_EXCEEDED -> stringResource(
            R.string.meds_alert_daily,
            alert.actualMg ?: 0.0,
            alert.limitMg ?: 0.0
        )
        SafetyAlertType.PER_INTAKE_MAX_EXCEEDED -> stringResource(
            R.string.meds_alert_intake,
            alert.actualMg ?: 0.0,
            alert.limitMg ?: 0.0
        )
        SafetyAlertType.MIN_INTERVAL_NOT_ELAPSED -> stringResource(R.string.meds_alert_interval)
        SafetyAlertType.CLASS_DUPLICATE -> stringResource(R.string.meds_alert_class)
        SafetyAlertType.INTERACTION_RULE -> ruleMessage ?: stringResource(R.string.meds_alert_rule)
        SafetyAlertType.CONTRAINDICATED_SUBSTANCE -> stringResource(R.string.meds_alert_contraindication)
    }
    return if (alert.unvalidated) "$base ${stringResource(R.string.meds_alert_unvalidated)}" else base
}
