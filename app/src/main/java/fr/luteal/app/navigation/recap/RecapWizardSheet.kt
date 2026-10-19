package fr.luteal.app.navigation.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.luteal.app.R
import fr.luteal.core.designsystem.component.FullScreenEntry
import fr.luteal.core.designsystem.component.ScreenBack
import fr.luteal.app.navigation.recap.steps.BowelStep
import fr.luteal.app.navigation.recap.steps.CycleStep
import fr.luteal.app.navigation.recap.steps.GeneralStep
import fr.luteal.app.navigation.recap.steps.PainStep
import fr.luteal.app.navigation.recap.steps.PsyStep
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.model.recap.RecapBowelPayload
import fr.luteal.core.model.recap.RecapCyclePayload
import fr.luteal.core.model.recap.RecapGeneralPayload
import fr.luteal.core.model.recap.RecapPainPayload
import fr.luteal.core.model.recap.RecapPsyPayload
import fr.luteal.core.model.recap.RecapStep
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun RecapWizardSheet(
    date: LocalDate,
    viewModel: RecapWizardViewModel,
    startAtFirstPage: Boolean = false,
    onDismiss: () -> Unit
) {
    LaunchedEffect(date) { viewModel.open(date) }
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val cycleUi by viewModel.cycleUi.collectAsStateWithLifecycle()
    val hideDyspareunia by viewModel.hideDyspareunia.collectAsStateWithLifecycle()
    val hideLibido by viewModel.hideLibido.collectAsStateWithLifecycle()
    val steps = RecapStep.ordered
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    var saveCurrentStep by remember { mutableStateOf<(() -> Unit)?>(null) }
    var painSubPage by remember { mutableIntStateOf(0) }
    val activePage = pagerState.settledPage
    val currentStep = steps.getOrNull(activePage)

    LaunchedEffect(recap?.date, startAtFirstPage) {
        val loaded = recap ?: return@LaunchedEffect
        val target = loaded.wizardStartPage(startAtFirstPage)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    LaunchedEffect(activePage) {
        saveCurrentStep = null
        if (steps.getOrNull(activePage) != RecapStep.PAIN) {
            painSubPage = 0
        }
    }

    fun advanceFrom(page: Int) {
        if (page < steps.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(page + 1) }
        } else {
            viewModel.clear()
            onDismiss()
        }
    }

    FullScreenEntry {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            ScreenBack(onClick = {
                viewModel.clear()
                onDismiss()
            })
            Text(
                text = stringResource(R.string.recap_wizard_title),
                style = MaterialTheme.typography.titleLarge
            )
            val progress = (recap?.completedSteps?.size ?: 0).toFloat() / steps.size
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            val currentRecap = recap
            if (currentRecap == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true
            ) { page ->
                val stepActive = page == activePage
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
                ) {
                    val step = steps[page]
                    when (step) {
                        RecapStep.CYCLE -> CycleStep(
                            ui = cycleUi,
                            initial = currentRecap.cycle,
                            active = stepActive,
                            onBindSave = { saveCurrentStep = it },
                            onContinue = { payload ->
                                viewModel.finishCycleStep(payload) { advanceFrom(page) }
                            }
                        )
                        RecapStep.PAIN -> PainStep(
                            hideDyspareunia = hideDyspareunia,
                            initial = currentRecap.pain,
                            subPage = if (stepActive) painSubPage else 0,
                            onSubPageChange = { painSubPage = it },
                            active = stepActive,
                            onBindSave = { saveCurrentStep = it },
                            onContinue = { payload ->
                                painSubPage = 0
                                viewModel.finishPainStep(payload) { advanceFrom(page) }
                            }
                        )
                        RecapStep.PSY -> PsyStep(
                            hideLibido = hideLibido,
                            initial = currentRecap.psy,
                            active = stepActive,
                            onBindSave = { saveCurrentStep = it },
                            onContinue = { payload ->
                                viewModel.finishPsyStep(payload) { advanceFrom(page) }
                            }
                        )
                        RecapStep.GENERAL -> GeneralStep(
                            initial = currentRecap.general,
                            active = stepActive,
                            onBindSave = { saveCurrentStep = it },
                            onContinue = { payload ->
                                viewModel.finishGeneralStep(payload) { advanceFrom(page) }
                            }
                        )
                        RecapStep.BOWEL -> BowelStep(
                            initial = currentRecap.bowel,
                            active = stepActive,
                            onBindSave = { saveCurrentStep = it },
                            onContinue = { payload ->
                                viewModel.finishBowelStep(payload) { advanceFrom(page) }
                            }
                        )
                    }
                }
            }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                val showPrevious = currentRecap != null && (
                    pagerState.currentPage > 0 ||
                        (currentStep == RecapStep.PAIN && painSubPage > 0)
                )
                if (showPrevious) {
                    TextButton(
                        onClick = {
                            if (currentStep == RecapStep.PAIN && painSubPage > 0) {
                                painSubPage -= 1
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        modifier = Modifier.testTag("recap_previous")
                    ) { Text(stringResource(R.string.recap_previous)) }
                } else {
                    Spacer(modifier = Modifier)
                }
                TextButton(
                    onClick = { saveCurrentStep?.invoke() },
                    enabled = currentRecap != null && saveCurrentStep != null,
                    modifier = Modifier.testTag("recap_continue")
                ) { Text(stringResource(R.string.recap_continue)) }
            }
        }
    }
}
