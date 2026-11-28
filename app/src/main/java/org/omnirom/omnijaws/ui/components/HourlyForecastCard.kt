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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import org.omnirom.omnijaws.client.OmniJawsClient
import org.omnirom.omnijaws.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val ITEM_WIDTH = 64.dp
private val ITEM_SPACING = 4.dp
private val ROW_HORIZONTAL_PADDING = 12.dp
private const val MAX_HOURS = 24
private const val ONE_HOUR_MS = 60L * 60L * 1000L
private val BAR_AREA_HEIGHT = 48.dp
private val BAR_WIDTH = 12.dp

private enum class HourlyTab { TEMPERATURE, PRECIPITATION, WIND, HUMIDITY }

// Upper bound in km/h of Beaufort forces 0..11; anything above is 12.
private val BEAUFORT_KMH = floatArrayOf(1f, 6f, 12f, 20f, 29f, 39f, 50f, 62f, 75f, 89f, 103f, 118f)
private val BEAUFORT_COLORS = listOf(
    0xFF90CAF9, 0xFF81D4FA, 0xFF80DEEA, 0xFF80CBC4, 0xFFA5D6A7, 0xFFC5E1A5,
    0xFFE6EE9C, 0xFFFFF59D, 0xFFFFE082, 0xFFFFCC80, 0xFFFFAB91, 0xFFEF9A9A, 0xFFE57373
).map { Color(it) }

private fun beaufortColor(speed: Float, windUnits: String): Color {
    val kmh = if (windUnits == "mph") speed * 1.609344f else speed
    val force = BEAUFORT_KMH.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
    return BEAUFORT_COLORS[force]
}

