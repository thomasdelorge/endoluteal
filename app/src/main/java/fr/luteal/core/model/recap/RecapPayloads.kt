package fr.luteal.core.model.recap

import kotlinx.serialization.Serializable

@Serializable
data class RecapCyclePayload(
    val phaseOverride: String = "",
    val acceptEstimatedPhase: Boolean = true,
    val hasBleeding: Boolean = false,
    val bleedingIntensity: String = "",
    val hasCervicalMucus: Boolean = false,
    val mucusTexture: String = ""
)

@Serializable
data class RecapZoneRange(
    val zone: String,
    val minIntensity: Int = 0,
    val maxIntensity: Int = 0
)

@Serializable
data class RecapPainMark(
    val id: String,
    val zone: String,
    val intensity: Int,
    val painTypes: List<String> = emptyList(),
    val pinSide: String? = null,
    val pinX: Float? = null,
    val pinY: Float? = null
)

@Serializable
data class RecapPainPayload(
    val marks: List<RecapPainMark> = emptyList(),
    val zoneRanges: List<RecapZoneRange> = emptyList(),
    val dysmenorrhea: Int? = null,
    val dyspareunia: Int? = null,
    val dyschezia: Int? = null,
    val dysuria: Int? = null,
    val chronicPelvicPain: Int? = null,
    val notes: String = ""
)

@Serializable
data class RecapPsyPayload(
    val mood: Int? = null,
    val anxiety: Int? = null,
    val irritability: Int? = null,
    val discouragement: Int? = null,
    val overwhelmed: Int? = null,
    val isolation: Int? = null,
    val libido: Int? = null
)

enum class RecapDailyImpact {
    NONE,
    REDUCED_ACTIVITY,
    CANCELLED_WORK,
    BEDREST
}

@Serializable
data class RecapGeneralPayload(
    val fatigue: Int? = null,
    val stress: Int? = null,
    val sleepQuality: Int? = null,
    val headache: Int? = null,
    val nausea: Int? = null,
    val alcoholDrinks: Int? = null,
    val dailyImpact: String = ""
)

@Serializable
data class RecapBowelPayload(
    val constipation: Boolean = false,
    val bristolType: Int? = null,
    val bloating: Int? = null
)
