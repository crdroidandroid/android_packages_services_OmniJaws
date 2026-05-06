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

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.internal.util.crdroid.OmniJawsClient
import com.android.axion.compose.theme.AxionTheme

class WeatherDashboardActivity : ComponentActivity(), OmniJawsClient.OmniJawsObserver {

    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxionTheme {
                val uiState by viewModel.uiState.collectAsState()
                WeatherDashboardScreen(
                    uiState = uiState,
                    onRefresh = { viewModel.forceRefresh() },
                    onSettingsClick = { openSettings() },
                    onLocationClick = { openLocationPicker() },
                    iconPack = uiState.iconPack,
                    iconTheme = uiState.iconTheme,
                    getConditionIcon = { viewModel.getConditionIcon(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OmniJawsClient.get().addObserver(this, this)
        viewModel.queryWeather()
    }

    override fun onPause() {
        super.onPause()
        OmniJawsClient.get().removeObserver(this, this)
    }

    override fun weatherUpdated() {
        viewModel.queryWeather()
    }

    override fun weatherError(errorReason: Int) {
        viewModel.onWeatherError(errorReason)
    }

    private fun openSettings() {
        startActivity(Intent(this, WeatherSettingsActivity::class.java))
    }

    private fun openLocationPicker() {
        startActivity(Intent(this, LocationPickerActivity::class.java))
    }
}
