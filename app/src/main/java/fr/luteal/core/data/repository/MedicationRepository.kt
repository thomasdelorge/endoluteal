package fr.luteal.core.data.repository

import fr.luteal.core.data.entity.InteractionRuleEntity
import fr.luteal.core.data.entity.MedicationEntity
import fr.luteal.core.data.entity.MedicationContraindicationEntity
import fr.luteal.core.data.entity.MedicationSubstanceEntity
import fr.luteal.core.data.entity.SubstanceEntity
import fr.luteal.core.data.local.MedicationDao
import fr.luteal.core.model.meds.AlertSeverity
import fr.luteal.core.model.meds.Composition
import fr.luteal.core.model.meds.DrugClass
import fr.luteal.core.model.meds.InteractionRule
import fr.luteal.core.model.meds.InteractionTargetType
import fr.luteal.core.model.meds.Medication
import fr.luteal.core.model.meds.MedicationCatalog
import fr.luteal.core.model.meds.Substance
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface MedicationRepository {
    fun observeCatalog(): Flow<MedicationCatalog>
    suspend fun catalogOnce(): MedicationCatalog
    suspend fun upsertMedication(medication: Medication)
    suspend fun upsertSubstance(substance: Substance)
    suspend fun upsertRule(rule: InteractionRule)
    suspend fun deleteRule(id: String)
    suspend fun applySeed(catalog: MedicationCatalog)
    suspend fun mergeMissingSeed(catalog: MedicationCatalog)
}

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao
) : MedicationRepository {
    override fun observeCatalog(): Flow<MedicationCatalog> = combine(
        medicationDao.observeMedications(),
        medicationDao.observeCompositions(),
        medicationDao.observeSubstances(),
        medicationDao.observeRules(),
        medicationDao.observeContraindications()
    ) { medications, compositions, substances, rules, contraindications ->
        toCatalog(medications, compositions, substances, rules, contraindications)
    }

    override suspend fun catalogOnce(): MedicationCatalog = toCatalog(
        medicationDao.medicationsOnce(),
        medicationDao.compositionsOnce(),
        medicationDao.substancesOnce(),
        medicationDao.rulesOnce(),
        medicationDao.contraindicationsOnce()
    )

    override suspend fun upsertMedication(medication: Medication) {
        require(medication.name.isNotBlank()) { "Medication name is required" }
        require(medication.compositions.isNotEmpty()) { "A medication needs at least one substance" }
        medication.compositions.forEach { composition ->
            require(composition.amountMgPerUnit > 0.0) { "Amount per unit must be positive" }
        }
        medicationDao.replaceMedication(
            medication.toEntity(),
            medication.compositions.map { it.toEntity(medication.id) },
            medication.contraindicatedSubstanceIds.map { substanceId ->
                MedicationContraindicationEntity(medication.id, substanceId)
            }
        )
    }

    override suspend fun upsertSubstance(substance: Substance) {
        require(substance.name.isNotBlank()) { "Substance name is required" }
        medicationDao.upsertSubstance(substance.toEntity())
    }

    override suspend fun upsertRule(rule: InteractionRule) {
        require(rule.message.isNotBlank()) { "Rule message is required" }
        medicationDao.upsertRule(rule.toEntity())
    }

    override suspend fun deleteRule(id: String) {
        medicationDao.deleteRule(id)
    }

    override suspend fun applySeed(catalog: MedicationCatalog) {
        catalog.substances.forEach { medicationDao.upsertSubstance(it.toEntity()) }
        catalog.medications.forEach { medication ->
            medicationDao.replaceMedication(
                medication.toEntity(),
                medication.compositions.map { it.toEntity(medication.id) },
                medication.contraindicatedSubstanceIds.map { substanceId ->
                    MedicationContraindicationEntity(medication.id, substanceId)
                }
            )
        }
        catalog.rules.forEach { medicationDao.upsertRule(it.toEntity()) }
    }

    override suspend fun mergeMissingSeed(catalog: MedicationCatalog) {
        val current = catalogOnce()
        val existingSubstances = current.substances.associateBy { it.id }
        catalog.substances.forEach { seeded ->
            val existing = existingSubstances[seeded.id]
            if (existing == null) {
                medicationDao.upsertSubstance(seeded.toEntity())
            } else {
                val merged = existing.copy(
                    maxDailyMg = existing.maxDailyMg ?: seeded.maxDailyMg,
                    maxPerIntakeMg = existing.maxPerIntakeMg ?: seeded.maxPerIntakeMg,
                    minIntervalMinutes = existing.minIntervalMinutes ?: seeded.minIntervalMinutes
                )
                if (merged != existing) medicationDao.upsertSubstance(merged.toEntity())
            }
        }
        val medicationIds = current.medications.map { it.id }.toSet()
        catalog.medications.filter { it.id !in medicationIds }.forEach { medication ->
            medicationDao.replaceMedication(
                medication.toEntity(),
                medication.compositions.map { it.toEntity(medication.id) },
                medication.contraindicatedSubstanceIds.map { substanceId ->
                    MedicationContraindicationEntity(medication.id, substanceId)
                }
            )
        }
        val ruleIds = current.rules.map { it.id }.toSet()
        catalog.rules.filter { it.id !in ruleIds }.forEach { medicationDao.upsertRule(it.toEntity()) }
    }

    private fun toCatalog(
        medications: List<MedicationEntity>,
        compositions: List<MedicationSubstanceEntity>,
        substances: List<SubstanceEntity>,
        rules: List<InteractionRuleEntity>,
        contraindications: List<MedicationContraindicationEntity>
    ): MedicationCatalog {
        val byMedication = compositions.groupBy { it.medicationId }
        val contraindicatedByMedication = contraindications.groupBy { it.medicationId }
        return MedicationCatalog(
            substances = substances.map { it.toDomain() },
            medications = medications.map { entity ->
                entity.toDomain(
                    byMedication[entity.id].orEmpty(),
                    contraindicatedByMedication[entity.id].orEmpty().map { it.substanceId }
                )
            },
            rules = rules.map { it.toDomain() }
        )
    }
}

