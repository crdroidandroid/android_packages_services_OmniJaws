/*
 * Copyright 2025-2026 AxionOS Project
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

import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.launch
import org.omnirom.omnijaws.R
import org.omnirom.omnijaws.ui.components.DailyForecastCard
import org.omnirom.omnijaws.ui.components.DetailCardsGrid
import org.omnirom.omnijaws.ui.components.HourlyForecastCard
import org.omnirom.omnijaws.ui.components.rememberDrawablePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeatherDashboardScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                city = uiState.weatherInfo?.city ?: "",
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettingsClick()
                },
                onLocationClick = {
                    scope.launch { drawerState.close() }
                    onLocationClick()
                }
            )
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TopBar(
                    city = uiState.weatherInfo?.city ?: "",
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onRefreshClick = onRefresh,
                    onSettingsClick = onSettingsClick
                )

                val weatherInfo = uiState.weatherInfo
                when {
                    weatherInfo != null -> {
                        RefreshIndicator(visible = uiState.isLoading)
                        WeatherContent(
                            weather = weatherInfo,
                            iconPack = iconPack,
                            iconTheme = iconTheme,
                            getConditionIcon = getConditionIcon,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        ErrorState(
                            error = uiState.error,
                            locationIssue = uiState.locationIssue,
                            apiKeyMissing = uiState.apiKeyMissing,
                            onRefresh = onRefresh,
                            onSettingsClick = onSettingsClick,
                            onOpenLocationSettings = onOpenLocationSettings,
                            onRequestLocationPermission = onRequestLocationPermission,
                            onPickLocation = onLocationClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshIndicator(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
    ) {
        if (visible) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorState(
    error: Int?,
    locationIssue: LocationIssue,
    apiKeyMissing: Boolean,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPickLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocationError = error == OmniJawsClient.EXTRA_ERROR_LOCATION
    val locationFix = if (isLocationError) locationIssue else LocationIssue.NONE
    val showApiKeyError = apiKeyMissing && !isLocationError &&
            error != OmniJawsClient.EXTRA_ERROR_DISABLED

    val titleRes = if (showApiKeyError) R.string.omnijaws_error_api_key_missing else when (locationFix) {
        LocationIssue.SERVICES_DISABLED -> R.string.omnijaws_error_location_services_off
        LocationIssue.PERMISSION_MISSING -> R.string.omnijaws_error_location_permission
        LocationIssue.CUSTOM_LOCATION_MISSING -> R.string.omnijaws_error_custom_location_missing
        LocationIssue.NONE -> when (error) {
            OmniJawsClient.EXTRA_ERROR_NETWORK -> R.string.omnijaws_error_network
            OmniJawsClient.EXTRA_ERROR_LOCATION -> R.string.omnijaws_error_location
            OmniJawsClient.EXTRA_ERROR_DISABLED -> R.string.omnijaws_error_disabled
            else -> R.string.omnijaws_service_unkown
        }
    }

    val subtitleRes = if (showApiKeyError) R.string.omnijaws_error_api_key_missing_summary else when (locationFix) {
        LocationIssue.SERVICES_DISABLED -> R.string.omnijaws_error_location_services_off_summary
        LocationIssue.PERMISSION_MISSING -> R.string.omnijaws_error_location_permission_summary
        LocationIssue.CUSTOM_LOCATION_MISSING -> R.string.omnijaws_error_custom_location_missing_summary
        LocationIssue.NONE -> R.string.omnijaws_dashboard_error_subtitle
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            when (locationFix) {
                LocationIssue.SERVICES_DISABLED -> {
                    PrimaryActionButton(
                        icon = Icons.Outlined.LocationOn,
                        labelRes = R.string.omnijaws_action_open_location_settings,
                        onClick = onOpenLocationSettings
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                LocationIssue.PERMISSION_MISSING -> {
                    PrimaryActionButton(
                        icon = Icons.Outlined.MyLocation,
                        labelRes = R.string.omnijaws_action_grant_location_permission,
                        onClick = onRequestLocationPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                LocationIssue.CUSTOM_LOCATION_MISSING -> {
                    PrimaryActionButton(
                        icon = Icons.Outlined.LocationOn,
                        labelRes = R.string.omnijaws_action_choose_location,
                        onClick = onPickLocation
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                LocationIssue.NONE -> Unit
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onRefresh) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.omnijaws_update_title))
                }
                FilledTonalButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.omnijaws_settings_title))
                }
            }

            if (isLocationError && locationFix != LocationIssue.CUSTOM_LOCATION_MISSING) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onPickLocation) {
                    Text(stringResource(R.string.omnijaws_action_pick_location))
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    icon: ImageVector,
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
}

@Composable
private fun DrawerContent(
    city: String,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    ModalDrawerSheet(
        windowInsets = WindowInsets.statusBars
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.omnijaws_activity_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )
        if (city.isNotEmpty()) {
            Text(
                text = city,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
            label = { Text(stringResource(R.string.omnijaws_weather_custom_location_title)) },
            selected = false,
            onClick = onLocationClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.omnijaws_settings_title)) },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

@Composable
private fun WeatherContent(
    weather: OmniJawsClient.WeatherInfo,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?,
    modifier: Modifier = Modifier
) {
    val providerName = providerDisplayName(weather.provider)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            WeatherSummaryRow(
                weather = weather,
                iconPack = iconPack,
                iconTheme = iconTheme,
                getConditionIcon = getConditionIcon
            )
        }

        if (!weather.hourlyForecasts.isNullOrEmpty()) {
            item {
                HourlyForecastCard(
                    hourlyForecasts = weather.hourlyForecasts,
                    tempUnits = weather.tempUnits ?: "",
                    iconPack = iconPack,
                    iconTheme = iconTheme,
                    getConditionIcon = getConditionIcon
                )
            }
        }

        if (!weather.forecasts.isNullOrEmpty()) {
            item {
                DailyForecastCard(
                    forecasts = weather.forecasts,
                    currentTemp = weather.temp,
                    iconPack = iconPack,
                    iconTheme = iconTheme,
                    getConditionIcon = getConditionIcon
                )
            }
        }

        item {
            DetailCardsGrid(weather = weather)
        }

        if (providerName.isNotBlank()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.omnijaws_dashboard_provider_attribution,
                        providerName
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun providerDisplayName(providerId: String?): String = when (providerId) {
    "0" -> stringResource(R.string.omnijaws_provider_openweathermap)
    "1" -> stringResource(R.string.omnijaws_provider_metnorway)
    "2" -> stringResource(R.string.omnijaws_provider_pirate_weather)
    "3" -> stringResource(R.string.omnijaws_provider_openmeteo)
    "4" -> stringResource(R.string.omnijaws_provider_visualcrossing)
    else -> providerId ?: ""
}

@Composable
private fun TopBar(
    city: String,
    onMenuClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.omnijaws_menu_title)
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (city.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = city,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onRefreshClick) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.omnijaws_update_title)
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.omnijaws_settings_title)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeatherSummaryRow(
    weather: OmniJawsClient.WeatherInfo,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val icon = remember(weather.conditionCode, iconPack, iconTheme) {
        if (weather.conditionCode < 0) null else getConditionIcon(weather.conditionCode)
    }

    val today = remember(weather.forecasts) { weather.forecasts?.firstValidToday() }

    val enterScale = remember { Animatable(0.5f) }
    val enterAlpha = remember { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(weather.conditionCode) {
        enterScale.snapTo(0.5f)
        enterAlpha.snapTo(0f)
        launch { enterScale.animateTo(1f, spatialSpec) }
        launch { enterAlpha.animateTo(1f, tween(400)) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${weather.temp}${weather.tempUnits ?: ""}",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = weather.condition ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (today != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = stringResource(R.string.omnijaws_temperature_low),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${today.low}\u00b0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Outlined.ArrowUpward,
                            contentDescription = stringResource(R.string.omnijaws_temperature_high),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${today.high}\u00b0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (icon != null) {
            Image(
                painter = rememberDrawablePainter(icon),
                contentDescription = weather.condition,
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = enterScale.value
                        scaleY = enterScale.value
                        alpha = enterAlpha.value
                    }
            )
        }
    }
}

private fun List<OmniJawsClient.DayForecast>.firstValidToday(): OmniJawsClient.DayForecast? {
    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val candidate = firstOrNull { it.date == todayKey } ?: firstOrNull()
    if (candidate == null) return null
    if (candidate.date.isNullOrBlank() || candidate.date.equals("NaN", ignoreCase = true)) return null
    val low = candidate.low?.toFloatOrNull()
    val high = candidate.high?.toFloatOrNull()
    if (low == null || high == null || !low.isFinite() || !high.isFinite()) return null
    return candidate
}
