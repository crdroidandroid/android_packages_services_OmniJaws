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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import org.omnirom.omnijaws.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ITEM_WIDTH = 64.dp
private val ITEM_SPACING = 4.dp
private val ROW_HORIZONTAL_PADDING = 12.dp

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
                text = stringResource(R.string.forecast_card_hourly_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val scrollState = rememberScrollState()
            val contentWidth = ITEM_WIDTH * items.size + ITEM_SPACING * (items.size - 1)

            Column(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(horizontal = ROW_HORIZONTAL_PADDING)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
                    items.forEach { hourly ->
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
                            .width(contentWidth)
                            .height(48.dp)
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
    val density = LocalDensity.current
    val itemWidthPx = with(density) { ITEM_WIDTH.toPx() }
    val cellStepPx = with(density) { (ITEM_WIDTH + ITEM_SPACING).toPx() }

    Canvas(modifier = modifier) {
        if (temps.size < 2) return@Canvas

        val finite = temps.filter { it.isFinite() }
        if (finite.size < 2) return@Canvas

        val minTemp = finite.min()
        val maxTemp = finite.max()
        val range = (maxTemp - minTemp).coerceAtLeast(1f)
        val paddingY = 8f

        val path = Path()
        temps.forEachIndexed { index, temp ->
            if (!temp.isFinite()) return@forEachIndexed
            val x = index * cellStepPx + itemWidthPx / 2f
            val y = paddingY + (1f - (temp - minTemp) / range) * (size.height - 2 * paddingY)
            if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
