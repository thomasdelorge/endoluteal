package fr.luteal.core.data.report

import android.content.Context
import fr.luteal.core.designsystem.component.bodymap.ChoirPainMap
import fr.luteal.core.model.BodyZone
import fr.luteal.core.model.report.SynthesisPdfSection
import fr.luteal.core.model.report.SynthesisTrend
import fr.luteal.core.model.report.TimelineVisitKind

internal object SynthesisPdfLabels {
    fun metricLabel(key: String): String = when (key) {
        "5d_dysmenorrhea" -> "Dysménorrhée"
        "5d_dyspareunia" -> "Dyspareunie"
        "5d_dyschezia" -> "Dyschezie"
        "5d_dysuria" -> "Dysurie"
        "5d_chronic" -> "Douleur pelvienne chronique"
        "pain_marks" -> "Intensité des marques"
        "psy_mood" -> "Humeur"
        "psy_anxiety" -> "Anxiété"
        "general_fatigue" -> "Fatigue"
        "general_stress" -> "Stress"
        "general_sleep" -> "Sommeil"
        "bowel_bloating" -> "Ballonnements"
        else -> key
    }

    fun sectionReliabilityLabel(section: SynthesisPdfSection): String = when (section) {
        SynthesisPdfSection.PAIN -> "Douleur"
        SynthesisPdfSection.PAIN_HEATMAP -> "Carte douleur"
        SynthesisPdfSection.PSY -> "Psycho"
        SynthesisPdfSection.GENERAL -> "Général"
        SynthesisPdfSection.BOWEL -> "Intestinal"
        SynthesisPdfSection.TREATMENTS -> "Traitements"
        SynthesisPdfSection.JOURNAL -> "Journal"
    }

    fun trendLabel(trend: SynthesisTrend): String = when (trend) {
        SynthesisTrend.UP -> "tendance à la hausse"
        SynthesisTrend.DOWN -> "tendance à la baisse"
        SynthesisTrend.STABLE -> "tendance stable"
        SynthesisTrend.INSUFFICIENT -> "données insuffisantes pour une tendance"
    }

    fun visitKindLabel(kind: TimelineVisitKind): String = when (kind) {
        TimelineVisitKind.APPOINTMENT -> "Rendez-vous"
        TimelineVisitKind.HOSPITALIZATION -> "Hospitalisation"
        TimelineVisitKind.MEDICATION_INTAKE -> "Médicaments"
        TimelineVisitKind.SYMPTOM -> "Symptômes"
        TimelineVisitKind.CARE_SESSION -> "Soins"
    }

    fun zoneHumanLabel(context: Context, zoneId: String): String {
        BodyZone.fromId(zoneId)?.let { return context.getString(it.labelRes) }
        ChoirPainMap.regions(context).firstOrNull { it.key == zoneId }?.let { return it.title }
        return zoneId
    }
}
