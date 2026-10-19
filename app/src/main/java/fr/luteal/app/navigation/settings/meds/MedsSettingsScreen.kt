package fr.luteal.app.navigation.settings.meds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.FormSaveButton
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.LutealSecondaryButton
import fr.luteal.core.designsystem.component.ScreenBack
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.meds.Composition
import fr.luteal.core.model.meds.DrugClass
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.meds.MedicationCatalog
import fr.luteal.core.model.meds.Substance
import java.util.UUID
import kotlin.math.roundToInt

private sealed interface MedsPage {
    data object List : MedsPage
    data class MedicationForm(val id: String?) : MedsPage
    data object Substances : MedsPage
    data class SubstanceForm(val id: String?) : MedsPage
}

@Composable
fun MedsSettingsScreen(
    onBack: () -> Unit,
    viewModel: MedicationCatalogViewModel = hiltViewModel()
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val seedOffered by viewModel.seedOffered.collectAsStateWithLifecycle()
    val followupDelay by viewModel.painFollowupDelayMinutes.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf<MedsPage>(MedsPage.List) }
    val navigateBack: () -> Unit = {
        if (current == MedsPage.List) onBack() else current = MedsPage.List
    }
    BackHandler(onBack = navigateBack)

    when (val shown = current) {
        is MedsPage.MedicationForm -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = navigateBack)
            MedicationForm(
                medication = catalog.medications.find { it.id == shown.id },
                substances = catalog.substances,
                modifier = Modifier.weight(1f),
                onSave = {
                    viewModel.saveMedication(it)
                    current = MedsPage.List
                }
            )
        }
        is MedsPage.SubstanceForm -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = navigateBack)
            SubstanceForm(
                substance = catalog.substances.find { it.id == shown.id },
                onSave = {
                    viewModel.saveSubstance(it)
                    current = MedsPage.Substances
                }
            )
        }
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = navigateBack)
            Text(
                text = stringResource(R.string.meds_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (shown) {
                MedsPage.List -> MedicationList(
                    catalog = catalog,
                    seedOffered = seedOffered,
                    followupDelay = followupDelay,
                    onAcceptSeed = viewModel::acceptSeed,
                    onDismissSeed = viewModel::dismissSeed,
                    onFollowupDelay = viewModel::setPainFollowupDelayMinutes,
                    onOpenMedication = { current = MedsPage.MedicationForm(it) },
                    onAddMedication = { current = MedsPage.MedicationForm(null) },
                    onOpenSubstances = { current = MedsPage.Substances }
                )
                MedsPage.Substances -> SubstancesList(
                    catalog = catalog,
                    onOpenSubstance = { current = MedsPage.SubstanceForm(it) },
                    onAddSubstance = { current = MedsPage.SubstanceForm(null) }
                )
            }
        }
    }
}

