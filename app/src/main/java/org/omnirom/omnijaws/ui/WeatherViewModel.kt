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
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.WeatherUpdateService
import org.omnirom.omnijaws.icon.IconProvider

enum class LocationIssue {
    NONE,
    SERVICES_DISABLED,
    PERMISSION_MISSING,
    CUSTOM_LOCATION_MISSING
}

data class WeatherUiState(
    val weatherInfo: OmniJawsClient.WeatherInfo? = null,
    val isLoading: Boolean = true,
    val error: Int? = null,
    val iconPack: String = "",
    val iconTheme: Int = IconProvider.ICON_THEME_DEFAULT,
    val locationIssue: LocationIssue = LocationIssue.NONE
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "WeatherDashboard"
        const val DEBUG = false
        const val REFRESH_TIMEOUT_MS = 20_000L
        const val CONTROL_URI = "content://org.omnirom.omnijaws.provider/control"
    }

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val client = OmniJawsClient.get()
    private var refreshTimeoutJob: Job? = null

    private var otherSourceUpdateRequested = false

    private val isRefreshPending: Boolean
        get() = refreshTimeoutJob?.isActive == true

    fun queryWeather(endRefresh: Boolean = true) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                client.queryWeather(context)
                client.getWeatherInfo()
            }

            val (enabled, locationIssue, otherSource) = withContext(Dispatchers.IO) {
                Triple(
                    Config.isEnabled(context),
                    resolveLocationIssue(),
                    WeatherUpdateService.isCachedDataForOtherSource(context)
                )
            }
            val iconPack = Config.getIconPack(context) ?: ""
            val iconTheme = Config.getIconTheme(context)

            val wrongSource = info != null && otherSource
            val usableInfo = if (wrongSource) null else info
            if (!wrongSource) otherSourceUpdateRequested = false

            if (DEBUG) {
                Log.d(TAG, "info=$info wrongSource=$wrongSource issue=$locationIssue")
            }

            if (endRefresh) refreshTimeoutJob?.cancel()

            val requestUpdate = wrongSource && enabled &&
                    !otherSourceUpdateRequested && !isRefreshPending
            val showLoading = requestUpdate && locationIssue == LocationIssue.NONE

            _uiState.update { current ->
                current.copy(
                    weatherInfo = usableInfo,
                    isLoading = isRefreshPending || showLoading,
                    error = when {
                        usableInfo != null -> null
                        !enabled -> OmniJawsClient.EXTRA_ERROR_DISABLED
                        locationIssue != LocationIssue.NONE -> OmniJawsClient.EXTRA_ERROR_LOCATION
                        current.error != null -> current.error
                        else -> OmniJawsClient.EXTRA_ERROR_NETWORK
                    },
                    iconPack = iconPack,
                    iconTheme = iconTheme,
                    locationIssue = locationIssue
                )
            }

            if (requestUpdate) {
                otherSourceUpdateRequested = true
                if (showLoading) forceRefresh() else requestServiceUpdate()
            }
        }
    }

    fun onWeatherError(errorReason: Int) {
        refreshTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                error = errorReason,
                isLoading = false,
                locationIssue = resolveLocationIssue()
            )
        }
    }

    fun syncWithLocationState() {
        val state = _uiState.value
        val issue = resolveLocationIssue()
        val recovered = issue == LocationIssue.NONE &&
                state.locationIssue != LocationIssue.NONE &&
                state.weatherInfo == null &&
                state.error == OmniJawsClient.EXTRA_ERROR_LOCATION &&
                !isRefreshPending

        if (recovered) {
            _uiState.update { it.copy(locationIssue = issue) }
            forceRefresh()
        } else {
            queryWeather(endRefresh = false)
        }
    }

    fun forceRefresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        requestServiceUpdate()
        startRefreshTimeout()
    }

    fun setLocationResult(name: String, lat: Double, lon: Double) {
        val context = getApplication<Application>()
        WeatherUpdateService.ensureDataSourceRecorded(context)

        val locationId = String.format(Locale.US, "lat=%f&lon=%f", lat, lon)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(Config.PREF_KEY_CUSTOM_LOCATION, true)
            .apply()
        Config.setLocationId(context, locationId)
        Config.setLocationName(context, name)

        otherSourceUpdateRequested = true
        _uiState.update {
            it.copy(
                weatherInfo = null,
                isLoading = true,
                error = null,
                locationIssue = LocationIssue.NONE
            )
        }
        WeatherUpdateService.scheduleUpdateNow(context)

        startRefreshTimeout()
    }

    fun getConditionIcon(conditionCode: Int): Drawable? {
        if (conditionCode < 0) return null
        return IconProvider.getConditionDrawable(getApplication(), conditionCode)
    }

    private fun requestServiceUpdate() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val values = ContentValues()
            values.put("update", true)
            runCatching {
                context.contentResolver.update(Uri.parse(CONTROL_URI), values, null, null)
            }.onFailure { Log.e(TAG, "update request failed", it) }
        }
    }

    private fun startRefreshTimeout() {
        refreshTimeoutJob?.cancel()
        refreshTimeoutJob = viewModelScope.launch {
            delay(REFRESH_TIMEOUT_MS)
            if (_uiState.value.isLoading) {
                queryWeather()
            }
        }
    }

    private fun resolveLocationIssue(): LocationIssue {
        val context = getApplication<Application>()
        val customLocation = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false)
        if (customLocation) {
            return if (Config.getLocationId(context).isNullOrEmpty()) {
                LocationIssue.CUSTOM_LOCATION_MISSING
            } else {
                LocationIssue.NONE
            }
        }

        val locationManager = context.getSystemService(LocationManager::class.java)
        if (locationManager != null && !locationManager.isLocationEnabled) {
            return LocationIssue.SERVICES_DISABLED
        }

        val hasPermission =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return LocationIssue.PERMISSION_MISSING

        return LocationIssue.NONE
    }
}
