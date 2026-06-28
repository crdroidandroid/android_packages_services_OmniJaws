/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.ListPreference
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.scaffold.AxionScaffold

import org.omnirom.omnijaws.R

@Composable
fun WeatherAppWidgetConfigureScreen(
    state: WidgetConfigureUiState,
    onColorThemeChanged: (String) -> Unit,
    onBgTransparencyChanged: (String) -> Unit,
    onIconThemeChanged: (String) -> Unit,
    onOpenWeatherSettings: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AxionScaffold(
        title = stringResource(R.string.omnijaws_weather_widget),
        onBackClick = onCancel
    ) { padding ->
        val colorValues = stringArrayResource(R.array.color_theme_values)
        val colorEntries = stringArrayResource(R.array.color_theme_entries)
        val bgValues = stringArrayResource(R.array.bg_transparency_values)
        val bgEntries = stringArrayResource(R.array.bg_transparency_entries)
        val iconValues = stringArrayResource(R.array.widget_icon_theme_values)
        val iconEntries = stringArrayResource(R.array.widget_icon_theme_entries)

        val colorOptions = remember(colorValues, colorEntries) { colorValues.zip(colorEntries) }
        val bgOptions = remember(bgValues, bgEntries) { bgValues.zip(bgEntries) }
        val iconOptions = remember(iconValues, iconEntries) { iconValues.zip(iconEntries) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PreferenceGroup(title = stringResource(R.string.omnijaws_category_appearance)) {
                item {
                    ListPreference(
                        title = stringResource(R.string.omnijaws_color_theme_title),
                        summary = labelFor(colorOptions, state.colorTheme),
                        options = colorOptions,
                        value = state.colorTheme,
                        onValueChange = onColorThemeChanged
                    )
                }
                item {
                    ListPreference(
                        title = stringResource(R.string.omnijaws_bg_transparency_title),
                        summary = labelFor(bgOptions, state.bgTransparency),
                        options = bgOptions,
                        value = state.bgTransparency,
                        onValueChange = onBgTransparencyChanged
                    )
                }
                if (state.showIconTheme) {
                    item {
                        ListPreference(
                            title = stringResource(R.string.omnijaws_icon_theme_title),
                            summary = labelFor(iconOptions, state.iconTheme),
                            options = iconOptions,
                            value = state.iconTheme,
                            onValueChange = onIconThemeChanged
                        )
                    }
                }
            }

            PreferenceGroup {
                item {
                    ClickablePreference(
                        title = stringResource(R.string.omnijaws_weather_config_title),
                        icon = Icons.Outlined.Tune,
                        onClick = onOpenWeatherSettings
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(onClick = onConfirm) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

private fun labelFor(options: List<Pair<String, String>>, value: String): String? =
    options.firstOrNull { it.first == value }?.second
