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
package org.omnirom.omnijaws.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import org.omnirom.omnijaws.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyForecastCard(
    forecasts: List<OmniJawsClient.DayForecast>,
    currentTemp: String?,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    var expanded by remember { mutableStateOf(false) }

    val validForecasts = remember(forecasts) { forecasts.filter { it.isValid() } }
    if (validForecasts.isEmpty()) return

    // Rows that are always visible (first 3, or fewer if there isn't enough data)
    val collapsedCount = 3.coerceAtMost(validForecasts.size)

    val allLows = validForecasts.mapNotNull { it.low?.toFloatOrNull() }
    val allHighs = validForecasts.mapNotNull { it.high?.toFloatOrNull() }
    val globalMin = allLows.minOrNull() ?: 0f
    val globalMax = allHighs.maxOrNull() ?: 100f
    val currentTempFloat = currentTemp?.toFloatOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.omnijaws_forecast_card_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Always-visible rows
            validForecasts.take(collapsedCount).forEachIndexed { index, forecast ->
                DailyForecastRow(
                    forecast = forecast,
                    isToday = index == 0,
                    globalMin = globalMin,
                    globalMax = globalMax,
                    currentTemp = if (index == 0) currentTempFloat else null,
                    iconPack = iconPack,
                    iconTheme = iconTheme,
                    getConditionIcon = getConditionIcon
                )
            }

            // Remaining rows, revealed only when expanded
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    validForecasts.drop(collapsedCount).forEach { forecast ->
                        DailyForecastRow(
                            forecast = forecast,
                            isToday = false,
                            globalMin = globalMin,
                            globalMax = globalMax,
                            currentTemp = null,
                            iconPack = iconPack,
                            iconTheme = iconTheme,
                            getConditionIcon = getConditionIcon
                        )
                    }
                }
            }

            if (validForecasts.size > collapsedCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.omnijaws_forecast_show_less)
                        } else {
                            stringResource(R.string.omnijaws_forecast_show_more)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyForecastRow(
    forecast: OmniJawsClient.DayForecast,
    isToday: Boolean,
    globalMin: Float,
    globalMax: Float,
    currentTemp: Float?,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val icon = remember(forecast.conditionCode, iconPack, iconTheme) { getConditionIcon(forecast.conditionCode) }
    val todayLabel = stringResource(R.string.omnijaws_forecast_today)
    val dayName = remember(forecast.date, isToday, todayLabel) {
        formatDayName(forecast.date, isToday, todayLabel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(56.dp)
        )

        if (icon != null) {
            Image(
                painter = DrawablePainter(icon),
                contentDescription = forecast.condition,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "${forecast.low}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )

        TemperatureBar(
            low = forecast.low?.toFloatOrNull() ?: 0f,
            high = forecast.high?.toFloatOrNull() ?: 0f,
            globalMin = globalMin,
            globalMax = globalMax,
            currentTemp = currentTemp,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .padding(horizontal = 8.dp)
        )

        Text(
            text = "${forecast.high}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun TemperatureBar(
    low: Float,
    high: Float,
    globalMin: Float,
    globalMax: Float,
    currentTemp: Float?,
    modifier: Modifier = Modifier
) {
    val range = (globalMax - globalMin).coerceAtLeast(1f)
    val startFraction = ((low - globalMin) / range).coerceIn(0f, 1f)
    val endFraction = ((high - globalMin) / range).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val markerColor = MaterialTheme.colorScheme.onPrimary

    Canvas(modifier = modifier) {
        val radius = size.height / 2

        drawLine(
            color = trackColor,
            start = Offset(radius, size.height / 2),
            end = Offset(size.width - radius, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )

        val barStart = radius + (size.width - 2 * radius) * startFraction
        val barEnd = radius + (size.width - 2 * radius) * endFraction
        if (barEnd > barStart) {
            drawLine(
                color = primaryColor,
                start = Offset(barStart, size.height / 2),
                end = Offset(barEnd, size.height / 2),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }

        if (currentTemp != null) {
            val currentFraction = ((currentTemp - globalMin) / range).coerceIn(0f, 1f)
            val cx = radius + (size.width - 2 * radius) * currentFraction
            drawCircle(
                color = markerColor,
                radius = radius * 0.8f,
                center = Offset(cx, size.height / 2)
            )
        }
    }
}

private fun formatDayName(dateStr: String?, isToday: Boolean, todayLabel: String): String {
    if (isToday) return todayLabel
    if (dateStr == null) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        dateStr
    }
}

private fun OmniJawsClient.DayForecast.isValid(): Boolean {
    val d = date
    if (d.isNullOrBlank() || d.equals("NaN", ignoreCase = true)) return false

    val lowVal = low?.toFloatOrNull()
    val highVal = high?.toFloatOrNull()
    if (lowVal == null || highVal == null) return false
    if (!lowVal.isFinite() || !highVal.isFinite()) return false

    return true
}
