package fr.luteal.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.local.BiomarkerDao
import fr.luteal.core.data.local.AppointmentDao
import fr.luteal.core.data.local.CareTypeDao
import fr.luteal.core.data.local.ProfessionalDao
import fr.luteal.core.data.local.CycleDao
import fr.luteal.core.data.local.DailyEntryDao
import fr.luteal.core.data.local.DailyRecapDao
import fr.luteal.core.data.local.QuestionnaireResultDao
import fr.luteal.core.data.local.JournalEventDao
import fr.luteal.core.data.local.LutealDatabase
import fr.luteal.core.data.local.MIGRATION_10_11
import fr.luteal.core.data.local.MIGRATION_11_12
import fr.luteal.core.data.local.MIGRATION_12_13
import fr.luteal.core.data.local.MIGRATION_13_14
import fr.luteal.core.data.local.MIGRATION_14_15
import fr.luteal.core.data.local.MIGRATION_15_16
import fr.luteal.core.data.local.AppointmentAttachmentDao
import fr.luteal.core.data.local.MedicationDao
import fr.luteal.core.data.local.MedicationIntakeDao
import fr.luteal.core.data.local.ReminderDao
import fr.luteal.core.data.local.SymptomDao
import fr.luteal.core.data.local.TreatmentPlanDao
import fr.luteal.core.data.local.UserProfileDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLutealDatabase(
        @ApplicationContext context: Context
    ): LutealDatabase {
        return Room.databaseBuilder(
            context,
            LutealDatabase::class.java,
            "endoluteal.db"
        )
            .addMigrations(
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16
            )
            // Versions below 10 may wipe. 10 starts MIGRATION_10_11, so Room
            // forbids listing it here. From 11, a missing migration fails open.
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9)
            .build()
    }

    @Provides
    @Singleton
    fun provideCycleDao(database: LutealDatabase): CycleDao = database.cycleDao()

    @Provides
    @Singleton
    fun provideDailyEntryDao(database: LutealDatabase): DailyEntryDao = database.dailyEntryDao()

    @Provides
    @Singleton
    fun provideSymptomDao(database: LutealDatabase): SymptomDao = database.symptomDao()

    @Provides
    @Singleton
    fun provideUserProfileDao(database: LutealDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    @Singleton
    fun provideBiomarkerDao(database: LutealDatabase): BiomarkerDao = database.biomarkerDao()

    @Provides
    @Singleton
    fun provideJournalEventDao(database: LutealDatabase): JournalEventDao = database.journalEventDao()

    @Provides
    @Singleton
    fun provideReminderDao(database: LutealDatabase): ReminderDao = database.reminderDao()

    @Provides
    @Singleton
    fun provideMedicationDao(database: LutealDatabase): MedicationDao = database.medicationDao()

    @Provides
    @Singleton
    fun provideMedicationIntakeDao(database: LutealDatabase): MedicationIntakeDao = database.medicationIntakeDao()

    @Provides
    @Singleton
    fun provideTreatmentPlanDao(database: LutealDatabase): TreatmentPlanDao = database.treatmentPlanDao()

    @Provides
    @Singleton
    fun provideCareTypeDao(database: LutealDatabase): CareTypeDao = database.careTypeDao()

    @Provides
    @Singleton
    fun provideProfessionalDao(database: LutealDatabase): ProfessionalDao = database.professionalDao()

    @Provides
    @Singleton
    fun provideAppointmentDao(database: LutealDatabase): AppointmentDao = database.appointmentDao()

    @Provides
    @Singleton
    fun provideAppointmentAttachmentDao(database: LutealDatabase): AppointmentAttachmentDao =
        database.appointmentAttachmentDao()

    @Provides
    @Singleton
    fun provideDailyRecapDao(database: LutealDatabase): DailyRecapDao = database.dailyRecapDao()

    @Provides
    fun provideQuestionnaireResultDao(database: LutealDatabase): QuestionnaireResultDao =
        database.questionnaireResultDao()
}
