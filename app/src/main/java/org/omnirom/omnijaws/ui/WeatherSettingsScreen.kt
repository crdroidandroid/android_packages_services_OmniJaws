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

import android.text.style.URLSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.scaffold.AxionScaffold

import org.omnirom.omnijaws.R
import org.omnirom.omnijaws.icon.IconProvider

private const val URL_TAG = "URL"

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
    onPirateWeatherKeyChanged: (String) -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    AxionScaffold(
        title = stringResource(R.string.weather_config_title),
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
                        title = stringResource(R.string.enable_title),
                        checked = state.enabled,
                        onCheckedChange = onEnableChanged,
                        icon = Icons.Outlined.Cloud
                    )
                }
            }

            if (state.enabled) {
                PreferenceGroup(title = stringResource(R.string.category_general)) {
                    item {
                        ListPreference(
                            title = stringResource(R.string.provider_title),
                            summary = providerLabel(state.provider),
                            options = listOf(
                                "0" to stringResource(R.string.provider_openweathermap),
                                "1" to stringResource(R.string.provider_metnorway),
                                "2" to stringResource(R.string.provider_pirate_weather)
                            ),
                            value = state.provider,
                            onValueChange = onProviderChanged
                        )
                    }
                    item {
                        ListPreference(
                            title = stringResource(R.string.units_title),
                            summary = unitsLabel(state.units),
                            options = listOf(
                                "0" to stringResource(R.string.unit_metric),
                                "1" to stringResource(R.string.unit_imperial)
                            ),
                            value = state.units,
                            onValueChange = onUnitsChanged
                        )
                    }
                    item {
                        ListPreference(
                            title = stringResource(R.string.update_interval_title),
                            summary = intervalLabel(state.updateInterval),
                            options = listOf(
                                "1" to stringResource(R.string.interval_1_hour),
                                "2" to stringResource(R.string.interval_2_hour),
                                "4" to stringResource(R.string.interval_4_hour),
                                "6" to stringResource(R.string.interval_6_hour),
                                "12" to stringResource(R.string.interval_12_hour)
                            ),
                            value = state.updateInterval,
                            onValueChange = onIntervalChanged
                        )
                    }
                    item {
                        ClickablePreference(
                            title = stringResource(R.string.last_update_time),
                            summary = state.lastUpdateTime.ifEmpty {
                                stringResource(R.string.weather_last_update_never)
                            },
                            icon = Icons.Outlined.Update,
                            onClick = {}
                        )
                    }
                }

                PreferenceGroup(title = stringResource(R.string.weather_custom_location_title)) {
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.custom_location_title),
                            summary = stringResource(R.string.custom_location_summary),
                            checked = state.customLocation,
                            onCheckedChange = onCustomLocationChanged,
                            icon = Icons.Outlined.MyLocation
                        )
                    }
                    if (state.customLocation) {
                        item {
                            ClickablePreference(
                                title = stringResource(R.string.weather_custom_location_title),
                                summary = state.locationName.ifEmpty {
                                    stringResource(R.string.weather_custom_location_missing)
                                },
                                icon = Icons.Outlined.LocationOn,
                                onClick = onLocationPickerClick
                            )
                        }
                    }
                    if (!state.customLocation && !state.hasLocationPermission) {
                        item {
                            ClickablePreference(
                                title = stringResource(R.string.grant_location_permission_title),
                                summary = stringResource(R.string.grant_location_permission_summary),
                                icon = Icons.Outlined.Security,
                                onClick = onRequestLocationPermission
                            )
                        }
                    }
                }

                if (state.iconPacks.isNotEmpty()) {
                    PreferenceGroup(title = stringResource(R.string.category_appearance)) {
                        item {
                            ListPreference(
                                title = stringResource(R.string.weather_icon_pack_title),
                                summary = state.iconPacks.firstOrNull { it.value == state.iconPack }?.label,
                                options = state.iconPacks.map { it.value to it.label },
                                value = state.iconPack,
                                onValueChange = onIconPackChanged
                            )
                        }
                        if (state.iconPackSupportsTheming) {
                            item {
                                ListPreference(
                                    title = stringResource(R.string.icon_theme_title),
                                    summary = iconThemeLabel(state.iconTheme),
                                    options = listOf(
                                        IconProvider.ICON_THEME_SYSTEM.toString() to
                                            stringResource(R.string.theme_system),
                                        IconProvider.ICON_THEME_LIGHT.toString() to
                                            stringResource(R.string.theme_light),
                                        IconProvider.ICON_THEME_DARK.toString() to
                                            stringResource(R.string.theme_dark)
                                    ),
                                    value = state.iconTheme,
                                    onValueChange = onIconThemeChanged
                                )
                            }
                        }
                    }
                }

                // API section is only relevant for providers that require an API key.
                // MET Norway (provider == "1") does not need one, so the whole section is hidden.
                if (state.provider == "0" || state.provider == "2") {
                    PreferenceGroup(title = stringResource(R.string.category_api)) {
                        if (state.provider == "0") {
                            item {
                                EditTextPreference(
                                    title = stringResource(R.string.owm_key),
                                    value = state.owmKey,
                                    emptyText = stringResource(R.string.service_disabled),
                                    onValueChange = onOwmKeyChanged
                                )
                            }
                        }
                        if (state.provider == "2") {
                            item {
                                EditTextPreference(
                                    title = stringResource(R.string.pirate_weather_key),
                                    value = state.pirateWeatherKey,
                                    emptyText = stringResource(R.string.service_disabled),
                                    onValueChange = onPirateWeatherKeyChanged
                                )
                            }
                        }
                        item {
                            PlainNotePreference(text = stringResource(R.string.api_key_note))
                        }
                        item {
                            HtmlNotePreference(html = stringResource(R.string.api_links_note))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun providerLabel(value: String): String = when (value) {
    "0" -> stringResource(R.string.provider_openweathermap)
    "1" -> stringResource(R.string.provider_metnorway)
    "2" -> stringResource(R.string.provider_pirate_weather)
    else -> value
}

@Composable
private fun unitsLabel(value: String): String = when (value) {
    "0" -> stringResource(R.string.unit_metric)
    "1" -> stringResource(R.string.unit_imperial)
    else -> value
}

@Composable
private fun intervalLabel(value: String): String = when (value) {
    "1" -> stringResource(R.string.interval_1_hour)
    "2" -> stringResource(R.string.interval_2_hour)
    "4" -> stringResource(R.string.interval_4_hour)
    "6" -> stringResource(R.string.interval_6_hour)
    "12" -> stringResource(R.string.interval_12_hour)
    else -> value
}

@Composable
private fun iconThemeLabel(value: String): String = when (value) {
    IconProvider.ICON_THEME_SYSTEM.toString() -> stringResource(R.string.theme_system)
    IconProvider.ICON_THEME_LIGHT.toString() -> stringResource(R.string.theme_light)
    IconProvider.ICON_THEME_DARK.toString() -> stringResource(R.string.theme_dark)
    else -> value
}

@Composable
private fun EditTextPreference(
    title: String,
    value: String,
    emptyText: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var textValue by remember(value) { mutableStateOf(value) }

    ClickablePreference(
        title = title,
        summary = value.ifEmpty { emptyText },
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
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PlainNotePreference(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HtmlNotePreference(html: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val uriHandler = LocalUriHandler.current

    val annotated = remember(html, linkColor) {
        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        buildAnnotatedString {
            append(spanned.toString())
            val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
            for (span in urlSpans) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                if (start in 0..end && end <= length) {
                    addStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start, end
                    )
                    addStringAnnotation(URL_TAG, span.url, start, end)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ClickableText(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceVariant),
            onClick = { offset ->
                annotated.getStringAnnotations(URL_TAG, offset, offset)
                    .firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
            }
        )
    }
}
