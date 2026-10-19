package fr.luteal.app.navigation.recap.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.app.navigation.recap.RecapCycleUiState
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.BleedingIntensity
import fr.luteal.core.model.CervicalMucusTexture
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.PhaseCertainty
import fr.luteal.core.model.recap.RecapCyclePayload

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleStep(
    ui: RecapCycleUiState?,
    initial: RecapCyclePayload,
    onContinue: (RecapCyclePayload) -> Unit,
    active: Boolean = true,
    onBindSave: (() -> Unit) -> Unit = {}
) {
    val estimated = ui?.estimatedPhase
    var phaseOverride by remember(initial) {
        mutableStateOf(initial.phaseOverride.ifBlank { estimated?.name ?: "" })
    }
    var acceptEstimate by remember(initial) { mutableStateOf(initial.acceptEstimatedPhase) }
    var hasBleeding by remember(initial) { mutableStateOf(initial.hasBleeding) }
    var bleeding by remember(initial) {
        mutableStateOf(initial.bleedingIntensity.ifBlank { BleedingIntensity.LIGHT.name })
    }
    var hasMucus by remember(initial) { mutableStateOf(initial.hasCervicalMucus) }
    var mucus by remember(initial) {
        mutableStateOf(initial.mucusTexture.ifBlank { CervicalMucusTexture.STICKY.name })
    }

    LaunchedEffect(
        phaseOverride,
        acceptEstimate,
        hasBleeding,
        bleeding,
        hasMucus,
        mucus,
        active
    ) {
        if (!active) return@LaunchedEffect
        onBindSave {
            onContinue(buildRecapCyclePayload(phaseOverride, acceptEstimate, hasBleeding, bleeding, hasMucus, mucus))
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            CyclePhase.entries.forEach { phase ->
                FilterChip(
                    selected = phaseOverride == phase.name,
                    onClick = {
                        phaseOverride = phase.name
                        acceptEstimate = false
                    },
                    label = { Text(phaseLabel(phase)) }
                )
            }
        }
        Text(stringResource(R.string.recap_bleeding_question))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            FilterChip(selected = !hasBleeding, onClick = { hasBleeding = false }, label = { Text(stringResource(R.string.recap_no)) })
            FilterChip(selected = hasBleeding, onClick = { hasBleeding = true }, label = { Text(stringResource(R.string.recap_yes)) })
        }
        if (hasBleeding) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                listOf(BleedingIntensity.LIGHT, BleedingIntensity.MEDIUM, BleedingIntensity.HEAVY, BleedingIntensity.SPOTTING).forEach { intensity ->
                    FilterChip(
                        selected = bleeding == intensity.name,
                        onClick = { bleeding = intensity.name },
                        label = { Text(bleedingLabel(intensity)) }
                    )
                }
            }
        }
        Text(stringResource(R.string.recap_mucus_question))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            FilterChip(selected = !hasMucus, onClick = { hasMucus = false }, label = { Text(stringResource(R.string.recap_no)) })
            FilterChip(selected = hasMucus, onClick = { hasMucus = true }, label = { Text(stringResource(R.string.recap_yes)) })
        }
        if (hasMucus) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                FilterChip(
                    selected = mucus == CervicalMucusTexture.STICKY.name,
                    onClick = { mucus = CervicalMucusTexture.STICKY.name },
                    label = { Text(stringResource(R.string.recap_mucus_sticky)) }
                )
                FilterChip(
                    selected = mucus == CervicalMucusTexture.CREAMY.name,
                    onClick = { mucus = CervicalMucusTexture.CREAMY.name },
                    label = { Text(stringResource(R.string.recap_mucus_creamy)) }
                )
                FilterChip(
                    selected = mucus == CervicalMucusTexture.EGG_WHITE.name,
                    onClick = { mucus = CervicalMucusTexture.EGG_WHITE.name },
                    label = { Text(stringResource(R.string.recap_mucus_egg_white)) }
                )
            }
        }
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
private fun phaseLabel(phase: CyclePhase): String = when (phase) {
    CyclePhase.MENSTRUAL -> stringResource(R.string.phase_menstrual)
    CyclePhase.FOLLICULAR -> stringResource(R.string.phase_follicular)
    CyclePhase.OVULATORY -> stringResource(R.string.phase_ovulatory)
    CyclePhase.LUTEAL -> stringResource(R.string.phase_luteal)
}

@Composable
private fun bleedingLabel(intensity: BleedingIntensity): String = when (intensity) {
    BleedingIntensity.SPOTTING -> stringResource(R.string.bleeding_spotting)
    BleedingIntensity.LIGHT -> stringResource(R.string.bleeding_light)
    BleedingIntensity.MEDIUM -> stringResource(R.string.bleeding_medium)
    BleedingIntensity.HEAVY -> stringResource(R.string.bleeding_heavy)
    BleedingIntensity.NONE -> stringResource(R.string.bleeding_none)
}
