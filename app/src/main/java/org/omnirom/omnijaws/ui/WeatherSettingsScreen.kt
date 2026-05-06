/*
 * Copyright 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.omnirom.omnijaws.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.scaffold.AxionScaffold

import org.omnirom.omnijaws.icon.IconProvider

@Composable
fun WeatherSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onEnableChanged: (Boolean) -> Unit,
    onProviderChanged: (String) -> Unit,
    onUnitsChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onCustomLocationChanged: (Boolean) -> Unit,
    onLocationPickerClick: () -> Unit,
    onIconPackChanged: (String) -> Unit,
    onIconThemeChanged: (String) -> Unit,
    onOwmKeyChanged: (String) -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    AxionScaffold(
        title = "Weather settings",
        onBackClick = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PreferenceGroup {
                item {
                    SwitchPreference(
                        title = "Enable weather service",
                        checked = state.enabled,
                        onCheckedChange = onEnableChanged,
                        icon = Icons.Outlined.Cloud
                    )
                }
            }

            if (state.enabled) {
                PreferenceGroup(title = "General") {
                    item {
                        ListPreference(
                            title = "Weather provider",
                            summary = state.providerLabel,
                            options = listOf("0" to "OpenWeatherMap", "1" to "MET Norway"),
                            value = state.provider,
                            onValueChange = onProviderChanged
                        )
                    }
                    item {
                        ListPreference(
                            title = "Temperature unit",
                            summary = state.unitsLabel,
                            options = listOf("0" to "Metric (\u00b0C)", "1" to "Imperial (\u00b0F)"),
                            value = state.units,
                            onValueChange = onUnitsChanged
                        )
                    }
                    item {
                        ListPreference(
                            title = "Update interval",
                            summary = state.intervalLabel,
                            options = listOf(
                                "1" to "1 hour",
                                "2" to "2 hours",
                                "4" to "4 hours",
                                "6" to "6 hours",
                                "12" to "12 hours"
                            ),
                            value = state.updateInterval,
                            onValueChange = onIntervalChanged
                        )
                    }
                    item {
                        ClickablePreference(
                            title = "Last update",
                            summary = state.lastUpdateTime.ifEmpty { "Never" },
                            icon = Icons.Outlined.Update,
                            onClick = {}
                        )
                    }
                }

                PreferenceGroup(title = "Location") {
                    item {
                        SwitchPreference(
                            title = "Custom location",
                            summary = "Use a manually selected location",
                            checked = state.customLocation,
                            onCheckedChange = onCustomLocationChanged,
                            icon = Icons.Outlined.MyLocation
                        )
                    }
                    if (state.customLocation) {
                        item {
                            ClickablePreference(
                                title = "Location",
                                summary = state.locationName.ifEmpty { "Not set" },
                                icon = Icons.Outlined.LocationOn,
                                onClick = onLocationPickerClick
                            )
                        }
                    }
                    if (!state.customLocation && !state.hasLocationPermission) {
                        item {
                            ClickablePreference(
                                title = "Grant location permission",
                                summary = "Required for automatic location",
                                icon = Icons.Outlined.Security,
                                onClick = onRequestLocationPermission
                            )
                        }
                    }
                }

                if (state.iconPacks.isNotEmpty()) {
                    PreferenceGroup(title = "Appearance") {
                        item {
                            ListPreference(
                                title = "Icon pack",
                                summary = state.iconPacks.firstOrNull { it.value == state.iconPack }?.label,
                                options = state.iconPacks.map { it.value to it.label },
                                value = state.iconPack,
                                onValueChange = onIconPackChanged
                            )
                        }

                        item {
                            ListPreference(
                                title = "Icon theme",
                                summary = state.iconThemeLabel,
                                options = listOf(
                                    IconProvider.ICON_THEME_SYSTEM.toString() to "Follow system",
                                    IconProvider.ICON_THEME_LIGHT.toString() to "Light",
                                    IconProvider.ICON_THEME_DARK.toString() to "Dark"
                                ),
                                value = state.iconTheme,
                                onValueChange = onIconThemeChanged
                            )
                        }
                    }
                }

                if (state.provider == "0") {
                    PreferenceGroup(title = "API") {
                        item {
                            EditTextPreference(
                                title = "OpenWeatherMap API key",
                                value = state.owmKey,
                                onValueChange = onOwmKeyChanged
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTextPreference(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(value) }

    ClickablePreference(
        title = title,
        summary = value.ifEmpty { "Not set" },
        icon = Icons.Outlined.Key,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(textValue)
                    showDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
