package fr.luteal.app.navigation.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import fr.luteal.core.model.professional.Specialty
import fr.luteal.core.model.report.SinceLastVisitSummary
import fr.luteal.core.model.report.TimelineVisit
import fr.luteal.core.model.report.TimelineVisitKind
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(
    onEditAppointment: (eventId: String, date: LocalDate) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) writeTimelinePdf(context, uri, viewModel.pdfContent())
    }
    val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val zone = ZoneId.systemDefault()

    state.selected?.let { visit ->
        AlertDialog(
            onDismissRequest = { viewModel.select(null) },
            title = { Text(visit.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    if (visit.subtitle.isNotBlank()) Text(visit.subtitle)
                    Text(
                        text = visit.detail.ifBlank { stringResource(R.string.timeline_no_report) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                if (visit.kind == TimelineVisitKind.APPOINTMENT) {
                    TextButton(onClick = {
                        viewModel.select(null)
                        onEditAppointment(visit.eventId, visit.at.atZone(zone).toLocalDate())
                    }) { Text(stringResource(R.string.timeline_edit_appointment)) }
                } else {
                    TextButton(onClick = { viewModel.select(null) }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            },
            dismissButton = if (visit.kind == TimelineVisitKind.APPOINTMENT) {
                {
                    TextButton(onClick = { viewModel.select(null) }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            } else {
                null
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
                onClick = { exportPdf.launch("frise.pdf") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(stringResource(R.string.timeline_filter_professional), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                FilterChip(
                    selected = state.filters.professionalId == null,
                    onClick = { viewModel.setProfessional(null) },
                    label = { Text(stringResource(R.string.timeline_filter_all)) }
                )
                state.professionals.forEach { professional ->
                    FilterChip(
                        selected = state.filters.professionalId == professional.id,
                        onClick = { viewModel.setProfessional(professional.id) },
                        label = { Text(professional.name) }
                    )
                }
            }
        }
        item {
            SettingsMultiSelectDropdown(
                label = stringResource(R.string.timeline_filter_specialty),
                options = Specialty.entries,
                selected = state.filters.specialties,
                optionLabel = { it.label },
                onToggle = viewModel::toggleSpecialty,
                emptySummary = stringResource(R.string.timeline_filter_all)
            )
        }
        item {
            Text(stringResource(R.string.timeline_filter_period), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                listOf(null to R.string.timeline_period_all, 30 to R.string.timeline_period_30, 90 to R.string.timeline_period_90, 365 to R.string.timeline_period_365)
                    .forEach { (days, label) ->
                        FilterChip(
                            selected = state.filters.periodDays == days,
                            onClick = { viewModel.setPeriodDays(days) },
                            label = { Text(stringResource(label)) }
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
            TimelineRow(visit, dateFormat, zone) { viewModel.select(visit.eventId) }
        }
    }
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
    val kind = if (visit.kind == TimelineVisitKind.APPOINTMENT) {
        stringResource(R.string.timeline_kind_appointment)
    } else {
        stringResource(R.string.timeline_kind_hospital)
    }
    LutealCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Text(kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
