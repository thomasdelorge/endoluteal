package fr.luteal.core.model.meds

enum class DrugClass {
    PARACETAMOL,
    NSAID,
    OPIOID,
    ANTISPASMODIC,
    TRIPTAN,
    HORMONAL,
    OTHER
}

enum class InteractionTargetType {
    SUBSTANCE,
    CLASS
}

enum class AlertSeverity {
    WARNING,
    DANGER
}

data class Substance(
    val id: String,
    val name: String,
    val drugClass: DrugClass,
    val maxDailyMg: Double?,
    val maxPerIntakeMg: Double?,
    val minIntervalMinutes: Int?,
    val validated: Boolean,
    val notes: String
)

data class Composition(
    val substanceId: String,
    val amountMgPerUnit: Double
)

data class Medication(
    val id: String,
    val name: String,
    val form: String,
    val contraindications: String,
    val archived: Boolean,
    val compositions: List<Composition>,
    val contraindicatedSubstanceIds: List<String> = emptyList()
)

data class InteractionRule(
    val id: String,
    val targetAType: InteractionTargetType,
    val targetAId: String,
    val targetBType: InteractionTargetType,
    val targetBId: String,
    val severity: AlertSeverity,
    val message: String
)

data class MedicationCatalog(
    val substances: List<Substance>,
    val medications: List<Medication>,
    val rules: List<InteractionRule>
)
