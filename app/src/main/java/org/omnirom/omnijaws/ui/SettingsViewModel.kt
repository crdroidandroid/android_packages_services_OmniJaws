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
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.WeatherUpdateService
import org.omnirom.omnijaws.widget.WeatherAppWidgetProvider

data class IconPackItem(val label: String, val value: String)

data class SettingsUiState(
    val enabled: Boolean = false,
    val provider: String = "1",
    val units: String = "0",
    val updateInterval: String = "2",
    val customLocation: Boolean = false,
    val locationName: String = "",
    val iconPack: String = "",
    val owmKey: String = "",
    val lastUpdateTime: String = "",
    val iconPacks: List<IconPackItem> = emptyList(),
    val hasLocationPermission: Boolean = false
) {
    val providerLabel: String get() = when (provider) {
        "0" -> "OpenWeatherMap"
        "1" -> "MET Norway"
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
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val ctx get() = getApplication<Application>()

    fun loadSettings() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        _uiState.value = SettingsUiState(
            enabled = Config.isEnabled(ctx),
            provider = prefs.getString(Config.PREF_KEY_PROVIDER, "1") ?: "1",
            units = prefs.getString(Config.PREF_KEY_UNITS, "0") ?: "0",
            updateInterval = prefs.getString(Config.PREF_KEY_UPDATE_INTERVAL, "2") ?: "2",
            customLocation = prefs.getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false),
            locationName = Config.getLocationName(ctx) ?: "",
            iconPack = Config.getIconPack(ctx) ?: DEFAULT_ICON_PACK,
            owmKey = Config.getOwmKey(ctx) ?: "",
            lastUpdateTime = queryLastUpdate(),
            iconPacks = loadIconPacks(),
            hasLocationPermission = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
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
        prefs.edit().putString(Config.PREF_KEY_PROVIDER, value).commit()
        _uiState.value = _uiState.value.copy(provider = value)
        scheduleUpdate()
    }

    fun setUnits(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_UNITS, value).commit()
        _uiState.value = _uiState.value.copy(units = value)
        scheduleUpdate()
    }

    fun setUpdateInterval(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_UPDATE_INTERVAL, value).commit()
        _uiState.value = _uiState.value.copy(updateInterval = value)
        scheduleUpdate()
    }

    fun setCustomLocation(enabled: Boolean) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putBoolean(Config.PREF_KEY_CUSTOM_LOCATION, enabled).commit()
        _uiState.value = _uiState.value.copy(customLocation = enabled)
        scheduleUpdate()
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
        _uiState.value = _uiState.value.copy(iconPack = value)
        scheduleUpdate()
    }

    fun setOwmKey(value: String) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit().putString(Config.PREF_KEY_OWM_KEY, value).commit()
        _uiState.value = _uiState.value.copy(owmKey = value)
        scheduleUpdate()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasLocationPermission = granted)
        if (granted) scheduleUpdate()
    }

    fun refreshUpdateStatus() {
        _uiState.value = _uiState.value.copy(lastUpdateTime = queryLastUpdate())
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
            if (r.activityInfo.packageName == "org.omnirom.omnijaws" && value.contains("google_new_light")) {
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
        const val DEFAULT_ICON_PACK = "org.omnirom.omnijaws.google_new_light"
    }
}
