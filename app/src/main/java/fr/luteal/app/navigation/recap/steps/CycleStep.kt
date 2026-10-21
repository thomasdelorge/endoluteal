package fr.luteal.app.navigation.recap.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.app.navigation.recap.RecapCycleUiState
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CervicalMucusTexture
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.recap.RecapCyclePayload

internal const val RECAP_BLEEDING_NONE = "NONE"
internal const val RECAP_MUCUS_NONE = "NONE"
internal const val RECAP_PHASE_USE_ESTIMATE = "ESTIMATE"

internal fun phaseDropdownId(acceptEstimatedPhase: Boolean, phaseOverride: String): String =
    if (acceptEstimatedPhase || phaseOverride.isBlank()) RECAP_PHASE_USE_ESTIMATE else phaseOverride

internal fun applyPhaseDropdown(id: String): Pair<Boolean, String> =
    if (id == RECAP_PHASE_USE_ESTIMATE) true to "" else false to id

internal fun bleedingDropdownId(hasBleeding: Boolean, intensity: String): String =
    if (!hasBleeding) RECAP_BLEEDING_NONE else intensity.ifBlank { BleedingIntensity.LIGHT.name }

internal fun applyBleedingDropdown(id: String): Pair<Boolean, String> =
    if (id == RECAP_BLEEDING_NONE) false to "" else true to id

internal fun mucusDropdownId(hasMucus: Boolean, texture: String): String =
    if (!hasMucus) RECAP_MUCUS_NONE else texture.ifBlank { CervicalMucusTexture.STICKY.name }

internal fun applyMucusDropdown(id: String): Pair<Boolean, String> =
    if (id == RECAP_MUCUS_NONE) false to "" else true to id

@Composable
fun CycleStep(
    ui: RecapCycleUiState?,
    initial: RecapCyclePayload,
    onContinue: (RecapCyclePayload) -> Unit,
    active: Boolean = true,
    onBindSave: (() -> Unit) -> Unit = {}
) {
    var phaseChoice by remember(initial) {
        mutableStateOf(phaseDropdownId(initial.acceptEstimatedPhase, initial.phaseOverride))
    }
    var bleedingChoice by remember(initial) {
        mutableStateOf(bleedingDropdownId(initial.hasBleeding, initial.bleedingIntensity))
    }
    var mucusChoice by remember(initial) {
        mutableStateOf(mucusDropdownId(initial.hasCervicalMucus, initial.mucusTexture))
    }

    LaunchedEffect(phaseChoice, bleedingChoice, mucusChoice, active) {
        if (!active) return@LaunchedEffect
        val (acceptEstimate, phaseOverride) = applyPhaseDropdown(phaseChoice)
        val (hasBleeding, bleeding) = applyBleedingDropdown(bleedingChoice)
        val (hasMucus, mucus) = applyMucusDropdown(mucusChoice)
        onBindSave {
            onContinue(
                buildRecapCyclePayload(phaseOverride, acceptEstimate, hasBleeding, bleeding, hasMucus, mucus)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(stringResource(R.string.recap_step_cycle), style = MaterialTheme.typography.titleMedium)
        val phaseLabel = when (val phase = ui?.phase) {
            is CurrentCyclePhase.Available -> phaseLabel(phase.phase)
            else -> stringResource(R.string.phase_indeterminate)
        }
        val estimateNote = if (ui?.isEstimate == true) {
            stringResource(R.string.recap_phase_estimate_note)
        } else {
            stringResource(R.string.recap_phase_recorded_note)
        }
        Text("$phaseLabel · $estimateNote", style = MaterialTheme.typography.bodyMedium)
        SettingsDropdownField(
            label = stringResource(R.string.recap_cycle_phase_label),
            selectedLabel = phaseDropdownLabel(phaseChoice),
            options = recapPhaseDropdownOptions(),
            onSelect = { phaseChoice = it }
        )
        SettingsDropdownField(
            label = stringResource(R.string.recap_bleeding_question),
            selectedLabel = bleedingDropdownLabel(bleedingChoice),
            options = recapBleedingDropdownOptions(),
            onSelect = { bleedingChoice = it }
        )
        SettingsDropdownField(
            label = stringResource(R.string.recap_mucus_question),
            selectedLabel = mucusDropdownLabel(mucusChoice),
            options = recapMucusDropdownOptions(),
            onSelect = { mucusChoice = it }
        )
    }
}

internal fun buildRecapCyclePayload(
    phaseOverride: String,
    acceptEstimate: Boolean,
    hasBleeding: Boolean,
    bleeding: String,
    hasMucus: Boolean,
    mucus: String
): RecapCyclePayload = RecapCyclePayload(
    phaseOverride = if (acceptEstimate) "" else phaseOverride,
    acceptEstimatedPhase = acceptEstimate,
    hasBleeding = hasBleeding,
    bleedingIntensity = if (hasBleeding) bleeding else "",
    hasCervicalMucus = hasMucus,
    mucusTexture = if (hasMucus) mucus else ""
)

@Composable
private fun recapBleedingDropdownOptions(): List<Pair<String, String>> = listOf(
    RECAP_BLEEDING_NONE to stringResource(R.string.recap_bleeding_none),
    BleedingIntensity.SPOTTING.name to stringResource(R.string.recap_bleeding_yes_spotting),
    BleedingIntensity.LIGHT.name to stringResource(R.string.recap_bleeding_yes_light),
    BleedingIntensity.MEDIUM.name to stringResource(R.string.recap_bleeding_yes_medium),
    BleedingIntensity.HEAVY.name to stringResource(R.string.recap_bleeding_yes_heavy)
)

@Composable
private fun recapMucusDropdownOptions(): List<Pair<String, String>> = listOf(
    RECAP_MUCUS_NONE to stringResource(R.string.recap_mucus_none),
    CervicalMucusTexture.STICKY.name to stringResource(R.string.recap_mucus_yes_sticky),
    CervicalMucusTexture.CREAMY.name to stringResource(R.string.recap_mucus_yes_creamy),
    CervicalMucusTexture.EGG_WHITE.name to stringResource(R.string.recap_mucus_yes_egg_white)
)

@Composable
private fun bleedingDropdownLabel(id: String): String = recapBleedingDropdownOptions()
    .firstOrNull { it.first == id }
    ?.second
    ?: stringResource(R.string.recap_bleeding_none)

@Composable
private fun mucusDropdownLabel(id: String): String = recapMucusDropdownOptions()
    .firstOrNull { it.first == id }
    ?.second
    ?: stringResource(R.string.recap_mucus_none)

@Composable
private fun recapPhaseDropdownOptions(): List<Pair<String, String>> =
    listOf(RECAP_PHASE_USE_ESTIMATE to stringResource(R.string.recap_cycle_phase_use_estimate)) +
        CyclePhase.entries.map { it.name to phaseLabel(it) }

@Composable
private fun phaseDropdownLabel(id: String): String = recapPhaseDropdownOptions()
    .firstOrNull { it.first == id }
    ?.second
    ?: stringResource(R.string.recap_cycle_phase_use_estimate)

@Composable
private fun phaseLabel(phase: CyclePhase): String = when (phase) {
    CyclePhase.MENSTRUAL -> stringResource(R.string.phase_menstrual)
    CyclePhase.FOLLICULAR -> stringResource(R.string.phase_follicular)
    CyclePhase.OVULATORY -> stringResource(R.string.phase_ovulatory)
    CyclePhase.LUTEAL -> stringResource(R.string.phase_luteal)
}
