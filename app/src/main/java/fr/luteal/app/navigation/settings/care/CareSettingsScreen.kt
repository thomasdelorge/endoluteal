package fr.luteal.app.navigation.settings.care

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
import fr.luteal.core.designsystem.component.SettingsListHeader
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.care.CareProgram
import fr.luteal.core.model.care.CareTypeSeed

private sealed interface TensProgramsPage {
    data object List : TensProgramsPage
    data class Edit(val programName: String?) : TensProgramsPage
}

@Composable
fun CareSettingsScreen(
    onBack: () -> Unit,
    viewModel: CareCatalogViewModel = hiltViewModel()
) {
    val types by viewModel.types.collectAsStateWithLifecycle()
    val tens = types.find { it.id == CareTypeSeed.TENS_ID && !it.archived }
    var page by remember { mutableStateOf<TensProgramsPage>(TensProgramsPage.List) }
    val navigateBack: () -> Unit = {
        when (page) {
            is TensProgramsPage.Edit -> page = TensProgramsPage.List
            TensProgramsPage.List -> onBack()
        }
    }
    BackHandler(onBack = navigateBack)

    if (tens == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LutealSpacing.md)
        ) {
            ScreenBack(onClick = onBack)
            Text(stringResource(R.string.care_tens_programs_title), style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    when (val shown = page) {
        is TensProgramsPage.Edit -> {
            val existing = shown.programName?.let { name ->
                tens.programs.find { it.name == name }
            }
            TensProgramEditor(
                program = existing,
                previousName = shown.programName,
                onBack = navigateBack,
                onSave = { name, duration ->
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) return@TensProgramEditor
                    val program = CareProgram(trimmed, duration)
                    if (shown.programName == null) {
                        viewModel.addProgram(tens.id, program)
                    } else {
                        viewModel.renameProgram(tens.id, shown.programName, program)
                    }
                    page = TensProgramsPage.List
                },
                onDelete = shown.programName?.let { previous ->
                    {
                        viewModel.removeProgram(tens.id, previous)
                        page = TensProgramsPage.List
                    }
                }
            )
        }
        TensProgramsPage.List -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            SettingsListHeader(
                onBack = navigateBack,
                onAdd = { page = TensProgramsPage.Edit(null) },
                addContentDescription = stringResource(R.string.care_add_program)
            )
            Text(stringResource(R.string.care_tens_programs_title), style = MaterialTheme.typography.titleMedium)
            tens.programs.forEach { program ->
                LutealCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { page = TensProgramsPage.Edit(program.name) }
                ) {
                    Text(program.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.care_duration) + ": ${program.defaultDurationMinutes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TensProgramEditor(
    program: CareProgram?,
    previousName: String?,
    onBack: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(previousName) { mutableStateOf(program?.name.orEmpty()) }
    var duration by remember(previousName) {
        mutableStateOf(program?.defaultDurationMinutes?.toString().orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val durationMinutes = duration.filter { it.isDigit() }.toIntOrNull() ?: 0
    val canSave = name.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LutealSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        ScreenBack(onClick = onBack)
        Text(
            stringResource(
                if (previousName == null) R.string.care_add_program else R.string.care_rename_program
            ),
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
                label = { Text(stringResource(R.string.care_program_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = duration,
                onValueChange = { duration = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.care_duration)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        FormEditorFooter(
            onSave = { onSave(name, durationMinutes) },
            saveEnabled = canSave,
            onDestructive = onDelete?.let { { confirmDelete = true } }
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.care_program_delete_title)) },
            text = { Text(stringResource(R.string.care_program_delete_body)) },
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