@Composable
private fun MedsSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun MedicationList(
    catalog: MedicationCatalog,
    seedOffered: Boolean,
    followupDelay: Int,
    onAcceptSeed: () -> Unit,
    onDismissSeed: () -> Unit,
    onFollowupDelay: (Int) -> Unit,
    onOpenMedication: (String) -> Unit,
    onAddMedication: () -> Unit,
    onOpenSubstances: () -> Unit
) {
    if (!seedOffered) {
        LutealCard {
            Text(stringResource(R.string.meds_seed_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.meds_seed_body))
            Row(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                LutealPrimaryButton(text = stringResource(R.string.meds_seed_accept), onClick = onAcceptSeed)
                LutealSecondaryButton(text = stringResource(R.string.meds_seed_dismiss), onClick = onDismissSeed)
            }
        }
    }
    if (catalog.medications.isEmpty()) {
        Text(stringResource(R.string.meds_empty))
    }
    catalog.medications.forEach { medication ->
        val compositionSummary = medication.compositions.mapNotNull { composition ->
            catalog.substances.find { it.id == composition.substanceId }?.name
        }.joinToString(" · ")
        LutealCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenMedication(medication.id) }
        ) {
            Text(medication.name, style = MaterialTheme.typography.titleMedium)
            if (compositionSummary.isNotBlank()) {
                Text(compositionSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (medication.archived) {
                Text(
                    stringResource(R.string.meds_archived),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    LutealPrimaryButton(
        text = stringResource(R.string.meds_add_medication),
        onClick = onAddMedication,
        modifier = Modifier.fillMaxWidth()
    )
    LutealSecondaryButton(
        text = stringResource(R.string.meds_substances),
        onClick = onOpenSubstances,
        modifier = Modifier.fillMaxWidth()
    )
    var delay by rememberSaveable { mutableStateOf(followupDelay.toString()) }
    OutlinedTextField(
        value = delay,
        onValueChange = { raw ->
            delay = raw.filter { it.isDigit() }
            delay.toIntOrNull()?.let(onFollowupDelay)
        },
        label = { Text(stringResource(R.string.meds_followup_delay)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SubstancesList(
    catalog: MedicationCatalog,
    onOpenSubstance: (String) -> Unit,
    onAddSubstance: () -> Unit
) {
    catalog.substances.forEach { substance ->
        LutealCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenSubstance(substance.id) }
        ) {
            Text(substance.name, style = MaterialTheme.typography.titleMedium)
            Text(
                substanceLimitsSummary(substance),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    LutealPrimaryButton(
        text = stringResource(R.string.meds_add_composition),
        onClick = onAddSubstance,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun substanceLimitsSummary(substance: Substance): String {
    val parts = mutableListOf<String>()
    val periodMax = substance.maxPerIntakeMg
    val hours = substance.minIntervalMinutes?.let { (it / 60.0).roundToInt() }
    if (periodMax != null && hours != null) {
        parts += stringResource(R.string.meds_substance_limit_period, periodMax, hours)
    }
    substance.maxDailyMg?.let { parts += stringResource(R.string.meds_substance_limit_daily, it) }
    return parts.joinToString(" · ").ifBlank { stringResource(R.string.meds_substance_no_limits) }
}

@Composable
private fun MedicationForm(
    medication: Medication?,
    substances: List<Substance>,
    modifier: Modifier = Modifier,
    onSave: (Medication) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(medication?.name.orEmpty()) }
    val storedForm = medication?.form.orEmpty()
    val legacyContraindications = medication?.contraindications.orEmpty()
    var repeatHours by rememberSaveable { mutableStateOf(medication?.repeatEveryHours?.toString().orEmpty()) }
    var archived by rememberSaveable { mutableStateOf(medication?.archived ?: false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var rows by remember {
        mutableStateOf(
            medication?.compositions?.map { it.substanceId to it.amountMgPerUnit.toString() }
                ?: listOf("" to "")
        )
    }
    var contraindicatedIds by remember {
        mutableStateOf(medication?.contraindicatedSubstanceIds?.toSet() ?: emptySet())
    }
    var pickContraindication by rememberSaveable { mutableStateOf("") }
    val trySave = {
        val compositions = rows.mapNotNull { (substanceId, amount) ->
            val mg = amount.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            if (substanceId.isBlank() || mg <= 0.0) null else Composition(substanceId, mg)
        }
        if (name.isNotBlank() && compositions.isNotEmpty()) {
            onSave(
                Medication(
                    id = medication?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    form = storedForm,
                    contraindications = legacyContraindications,
                    archived = archived,
                    compositions = compositions,
                    contraindicatedSubstanceIds = contraindicatedIds.toList(),
                    repeatEveryHours = repeatHours.toIntOrNull()?.takeIf { it > 0 }
                )
            )
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            MedsSectionTitle(stringResource(R.string.meds_section_identity))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.meds_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repeatHours,
                onValueChange = { repeatHours = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.meds_repeat_every_hours)) },
                modifier = Modifier.fillMaxWidth()
            )
            MedsSectionTitle(stringResource(R.string.meds_section_composition))
            rows.forEachIndexed { index, row ->
                SettingsDropdownField(
                    label = stringResource(R.string.meds_substances),
                    selectedLabel = substances.find { it.id == row.first }?.name
                        ?: stringResource(R.string.meds_substances),
                    options = substances.map { it.id to it.name },
                    onSelect = { picked ->
                        rows = rows.toMutableList().also { it[index] = picked to row.second }
                    }
                )
                OutlinedTextField(
                    value = row.second,
                    onValueChange = { value ->
                        rows = rows.toMutableList().also { it[index] = row.first to value }
                    },
                    label = { Text(stringResource(R.string.meds_amount_mg)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LutealSecondaryButton(
                text = stringResource(R.string.meds_add_composition),
                onClick = { rows = rows + ("" to "") },
                modifier = Modifier.fillMaxWidth()
            )
            MedsSectionTitle(stringResource(R.string.meds_section_contraindications))
            val availableContra = substances.filter { it.id !in contraindicatedIds }
            if (availableContra.isNotEmpty()) {
                SettingsDropdownField(
                    label = stringResource(R.string.meds_add_contraindicated_substance),
                    selectedLabel = substances.find { it.id == pickContraindication }?.name
                        ?: stringResource(R.string.meds_add_contraindicated_substance),
                    options = availableContra.map { it.id to it.name },
                    onSelect = { picked ->
                        contraindicatedIds = contraindicatedIds + picked
                        pickContraindication = ""
                    }
                )
            }
            contraindicatedIds.forEach { substanceId ->
                val substanceName = substances.find { it.id == substanceId }?.name ?: substanceId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(substanceName)
                    LutealSecondaryButton(
                        text = stringResource(R.string.meds_remove_contraindicated),
                        onClick = { contraindicatedIds = contraindicatedIds - substanceId }
                    )
                }
            }
            if (medication != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = {
                            if (archived) {
                                archived = false
                                trySave()
                            } else {
                                confirmArchive = true
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(
                                if (archived) R.string.meds_restore else R.string.meds_archive
                            )
                        )
                    }
                }
                if (archived) {
                    Text(
                        stringResource(R.string.meds_archived),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (confirmArchive && medication != null) {
                AlertDialog(
                    onDismissRequest = { confirmArchive = false },
                    title = { Text(stringResource(R.string.meds_archive)) },
                    text = { Text(stringResource(R.string.meds_archive_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            archived = true
                            confirmArchive = false
                            trySave()
                        }) { Text(stringResource(R.string.meds_archive)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmArchive = false }) {
                            Text(stringResource(R.string.intake_cancel))
                        }
                    }
                )
            }
        }
        FormSaveButton(
            onClick = trySave,
            enabled = name.isNotBlank() && rows.any { (substanceId, amount) ->
                substanceId.isNotBlank() && (amount.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0
            }
        )
    }
}

@Composable
private fun SubstanceForm(substance: Substance?, onSave: (Substance) -> Unit) {
    var name by rememberSaveable { mutableStateOf(substance?.name.orEmpty()) }
    var maxPeriod by rememberSaveable { mutableStateOf(substance?.maxPerIntakeMg?.toString().orEmpty()) }
    var everyHours by rememberSaveable {
        mutableStateOf(
            substance?.minIntervalMinutes?.let { (it / 60.0).roundToInt().toString() }.orEmpty()
        )
    }
    var maxDaily by rememberSaveable { mutableStateOf(substance?.maxDailyMg?.toString().orEmpty()) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.meds_name)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = maxPeriod,
        onValueChange = { maxPeriod = it },
        label = { Text(stringResource(R.string.meds_max_period)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = everyHours,
        onValueChange = { everyHours = it.filter { ch -> ch.isDigit() } },
        label = { Text(stringResource(R.string.meds_every_hours)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = maxDaily,
        onValueChange = { maxDaily = it },
        label = { Text(stringResource(R.string.meds_max_daily)) },
        modifier = Modifier.fillMaxWidth()
    )
    LutealPrimaryButton(
        text = stringResource(R.string.meds_save),
        onClick = {
            if (name.isBlank()) return@LutealPrimaryButton
            val hours = everyHours.toIntOrNull()
            onSave(
                Substance(
                    id = substance?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    drugClass = substance?.drugClass ?: DrugClass.OTHER,
                    maxDailyMg = maxDaily.replace(',', '.').toDoubleOrNull(),
                    maxPerIntakeMg = maxPeriod.replace(',', '.').toDoubleOrNull(),
                    minIntervalMinutes = hours?.takeIf { it > 0 }?.times(60),
                    validated = substance?.validated ?: true,
                    notes = substance?.notes.orEmpty()
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}
