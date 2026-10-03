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
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.android.internal.util.crdroid.OmniJawsClient
import com.android.axion.compose.theme.AxionTheme

class WeatherDashboardActivity : ComponentActivity(), OmniJawsClient.OmniJawsObserver {

    private companion object {
        const val TAG = "WeatherDashboard"
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private val viewModel: WeatherViewModel by viewModels()

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val name = data.getStringExtra(LocationPickerActivity.DATA_LOCATION_NAME)
            ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(LocationPickerActivity.DATA_LOCATION_LAT, 0.0)
        val lon = data.getDoubleExtra(LocationPickerActivity.DATA_LOCATION_LON, 0.0)
        viewModel.setLocationResult(name, lat, lon)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.isEmpty()) return@registerForActivityResult
        val granted = results.values.any { it }
        if (!granted && LOCATION_PERMISSIONS.none { shouldShowRequestPermissionRationale(it) }) {
            openAppDetailsSettings()
        }
        viewModel.syncWithLocationState()
    }

    private val locationModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.syncWithLocationState()
        }
    }

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
                    onOpenLocationSettings = { openLocationSettings() },
                    onRequestLocationPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
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
        registerReceiver(
            locationModeReceiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )
        viewModel.syncWithLocationState()
    }

    override fun onPause() {
        super.onPause()
        OmniJawsClient.get().removeObserver(this, this)
        unregisterReceiver(locationModeReceiver)
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
        locationPickerLauncher.launch(Intent(this, LocationPickerActivity::class.java))
    }

    private fun openLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Location settings not available, falling back to Settings", e)
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun openAppDetailsSettings() {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
            )
        }.onFailure { Log.w(TAG, "App details settings not available", it) }
    }
}
