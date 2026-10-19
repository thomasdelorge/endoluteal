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
import androidx.compose.material3.Switch
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
import fr.luteal.core.model.meds.AlertSeverity
import fr.luteal.core.model.meds.Composition
import fr.luteal.core.model.meds.DrugClass
import fr.luteal.core.model.meds.InteractionRule
import fr.luteal.core.model.meds.InteractionTargetType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.meds.MedicationCatalog
import fr.luteal.core.model.meds.Substance
import java.util.UUID

private sealed interface MedsPage {
    data object List : MedsPage
    data class MedicationForm(val id: String?) : MedsPage
    data object Substances : MedsPage
    data class SubstanceForm(val id: String?) : MedsPage
    data object Rules : MedsPage
    data class RuleForm(val id: String?) : MedsPage
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
        is MedsPage.RuleForm -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = navigateBack)
            RuleForm(
                rule = catalog.rules.find { it.id == shown.id },
                substances = catalog.substances,
                onSave = {
                    viewModel.saveRule(it)
                    current = MedsPage.Rules
                },
                onDelete = shown.id?.let { id ->
                    {
                        viewModel.deleteRule(id)
                        current = MedsPage.Rules
                    }
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
                    onOpenSubstances = { current = MedsPage.Substances },
                    onOpenRules = { current = MedsPage.Rules }
                )
                MedsPage.Substances -> SubstancesList(
                    catalog = catalog,
                    onOpenSubstance = { current = MedsPage.SubstanceForm(it) },
                    onAddSubstance = { current = MedsPage.SubstanceForm(null) }
                )
                MedsPage.Rules -> RulesList(
                    catalog = catalog,
                    onOpenRule = { current = MedsPage.RuleForm(it) },
                    onAddRule = { current = MedsPage.RuleForm(null) }
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
    onOpenSubstances: () -> Unit,
    onOpenRules: () -> Unit
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
    LutealSecondaryButton(
        text = stringResource(R.string.meds_rules),
        onClick = onOpenRules,
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
            Text(substanceLabel(substance), style = MaterialTheme.typography.titleMedium)
            Text(
                drugClassLabel(substance.drugClass),
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
private fun RulesList(
    catalog: MedicationCatalog,
    onOpenRule: (String) -> Unit,
    onAddRule: () -> Unit
) {
    catalog.rules.forEach { rule ->
        LutealCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpenRule(rule.id) }
        ) {
            Text(rule.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
    LutealPrimaryButton(
        text = stringResource(R.string.meds_add_rule),
        onClick = onAddRule,
        modifier = Modifier.fillMaxWidth()
    )
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
                    contraindicatedSubstanceIds = contraindicatedIds.toList()
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
            MedsSectionTitle(stringResource(R.string.meds_section_composition))
            rows.forEachIndexed { index, row ->
                val substanceOptions = substances.map { it.id to it.name }
                SettingsDropdownField(
                    label = stringResource(R.string.meds_substances),
                    selectedLabel = substances.find { it.id == row.first }?.name
                        ?: stringResource(R.string.meds_substances),
                    options = substanceOptions,
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
    var drugClass by rememberSaveable { mutableStateOf(substance?.drugClass ?: DrugClass.OTHER) }
    var maxDaily by rememberSaveable { mutableStateOf(substance?.maxDailyMg?.toString().orEmpty()) }
    var maxIntake by rememberSaveable { mutableStateOf(substance?.maxPerIntakeMg?.toString().orEmpty()) }
    var interval by rememberSaveable { mutableStateOf(substance?.minIntervalMinutes?.toString().orEmpty()) }
    var validated by rememberSaveable { mutableStateOf(substance?.validated ?: false) }
    var notes by rememberSaveable { mutableStateOf(substance?.notes.orEmpty()) }
    val classOptions = DrugClass.entries.map { it.name to drugClassLabel(it) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.meds_name)) },
        modifier = Modifier.fillMaxWidth()
    )
    SettingsDropdownField(
        label = stringResource(R.string.meds_target_class),
        selectedLabel = drugClassLabel(drugClass),
        options = classOptions,
        onSelect = { picked -> drugClass = DrugClass.valueOf(picked) }
    )
    OutlinedTextField(maxDaily, { maxDaily = it }, label = { Text(stringResource(R.string.meds_max_daily)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(maxIntake, { maxIntake = it }, label = { Text(stringResource(R.string.meds_max_intake)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(interval, { interval = it }, label = { Text(stringResource(R.string.meds_interval)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.meds_notes)) }, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.meds_validated))
        Switch(checked = validated, onCheckedChange = { validated = it })
    }
    LutealPrimaryButton(
        text = stringResource(R.string.meds_save),
        onClick = {
            if (name.isBlank()) return@LutealPrimaryButton
            onSave(
                Substance(
                    id = substance?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    drugClass = drugClass,
                    maxDailyMg = maxDaily.replace(',', '.').toDoubleOrNull(),
                    maxPerIntakeMg = maxIntake.replace(',', '.').toDoubleOrNull(),
                    minIntervalMinutes = interval.toIntOrNull(),
                    validated = validated,
                    notes = notes
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RuleForm(
    rule: InteractionRule?,
    substances: List<Substance>,
    onSave: (InteractionRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    var typeA by rememberSaveable { mutableStateOf(rule?.targetAType ?: InteractionTargetType.CLASS) }
    var idA by rememberSaveable { mutableStateOf(rule?.targetAId.orEmpty()) }
    var typeB by rememberSaveable { mutableStateOf(rule?.targetBType ?: InteractionTargetType.CLASS) }
    var idB by rememberSaveable { mutableStateOf(rule?.targetBId.orEmpty()) }
    var severity by rememberSaveable { mutableStateOf(rule?.severity ?: AlertSeverity.WARNING) }
    var message by rememberSaveable { mutableStateOf(rule?.message.orEmpty()) }
    TargetPicker(typeA, idA, substances, { typeA = it }, { idA = it })
    TargetPicker(typeB, idB, substances, { typeB = it }, { idB = it })
    SettingsDropdownField(
        label = stringResource(R.string.meds_severity),
        selectedLabel = when (severity) {
            AlertSeverity.WARNING -> stringResource(R.string.meds_severity_warning)
            AlertSeverity.DANGER -> stringResource(R.string.meds_severity_danger)
        },
        options = listOf(
            AlertSeverity.WARNING.name to stringResource(R.string.meds_severity_warning),
            AlertSeverity.DANGER.name to stringResource(R.string.meds_severity_danger)
        ),
        onSelect = { severity = AlertSeverity.valueOf(it) }
    )
    OutlinedTextField(
        value = message,
        onValueChange = { message = it },
        label = { Text(stringResource(R.string.meds_rule_message)) },
        modifier = Modifier.fillMaxWidth()
    )
    LutealPrimaryButton(
        text = stringResource(R.string.meds_save),
        onClick = {
            if (message.isBlank() || idA.isBlank() || idB.isBlank()) return@LutealPrimaryButton
            onSave(
                InteractionRule(
                    id = rule?.id ?: UUID.randomUUID().toString(),
                    targetAType = typeA,
                    targetAId = idA,
                    targetBType = typeB,
                    targetBId = idB,
                    severity = severity,
                    message = message
                )
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    if (onDelete != null) {
        LutealSecondaryButton(
            text = stringResource(R.string.meds_delete),
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TargetPicker(
    type: InteractionTargetType,
    id: String,
    substances: List<Substance>,
    onType: (InteractionTargetType) -> Unit,
    onId: (String) -> Unit
) {
    SettingsDropdownField(
        label = stringResource(R.string.meds_target_substance),
        selectedLabel = when (type) {
            InteractionTargetType.SUBSTANCE -> stringResource(R.string.meds_target_substance)
            InteractionTargetType.CLASS -> stringResource(R.string.meds_target_class)
        },
        options = listOf(
            InteractionTargetType.SUBSTANCE.name to stringResource(R.string.meds_target_substance),
            InteractionTargetType.CLASS.name to stringResource(R.string.meds_target_class)
        ),
        onSelect = { onType(InteractionTargetType.valueOf(it)) }
    )
    val options = if (type == InteractionTargetType.SUBSTANCE) {
        substances.map { it.id to it.name }
    } else {
        DrugClass.entries.map { it.name to drugClassLabel(it) }
    }
    SettingsDropdownField(
        label = if (type == InteractionTargetType.SUBSTANCE) {
            stringResource(R.string.meds_substances)
        } else {
            stringResource(R.string.meds_target_class)
        },
        selectedLabel = options.find { it.first == id }?.second ?: "",
        options = options,
        onSelect = onId
    )
}

@Composable
private fun substanceLabel(substance: Substance): String {
    val badge = if (substance.validated) "" else " · ${stringResource(R.string.meds_to_validate)}"
    return substance.name + badge
}

@Composable
private fun drugClassLabel(drugClass: DrugClass): String = when (drugClass) {
    DrugClass.PARACETAMOL -> stringResource(R.string.drug_class_paracetamol)
    DrugClass.NSAID -> stringResource(R.string.drug_class_nsaid)
    DrugClass.OPIOID -> stringResource(R.string.drug_class_opioid)
    DrugClass.ANTISPASMODIC -> stringResource(R.string.drug_class_antispasmodic)
    DrugClass.TRIPTAN -> stringResource(R.string.drug_class_triptan)
    DrugClass.HORMONAL -> stringResource(R.string.drug_class_hormonal)
    DrugClass.OTHER -> stringResource(R.string.drug_class_other)
}
