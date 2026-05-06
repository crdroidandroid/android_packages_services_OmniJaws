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
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.internal.util.crdroid.OmniJawsClient
import com.android.axion.compose.theme.AxionTheme

class WeatherSettingsActivity : ComponentActivity(), OmniJawsClient.OmniJawsObserver {

    private val viewModel: SettingsViewModel by viewModels()

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(LocationPickerActivity.DATA_LOCATION_NAME) ?: return@registerForActivityResult
            val lat = data.getDoubleExtra(LocationPickerActivity.DATA_LOCATION_LAT, 0.0)
            val lon = data.getDoubleExtra(LocationPickerActivity.DATA_LOCATION_LON, 0.0)
            viewModel.setLocationResult(name, lat, lon)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AxionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                val state by viewModel.uiState.collectAsState()
                WeatherSettingsScreen(
                    state = state,
                    onBack = { finish() },
                    onEnableChanged = { viewModel.setEnabled(it) },
                    onProviderChanged = { viewModel.setProvider(it) },
                    onUnitsChanged = { viewModel.setUnits(it) },
                    onIntervalChanged = { viewModel.setUpdateInterval(it) },
                    onCustomLocationChanged = {
                        viewModel.setCustomLocation(it)
                        if (!it) requestLocationPermissionIfNeeded()
                    },
                    onLocationPickerClick = {
                        locationPickerLauncher.launch(
                            Intent(this, LocationPickerActivity::class.java)
                        )
                    },
                    onIconPackChanged = { viewModel.setIconPack(it) },
                    onIconThemeChanged = { viewModel.setIconTheme(it) },
                    onOwmKeyChanged = { viewModel.setOwmKey(it) },
                    onRequestLocationPermission = { requestLocationPermissionIfNeeded() }
                )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OmniJawsClient.get().addObserver(this, this)
        viewModel.loadSettings()
    }

    override fun onPause() {
        super.onPause()
        OmniJawsClient.get().removeObserver(this, this)
    }

    override fun weatherUpdated() {
        viewModel.refreshUpdateStatus()
    }

    override fun weatherError(errorReason: Int) {}

    private fun requestLocationPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
