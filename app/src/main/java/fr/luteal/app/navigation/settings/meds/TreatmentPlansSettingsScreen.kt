package fr.luteal.app.navigation.settings.meds

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.app.navigation.meds.MedsViewModel
import fr.luteal.core.common.LocalizedDateFormatter
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.ScreenBack
import fr.luteal.core.designsystem.component.SettingsListHeader
import fr.luteal.core.designsystem.theme.LutealSpacing

private sealed interface PlansPage {
    data object List : PlansPage
    data class Edit(val planId: String?) : PlansPage
}

@Composable
fun TreatmentPlansSettingsScreen(
    onBack: () -> Unit,
    viewModel: MedsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf<PlansPage>(PlansPage.List) }
    val navigateBack: () -> Unit = {
        when (page) {
            is PlansPage.Edit -> page = PlansPage.List
            PlansPage.List -> onBack()
        }
    }
    BackHandler(onBack = navigateBack)

    when (val shown = page) {
        is PlansPage.Edit -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = navigateBack)
            Text(stringResource(R.string.meds_plans), style = MaterialTheme.typography.titleLarge)
            TreatmentPlanEditor(
                medications = state.medications,
                plan = shown.planId?.let { id -> state.plans.find { it.plan.id == id }?.plan },
                modifier = Modifier.weight(1f),
                onSave = { plan ->
                    viewModel.upsertPlan(plan)
                    page = PlansPage.List
                },
                onDelete = shown.planId?.let { planId ->
                    {
                        viewModel.deletePlan(planId)
                        page = PlansPage.List
                    }
                }
            )
        }
        PlansPage.List -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            SettingsListHeader(
                onBack = navigateBack,
                onAdd = { page = PlansPage.Edit(null) },
                addContentDescription = stringResource(R.string.meds_add_plan)
            )
            Text(stringResource(R.string.meds_plans), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.meds_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.plans.isEmpty()) {
                Text(
                    stringResource(R.string.meds_plans_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.plans.forEach { row ->
                val timesLabel = row.plan.timesOfDay
                    .map { LocalizedDateFormatter.formatTime(it) }
                    .joinToString(", ")
                LutealCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { page = PlansPage.Edit(row.plan.id) }
                ) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(timesLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    row.nextAt?.let {
                        Text(
                            stringResource(
                                R.string.meds_next_slot,
                                LocalizedDateFormatter.formatNumericDateTime(it)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
