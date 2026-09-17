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

import android.app.Application
import android.content.ContentValues
import android.graphics.drawable.Drawable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.WeatherUpdateService
import org.omnirom.omnijaws.icon.IconProvider

data class WeatherUiState(
    val weatherInfo: OmniJawsClient.WeatherInfo? = null,
    val isLoading: Boolean = true,
    val error: Int? = null,
    val iconPack: String = "",
    val iconTheme: Int = IconProvider.ICON_THEME_DEFAULT
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WeatherUiState())

    private companion object {
        const val TAG = "WeatherDashboard"
        const val DEBUG = false
        const val REFRESH_TIMEOUT_MS = 20_000L
        const val CONTROL_URI = "content://org.omnirom.omnijaws.provider/control"
    }

    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val client = OmniJawsClient.get()
    private var refreshTimeoutJob: Job? = null

    fun queryWeather() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                client.queryWeather(context)
                client.getWeatherInfo()
            }

            if (DEBUG) {
                if (info != null) {
                    Log.d(TAG, "city=${info.city} temp=${info.temp} condition=${info.condition}" +
                            " forecasts=${info.forecasts?.size} hourly=${info.hourlyForecasts?.size}")
                } else {
                    Log.d(TAG, "Weather data: null")
                }
            }

            val enabled = withContext(Dispatchers.IO) { Config.isEnabled(context) }
            val resolvedError = when {
                info != null -> null
                !enabled -> OmniJawsClient.EXTRA_ERROR_DISABLED
                _uiState.value.error != null -> _uiState.value.error
                else -> OmniJawsClient.EXTRA_ERROR_NETWORK
            }

            refreshTimeoutJob?.cancel()

            _uiState.value = WeatherUiState(
                weatherInfo = info,
                isLoading = false,
                error = resolvedError,
                iconPack = Config.getIconPack(context) ?: "",
                iconTheme = Config.getIconTheme(context)
            )
        }
    }

    fun onWeatherError(errorReason: Int) {
        refreshTimeoutJob?.cancel()
        _uiState.value = _uiState.value.copy(error = errorReason, isLoading = false)
    }

    fun forceRefresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val context = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val values = ContentValues()
            values.put("update", true)
            runCatching {
                context.contentResolver.update(Uri.parse(CONTROL_URI), values, null, null)
            }.onFailure { Log.e(TAG, "forceRefresh failed", it) }
        }

        refreshTimeoutJob?.cancel()
        refreshTimeoutJob = viewModelScope.launch {
            delay(REFRESH_TIMEOUT_MS)
            if (_uiState.value.isLoading) {
                queryWeather()
            }
        }
    }

    fun setLocationResult(name: String, lat: Double, lon: Double) {
        val context = getApplication<Application>()
        val locationId = String.format(java.util.Locale.US, "lat=%f&lon=%f", lat, lon)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(Config.PREF_KEY_CUSTOM_LOCATION, true)
            .apply()
        Config.setLocationId(context, locationId)
        Config.setLocationName(context, name)

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        WeatherUpdateService.scheduleUpdateNow(context)

        refreshTimeoutJob?.cancel()
        refreshTimeoutJob = viewModelScope.launch {
            delay(REFRESH_TIMEOUT_MS)
            if (_uiState.value.isLoading) {
                queryWeather()
            }
        }
    }

    fun getConditionIcon(conditionCode: Int): Drawable? {
        if (conditionCode < 0) return null
        return IconProvider.getConditionDrawable(getApplication(), conditionCode)
    }
}
