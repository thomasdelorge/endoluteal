package fr.luteal.app.navigation.meds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.theme.LutealSpacing
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MedsScreen(
    onLogIntake: () -> Unit,
    onOpenIntake: (eventId: String, date: LocalDate) -> Unit,
    viewModel: MedsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LutealSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        Text(stringResource(R.string.meds_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.medications.isNotEmpty()) {
            LutealPrimaryButton(
                text = stringResource(R.string.meds_log_intake),
                onClick = onLogIntake,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(stringResource(R.string.meds_recent), style = MaterialTheme.typography.titleMedium)
        state.recentIntakes.forEach { intake ->
            val takenLabel = intake.takenAt.atZone(zone).toLocalDateTime()
                .let(LocalizedDateFormatter::formatNumericDateTime)
            val nextLabel = intake.nextReminderAt?.atZone(zone)?.toLocalDateTime()
                ?.let(LocalizedDateFormatter::formatNumericDateTime)
            LutealCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onOpenIntake(
                        intake.eventId,
                        intake.takenAt.atZone(zone).toLocalDate()
                    )
                }
            ) {
                Text(intake.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.meds_last_intake, takenLabel))
                if (nextLabel != null) {
                    Text(
                        stringResource(R.string.meds_next_possible, nextLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TreatmentEffectCard()
    }
}
