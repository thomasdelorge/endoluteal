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
import fr.luteal.core.designsystem.component.LabeledScale
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.recap.RecapDailyImpact
import fr.luteal.core.model.recap.RecapGeneralPayload

@Composable
fun GeneralStep(
    initial: RecapGeneralPayload,
    onContinue: (RecapGeneralPayload) -> Unit,
    active: Boolean = true,
    onBindSave: (() -> Unit) -> Unit = {}
) {
    var fatigue by remember(initial) { mutableStateOf(initial.fatigue) }
    var stress by remember(initial) { mutableStateOf(initial.stress) }
    var sleepQuality by remember(initial) { mutableStateOf(initial.sleepQuality) }
    var headache by remember(initial) { mutableStateOf(initial.headache) }
    var nausea by remember(initial) { mutableStateOf(initial.nausea) }
    var dizziness by remember(initial) { mutableStateOf(initial.dizziness) }
    var vertigo by remember(initial) { mutableStateOf(initial.vertigo) }
    var alcohol by remember(initial) { mutableStateOf(initial.alcoholDrinks) }
    var impact by remember(initial) { mutableStateOf(initial.dailyImpact.ifBlank { RecapDailyImpact.NONE.name }) }

    LaunchedEffect(
        fatigue,
        stress,
        sleepQuality,
        headache,
        nausea,
        dizziness,
        vertigo,
        alcohol,
        impact,
        active
    ) {
        if (!active) return@LaunchedEffect
        onBindSave {
            onContinue(
                RecapGeneralPayload(
                    fatigue = fatigue,
                    stress = stress,
                    sleepQuality = sleepQuality,
                    headache = headache,
                    nausea = nausea,
                    dizziness = dizziness,
                    vertigo = vertigo,
                    alcoholDrinks = alcohol,
                    dailyImpact = impact
                )
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        Text(stringResource(R.string.recap_step_general), style = MaterialTheme.typography.titleMedium)
        LabeledScale(stringResource(R.string.recap_general_fatigue), fatigue, onValueChange = { fatigue = it })
        LabeledScale(stringResource(R.string.recap_general_stress), stress, onValueChange = { stress = it })
        LabeledScale(stringResource(R.string.recap_general_sleep), sleepQuality, onValueChange = { sleepQuality = it })
        LabeledScale(stringResource(R.string.recap_general_headache), headache, onValueChange = { headache = it })
        LabeledScale(stringResource(R.string.recap_general_nausea), nausea, onValueChange = { nausea = it })
        LabeledScale(stringResource(R.string.recap_general_dizziness), dizziness, onValueChange = { dizziness = it })
        LabeledScale(stringResource(R.string.recap_general_vertigo), vertigo, onValueChange = { vertigo = it })
        SettingsDropdownField(
            label = stringResource(R.string.recap_general_alcohol),
            selectedLabel = alcoholLabel(alcohol ?: 0),
            options = listOf(0, 1, 2, 3).map { it.toString() to alcoholLabel(it) },
            onSelect = { picked -> alcohol = picked.toInt() }
        )
        SettingsDropdownField(
            label = stringResource(R.string.recap_general_impact),
            selectedLabel = dailyImpactLabel(RecapDailyImpact.valueOf(impact)),
            options = RecapDailyImpact.entries.map { it.name to dailyImpactLabel(it) },
            onSelect = { picked -> impact = picked }
        )
    }
}

@Composable
private fun alcoholLabel(drinks: Int): String = stringResource(
    when (drinks) {
        0 -> R.string.recap_alcohol_0
        1 -> R.string.recap_alcohol_1
        2 -> R.string.recap_alcohol_2
        else -> R.string.recap_alcohol_3_plus
    }
)

@Composable
private fun dailyImpactLabel(impact: RecapDailyImpact): String = stringResource(
    when (impact) {
        RecapDailyImpact.NONE -> R.string.recap_impact_none
        RecapDailyImpact.REDUCED_ACTIVITY -> R.string.recap_impact_reduced
        RecapDailyImpact.CANCELLED_WORK -> R.string.recap_impact_cancelled
        RecapDailyImpact.BEDREST -> R.string.recap_impact_bedrest
    }
)
