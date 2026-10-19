package fr.luteal.app.navigation.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.recapScaleLabels
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.recap.BristolTypeDescriptions
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapDailyImpact
import fr.luteal.core.model.recap.RecapStep

@Composable
fun DailyRecapReadOnlyContent(
    recap: DailyRecap,
    modifier: Modifier = Modifier
) {
    if (recap.completedSteps.isEmpty()) return
    val scaleLabels = recapScaleLabels()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
    ) {
        if (RecapStep.PSY in recap.completedSteps) {
            SectionTitle(stringResource(R.string.recap_step_psy))
            recap.psy.mood?.let { ReadOnlyLine(stringResource(R.string.recap_psy_mood), scaleLine(it, scaleLabels)) }
            recap.psy.anxiety?.let { ReadOnlyLine(stringResource(R.string.recap_psy_anxiety), scaleLine(it, scaleLabels)) }
            recap.psy.irritability?.let { ReadOnlyLine(stringResource(R.string.recap_psy_irritability), scaleLine(it, scaleLabels)) }
            recap.psy.discouragement?.let { ReadOnlyLine(stringResource(R.string.recap_psy_discouragement), scaleLine(it, scaleLabels)) }
            recap.psy.overwhelmed?.let { ReadOnlyLine(stringResource(R.string.recap_psy_overwhelmed), scaleLine(it, scaleLabels)) }
            recap.psy.isolation?.let { ReadOnlyLine(stringResource(R.string.recap_psy_isolation), scaleLine(it, scaleLabels)) }
            recap.psy.libido?.let { ReadOnlyLine(stringResource(R.string.recap_psy_libido), scaleLine(it, scaleLabels)) }
        }
        if (RecapStep.GENERAL in recap.completedSteps) {
            SectionTitle(stringResource(R.string.recap_step_general))
            recap.general.fatigue?.let { ReadOnlyLine(stringResource(R.string.recap_general_fatigue), scaleLine(it, scaleLabels)) }
            recap.general.stress?.let { ReadOnlyLine(stringResource(R.string.recap_general_stress), scaleLine(it, scaleLabels)) }
            recap.general.sleepQuality?.let { ReadOnlyLine(stringResource(R.string.recap_general_sleep), scaleLine(it, scaleLabels)) }
            recap.general.headache?.let { ReadOnlyLine(stringResource(R.string.recap_general_headache), scaleLine(it, scaleLabels)) }
            recap.general.nausea?.let { ReadOnlyLine(stringResource(R.string.recap_general_nausea), scaleLine(it, scaleLabels)) }
            recap.general.dizziness?.let {
                ReadOnlyLine(stringResource(R.string.recap_general_dizziness), scaleLine(it, scaleLabels))
            }
            recap.general.vertigo?.let {
                ReadOnlyLine(stringResource(R.string.recap_general_vertigo), scaleLine(it, scaleLabels))
            }
            if (!recap.general.dailyImpact.isNullOrBlank() && recap.general.dailyImpact != RecapDailyImpact.NONE.name) {
                val impact = runCatching { RecapDailyImpact.valueOf(recap.general.dailyImpact) }.getOrNull()
                if (impact != null) {
                    ReadOnlyLine(stringResource(R.string.recap_general_impact), dailyImpactLabel(impact))
                }
            }
        }
        if (RecapStep.PAIN in recap.completedSteps) {
            SectionTitle(stringResource(R.string.recap_step_pain))
            recap.pain.dysmenorrhea?.let { ReadOnlyLine(stringResource(R.string.recap_5d_dysmenorrhea), painLine(it)) }
            recap.pain.dyspareunia?.let { ReadOnlyLine(stringResource(R.string.recap_5d_dyspareunia), painLine(it)) }
            recap.pain.dyschezia?.let { ReadOnlyLine(stringResource(R.string.recap_5d_dyschezia), painLine(it)) }
            recap.pain.dysuria?.let { ReadOnlyLine(stringResource(R.string.recap_5d_dysuria), painLine(it)) }
            recap.pain.chronicPelvicPain?.let { ReadOnlyLine(stringResource(R.string.recap_5d_chronic_pelvic), painLine(it)) }
        }
        if (RecapStep.BOWEL in recap.completedSteps) {
            SectionTitle(stringResource(R.string.recap_step_bowel))
            ReadOnlyLine(
                stringResource(R.string.recap_bowel_constipation_question),
                if (recap.bowel.constipation) stringResource(R.string.recap_yes) else stringResource(R.string.recap_no)
            )
            recap.bowel.bristolType?.let {
                ReadOnlyLine(stringResource(R.string.recap_bowel_stool), BristolTypeDescriptions.label(it))
            }
            recap.bowel.bloating?.let { ReadOnlyLine(stringResource(R.string.recap_bowel_bloating), scaleLine(it, scaleLabels)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = LutealSpacing.xs))
}

@Composable
private fun ReadOnlyLine(label: String, value: String) {
    Text(
        text = "$label : $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun scaleLine(value: Int, labels: List<String>): String =
    if (value in 1..5) "$value — ${labels[value - 1]}" else value.toString()

private fun painLine(value: Int): String = "$value / 10"

@Composable
private fun dailyImpactLabel(impact: RecapDailyImpact): String = stringResource(
    when (impact) {
        RecapDailyImpact.NONE -> R.string.recap_impact_none
        RecapDailyImpact.REDUCED_ACTIVITY -> R.string.recap_impact_reduced
        RecapDailyImpact.CANCELLED_WORK -> R.string.recap_impact_cancelled
        RecapDailyImpact.BEDREST -> R.string.recap_impact_bedrest
    }
)
