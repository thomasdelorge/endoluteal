package fr.luteal.app.navigation.timeline

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import fr.luteal.core.model.report.SynthesisPdfSection
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.app.navigation.ScreenHeader
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealPrimaryButton
import fr.luteal.core.designsystem.component.SettingsMultiSelectDropdown
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.report.SinceLastVisitSummary
import fr.luteal.core.model.report.TimelineVisit
import fr.luteal.core.model.report.TimelineVisitKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(
    onOpenAppointmentDetail: (eventId: String) -> Unit = {},
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingSections by remember { mutableStateOf<Set<SynthesisPdfSection>?>(null) }
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val sections = pendingSections
        if (uri != null && sections != null) {
            scope.launch {
                val synthesis = viewModel.buildSynthesis(sections)
                writeSynthesisPdf(context, uri, synthesis)
                pendingSections = null
            }
        }
    }
    val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val zone = ZoneId.systemDefault()

    if (showExportDialog) {
        TimelineExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { sections ->
                showExportDialog = false
                pendingSections = sections
                exportPdf.launch("synthese-endoluteal.pdf")
            }
        )
    }

    state.selected?.let { visit ->
        AlertDialog(
            onDismissRequest = { viewModel.select(null) },
            title = { Text(visit.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    if (visit.subtitle.isNotBlank()) Text(visit.subtitle)
                    if (state.filters.showDetails) {
                        Text(
                            text = visit.detail.ifBlank {
                                if (visit.kind == TimelineVisitKind.APPOINTMENT) {
                                    stringResource(R.string.timeline_no_report)
                                } else {
                                    ""
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.select(null) }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(LutealSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_timeline),
                subtitle = stringResource(R.string.timeline_subtitle)
            )
            LutealPrimaryButton(
                text = stringResource(R.string.timeline_export_pdf),
                onClick = { showExportDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val resources = LocalContext.current.resources
            val allKindsLabel = stringResource(R.string.timeline_filter_all)
            val noneKindsLabel = stringResource(R.string.timeline_filter_none)
            val kindsSummary = when {
                state.filters.kinds.isEmpty() -> noneKindsLabel
                state.filters.kinds == TimelineVisitKind.FILTERABLE_ALL -> allKindsLabel
                else -> TimelineVisitKind.entries
                    .filter { it in state.filters.kinds }
                    .sortedBy { it.ordinal }
                    .map { resources.timelineKindLabel(it) }
                    .joinToString()
            }
            SettingsMultiSelectDropdown(
                label = stringResource(R.string.timeline_filter_kind),
                options = TimelineVisitKind.entries,
                selected = state.filters.kinds,
                optionLabel = { resources.timelineKindLabel(it) },
                onToggle = viewModel::toggleKind,
                emptySummary = noneKindsLabel,
                summaryText = kindsSummary.takeIf { state.filters.kinds.isNotEmpty() }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.timeline_show_details), style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = state.filters.showDetails,
                    onCheckedChange = viewModel::setShowDetails
                )
            }
        }
        item {
            val proNames = state.professionals.associate { it.id to it.name }
            val allProsLabel = stringResource(R.string.timeline_filter_all)
            val proSummary = when {
                state.filters.professionalIds.isEmpty() -> allProsLabel
                else -> state.filters.professionalIds
                    .mapNotNull { proNames[it] }
                    .sorted()
                    .joinToString()
            }
            SettingsMultiSelectDropdown(
                label = stringResource(R.string.timeline_filter_professional),
                options = state.professionals.map { it.id },
                selected = state.filters.professionalIds,
                optionLabel = { proNames[it] ?: it },
                onToggle = viewModel::toggleProfessional,
                emptySummary = allProsLabel,
                summaryText = proSummary.takeIf { state.filters.professionalIds.isNotEmpty() }
            )
        }
        item {
            Text(stringResource(R.string.timeline_filter_period), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                listOf(null to R.string.timeline_period_all, 30 to R.string.timeline_period_30, 90 to R.string.timeline_period_90, 365 to R.string.timeline_period_365)
                    .forEach { (days, label) ->
                        FilterChip(
                            selected = state.filters.periodDays == days && !state.filters.sinceLastVisit,
                            onClick = { viewModel.setPeriodDays(days) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                if (state.sinceLastVisitAvailable) {
                    FilterChip(
                        selected = state.filters.sinceLastVisit,
                        onClick = { viewModel.setSinceLastVisit(!state.filters.sinceLastVisit) },
                        label = { Text(stringResource(R.string.timeline_period_since_visit)) }
                    )
                }
            }
        }
        state.summary?.let { summary ->
            item { SinceLastVisitCard(summary) }
        }
        if (state.visits.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.timeline_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.visits, key = { it.eventId }) { visit ->
            TimelineRow(visit, dateFormat, zone) {
                if (visit.kind == TimelineVisitKind.APPOINTMENT) {
                    onOpenAppointmentDetail(visit.eventId)
                } else {
                    viewModel.select(visit.eventId)
                }
            }
        }
    }
}

@Composable
private fun timelineKindLabel(kind: TimelineVisitKind): String =
    stringResource(kind.labelRes())

private fun android.content.res.Resources.timelineKindLabel(kind: TimelineVisitKind): String =
    getString(kind.labelRes())

private fun TimelineVisitKind.labelRes(): Int = when (this) {
    TimelineVisitKind.APPOINTMENT -> R.string.event_type_appointment
    TimelineVisitKind.HOSPITALIZATION -> R.string.event_type_hospitalization
    TimelineVisitKind.MEDICATION_INTAKE -> R.string.event_type_medication_intake
    TimelineVisitKind.SYMPTOM -> R.string.event_type_symptom
    TimelineVisitKind.CARE_SESSION -> R.string.event_type_care_session
}

@Composable
private fun SinceLastVisitCard(summary: SinceLastVisitSummary) {
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
            Text(stringResource(R.string.timeline_since_last_title), style = MaterialTheme.typography.titleMedium)
            summary.weeklyPain.forEach { week ->
                Text(
                    stringResource(
                        R.string.timeline_week_pain,
                        week.weekStart.toString(),
                        week.average,
                        week.max
                    )
                )
            }
            summary.zonePeaks.forEach { peak ->
                Text(stringResource(R.string.timeline_zone_peak, peak.zone, peak.max))
            }
            summary.intakes.forEach { intake ->
                Text(stringResource(R.string.timeline_intake_count, intake.name, intake.count))
            }
            Text(stringResource(R.string.timeline_hospital_count, summary.hospitalizationCount))
            Text(stringResource(R.string.timeline_care_count, summary.careSessionCount))
        }
    }
}

@Composable
private fun TimelineRow(
    visit: TimelineVisit,
    dateFormat: DateTimeFormatter,
    zone: ZoneId,
    onClick: () -> Unit
) {
    LutealCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Text(
                timelineKindLabel(visit.kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(visit.title, style = MaterialTheme.typography.titleMedium)
            Text(
                visit.at.atZone(zone).toLocalDate().format(dateFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (visit.subtitle.isNotBlank()) {
                Text(visit.subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
