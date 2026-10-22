package fr.luteal.app.backup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import fr.luteal.core.designsystem.component.LutealTextField
import fr.luteal.core.designsystem.component.TimeField
import java.time.LocalTime
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import fr.luteal.core.designsystem.theme.LutealSpacing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.R
import fr.luteal.core.data.backup.AutoBackupPassphraseStore
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealToggleRow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AutoBackupViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val backupWriter: BackupWriter,
    private val passphraseStore: AutoBackupPassphraseStore
) : ViewModel() {
    val preferences = userPreferencesDataStore.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), fr.luteal.core.data.datastore.UserPreferences())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setAutoBackupEnabled(enabled)
            backupWriter.reschedule()
        }
    }

    fun setUri(uri: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setAutoBackupUri(uri)
            backupWriter.reschedule()
        }
    }

    fun setHour(hour: String) {
        viewModelScope.launch {
            userPreferencesDataStore.setAutoBackupHour(hour)
            backupWriter.reschedule()
        }
    }

    fun setKeep(keep: Int) {
        viewModelScope.launch { userPreferencesDataStore.setAutoBackupKeep(keep) }
    }

    fun backupNow() {
        viewModelScope.launch { backupWriter.writeNow() }
    }

    /** Stores the passphrase then turns encryption on. Returns false when the two entries differ or are empty. */
    fun enableEncryption(password: CharArray, confirm: CharArray): Boolean {
        if (password.isEmpty() || !password.contentEquals(confirm)) return false
        passphraseStore.setPassphrase(password)
        viewModelScope.launch { userPreferencesDataStore.setBackupEncryptionEnabled(true) }
        return true
    }

    fun disableEncryption() {
        passphraseStore.clear()
        viewModelScope.launch { userPreferencesDataStore.setBackupEncryptionEnabled(false) }
    }
}

@Composable
fun AutoBackupCard(viewModel: AutoBackupViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var showPassphraseDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setUri(uri.toString())
        }
    }
    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            LutealToggleRow(
                title = stringResource(R.string.backup_auto_title),
                description = stringResource(R.string.backup_auto_body),
                checked = prefs.autoBackupEnabled,
                onCheckedChange = viewModel::setEnabled
            )
            LutealToggleRow(
                title = stringResource(R.string.backup_encrypt_title),
                description = stringResource(R.string.backup_encrypt_body),
                checked = prefs.backupEncryptionEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showPassphraseDialog = true else viewModel.disableEncryption()
                }
            )
            if (showPassphraseDialog) {
                BackupPassphraseDialog(
                    onConfirm = { password, confirm ->
                        if (viewModel.enableEncryption(password, confirm)) {
                            showPassphraseDialog = false
                            true
                        } else {
                            false
                        }
                    },
                    onDismiss = { showPassphraseDialog = false }
                )
            }
            TextButton(onClick = { folderLauncher.launch(null) }) {
                Text(stringResource(R.string.backup_choose_folder))
            }
            val backupHour = remember(prefs.autoBackupHour) {
                runCatching { LocalTime.parse(prefs.autoBackupHour) }.getOrDefault(LocalTime.of(3, 0))
            }
            TimeField(
                value = backupHour,
                onValueChange = { viewModel.setHour(String.format("%02d:%02d", it.hour, it.minute)) },
                label = stringResource(R.string.backup_hour),
                modifier = Modifier.fillMaxWidth()
            )
            LutealTextField(
                value = prefs.autoBackupKeep.toString(),
                onValueChange = { raw -> raw.toIntOrNull()?.let(viewModel::setKeep) },
                label = { Text(stringResource(R.string.backup_keep)) },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = viewModel::backupNow) { Text(stringResource(R.string.backup_now)) }
            if (prefs.autoBackupLastSuccess.isNotBlank()) {
                Text(stringResource(R.string.backup_last_success, prefs.autoBackupLastSuccess))
            }
            if (prefs.autoBackupLastError.isNotBlank()) {
                Text(stringResource(R.string.backup_last_error, prefs.autoBackupLastError))
            }
        }
    }
}

@Composable
private fun BackupPassphraseDialog(
    onConfirm: (CharArray, CharArray) -> Boolean,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_encrypt_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
                Text(stringResource(R.string.backup_encrypt_dialog_body))
                LutealTextField(
                    value = password,
                    onValueChange = { password = it; mismatch = false },
                    label = { Text(stringResource(R.string.backup_encrypt_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                LutealTextField(
                    value = confirm,
                    onValueChange = { confirm = it; mismatch = false },
                    label = { Text(stringResource(R.string.backup_encrypt_password_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.backup_encrypt_mismatch),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                mismatch = !onConfirm(password.toCharArray(), confirm.toCharArray())
            }) { Text(stringResource(R.string.backup_encrypt_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
