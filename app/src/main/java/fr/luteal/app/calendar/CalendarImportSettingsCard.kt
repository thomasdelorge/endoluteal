package fr.luteal.app.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import fr.luteal.core.designsystem.component.LutealTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.luteal.app.R
import fr.luteal.core.data.datastore.UserPreferencesDataStore
import fr.luteal.core.designsystem.component.LutealCard
import fr.luteal.core.designsystem.component.LutealToggleRow
import fr.luteal.core.designsystem.theme.LutealSpacing
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarImportSettingsViewModel @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val deviceCalendarCatalog: DeviceCalendarCatalog,
    private val calendarImportScheduler: CalendarImportScheduler
) : ViewModel() {
    val preferences = userPreferencesDataStore.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), fr.luteal.core.data.datastore.UserPreferences())

    private val _calendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val calendars: StateFlow<List<DeviceCalendar>> = _calendars.asStateFlow()

    fun refreshCalendars(hasPermission: Boolean) {
        if (!hasPermission) {
            _calendars.value = emptyList()
            return
        }
        _calendars.value = deviceCalendarCatalog.listCalendars()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesDataStore.setCalendarImportEnabled(enabled)
            if (enabled) {
                calendarImportScheduler.enqueueNow()
                calendarImportScheduler.schedulePeriodic()
            }
        }
    }

    fun setKeywords(raw: String) {
        viewModelScope.launch { userPreferencesDataStore.setCalendarImportKeywords(raw) }
    }

    fun toggleCalendar(id: Long, selected: Boolean) {
        viewModelScope.launch {
            val prefs = userPreferencesDataStore.userPreferencesFlow.first()
            val key = id.toString()
            val next = prefs.calendarSelectedIds.toMutableSet()
            if (selected) next += key else next -= key
            userPreferencesDataStore.setCalendarSelectedIds(next)
            calendarImportScheduler.enqueueNow()
        }
    }

    fun importNow() {
        viewModelScope.launch { calendarImportScheduler.enqueueNow() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarImportSettingsCard(
    viewModel: CalendarImportSettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keywords by remember(prefs.calendarImportKeywords) { mutableStateOf(prefs.calendarImportKeywords) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        viewModel.refreshCalendars(granted)
        if (granted) {
            viewModel.setEnabled(true)
        }
    }

    LaunchedEffect(hasPermission) {
        viewModel.refreshCalendars(hasPermission)
    }

    LutealCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(LutealSpacing.sm)) {
            LutealToggleRow(
                title = stringResource(R.string.calendar_import_title),
                description = stringResource(R.string.calendar_import_body),
                checked = prefs.calendarImportEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (hasPermission) {
                            viewModel.setEnabled(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        viewModel.setEnabled(false)
                    }
                }
            )
            LutealTextField(
                value = keywords,
                onValueChange = {
                    keywords = it
                    viewModel.setKeywords(it)
                },
                label = { Text(stringResource(R.string.calendar_import_keywords)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (!hasPermission && prefs.calendarImportEnabled) {
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text(stringResource(R.string.calendar_import_grant_permission))
                }
            }
            if (hasPermission && calendars.isNotEmpty()) {
                Text(stringResource(R.string.calendar_import_calendars), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LutealSpacing.xs)) {
                    calendars.forEach { calendar ->
                        val selected = prefs.calendarSelectedIds.contains(calendar.id.toString())
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleCalendar(calendar.id, !selected) },
                            label = { Text(calendar.displayName) }
                        )
                    }
                }
            }
            TextButton(onClick = viewModel::importNow, enabled = prefs.calendarImportEnabled && hasPermission) {
                Text(stringResource(R.string.calendar_import_now))
            }
        }
    }
}
