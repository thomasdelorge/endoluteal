package fr.luteal.app.navigation.timeline

import android.content.Context
import android.net.Uri
import fr.luteal.core.data.report.SynthesisPdfBuilder
import fr.luteal.core.model.report.ClinicalSynthesis

fun writeSynthesisPdf(context: Context, uri: Uri, synthesis: ClinicalSynthesis) {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        SynthesisPdfBuilder.write(context, synthesis, stream)
    }
}
