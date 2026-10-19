package fr.luteal.app.navigation.settings.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.DateField
import fr.luteal.core.designsystem.component.FormSaveButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.TimeListField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.meds.TreatmentPlan
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Composable
fun TreatmentPlanEditor(
    medications: List<Pair<String, String>>,
    plan: TreatmentPlan? = null,
    onSave: (TreatmentPlan) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val initialMedication = plan?.medicationId
    val initialName = medications.find { it.first == initialMedication }?.second.orEmpty()
    var medicationId by remember(plan?.id) { mutableStateOf(initialMedication) }
    var query by remember(plan?.id) { mutableStateOf(initialName) }
    var menuOpen by remember { mutableStateOf(false) }
    var units by remember(plan?.id) { mutableStateOf(plan?.unitsPerDose?.toString() ?: "1") }
    var times by remember(plan?.id) { mutableStateOf(plan?.timesOfDay ?: listOf(LocalTime.of(8, 0))) }
    var start by remember(plan?.id) { mutableStateOf(plan?.startDate ?: LocalDate.now()) }
    var end by remember(plan?.id) { mutableStateOf(plan?.endDate) }
    val selected = medications.find { it.first == medicationId }
    val filtering = selected == null || query != selected.second
    val options = medications.filter { (_, name) ->
        !filtering || query.isBlank() || name.contains(query, ignoreCase = true)
    }
    val count = units.replace(',', '.').toDoubleOrNull()
    val endDate = end
    val endAfterStart = endDate == null || !endDate.isBefore(start)
    val canSave = medicationId != null && count != null && count > 0.0 && times.isNotEmpty() && endAfterStart

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    menuOpen = true
                    if (selected?.second != it) medicationId = null
                },
                label = { Text(stringResource(R.string.intake_search)) },
                trailingIcon = {
                    IconButton(onClick = { menuOpen = !menuOpen }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.intake_search))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            medicationId = id
                            query = name
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
        TimeListField(
            times = times,
            onTimesChange = { times = it },
            label = stringResource(R.string.meds_times_label),
            modifier = Modifier.fillMaxWidth()
        )
        DateField(value = start, onValueChange = { start = it }, label = stringResource(R.string.meds_start))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            DateField(
                value = end,
                onValueChange = { end = it },
                label = stringResource(R.string.meds_end),
                modifier = Modifier.weight(1f)
            )
            if (end != null) {
                LutealSecondaryButton(
                    text = stringResource(R.string.meds_clear_end),
                    onClick = { end = null }
                )
            }
        }
        FormSaveButton(
            onClick = {
                val id = medicationId ?: return@FormSaveButton
                val unitsPerDose = count ?: return@FormSaveButton
                onSave(
                    TreatmentPlan(
                        id = plan?.id ?: UUID.randomUUID().toString(),
                        medicationId = id,
                        unitsPerDose = unitsPerDose,
                        timesOfDay = times,
                        startDate = start,
                        endDate = end,
                        active = plan?.active ?: true,
                        continuousHormonal = plan?.continuousHormonal ?: false
                    )
                )
            },
            enabled = canSave
        )
        if (onDelete != null) {
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(
                    text = stringResource(R.string.meds_delete),
                    modifier = Modifier.padding(start = LutealSpacing.xs)
                )
            }
        }
    }
}