private fun SubstanceEntity.toDomain() = Substance(
    id = id,
    name = name,
    drugClass = DrugClass.valueOf(drugClass),
    maxDailyMg = maxDailyMg,
    maxPerIntakeMg = maxPerIntakeMg,
    minIntervalMinutes = minIntervalMinutes,
    validated = validated,
    notes = notes
)

private fun Substance.toEntity() = SubstanceEntity(
    id = id,
    name = name.trim(),
    drugClass = drugClass.name,
    maxDailyMg = maxDailyMg,
    maxPerIntakeMg = maxPerIntakeMg,
    minIntervalMinutes = minIntervalMinutes,
    validated = validated,
    notes = notes.trim()
)

private fun MedicationEntity.toDomain(
    rows: List<MedicationSubstanceEntity>,
    contraindicatedSubstanceIds: List<String>
) = Medication(
    id = id,
    name = name,
    form = form,
    contraindications = contraindications,
    archived = archived,
    compositions = rows.map { Composition(it.substanceId, it.amountMgPerUnit) },
    contraindicatedSubstanceIds = contraindicatedSubstanceIds,
    repeatEveryHours = repeatEveryHours
)

private fun Medication.toEntity() = MedicationEntity(
    id = id,
    name = name.trim(),
    form = form.trim(),
    contraindications = contraindications.trim(),
    archived = archived,
    repeatEveryHours = repeatEveryHours
)

private fun Composition.toEntity(medicationId: String) = MedicationSubstanceEntity(
    medicationId = medicationId,
    substanceId = substanceId,
    amountMgPerUnit = amountMgPerUnit
)

private fun InteractionRuleEntity.toDomain() = InteractionRule(
    id = id,
    targetAType = InteractionTargetType.valueOf(targetAType),
    targetAId = targetAId,
    targetBType = InteractionTargetType.valueOf(targetBType),
    targetBId = targetBId,
    severity = AlertSeverity.valueOf(severity),
    message = message
)

private fun InteractionRule.toEntity() = InteractionRuleEntity(
    id = id,
    targetAType = targetAType.name,
    targetAId = targetAId,
    targetBType = targetBType.name,
    targetBId = targetBId,
    severity = severity.name,
    message = message.trim()
)
