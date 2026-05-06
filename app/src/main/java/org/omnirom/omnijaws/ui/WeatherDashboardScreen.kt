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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import kotlinx.coroutines.launch
import org.omnirom.omnijaws.ui.components.DailyForecastCard
import org.omnirom.omnijaws.ui.components.DetailCardsGrid
import org.omnirom.omnijaws.ui.components.DrawablePainter
import org.omnirom.omnijaws.ui.components.HourlyForecastCard

@Composable
fun WeatherDashboardScreen(
    uiState: WeatherUiState,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit,
    onLocationClick: () -> Unit,
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

                when {
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
                    uiState.weatherInfo == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Weather data unavailable",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Check settings or try refreshing",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilledTonalButton(onClick = onRefresh) {
                                        Icon(
                                            Icons.Outlined.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Refresh")
                                    }
                                    FilledTonalButton(onClick = onSettingsClick) {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Settings")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        WeatherContent(
                            weather = uiState.weatherInfo,
                            onRefresh = onRefresh,
                            iconPack = iconPack,
                            iconTheme = iconTheme,
                            getConditionIcon = getConditionIcon,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
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
            text = "Weather",
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
            label = { Text("Location") },
            selected = false,
            onClick = onLocationClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

@Composable
private fun WeatherContent(
    weather: OmniJawsClient.WeatherInfo,
    onRefresh: () -> Unit,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
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

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Weather data provided by ${weather.provider ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
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
                contentDescription = "Menu"
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onRefreshClick) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh"
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings"
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
        getConditionIcon(weather.conditionCode)
    }

    val enterScale = remember { Animatable(0.5f) }
    val enterAlpha = remember { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(weather.conditionCode) {
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = weather.condition ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (!weather.forecasts.isNullOrEmpty()) {
                    val today = weather.forecasts[0]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDownward,
                            contentDescription = "Low",
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
                            contentDescription = "High",
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
                painter = DrawablePainter(icon),
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
