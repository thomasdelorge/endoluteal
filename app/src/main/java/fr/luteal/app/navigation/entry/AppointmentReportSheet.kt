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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.EntryScaffold
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.Specialty
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentReportSheet(
    eventId: String,
    viewModel: AppointmentEntryViewModel,
    onDismiss: () -> Unit
) {
    val professionals by viewModel.professionals.collectAsStateWithLifecycle()
    var selectedProfessionalId by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf("") }
    var showNewProfessional by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(eventId, professionals) {
        val appointment = viewModel.getAppointment(eventId)
        val event = viewModel.getEvent(eventId)
        if (appointment != null) {
            report = appointment.report
            selectedProfessionalId = appointment.professionalId.takeIf { it.isNotBlank() }
        }
        if (selectedProfessionalId == null && event != null) {
            selectedProfessionalId = viewModel.suggestProfessionalId(event.notes, appointment?.location ?: "")
        }
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
        title = stringResource(R.string.appointment_report_prompt_title),
        confirmEnabled = selectedProfessionalId != null,
        onConfirm = {
            val professionalId = selectedProfessionalId ?: return@EntryScaffold
            scope.launch {
                viewModel.saveReport(eventId, professionalId, report)
                onDismiss()
            }
        }
    ) {
            Text(
                stringResource(R.string.appointment_report_prompt_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProfessionalPickerField(
                professionals = professionals,
                selectedId = selectedProfessionalId,
                onSelected = { selectedProfessionalId = it }
            )
            TextButton(onClick = { showNewProfessional = true }) {
                Text(stringResource(R.string.appointment_add_professional))
            }
            LutealTextField(
                value = report,
                onValueChange = { report = it },
                label = { Text(stringResource(R.string.appointment_report)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
    }
}
