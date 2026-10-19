package fr.luteal.app.navigation.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.report.SynthesisPdfSection

@Composable
fun TimelineExportDialog(
    onDismiss: () -> Unit,
    onExport: (Set<SynthesisPdfSection>) -> Unit
) {
    var pain by remember { mutableStateOf(true) }
    var heatmap by remember { mutableStateOf(true) }
    var psy by remember { mutableStateOf(true) }
    var general by remember { mutableStateOf(true) }
    var bowel by remember { mutableStateOf(true) }
    var treatments by remember { mutableStateOf(true) }
    var journal by remember { mutableStateOf(true) }

    fun sections(): Set<SynthesisPdfSection> = buildSet {
        if (pain) add(SynthesisPdfSection.PAIN)
        if (heatmap) add(SynthesisPdfSection.PAIN_HEATMAP)
        if (psy) add(SynthesisPdfSection.PSY)
        if (general) add(SynthesisPdfSection.GENERAL)
        if (bowel) add(SynthesisPdfSection.BOWEL)
        if (treatments) add(SynthesisPdfSection.TREATMENTS)
        if (journal) add(SynthesisPdfSection.JOURNAL)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timeline_export_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LutealSpacing.xs)
            ) {
                Text(stringResource(R.string.timeline_export_dialog_subtitle))
                ExportCheckRow(stringResource(R.string.timeline_export_section_pain), pain) { pain = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_heatmap), heatmap) { heatmap = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_psy), psy) { psy = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_general), general) { general = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_bowel), bowel) { bowel = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_treatments), treatments) { treatments = it }
                ExportCheckRow(stringResource(R.string.timeline_export_section_journal), journal) { journal = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(sections()) },
                enabled = sections().isNotEmpty()
            ) { Text(stringResource(R.string.timeline_export_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ExportCheckRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text(label)
    }
}
