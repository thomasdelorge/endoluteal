package fr.luteal.app.navigation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import fr.luteal.app.R
import fr.luteal.app.navigation.OpenRouterModelsUiState
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.component.SettingsDropdownField
import fr.luteal.core.designsystem.component.SettingsSearchableDropdownField
import fr.luteal.core.designsystem.theme.LutealSpacing
import fr.luteal.core.openrouter.OpenRouterModelInfo
import fr.luteal.core.openrouter.SttLanguageOptions
import fr.luteal.core.openrouter.SttModelCapabilities

private const val MASKED_KEY_PLACEHOLDER = "***"

@Composable
fun OpenRouterSettingsCard(
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    sttModel: String,
    summaryModel: String,
    sttLanguage: String,
    diarizationEnabled: Boolean,
    hasApiKey: Boolean,
    modelsState: OpenRouterModelsUiState,
    onRefreshModels: () -> Unit,
    onSttModelChange: (String) -> Unit,
    onSummaryModelChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onDiarizationChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit
) {
    var apiKeyDraft by rememberSaveable { mutableStateOf("") }
    val diarizationSupported = SttModelCapabilities.supportsDiarization(sttModel)
    val languageOptions = SttLanguageOptions.codes.map { code ->
        code to sttLanguageLabel(code)
    }
    val sttOptions = modelsState.sttModels.toDropdownOptions(sttModel)
    val textOptions = modelsState.textModels.toDropdownOptions(summaryModel)
    val modelsReady = modelsState.sttModels.isNotEmpty() && modelsState.textModels.isNotEmpty()
    val configEnabled = aiEnabled
    val apiKeyFieldValue = when {
        apiKeyDraft.isNotEmpty() -> apiKeyDraft
        hasApiKey -> MASKED_KEY_PLACEHOLDER
        else -> ""
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(LutealSpacing.md),
            verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)
        ) {
            LutealToggleRow(
                title = stringResource(R.string.settings_openrouter_ai_enabled),
                description = stringResource(R.string.settings_openrouter_ai_enabled_desc),
                checked = aiEnabled,
                onCheckedChange = onAiEnabledChange
            )
            Text(
                stringResource(R.string.settings_openrouter_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.settings_openrouter_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = apiKeyFieldValue,
                onValueChange = { raw ->
                    when {
                        raw.isEmpty() -> {
                            apiKeyDraft = ""
                            onApiKeyChange("")
                        }
                        apiKeyFieldValue == MASKED_KEY_PLACEHOLDER && raw != MASKED_KEY_PLACEHOLDER -> {
                            val typed = raw.removePrefix(MASKED_KEY_PLACEHOLDER)
                            apiKeyDraft = typed
                            onApiKeyChange(typed)
                        }
                        else -> {
                            apiKeyDraft = raw
                            onApiKeyChange(raw)
                        }
                    }
                },
                label = { Text(stringResource(R.string.settings_openrouter_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (configEnabled && modelsState.error != null) {
                Text(
                    modelsState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (configEnabled) {
                TextButton(
                    onClick = onRefreshModels,
                    enabled = !modelsState.loading
                ) {
                    Text(
                        if (modelsState.loading) {
                            stringResource(R.string.settings_openrouter_models_loading)
                        } else {
                            stringResource(R.string.settings_openrouter_models_refresh)
                        }
                    )
                }
            }
            SettingsSearchableDropdownField(
                label = stringResource(R.string.settings_openrouter_stt_model),
                selectedId = sttModel,
                options = sttOptions,
                onSelect = onSttModelChange,
                searchPlaceholder = stringResource(R.string.settings_openrouter_models_search),
                enabled = configEnabled && modelsReady && !modelsState.loading
            )
            SettingsDropdownField(
                label = stringResource(R.string.settings_openrouter_stt_language),
                selectedLabel = sttLanguageLabel(sttLanguage),
                options = languageOptions,
                onSelect = { if (configEnabled) onLanguageChange(it) }
            )
            LutealToggleRow(
                title = stringResource(R.string.settings_openrouter_diarization),
                description = if (diarizationSupported) {
                    stringResource(R.string.settings_openrouter_diarization_desc)
                } else {
                    stringResource(R.string.settings_openrouter_diarization_unsupported)
                },
                checked = diarizationEnabled && diarizationSupported,
                onCheckedChange = { if (diarizationSupported && configEnabled) onDiarizationChange(it) }
            )
            SettingsSearchableDropdownField(
                label = stringResource(R.string.settings_openrouter_summary_model),
                selectedId = summaryModel,
                options = textOptions,
                onSelect = onSummaryModelChange,
                searchPlaceholder = stringResource(R.string.settings_openrouter_models_search),
                enabled = configEnabled && modelsReady && !modelsState.loading
            )
            Text(
                stringResource(R.string.settings_openrouter_parakeet_eu),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun List<OpenRouterModelInfo>.toDropdownOptions(selectedId: String): List<Pair<String, String>> {
    val base = map { it.id to it.displayLabel() }
    if (selectedId.isBlank() || base.any { it.first == selectedId }) return base
    return listOf(selectedId to selectedId) + base
}

@Composable
private fun sttLanguageLabel(code: String): String = when (code) {
    "fr" -> stringResource(R.string.settings_openrouter_lang_fr)
    "en" -> stringResource(R.string.settings_openrouter_lang_en)
    "de" -> stringResource(R.string.settings_openrouter_lang_de)
    "es" -> stringResource(R.string.settings_openrouter_lang_es)
    "it" -> stringResource(R.string.settings_openrouter_lang_it)
    "pt" -> stringResource(R.string.settings_openrouter_lang_pt)
    "nl" -> stringResource(R.string.settings_openrouter_lang_nl)
    else -> code.uppercase()
}
