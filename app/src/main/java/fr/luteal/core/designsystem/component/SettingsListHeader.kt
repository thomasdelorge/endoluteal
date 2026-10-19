package fr.luteal.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsListHeader(
    onBack: () -> Unit,
    onAdd: (() -> Unit)?,
    addContentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ScreenBack(onClick = onBack)
        Spacer(Modifier.weight(1f))
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = addContentDescription)
            }
        }
    }
}
