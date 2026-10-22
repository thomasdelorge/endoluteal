package fr.luteal.app.navigation.settings.professionals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import fr.luteal.core.designsystem.component.LutealTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.FormEditorFooter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.ScreenBack
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.component.SettingsListHeader
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.professional.Professional
import fr.luteal.core.model.professional.Specialty
import java.util.UUID

private sealed interface ProfessionalsPage {
    data object List : ProfessionalsPage
    data class Edit(val professionalId: String?) : ProfessionalsPage
}

@Composable
fun ProfessionalsSettingsScreen(
    onBack: () -> Unit,
    viewModel: ProfessionalCatalogViewModel = hiltViewModel()
) {
    val professionals by viewModel.professionals.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<ProfessionalsPage>(ProfessionalsPage.List) }
    val navigateBack: () -> Unit = {
        when (page) {
            is ProfessionalsPage.Edit -> page = ProfessionalsPage.List
            ProfessionalsPage.List -> onBack()
        }
    }
    BackHandler(onBack = navigateBack)

    when (val shown = page) {
        is ProfessionalsPage.Edit -> {
            val target = shown.professionalId?.let { id -> professionals.find { it.id == id } }
            ProfessionalEditor(
                professional = target,
                onBack = navigateBack,
                onSave = { professional ->
                    viewModel.save(professional)
                    page = ProfessionalsPage.List
                },
                onDelete = shown.professionalId?.let { id ->
                    {
                        viewModel.delete(id)
                        page = ProfessionalsPage.List
                    }
                }
            )
        }
        ProfessionalsPage.List -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            SettingsListHeader(
                onBack = navigateBack,
                onAdd = { page = ProfessionalsPage.Edit(null) },
                addContentDescription = stringResource(R.string.professional_add)
            )
            professionals.forEach { professional ->
                LutealCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { page = ProfessionalsPage.Edit(professional.id) }
                ) {
                    Text(
                        "${professional.name} · ${professional.displaySpecialty()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (professional.place.isNotBlank()) {
                        Text(professional.place, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfessionalEditor(
    professional: Professional?,
    onBack: () -> Unit,
    onSave: (Professional) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(professional?.id) { mutableStateOf(professional?.name.orEmpty()) }
    var specialty by remember(professional?.id) {
        mutableStateOf(professional?.specialty ?: Specialty.GENERAL_PRACTITIONER)
    }
    var custom by remember(professional?.id) { mutableStateOf(professional?.customSpecialty.orEmpty()) }
    var place by remember(professional?.id) { mutableStateOf(professional?.place.orEmpty()) }
    var phone by remember(professional?.id) { mutableStateOf(professional?.phone.orEmpty()) }
    var notes by remember(professional?.id) { mutableStateOf(professional?.notes.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LutealSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        ScreenBack(onClick = onBack)
        Text(
            stringResource(if (professional == null) R.string.professional_add else R.string.professional_edit),
            style = MaterialTheme.typography.titleLarge
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            LutealTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.professional_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            SettingsDropdownField(
                label = stringResource(R.string.timeline_filter_specialty),
                selectedLabel = specialty.label,
                options = Specialty.entries.map { it.name to it.label },
                onSelect = { picked -> specialty = Specialty.fromStored(picked) }
            )
            if (specialty == Specialty.OTHER) {
                LutealTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text(stringResource(R.string.professional_custom_specialty)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LutealTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(R.string.professional_place)) },
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.professional_phone)) },
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.professional_notes)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        FormEditorFooter(
            onSave = {
                onSave(
                    Professional(
                        id = professional?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        specialty = specialty,
                        customSpecialty = custom.trim(),
                        place = place.trim(),
                        phone = phone.trim(),
                        notes = notes.trim()
                    )
                )
            },
            saveEnabled = canSave,
            onDestructive = onDelete?.let { { confirmDelete = true } }
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.professional_delete_title)) },
            text = { Text(stringResource(R.string.professional_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.meds_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }
}
