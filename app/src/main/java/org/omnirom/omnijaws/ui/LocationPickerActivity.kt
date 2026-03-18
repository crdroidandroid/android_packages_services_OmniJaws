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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.axion.compose.scaffold.AxionScaffold
import com.android.axion.compose.theme.AxionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.omnirom.omnijaws.NetworkUtils
import java.util.Locale

class LocationPickerActivity : ComponentActivity() {

    companion object {
        const val DATA_LOCATION_NAME = "location_name"
        const val DATA_LOCATION_LAT = "location_lat"
        const val DATA_LOCATION_LON = "location_lon"
        internal const val URL_PLACES =
            "https://secure.geonames.org/searchJSON?name_startsWith=%s&lang=%s&username=omnijaws&maxRows=20"
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
                LocationPickerScreen(
                    onBack = { finish() },
                    onLocationSelected = { name, lat, lon ->
                        val intent = Intent().apply {
                            putExtra(DATA_LOCATION_NAME, name)
                            putExtra(DATA_LOCATION_LAT, lat)
                            putExtra(DATA_LOCATION_LON, lon)
                        }
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    }
                )
                }
            }
        }
    }
}

private data class LocationItem(
    val city: String,
    val cityExt: String,
    val lat: Double,
    val lon: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPickerScreen(
    onBack: () -> Unit,
    onLocationSelected: (name: String, lat: Double, lon: Double) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<LocationItem>() }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    AxionScaffold(
        title = "Search location",
        onBackClick = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = { newQuery ->
                            query = newQuery
                            searchJob?.cancel()
                            if (newQuery.isEmpty()) {
                                results.clear()
                                isSearching = false
                            } else {
                                isSearching = true
                                searchJob = scope.launch {
                                    delay(500)
                                    val found = searchLocations(newQuery)
                                    results.clear()
                                    results.addAll(found)
                                    isSearching = false
                                }
                            }
                        },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("City name") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {}

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(results) { location ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLocationSelected(location.city, location.lat, location.lon)
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = location.city,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = location.cityExt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private suspend fun searchLocations(input: String): List<LocationItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<LocationItem>()
    try {
        val lang = Locale.getDefault().language.replaceFirst("_", "-")
        val url = String.format(
            LocationPickerActivity.URL_PLACES,
            Uri.encode(input.trim()),
            lang
        )
        val response = NetworkUtils.downloadUrlMemoryAsString(url) ?: return@withContext results
        val jsonResults = JSONObject(response).getJSONArray("geonames")
        val seen = mutableSetOf<String>()

        for (i in 0 until jsonResults.length()) {
            val result = jsonResults.getJSONObject(i)
            val population = if (result.has("population")) result.getInt("population") else 0
            if (population == 0) continue

            val city = result.getString("name")
            val country = result.getString("countryName")
            val countryId = result.getString("countryId")
            val adminName = if (result.has("adminName1")) result.getString("adminName1") else ""
            val cityExt = (if (TextUtils.isEmpty(adminName)) "" else "$adminName, ") + country
            val lat = result.getDouble("lat")
            val lon = result.getDouble("lng")
            val id = "$city,$countryId"

            if (id !in seen) {
                seen.add(id)
                results.add(LocationItem(city, cityExt, lat, lon))
                if (results.size >= 5) break
            }
        }
    } catch (e: Exception) {
        Log.e("LocationPicker", "Search failed for: $input", e)
    }
    results
}
