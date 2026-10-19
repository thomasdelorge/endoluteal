package fr.luteal.app.navigation.recap.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import fr.luteal.core.designsystem.component.PainScale
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.recap.RecapPainPayload

const val PAIN_STEP_SUBPAGES = 2

@Composable
fun PainStep(
    hideDyspareunia: Boolean,
    initial: RecapPainPayload,
    subPage: Int,
    onSubPageChange: (Int) -> Unit,
    onContinue: (RecapPainPayload) -> Unit,
    active: Boolean = true,
    onBindSave: (() -> Unit) -> Unit = {}
) {
    var marks by remember(initial) { mutableStateOf(initial.marks) }
    var dysmenorrhea by remember(initial) { mutableStateOf(initial.dysmenorrhea) }
    var dyspareunia by remember(initial) { mutableStateOf(initial.dyspareunia) }
    var dyschezia by remember(initial) { mutableStateOf(initial.dyschezia) }
    var dysuria by remember(initial) { mutableStateOf(initial.dysuria) }
    var chronic by remember(initial) { mutableStateOf(initial.chronicPelvicPain) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }

    fun buildPayload(): RecapPainPayload = RecapPainPayload(
        marks = marks,
        dysmenorrhea = dysmenorrhea,
        dyspareunia = if (hideDyspareunia) null else dyspareunia,
        dyschezia = dyschezia,
        dysuria = dysuria,
        chronicPelvicPain = chronic,
        notes = notes
    )

    LaunchedEffect(
        marks,
        dysmenorrhea,
        dyspareunia,
        dyschezia,
        dysuria,
        chronic,
        notes,
        hideDyspareunia,
        subPage,
        active
    ) {
        if (!active) return@LaunchedEffect
        onBindSave {
            if (subPage == 0) {
                onSubPageChange(1)
            } else {
                onContinue(buildPayload())
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
        when (subPage) {
            0 -> {
                Text(stringResource(R.string.recap_step_pain), style = MaterialTheme.typography.titleMedium)
                BodyMapPainMarks(marks = marks, onChange = { marks = it })
            }
            else -> {
                Text(stringResource(R.string.recap_step_pain_scales), style = MaterialTheme.typography.titleMedium)
                FiveDField(
                    stringResource(R.string.recap_5d_dysmenorrhea),
                    stringResource(R.string.recap_5d_dysmenorrhea_help),
                    dysmenorrhea
                ) { dysmenorrhea = it }
                if (!hideDyspareunia) {
                    FiveDField(
                        stringResource(R.string.recap_5d_dyspareunia),
                        stringResource(R.string.recap_5d_dyspareunia_help),
                        dyspareunia
                    ) { dyspareunia = it }
                }
                FiveDField(
                    stringResource(R.string.recap_5d_dyschezia),
                    stringResource(R.string.recap_5d_dyschezia_help),
                    dyschezia
                ) { dyschezia = it }
                FiveDField(
                    stringResource(R.string.recap_5d_dysuria),
                    stringResource(R.string.recap_5d_dysuria_help),
                    dysuria
                ) { dysuria = it }
                FiveDField(
                    stringResource(R.string.recap_5d_chronic_pelvic),
                    stringResource(R.string.recap_5d_chronic_pelvic_help),
                    chronic
                ) { chronic = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.symptom_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FiveDField(label: String, help: String, value: Int?, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PainScale(value = value, onValueChange = onChange)
    }
}
