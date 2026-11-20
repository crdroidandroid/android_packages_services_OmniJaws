/*
 * Copyright 2025 AxionOS Project
 * Copyright (C) 2026 crDroid Android project
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
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LINE_COLUMN_WIDTH = 84.dp
private val LINE_HORIZONTAL_PADDING = 12.dp
private val LINE_GRAPH_HEIGHT = 112.dp
private val LINE_GRAPH_PADDING = 8.dp
private val LIST_ROW_HEIGHT = 52.dp
private const val VIEW_FADE_MS = 300
private const val PAST_ALPHA = 0.4f

private class DayItem(
    val forecast: OmniJawsClient.DayForecast,
    val label: String,
    val dateText: String,
    val isPast: Boolean,
    val low: Float,
    val high: Float
)

@Composable
fun DailyForecastCard(
    forecasts: List<OmniJawsClient.DayForecast>,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val context = LocalContext.current
    var view by remember { mutableIntStateOf(Config.getForecastView(context)) }

    val todayLabel = stringResource(R.string.omnijaws_forecast_today)
    val yesterdayLabel = stringResource(R.string.omnijaws_forecast_yesterday)
    val tomorrowLabel = stringResource(R.string.omnijaws_forecast_tomorrow)
    val days = remember(forecasts, todayLabel, yesterdayLabel, tomorrowLabel) {
        buildDayItems(forecasts, todayLabel, yesterdayLabel, tomorrowLabel)
    }
    if (days.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.omnijaws_forecast_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ForecastViewToggle(
                    selected = view,
                    onSelect = {
                        view = it
                        Config.setForecastView(context, it)
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val showList = view == Config.FORECAST_VIEW_LIST
            val listAlpha by animateFloatAsState(
                targetValue = if (showList) 1f else 0f,
                animationSpec = tween(VIEW_FADE_MS),
                label = "forecastView"
            )
            Layout(
                content = {
                    ForecastLineView(days, iconPack, iconTheme, getConditionIcon)
                    ForecastListView(days, iconPack, iconTheme, getConditionIcon)
                }
            ) { measurables, constraints ->
                val line = measurables[0].measure(constraints.copy(minHeight = 0))
                val height = line.height.coerceIn(constraints.minHeight, constraints.maxHeight)
                val list = measurables[1].measure(
                    constraints.copy(minHeight = height, maxHeight = height)
                )
                layout(constraints.maxWidth, height) {
                    if (listAlpha < 1f) line.placeWithLayer(0, 0) { alpha = 1f - listAlpha }
                    if (listAlpha > 0f) list.placeWithLayer(0, 0) { alpha = listAlpha }
                }
            }
        }
    }
}

@Composable
private fun ForecastViewToggle(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .padding(4.dp)
            .selectableGroup()
    ) {
        ToggleSegment(
            text = stringResource(R.string.omnijaws_forecast_view_line),
            selected = selected != Config.FORECAST_VIEW_LIST,
            onClick = { onSelect(Config.FORECAST_VIEW_LINE) }
        )
        ToggleSegment(
            text = stringResource(R.string.omnijaws_forecast_view_list),
            selected = selected == Config.FORECAST_VIEW_LIST,
            onClick = { onSelect(Config.FORECAST_VIEW_LIST) }
        )
    }
}

@Composable
private fun ToggleSegment(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else colors.surfaceContainerHighest,
        animationSpec = tween(VIEW_FADE_MS),
        label = "toggleBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        animationSpec = tween(VIEW_FADE_MS),
        label = "toggleText"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = textColor,
        modifier = Modifier
            .background(background, CircleShape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun ForecastLineView(
    days: List<DayItem>,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val contentWidth = LINE_COLUMN_WIDTH * days.size

    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LINE_HORIZONTAL_PADDING, vertical = 16.dp)
    ) {
        Row {
            days.forEach { day ->
                Column(
                    modifier = Modifier
                        .width(LINE_COLUMN_WIDTH)
                        .alpha(if (day.isPast) PAST_ALPHA else 1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = day.dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = day.forecast.condition ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        // two lines reserved in every column keep the icons aligned
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ConditionIcon(day, iconPack, iconTheme, getConditionIcon, 32.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${day.forecast.high}°",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        TemperatureLines(
            days = days,
            modifier = Modifier
                .width(contentWidth)
                .height(LINE_GRAPH_HEIGHT)
        )

        Row {
            days.forEach { day ->
                Text(
                    text = "${day.forecast.low}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(LINE_COLUMN_WIDTH)
                        .alpha(if (day.isPast) PAST_ALPHA else 1f)
                )
            }
        }
    }
}

@Composable
private fun TemperatureLines(days: List<DayItem>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val minT = days.minOf { it.low }
        val maxT = days.maxOf { it.high }
        val range = (maxT - minT).coerceAtLeast(1f)
        val pad = LINE_GRAPH_PADDING.toPx()
        val column = LINE_COLUMN_WIDTH.toPx()
        val strokeWidth = 2.dp.toPx()
        val radius = 4.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        fun x(i: Int) = column * i + column / 2f
        fun y(t: Float) = pad + (maxT - t) / range * (size.height - 2 * pad)

        for (temps in listOf(days.map { it.high }, days.map { it.low })) {
            for (i in 0 until days.size - 1) {
                val start = Offset(x(i), y(temps[i]))
                val end = Offset(x(i + 1), y(temps[i + 1]))
                val midX = (start.x + end.x) / 2f
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                }
                val past = days[i].isPast
                drawPath(
                    path = path,
                    color = if (past) lineColor.copy(alpha = PAST_ALPHA) else lineColor,
                    style = Stroke(width = strokeWidth, pathEffect = if (past) dash else null)
                )
            }
            temps.forEachIndexed { i, t ->
                val center = Offset(x(i), y(t))
                if (days[i].isPast) {
                    drawCircle(
                        color = pointColor.copy(alpha = PAST_ALPHA),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    drawCircle(color = pointColor, radius = radius, center = center)
                }
            }
        }
    }
}

@Composable
private fun ForecastListView(
    days: List<DayItem>,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        days.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LIST_ROW_HEIGHT)
                    .padding(horizontal = 20.dp)
                    .alpha(if (day.isPast) PAST_ALPHA else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = day.dateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1
                )
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ConditionIcon(day, iconPack, iconTheme, getConditionIcon, 28.dp)
                Text(
                    text = buildAnnotatedString {
                        append("${day.forecast.low}°")
                        withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(" / ") }
                        append("${day.forecast.high}°")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    }
}

@Composable
private fun ConditionIcon(
    day: DayItem,
    iconPack: String,
    iconTheme: Int,
    getConditionIcon: (Int) -> Drawable?,
    size: Dp
) {
    val code = day.forecast.conditionCode
    val icon = remember(code, iconPack, iconTheme) {
        if (code < 0) null else getConditionIcon(code)
    }
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = day.forecast.condition,
            modifier = Modifier.size(size)
        )
    } else {
        Spacer(modifier = Modifier.size(size))
    }
}

private fun buildDayItems(
    forecasts: List<OmniJawsClient.DayForecast>,
    todayLabel: String,
    yesterdayLabel: String,
    tomorrowLabel: String
): List<DayItem> {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val dayFormat = DateTimeFormatter.ofPattern("EEE", locale)
    val dateFormat = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "dMMM"), locale
    )
    return forecasts.mapNotNull { forecast ->
        val date = runCatching { LocalDate.parse(forecast.date) }.getOrNull()
            ?: return@mapNotNull null
        val low = forecast.low?.toFloatOrNull()?.takeIf { it.isFinite() }
            ?: return@mapNotNull null
        val high = forecast.high?.toFloatOrNull()?.takeIf { it.isFinite() }
            ?: return@mapNotNull null
        if (forecast.conditionCode < 0 && forecast.condition.isNullOrBlank()) {
            return@mapNotNull null
        }
        val label = when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            today.plusDays(1) -> tomorrowLabel
            else -> date.format(dayFormat)
        }
        DayItem(forecast, label, date.format(dateFormat), date.isBefore(today), low, high)
    }
}
