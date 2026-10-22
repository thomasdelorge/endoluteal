package fr.luteal.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.luteal.core.data.seed.TestDataSeeder
import fr.luteal.core.data.seed.TestDataSeederImpl
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.data.repository.BiomarkerRepository
import fr.luteal.core.data.repository.BiomarkerRepositoryImpl
import fr.luteal.core.data.repository.AppointmentAttachmentRepository
import fr.luteal.core.data.repository.AppointmentAttachmentRepositoryImpl
import fr.luteal.core.data.repository.AppointmentRepository
import fr.luteal.core.data.repository.AppointmentRepositoryImpl
import fr.luteal.core.data.repository.CareTypeRepository
import fr.luteal.core.data.repository.CareTypeRepositoryImpl
import fr.luteal.core.data.repository.ProfessionalRepository
import fr.luteal.core.data.repository.ProfessionalRepositoryImpl
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.CycleRepositoryImpl
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DailyEntryRepositoryImpl
import fr.luteal.core.data.repository.DailyRecapRepository
import fr.luteal.core.data.repository.DailyRecapRepositoryImpl
import fr.luteal.core.data.repository.QuestionnaireResultRepository
import fr.luteal.core.data.repository.QuestionnaireResultRepositoryImpl
import fr.luteal.core.data.repository.JournalEventRepository
import fr.luteal.core.data.repository.JournalEventRepositoryImpl
import fr.luteal.core.data.repository.MedicationIntakeRepository
import fr.luteal.core.data.repository.MedicationIntakeRepositoryImpl
import fr.luteal.core.data.repository.MedicationRepository
import fr.luteal.core.data.repository.MedicationRepositoryImpl
import fr.luteal.core.data.repository.SymptomRepository
import fr.luteal.core.data.repository.SymptomRepositoryImpl
import fr.luteal.core.data.repository.TreatmentPlanRepository
import fr.luteal.core.data.repository.TreatmentPlanRepositoryImpl
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.data.repository.UserRepositoryImpl
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCycleRepository(
        cycleRepositoryImpl: CycleRepositoryImpl
    ): CycleRepository

    @Binds
    @Singleton
    abstract fun bindDailyEntryRepository(
        dailyEntryRepositoryImpl: DailyEntryRepositoryImpl
    ): DailyEntryRepository

    @Binds
    @Singleton
    abstract fun bindDailyRecapRepository(
        dailyRecapRepositoryImpl: DailyRecapRepositoryImpl
    ): DailyRecapRepository

    @Binds
    @Singleton
    abstract fun bindQuestionnaireResultRepository(
        impl: QuestionnaireResultRepositoryImpl
    ): QuestionnaireResultRepository

    @Binds
    @Singleton
    abstract fun bindBiomarkerRepository(
        biomarkerRepositoryImpl: BiomarkerRepositoryImpl
    ): BiomarkerRepository

    @Binds
    @Singleton
    abstract fun bindSymptomRepository(
        symptomRepositoryImpl: SymptomRepositoryImpl
    ): SymptomRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindJournalEventRepository(
        journalEventRepositoryImpl: JournalEventRepositoryImpl
    ): JournalEventRepository

    @Binds
    @Singleton
    abstract fun bindMedicationRepository(
        medicationRepositoryImpl: MedicationRepositoryImpl
    ): MedicationRepository

    @Binds
    @Singleton
    abstract fun bindMedicationIntakeRepository(
        medicationIntakeRepositoryImpl: MedicationIntakeRepositoryImpl
    ): MedicationIntakeRepository

    @Binds
    @Singleton
    abstract fun bindTreatmentPlanRepository(
        treatmentPlanRepositoryImpl: TreatmentPlanRepositoryImpl
    ): TreatmentPlanRepository

    @Binds
    @Singleton
    abstract fun bindCareTypeRepository(
        careTypeRepositoryImpl: CareTypeRepositoryImpl
    ): CareTypeRepository

    @Binds
    @Singleton
    abstract fun bindProfessionalRepository(
        professionalRepositoryImpl: ProfessionalRepositoryImpl
    ): ProfessionalRepository

    @Binds
    @Singleton
    abstract fun bindAppointmentRepository(
        appointmentRepositoryImpl: AppointmentRepositoryImpl
    ): AppointmentRepository

    @Binds
    @Singleton
    abstract fun bindAppointmentAttachmentRepository(
        appointmentAttachmentRepositoryImpl: AppointmentAttachmentRepositoryImpl
    ): AppointmentAttachmentRepository

    @Binds
    @Singleton
    abstract fun bindTestDataSeeder(
        testDataSeederImpl: TestDataSeederImpl
    ): TestDataSeeder


    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock {
            return Clock.systemUTC()
        }

        @Provides
        @Singleton
        fun provideUserPreferencesDataStore(
            @ApplicationContext context: Context
        ): UserPreferencesDataStore {
            return UserPreferencesDataStore(context)
        }
    }
}
