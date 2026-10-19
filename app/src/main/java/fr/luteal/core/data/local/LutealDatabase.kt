package fr.luteal.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.luteal.core.data.entity.BiomarkerObservationEntity
import fr.luteal.core.data.entity.AppointmentEntity
import fr.luteal.core.data.entity.CareTypeEntity
import fr.luteal.core.data.entity.ProfessionalEntity
import fr.luteal.core.data.entity.DailyRecapEntity
import fr.luteal.core.data.entity.QuestionnaireResultEntity
import fr.luteal.core.data.entity.CycleEntity
import fr.luteal.core.data.entity.DailyEntryEntity
import fr.luteal.core.data.entity.InteractionRuleEntity
import fr.luteal.core.data.entity.JournalEventEntity
import fr.luteal.core.data.entity.MedicationEntity
import fr.luteal.core.data.entity.MedicationContraindicationEntity
import fr.luteal.core.data.entity.MedicationIntakeEntity
import fr.luteal.core.data.entity.MedicationSubstanceEntity
import fr.luteal.core.data.entity.PainMarkEntity
import fr.luteal.core.data.entity.ReminderEntity
import fr.luteal.core.data.entity.SubstanceEntity
import fr.luteal.core.data.entity.SymptomLogEntity
import fr.luteal.core.data.entity.TreatmentPlanEntity
import fr.luteal.core.data.entity.UserProfileEntity

@Database(
    entities = [
        CycleEntity::class,
        DailyEntryEntity::class,
        SymptomLogEntity::class,
        UserProfileEntity::class,
        BiomarkerObservationEntity::class,
        JournalEventEntity::class,
        PainMarkEntity::class,
        ReminderEntity::class,
        SubstanceEntity::class,
        MedicationEntity::class,
        MedicationSubstanceEntity::class,
        MedicationContraindicationEntity::class,
        InteractionRuleEntity::class,
        MedicationIntakeEntity::class,
        TreatmentPlanEntity::class,
        CareTypeEntity::class,
        ProfessionalEntity::class,
        AppointmentEntity::class,
        DailyRecapEntity::class,
        QuestionnaireResultEntity::class
    ],
    version = 15,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LutealDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun symptomDao(): SymptomDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun biomarkerDao(): BiomarkerDao
    abstract fun journalEventDao(): JournalEventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationIntakeDao(): MedicationIntakeDao
    abstract fun treatmentPlanDao(): TreatmentPlanDao
    abstract fun careTypeDao(): CareTypeDao
    abstract fun professionalDao(): ProfessionalDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun dailyRecapDao(): DailyRecapDao
    abstract fun questionnaireResultDao(): QuestionnaireResultDao
}
