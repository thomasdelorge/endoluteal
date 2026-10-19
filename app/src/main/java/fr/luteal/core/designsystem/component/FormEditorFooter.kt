package fr.luteal.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.luteal.app.R

@Composable
fun FormEditorFooter(
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDestructive: (() -> Unit)? = null,
    destructiveContentDescription: String = stringResource(R.string.meds_delete),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onDestructive != null) {
            Button(
                onClick = onDestructive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Filled.Delete, contentDescription = destructiveContentDescription)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onSave, enabled = saveEnabled) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.meds_save)
            )
        }
    }
}
