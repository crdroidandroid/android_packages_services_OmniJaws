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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HourlyForecastCard(
    hourlyForecasts: List<OmniJawsClient.HourlyForecast>,
    tempUnits: String,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val items = hourlyForecasts.take(24)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                text = "Hourly forecast",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items) { hourly ->
                    HourlyItem(
                        hourly = hourly,
                        tempUnits = tempUnits,
                        iconPack = iconPack,
                        iconTheme = iconTheme,
                        getConditionIcon = getConditionIcon
                    )
                }
            }

            if (items.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                TemperatureGraph(
                    temps = items.map { it.temperature },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 20.dp)
                )
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
    getConditionIcon: (Int) -> Drawable?
) {
    val timeFormat = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    val timeText = remember(hourly.timestamp) {
        if (hourly.timestamp <= 0) "Now" else timeFormat.format(Date(hourly.timestamp))
    }
    val icon = remember(hourly.conditionCode, iconPack, iconTheme) { getConditionIcon(hourly.conditionCode) }
    val tempText = remember(hourly.temperature) {
        "${hourly.temperature.toInt()}°"
    }

    Column(
        modifier = Modifier
            .width(64.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tempText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (icon != null) {
            Image(
                painter = DrawablePainter(icon),
                contentDescription = hourly.condition,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TemperatureGraph(
    temps: List<Float>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (temps.size < 2) return@Canvas

        val minTemp = temps.min()
        val maxTemp = temps.max()
        val range = (maxTemp - minTemp).coerceAtLeast(1f)
        val stepX = size.width / (temps.size - 1)
        val paddingY = 8f

        val path = Path()
        temps.forEachIndexed { index, temp ->
            val x = index * stepX
            val y = paddingY + (1f - (temp - minTemp) / range) * (size.height - 2 * paddingY)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
