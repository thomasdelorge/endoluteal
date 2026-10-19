package fr.luteal.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun <T> SettingsMultiSelectDropdown(
    label: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onToggle: (T) -> Unit,
    emptySummary: String,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { menuOpen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selected.isEmpty()) {
                    emptySummary
                } else {
                    selected.map(optionLabel).joinToString()
                }
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = {
                        Checkbox(
                            checked = option in selected,
                            onCheckedChange = null
                        )
                    },
                    onClick = { onToggle(option) }
                )
            }
        }
    }
}
