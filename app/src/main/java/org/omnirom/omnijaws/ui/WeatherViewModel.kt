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
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WeatherUiState(
    val weatherInfo: OmniJawsClient.WeatherInfo? = null,
    val isLoading: Boolean = true,
    val error: Int? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WeatherUiState())
    private companion object {
        const val TAG = "WeatherDashboard"
    }
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val client = OmniJawsClient.get()

    fun queryWeather() {
        val context = getApplication<Application>()
        client.queryWeather(context)
        val info = client.getWeatherInfo()
        if (info != null) {
            Log.d(TAG, "Weather data: city=${info.city} temp=${info.temp} condition=${info.condition}")
            Log.d(TAG, "  humidity=${info.humidity} windSpeed=${info.windSpeed} pinWheel=${info.pinWheel}")
            Log.d(TAG, "  feelsLike=${info.feelsLike} isNaN=${info.feelsLike.isNaN()}")
            Log.d(TAG, "  pressure=${info.pressure} isNaN=${info.pressure.isNaN()}")
            Log.d(TAG, "  uvi=${info.uvi} isNaN=${info.uvi.isNaN()}")
            Log.d(TAG, "  visibility=${info.visibility} isNaN=${info.visibility.isNaN()}")
            Log.d(TAG, "  dewPoint=${info.dewPoint} isNaN=${info.dewPoint.isNaN()}")
            Log.d(TAG, "  sunrise=${info.sunrise} sunset=${info.sunset}")
            Log.d(TAG, "  forecasts=${info.forecasts?.size} hourly=${info.hourlyForecasts?.size}")
        } else {
            Log.d(TAG, "Weather data: null")
        }
        _uiState.value = WeatherUiState(
            weatherInfo = info,
            isLoading = false,
            error = if (info == null) OmniJawsClient.EXTRA_ERROR_DISABLED else null
        )
    }

    fun onWeatherError(errorReason: Int) {
        _uiState.value = _uiState.value.copy(error = errorReason, isLoading = false)
    }

    fun forceRefresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val context = getApplication<Application>()
        val values = ContentValues()
        values.put("update", true)
        context.contentResolver.update(
            Uri.parse("content://org.omnirom.omnijaws.provider/control"),
            values, null, null
        )
    }

    fun getConditionIcon(conditionCode: Int): Drawable? {
        return client.getWeatherConditionImage(getApplication(), conditionCode)
    }
}
