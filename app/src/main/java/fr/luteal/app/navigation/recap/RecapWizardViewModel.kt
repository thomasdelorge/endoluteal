package fr.luteal.app.navigation.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.notification.NotificationScheduler
import fr.luteal.core.data.repository.BiomarkerRepository
import fr.luteal.core.data.repository.CycleRepository
import fr.luteal.core.data.repository.DailyEntryRepository
import fr.luteal.core.data.repository.DailyRecapRepository
import fr.luteal.core.data.repository.TreatmentPlanRepository
import fr.luteal.core.data.repository.UserRepository
import fr.luteal.core.domain.RecapCycleWriter
import fr.luteal.core.model.AgeBand
import fr.luteal.core.model.CycleEstimateCalculator
import fr.luteal.core.model.CycleEstimateResult
import fr.luteal.core.model.CurrentCyclePhase
import fr.luteal.core.model.CurrentCyclePhaseCalculator
import fr.luteal.core.model.CyclePhase
import fr.luteal.core.model.PhaseCertainty
import fr.luteal.core.model.recap.DailyRecap
import fr.luteal.core.model.recap.RecapBowelPayload
import fr.luteal.core.model.recap.RecapCyclePayload
import fr.luteal.core.model.recap.RecapGeneralPayload
import fr.luteal.core.model.recap.RecapPainPayload
import fr.luteal.core.model.recap.RecapPsyPayload
import fr.luteal.core.model.recap.RecapStep
import fr.luteal.core.model.meds.continuousHormonalActive
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecapWizardViewModel @Inject constructor(
    private val dailyRecapRepository: DailyRecapRepository,
    private val dailyEntryRepository: DailyEntryRepository,
    private val biomarkerRepository: BiomarkerRepository,
    private val cycleRepository: CycleRepository,
    private val userRepository: UserRepository,
    private val treatmentPlanRepository: TreatmentPlanRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clock: Clock
) : ViewModel() {
    private val _recapDate = MutableStateFlow<LocalDate?>(null)
    val recapDate: StateFlow<LocalDate?> = _recapDate.asStateFlow()

    private val _recap = MutableStateFlow<DailyRecap?>(null)
    val recap: StateFlow<DailyRecap?> = _recap.asStateFlow()

    private val _cycleUi = MutableStateFlow<RecapCycleUiState?>(null)
    val cycleUi: StateFlow<RecapCycleUiState?> = _cycleUi.asStateFlow()

    val hideDyspareunia: StateFlow<Boolean> = userRepository.getUserPreferences()
        .map { it.hideDyspareuniaInRecap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hideLibido: StateFlow<Boolean> = userRepository.getUserPreferences()
        .map { it.hideLibidoInRecap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun open(date: LocalDate) {
        if (_recapDate.value == date && _recap.value != null) return
        _recapDate.value = date
        viewModelScope.launch {
            val existing = dailyRecapRepository.get(date)
            _recap.value = existing ?: DailyRecap.empty(date, clock.instant())
            refreshCycleUi(date)
        }
    }

    fun initialPageIndex(): Int {
        val recap = _recap.value ?: return 0
        return RecapStep.ordered.indexOf(recap.firstIncompleteStep()).coerceAtLeast(0)
    }

    fun completeStep(step: RecapStep) {
        val date = _recapDate.value ?: return
        viewModelScope.launch {
            dailyRecapRepository.markStepComplete(date, step)
            _recap.value = dailyRecapRepository.get(date)
        }
    }

    fun finishCycleStep(payload: RecapCyclePayload, onDone: () -> Unit) {
        val date = _recapDate.value ?: return
        viewModelScope.launch {
            val now = clock.instant()
            val current = dailyRecapRepository.get(date) ?: DailyRecap.empty(date, now)
            dailyRecapRepository.upsert(current.copy(cycle = payload, updatedAt = now))
            val cycles = cycleRepository.getCyclesOnce()
            RecapCycleWriter.apply(
                date = date,
                payload = payload,
                cycles = cycles,
                dailyEntryRepository = dailyEntryRepository,
                biomarkerRepository = biomarkerRepository,
                cycleRepository = cycleRepository,
                clock = clock
            )
            dailyRecapRepository.markStepComplete(date, RecapStep.CYCLE)
            notificationScheduler.reconcileAllSchedules()
            _recap.value = dailyRecapRepository.get(date)
            onDone()
        }
    }

    fun finishPainStep(payload: RecapPainPayload, onDone: () -> Unit) {
        finishPayloadStep(RecapStep.PAIN, onDone) { current, now ->
            current.copy(pain = payload, updatedAt = now)
        }
    }

    fun finishPsyStep(payload: RecapPsyPayload, onDone: () -> Unit) {
        finishPayloadStep(RecapStep.PSY, onDone) { current, now ->
            current.copy(psy = payload, updatedAt = now)
        }
    }

    fun finishGeneralStep(payload: RecapGeneralPayload, onDone: () -> Unit) {
        finishPayloadStep(RecapStep.GENERAL, onDone) { current, now ->
            current.copy(general = payload, updatedAt = now)
        }
    }

    fun finishBowelStep(payload: RecapBowelPayload, onDone: () -> Unit) {
        finishPayloadStep(RecapStep.BOWEL, onDone) { current, now ->
            current.copy(bowel = payload, updatedAt = now)
        }
    }

    private fun finishPayloadStep(
        step: RecapStep,
        onDone: () -> Unit,
        merge: (DailyRecap, Instant) -> DailyRecap
    ) {
        val date = _recapDate.value ?: return
        viewModelScope.launch {
            val now = clock.instant()
            val current = dailyRecapRepository.get(date) ?: DailyRecap.empty(date, now)
            dailyRecapRepository.upsert(merge(current, now))
            dailyRecapRepository.markStepComplete(date, step)
            _recap.value = dailyRecapRepository.get(date)
            onDone()
        }
    }

    fun clear() {
        _recapDate.value = null
        _recap.value = null
        _cycleUi.value = null
    }

    private suspend fun refreshCycleUi(date: LocalDate) {
        val cycles = cycleRepository.getCyclesOnce()
        val currentCycle = cycles.firstOrNull { cycle ->
            cycle.startDate <= date && (cycle.endDate == null || cycle.endDate >= date)
        }
        val entry = dailyEntryRepository.getEntryOnce(date)
        val prefs = userRepository.getUserPreferences().first()
        val plans = treatmentPlanRepository.allOnce()
        val hideEstimate = continuousHormonalActive(plans, date)
        val estimateResult = if (hideEstimate) {
            CycleEstimateResult.NeedsMoreHistory
        } else {
            CycleEstimateCalculator.evaluate(
                cycles = cycles,
                ageBand = AgeBand.fromId(prefs.ageBand),
                hasTimingContext = prefs.hasTimingContext
            )
        }
        val phase = CurrentCyclePhaseCalculator.evaluate(
            today = date,
            currentCycle = currentCycle,
            todayEntry = entry,
            estimateResult = estimateResult
        )
        val estimated = (phase as? CurrentCyclePhase.Available)?.phase
        val isEstimate = phase is CurrentCyclePhase.Available && phase.certainty == PhaseCertainty.ESTIMATED
        _cycleUi.value = RecapCycleUiState(estimatedPhase = estimated, phase = phase, isEstimate = isEstimate)
    }

}
