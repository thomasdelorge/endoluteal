package fr.luteal.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.luteal.app.R
import kotlin.math.roundToInt

private val PainGreen = Color(0xFF2E7D32)
private val PainRed = Color(0xFFC62828)

fun painIntensityColor(intensity: Int): Color =
    lerp(PainGreen, PainRed, intensity.coerceIn(0, 10) / 10f)

fun painIntensityArgb(intensity: Int): Int = painIntensityColor(intensity).toArgb()

@StringRes
private fun painHintRes(level: Int): Int = when (level.coerceIn(0, 10)) {
    0 -> R.string.pain_hint_0
    1 -> R.string.pain_hint_1
    2 -> R.string.pain_hint_2
    3 -> R.string.pain_hint_3
    4 -> R.string.pain_hint_4
    5 -> R.string.pain_hint_5
    6 -> R.string.pain_hint_6
    7 -> R.string.pain_hint_7
    8 -> R.string.pain_hint_8
    9 -> R.string.pain_hint_9
    else -> R.string.pain_hint_10
}

@Composable
fun PainScale(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var touched by remember { mutableStateOf(value != null) }
    val sliderValue = (value ?: 0).toFloat()
    val hint = if (!touched) {
        stringResource(R.string.pain_hint_prompt)
    } else {
        val level = sliderValue.roundToInt()
        "$level — ${stringResource(painHintRes(level))}"
    }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = { raw ->
                touched = true
                onValueChange(raw.roundToInt())
            },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pain_scale_slider")
                .semantics {
                    if (touched) {
                        stateDescription = sliderValue.roundToInt().toString()
                    }
                }
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
