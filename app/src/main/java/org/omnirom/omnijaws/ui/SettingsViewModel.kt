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

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.WeatherUpdateService
import org.omnirom.omnijaws.widget.WeatherAppWidgetProvider
import org.omnirom.omnijaws.icon.IconProvider
import org.omnirom.omnijaws.icon.IconPack

data class IconPackItem(val label: String, val value: String)

data class SettingsUiState(
    val enabled: Boolean = false,
    val provider: String = "1",
    val units: String = "0",
    val updateInterval: String = "2",
    val customLocation: Boolean = false,
    val locationName: String = "",
    val iconPack: String = "",
    val iconTheme: String = IconProvider.ICON_THEME_DEFAULT.toString(),
    val iconPackSupportsTheming: Boolean = false,
    val owmKey: String = "",
    val pirateWeatherKey: String = "",
    val lastUpdateTime: String = "",
    val iconPacks: List<IconPackItem> = emptyList(),
    val hasLocationPermission: Boolean = false
) {
    val providerLabel: String get() = when (provider) {
        "0" -> "OpenWeatherMap"
        "1" -> "MET Norway"
        "2" -> "Pirate Weather"
        "3" -> "Open-Meteo"
        else -> provider
    }
    val unitsLabel: String get() = when (units) {
        "0" -> "Metric (\u00b0C)"
        "1" -> "Imperial (\u00b0F)"
        else -> units
    }
    val intervalLabel: String get() = when (updateInterval) {
        "1" -> "1 hour"
        "2" -> "2 hours"
        "4" -> "4 hours"
        "6" -> "6 hours"
        "12" -> "12 hours"
        else -> "$updateInterval hours"
    }

    val iconThemeLabel: String get() = when (iconTheme) {
        IconProvider.ICON_THEME_SYSTEM.toString() -> "Follow system"
        IconProvider.ICON_THEME_LIGHT.toString() -> "Light"
        IconProvider.ICON_THEME_DARK.toString() -> "Dark"
        else -> iconTheme
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val ctx get() = getApplication<Application>()

    fun loadSettings() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
                SettingsUiState(
                    enabled = Config.isEnabled(ctx),
                    provider = prefs.getString(Config.PREF_KEY_PROVIDER, "1") ?: "1",
                    units = prefs.getString(Config.PREF_KEY_UNITS, "0") ?: "0",
                    updateInterval = prefs.getString(Config.PREF_KEY_UPDATE_INTERVAL, "2") ?: "2",
                    customLocation = prefs.getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false),
                    locationName = Config.getLocationName(ctx) ?: "",
                    iconPack = Config.getIconPack(ctx) ?: DEFAULT_ICON_PACK,
                    iconTheme = Config.getIconTheme(ctx).toString(),
                    iconPackSupportsTheming = IconPack.supportsThemes(ctx),
                    owmKey = Config.getOwmKey(ctx) ?: "",
                    pirateWeatherKey = Config.getPirateWeatherKey(ctx) ?: "",
                    lastUpdateTime = queryLastUpdate(),
                    iconPacks = loadIconPacks(),
                    hasLocationPermission = hasLocationPermission()
                )
            }
            _uiState.value = state
        }
    }

    fun setEnabled(enabled: Boolean) {
        Config.setEnabled(ctx, enabled)
        _uiState.value = _uiState.value.copy(enabled = enabled)
        if (enabled) {
            WeatherUpdateService.scheduleUpdatePeriodic(ctx)
        } else {
            WeatherUpdateService.cancelAllUpdate(ctx)
            WeatherAppWidgetProvider.disableAllWidgets(ctx)
            WeatherUpdateService.disabledCall(ctx)
        }
    }

    fun setProvider(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_PROVIDER, value).apply()
        _uiState.value = _uiState.value.copy(provider = value)
        scheduleUpdate()
    }

    fun setUnits(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_UNITS, value).apply()
        _uiState.value = _uiState.value.copy(units = value)
        scheduleUpdate()
    }

    fun setUpdateInterval(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_UPDATE_INTERVAL, value).apply()
        _uiState.value = _uiState.value.copy(updateInterval = value)
        scheduleUpdate()
    }

    fun setCustomLocation(enabled: Boolean) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putBoolean(Config.PREF_KEY_CUSTOM_LOCATION, enabled).apply()
        _uiState.value = _uiState.value.copy(customLocation = enabled)
        if (enabled) {
            if (Config.getLocationId(ctx) != null) scheduleUpdate()
        } else {
            if (hasLocationPermission()) scheduleUpdate()
        }
    }

    fun setLocationResult(name: String, lat: Double, lon: Double) {
        val locationId = String.format(java.util.Locale.US, "lat=%f&lon=%f", lat, lon)
        Config.setLocationId(ctx, locationId)
        Config.setLocationName(ctx, name)
        _uiState.value = _uiState.value.copy(locationName = name)
        scheduleUpdate()
    }

    fun setIconPack(value: String) {
        Config.setIconPack(ctx, value)
        _uiState.value = _uiState.value.copy(
            iconPack = value,
            iconPackSupportsTheming = IconPack.supportsThemes(ctx)
            )
        scheduleUpdate()
    }

    fun setIconTheme(value: String) {
        Config.setIconTheme(ctx, value.toInt())
        _uiState.value = _uiState.value.copy(iconTheme = value)
        WeatherAppWidgetProvider.updateAllWidgets(ctx)
    }

    fun setOwmKey(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_OWM_KEY, value).apply()
        _uiState.value = _uiState.value.copy(owmKey = value)
        scheduleUpdate()
    }

    fun setPirateWeatherKey(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_PIRATE_WEATHER_KEY, value).apply()
        _uiState.value = _uiState.value.copy(pirateWeatherKey = value)
        scheduleUpdate()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasLocationPermission = granted)
        if (granted && !_uiState.value.customLocation) scheduleUpdate()
    }

    private fun hasLocationPermission(): Boolean {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshUpdateStatus() {
        viewModelScope.launch {
            val last = withContext(Dispatchers.IO) { queryLastUpdate() }
            _uiState.value = _uiState.value.copy(lastUpdateTime = last)
        }
    }

    private fun scheduleUpdate() {
        WeatherUpdateService.scheduleUpdateNow(ctx)
    }

    private fun queryLastUpdate(): String {
        OmniJawsClient.get().queryWeather(ctx)
        return OmniJawsClient.get().getWeatherInfo()?.getLastUpdateTime() ?: ""
    }

    private fun loadIconPacks(): List<IconPackItem> {
        val pm = ctx.packageManager
        val result = mutableListOf<IconPackItem>()
        val defaultList = mutableListOf<IconPackItem>()

        val intent = Intent().setAction("org.omnirom.WeatherIconPack")
        for (r in pm.queryIntentActivities(intent, 0)) {
            val label = r.activityInfo.loadLabel(pm)?.toString() ?: r.activityInfo.packageName
            val value = r.activityInfo.name
            if (value == DEFAULT_ICON_PACK) {
                defaultList.add(IconPackItem(label, value))
            } else {
                result.add(IconPackItem(label, value))
            }
        }

        val chronusIntent = Intent(Intent.ACTION_MAIN).addCategory("com.dvtonder.chronus.ICON_PACK")
        for (r in pm.queryIntentActivities(chronusIntent, 0)) {
            val label = r.activityInfo.loadLabel(pm)?.toString() ?: r.activityInfo.packageName
            result.add(IconPackItem(label, r.activityInfo.packageName + ".weather"))
        }

        return defaultList + result
    }

    companion object {
        val DEFAULT_ICON_PACK = Config.DEFAULT_ICON_PACK
    }
}