@Composable
fun HourlyForecastCard(
    hourlyForecasts: List<OmniJawsClient.HourlyForecast>,
    tempUnits: String,
    windUnits: String,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val items = remember(hourlyForecasts) { hourlyForecasts.take(MAX_HOURS) }
    if (items.isEmpty()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val hourFormat = remember(configuration, DateFormat.is24HourFormat(context)) {
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "j")
        SimpleDateFormat(pattern, locale)
    }
    val nowLabel = stringResource(R.string.omnijaws_hourly_now)
    val currentHourStart = remember(items) {
        System.currentTimeMillis() / ONE_HOUR_MS * ONE_HOUR_MS
    }
    val showPrecip = remember(items) { items.any { it.precipProbability.isNotablePrecipChance() } }
    val tabs = remember(items) {
        HourlyTab.values().filter { tab ->
            when (tab) {
                HourlyTab.TEMPERATURE -> true
                HourlyTab.PRECIPITATION -> items.any { it.precipProbability.isFinite() }
                HourlyTab.WIND -> items.any { it.windSpeed.isFinite() && it.windSpeed >= 0f }
                HourlyTab.HUMIDITY -> items.any { it.humidity.isFinite() && it.humidity >= 0f }
            }
        }
    }
    var selectedName by rememberSaveable { mutableStateOf(HourlyTab.TEMPERATURE.name) }
    val selected = tabs.firstOrNull { it.name == selectedName } ?: HourlyTab.TEMPERATURE
    val maxWind = remember(items) {
        items.maxOfOrNull { if (it.windSpeed.isFinite()) it.windSpeed else 0f }?.coerceAtLeast(1f) ?: 1f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.omnijaws_forecast_card_hourly_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (tabs.size > 1) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(4.dp)
                        .selectableGroup()
                ) {
                    tabs.forEach { tab ->
                        ToggleSegment(
                            text = tabLabel(tab),
                            selected = tab == selected,
                            onClick = { selectedName = tab.name }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ROW_HORIZONTAL_PADDING)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)
            ) {
                items.forEach { hourly ->
                    HourlyItem(
                        hourly = hourly,
                        tempUnits = tempUnits,
                        iconPack = iconPack,
                        iconTheme = iconTheme,
                        getConditionIcon = getConditionIcon,
                        hourFormat = hourFormat,
                        nowLabel = nowLabel,
                        currentHourStart = currentHourStart,
                        showPrecip = showPrecip,
                        tab = selected,
                        windUnits = windUnits,
                        maxWind = maxWind
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyItem(
    hourly: OmniJawsClient.HourlyForecast,
    tempUnits: String,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?,
    hourFormat: SimpleDateFormat,
    nowLabel: String,
    currentHourStart: Long,
    showPrecip: Boolean,
    tab: HourlyTab,
    windUnits: String,
    maxWind: Float
) {
    val timeText = remember(hourly.timestamp, hourFormat, currentHourStart) {
        when {
            hourly.timestamp <= 0L -> nowLabel
            hourly.timestamp < currentHourStart + ONE_HOUR_MS &&
                    hourly.timestamp >= currentHourStart -> nowLabel
            else -> hourFormat.format(Date(hourly.timestamp))
        }
    }
    val icon = remember(hourly.conditionCode, iconPack, iconTheme) {
        if (hourly.conditionCode < 0) null else getConditionIcon(hourly.conditionCode)
    }
    val tempText = remember(hourly.temperature) {
        if (hourly.temperature.isFinite()) "${hourly.temperature.roundToInt()}\u00b0" else "\u2013"
    }

    Column(
        modifier = Modifier
            .width(ITEM_WIDTH)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (icon != null) {
            Image(
                painter = rememberDrawablePainter(icon),
                contentDescription = listOfNotNull(
                    hourly.condition?.takeIf { it.isNotBlank() },
                    if (hourly.temperature.isFinite()) {
                        "${hourly.temperature.roundToInt()}$tempUnits"
                    } else null
                ).joinToString(", ").ifEmpty { null },
                modifier = Modifier.size(28.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(28.dp))
        }
        when (tab) {
            HourlyTab.TEMPERATURE -> {
                if (showPrecip) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PrecipitationChance(hourly.precipProbability)
                }
                Spacer(modifier = Modifier.height(8.dp))
                ValueText(tempText)
            }
            HourlyTab.PRECIPITATION -> {
                val chance = hourly.precipProbability
                MetricBar(chance / 100f, MaterialTheme.colorScheme.primary)
                ValueText(if (chance.isFinite()) "${chance.roundToInt()}%" else "\u2013")
            }
            HourlyTab.WIND -> {
                val speed = hourly.windSpeed
                val valid = speed.isFinite() && speed >= 0f
                MetricBar(
                    if (valid) speed / maxWind else Float.NaN,
                    if (valid) beaufortColor(speed, windUnits) else Color.Transparent
                )
                ValueText(if (valid) "${speed.roundToInt()}" else "\u2013")
                Text(
                    text = windUnits,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            HourlyTab.HUMIDITY -> {
                val humidity = hourly.humidity
                val valid = humidity.isFinite() && humidity >= 0f
                MetricBar(if (valid) humidity / 100f else Float.NaN, MaterialTheme.colorScheme.tertiary)
                ValueText(if (valid) "${humidity.roundToInt()}%" else "\u2013")
            }
        }
    }
}

@Composable
private fun tabLabel(tab: HourlyTab): String = when (tab) {
    HourlyTab.TEMPERATURE -> stringResource(R.string.omnijaws_hourly_tab_temperature)
    HourlyTab.PRECIPITATION -> stringResource(R.string.omnijaws_hourly_tab_precipitation)
    HourlyTab.WIND -> stringResource(R.string.omnijaws_detail_wind)
    HourlyTab.HUMIDITY -> stringResource(R.string.omnijaws_detail_humidity)
}

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

@Composable
private fun MetricBar(fraction: Float, color: Color) {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier.height(BAR_AREA_HEIGHT).width(BAR_WIDTH),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
        )
        if (fraction.isFinite() && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_AREA_HEIGHT * fraction.coerceIn(0.08f, 1f))
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
