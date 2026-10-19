package fr.luteal.app.navigation.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.model.meds.SafetyAlert
import fr.luteal.core.model.meds.SafetyAlertType
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
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { eventId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.intake_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteIntake(eventId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.intake_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.intake_cancel))
                }
            }
        )
    }
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
        state.history.forEach { row ->
            val last = row.lastAt?.atZone(zone)?.toLocalDateTime()?.let(LocalizedDateFormatter::formatNumericDateTime).orEmpty()
            val next = row.nextAt?.atZone(zone)?.toLocalDateTime()?.let(LocalizedDateFormatter::formatNumericDateTime).orEmpty()
            LutealCard {
                Text(row.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.meds_last_intake, last))
                Text(stringResource(R.string.meds_next_possible, next), style = MaterialTheme.typography.bodySmall)
                row.alert?.let { alert ->
                    Text(
                        text = medsAlertText(alert, row.ruleMessage),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(LutealSpacing.sm),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                row.intakes.forEach { intake ->
                    val whenLabel = intake.at.atZone(zone).toLocalDateTime().let(LocalizedDateFormatter::formatNumericDateTime)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$whenLabel · ${intake.units}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = {
                                onOpenIntake(intake.eventId, intake.at.atZone(zone).toLocalDate())
                            }) { Text(stringResource(R.string.timeline_edit_appointment)) }
                            TextButton(onClick = { pendingDelete = intake.eventId }) {
                                Text(stringResource(R.string.intake_delete))
                            }
                        }
                    }
                }
            }
        }
        TreatmentEffectCard()
    }
}

@Composable
private fun medsAlertText(alert: SafetyAlert, ruleMessage: String?): String {
    val base = when (alert.type) {
        SafetyAlertType.DAILY_MAX_EXCEEDED -> stringResource(
            R.string.meds_alert_daily,
            alert.actualMg ?: 0.0,
            alert.limitMg ?: 0.0
        )
        SafetyAlertType.PER_INTAKE_MAX_EXCEEDED -> stringResource(
            R.string.meds_alert_intake,
            alert.actualMg ?: 0.0,
            alert.limitMg ?: 0.0
        )
        SafetyAlertType.MIN_INTERVAL_NOT_ELAPSED -> stringResource(R.string.meds_alert_interval)
        SafetyAlertType.CLASS_DUPLICATE -> stringResource(R.string.meds_alert_class)
        SafetyAlertType.INTERACTION_RULE -> ruleMessage ?: stringResource(R.string.meds_alert_rule)
        SafetyAlertType.CONTRAINDICATED_SUBSTANCE -> stringResource(R.string.meds_alert_contraindication)
    }
    return if (alert.unvalidated) "$base ${stringResource(R.string.meds_alert_unvalidated)}" else base
}
