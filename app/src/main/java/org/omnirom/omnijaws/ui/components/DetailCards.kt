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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.internal.util.crdroid.OmniJawsClient
import org.omnirom.omnijaws.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DetailCardsGrid(weather: OmniJawsClient.WeatherInfo) {
    val cards = buildList<@Composable (Modifier) -> Unit> {
        if (!weather.feelsLike.isNaN()) {
            add { m ->
                FeelsLikeCard(weather.feelsLike, weather.temp, weather.tempUnits ?: "", m)
            }
        }
        if (!weather.uvi.isNaN()) {
            add { m -> UvIndexCard(weather.uvi, m) }
        }
        add { m -> HumidityCard(weather.humidity ?: "", m) }
        add { m ->
            WindCard(
                speed = weather.windSpeed ?: "",
                direction = weather.pinWheel ?: "",
                windUnits = weather.windUnits ?: "",
                windDeg = weather.windDirection?.replace("°", "")?.toIntOrNull() ?: 0,
                modifier = m
            )
        }
        if (!weather.pressure.isNaN()) {
            add { m -> PressureCard(weather.pressure, m) }
        }
        if (!weather.visibility.isNaN()) {
            add { m -> VisibilityCard(weather.visibility, m) }
        }
        if (weather.sunrise > 0 && weather.sunset > 0) {
            add { m -> SunriseSunsetCard(weather.sunrise, weather.sunset, m) }
        }
        if (!weather.dewPoint.isNaN()) {
            add { m ->
                DewPointCard(weather.dewPoint, weather.tempUnits ?: "", m)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cards.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { card ->
                    card(Modifier.weight(1f).fillMaxHeight())
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.heightIn(min = 160.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun FeelsLikeCard(
    feelsLike: Float,
    actualTemp: String?,
    tempUnits: String,
    modifier: Modifier
) {
    val actual = remember(actualTemp) { actualTemp?.trim()?.toFloatOrNull() }
    val isFahrenheit = tempUnits.contains("F", ignoreCase = true)
    val minT = if (isFahrenheit) 14f else -10f
    val maxT = if (isFahrenheit) 104f else 40f
    fun frac(t: Float) = ((t - minT) / (maxT - minT)).coerceIn(0f, 1f)

    DetailCard(
        icon = Icons.Outlined.DeviceThermostat,
        title = stringResource(R.string.omnijaws_detail_feels_like),
        modifier = modifier
    ) {
        Text(
            text = "${feelsLike.toInt()}${tempUnits}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actual != null) {
            val delta = (feelsLike - actual).toInt()
            val deltaText = when {
                delta > 0 -> "Δ +$delta°"
                delta < 0 -> "Δ $delta°"
                else -> "Δ 0°"
            }
            Text(
                text = deltaText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FeelsLikeBar(
            feelsFraction = frac(feelsLike),
            actualFraction = actual?.let { frac(it) },
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
    }
}

@Composable
private fun FeelsLikeBar(
    feelsFraction: Float,
    actualFraction: Float?,
    modifier: Modifier
) {
    val gradient = listOf(
        Color(0xFF42A5F5), // cold
        Color(0xFF66BB6A), // mild
        Color(0xFFFFA726), // warm
        Color(0xFFEF5350)  // hot
    )
    val markerColor = MaterialTheme.colorScheme.onSurface
    val f = feelsFraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val y = size.height / 2
        drawLine(
            brush = Brush.horizontalGradient(gradient),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        actualFraction?.let { af ->
            val ax = (size.width * af.coerceIn(0f, 1f)).coerceIn(0f, size.width)
            drawCircle(markerColor, 4.dp.toPx(), Offset(ax, y), style = Stroke(width = 2.dp.toPx()))
        }
        val fx = (size.width * f).coerceIn(0f, size.width)
        drawCircle(markerColor, 5.dp.toPx(), Offset(fx, y))
        drawCircle(Color.White, 3.dp.toPx(), Offset(fx, y))
    }
}

@Composable
private fun UvIndexCard(uvi: Float, modifier: Modifier) {
    val levelRes = remember(uvi) {
        when {
            uvi <= 2 -> R.string.omnijaws_uv_level_low
            uvi <= 5 -> R.string.omnijaws_uv_level_moderate
            uvi <= 7 -> R.string.omnijaws_uv_level_high
            uvi <= 10 -> R.string.omnijaws_uv_level_very_high
            else -> R.string.omnijaws_uv_level_extreme
        }
    }

    DetailCard(
        icon = Icons.Outlined.WbSunny,
        title = stringResource(R.string.omnijaws_detail_uv_index),
        modifier = modifier
    ) {
        Text(
            text = "${uvi.toInt()}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(levelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        UvBar(uvi = uvi, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}

@Composable
private fun UvBar(uvi: Float, modifier: Modifier) {
    val fraction = (uvi / 11f).coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = when {
        uvi <= 2 -> MaterialTheme.colorScheme.tertiary
        uvi <= 5 -> MaterialTheme.colorScheme.secondary
        uvi <= 7 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Canvas(modifier = modifier) {
        drawLine(
            color = trackColor,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width * fraction, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ProgressBar(
    fraction: Float,
    activeBrush: Brush,
    modifier: Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val y = size.height / 2
        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
        if (f > 0f) {
            drawLine(
                brush = activeBrush,
                start = Offset(0f, y),
                end = Offset(size.width * f, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ScaleBar(
    fraction: Float,
    gradient: List<Color>,
    modifier: Modifier
) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    val f = fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val y = size.height / 2
        val barHeight = 6.dp.toPx()
        drawLine(
            brush = Brush.horizontalGradient(gradient),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = barHeight,
            cap = StrokeCap.Round
        )
        val markerX = (size.width * f).coerceIn(0f, size.width)
        drawCircle(markerColor, 5.dp.toPx(), Offset(markerX, y))
        drawCircle(Color.White, 3.dp.toPx(), Offset(markerX, y))
    }
}

@Composable
private fun HumidityCard(humidity: String, modifier: Modifier) {
    val percent = remember(humidity) {
        humidity.filter { it.isDigit() }.toIntOrNull()
    }
    val humidityBrush = Brush.horizontalGradient(
        listOf(Color(0xFF81D4FA), Color(0xFF0288D1))
    )

    DetailCard(
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.omnijaws_detail_humidity),
        modifier = modifier
    ) {
        Text(
            text = humidity,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (percent != null) {
            Spacer(modifier = Modifier.weight(1f))
            ProgressBar(
                fraction = percent / 100f,
                activeBrush = humidityBrush,
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}

@Composable
private fun WindCard(
    speed: String,
    direction: String,
    windUnits: String,
    windDeg: Int,
    modifier: Modifier
) {
    DetailCard(
        icon = Icons.Outlined.Air,
        title = stringResource(R.string.omnijaws_detail_wind),
        modifier = modifier
    ) {
        Text(
            text = "$speed $windUnits",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = direction,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        WindCompass(
            degrees = windDeg,
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun WindCompass(degrees: Int, modifier: Modifier) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val faintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.minDimension / 2 - 4.dp.toPx()

        drawCircle(
            color = outlineColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val tickLength = 6.dp.toPx()
        for (i in 0 until 4) {
            val angle = (i * 90 - 90) * PI / 180
            val startX = cx + (radius - tickLength) * cos(angle).toFloat()
            val startY = cy + (radius - tickLength) * sin(angle).toFloat()
            val endX = cx + radius * cos(angle).toFloat()
            val endY = cy + radius * sin(angle).toFloat()
            drawLine(outlineColor, Offset(startX, startY), Offset(endX, endY), 1.5.dp.toPx())
        }

        drawCircle(faintColor, 1.5.dp.toPx(), Offset(cx, cy - radius))

        val angle = (degrees - 90) * PI / 180
        val tipLen = radius - 8.dp.toPx()
        val tipX = cx + tipLen * cos(angle).toFloat()
        val tipY = cy + tipLen * sin(angle).toFloat()

        val tailLen = radius - 14.dp.toPx()
        val tailX = cx - tailLen * cos(angle).toFloat()
        val tailY = cy - tailLen * sin(angle).toFloat()
        drawLine(outlineColor, Offset(cx, cy), Offset(tailX, tailY), 2.dp.toPx(), cap = StrokeCap.Round)

        drawLine(primaryColor, Offset(cx, cy), Offset(tipX, tipY), 2.5.dp.toPx(), cap = StrokeCap.Round)
        val headLen = 7.dp.toPx()
        val headSpread = (20 * PI / 180)
        val leftX = tipX - headLen * cos(angle - headSpread).toFloat()
        val leftY = tipY - headLen * sin(angle - headSpread).toFloat()
        val rightX = tipX - headLen * cos(angle + headSpread).toFloat()
        val rightY = tipY - headLen * sin(angle + headSpread).toFloat()
        drawLine(primaryColor, Offset(tipX, tipY), Offset(leftX, leftY), 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(primaryColor, Offset(tipX, tipY), Offset(rightX, rightY), 2.5.dp.toPx(), cap = StrokeCap.Round)

        drawCircle(outlineColor, 2.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
private fun PressureCard(pressure: Float, modifier: Modifier) {
    val levelRes = remember(pressure) {
        when {
            pressure < 1009 -> R.string.omnijaws_pressure_level_low
            pressure > 1022 -> R.string.omnijaws_pressure_level_high
            else -> R.string.omnijaws_pressure_level_normal
        }
    }
    val fraction = remember(pressure) { ((pressure - 980f) / 60f).coerceIn(0f, 1f) }
    val gradient = listOf(
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.error
    )

    DetailCard(
        icon = Icons.Outlined.Compress,
        title = stringResource(R.string.omnijaws_detail_pressure),
        modifier = modifier
    ) {
        Text(
            text = "${pressure.toInt()}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${stringResource(R.string.omnijaws_unit_hpa)} · ${stringResource(levelRes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        ScaleBar(
            fraction = fraction,
            gradient = gradient,
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
    }
}

@Composable
private fun VisibilityCard(visibility: Float, modifier: Modifier) {
    val levelRes = remember(visibility) {
        when {
            visibility >= 10 -> R.string.omnijaws_visibility_level_clear
            visibility >= 4 -> R.string.omnijaws_visibility_level_good
            visibility >= 1 -> R.string.omnijaws_visibility_level_moderate
            else -> R.string.omnijaws_visibility_level_poor
        }
    }
    val fraction = remember(visibility) { (visibility / 10f).coerceIn(0f, 1f) }
    val gradient = listOf(
        Color(0xFF607D8B),
        Color(0xFF90A4AE),
        Color(0xFF4FC3F7)
    )

    DetailCard(
        icon = Icons.Outlined.Visibility,
        title = stringResource(R.string.omnijaws_detail_visibility),
        modifier = modifier
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%.1f", visibility),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.omnijaws_visibility_subtitle, stringResource(levelRes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        ScaleBar(
            fraction = fraction,
            gradient = gradient,
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
    }
}

@Composable
private fun SunriseSunsetCard(sunrise: Long, sunset: Long, modifier: Modifier) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val sunriseTime = remember(sunrise) { timeFormat.format(Date(sunrise)) }
    val sunsetTime = remember(sunset) { timeFormat.format(Date(sunset)) }
    val daylightHours = remember(sunrise, sunset, context) {
        val diff = (sunset - sunrise).coerceAtLeast(0L)
        val hours = diff / 3600000
        val minutes = (diff % 3600000) / 60000
        context.getString(R.string.omnijaws_daylight_duration_format, hours, minutes)
    }

    DetailCard(
        icon = Icons.Outlined.WbTwilight,
        title = stringResource(R.string.omnijaws_detail_sunrise_sunset),
        modifier = modifier
    ) {
        Text(
            text = daylightHours,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        SunArc(
            sunrise = sunrise,
            sunset = sunset,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sunriseTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sunsetTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SunArc(sunrise: Long, sunset: Long, modifier: Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val horizonColor = MaterialTheme.colorScheme.outlineVariant
    val sunColor = MaterialTheme.colorScheme.primary
    val nightSunColor = MaterialTheme.colorScheme.onSurfaceVariant

    val now = remember { System.currentTimeMillis() }
    val dayLength = (sunset - sunrise).coerceAtLeast(1L)
    val isDay = now in sunrise..sunset
    val progress = ((now - sunrise).toFloat() / dayLength).coerceIn(0f, 1f)

    val skyGradient = listOf(
        Color(0xFFFFB74D),
        Color(0xFFFFD54F),
        Color(0xFFFF8A65)
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val arcPad = 4.dp.toPx()
        val belowHorizon = 8.dp.toPx()
        val baseY = h - belowHorizon
        val domeH = baseY - arcPad

        fun arcX(t: Float) = arcPad + (w - 2 * arcPad) * t
        fun arcY(t: Float) = baseY - domeH * sin(PI * t).toFloat()

        drawLine(
            color = horizonColor,
            start = Offset(0f, baseY),
            end = Offset(w, baseY),
            strokeWidth = 1.dp.toPx()
        )
        drawCircle(horizonColor, 2.dp.toPx(), Offset(arcX(0f), baseY))
        drawCircle(horizonColor, 2.dp.toPx(), Offset(arcX(1f), baseY))

        val steps = 60
        val track = Path()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (i == 0) track.moveTo(arcX(t), arcY(t)) else track.lineTo(arcX(t), arcY(t))
        }
        drawPath(track, trackColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))

        if (isDay) {
            if (progress > 0f) {
                val activeSteps = (steps * progress).toInt().coerceAtLeast(1)
                val active = Path()
                for (i in 0..activeSteps) {
                    val t = i.toFloat() / steps
                    if (i == 0) active.moveTo(arcX(t), arcY(t)) else active.lineTo(arcX(t), arcY(t))
                }
                drawPath(
                    active,
                    brush = Brush.horizontalGradient(skyGradient),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            val sx = arcX(progress)
            val sy = arcY(progress)
            drawCircle(sunColor.copy(alpha = 0.22f), 9.dp.toPx(), Offset(sx, sy))
            drawCircle(sunColor, 4.5.dp.toPx(), Offset(sx, sy))
        } else {
            val sx = if (now < sunrise) arcX(0f) else arcX(1f)
            val sy = baseY + belowHorizon / 2f
            drawCircle(
                nightSunColor.copy(alpha = 0.5f), 4.dp.toPx(), Offset(sx, sy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
private fun DewPointCard(dewPoint: Float, tempUnits: String, modifier: Modifier) {
    val isFahrenheit = tempUnits.contains("F", ignoreCase = true)
    val levelRes = remember(dewPoint, isFahrenheit) {
        if (isFahrenheit) {
            when {
                dewPoint < 50 -> R.string.omnijaws_dew_point_dry
                dewPoint < 61 -> R.string.omnijaws_dew_point_comfortable
                dewPoint < 70 -> R.string.omnijaws_dew_point_slightly_humid
                else -> R.string.omnijaws_dew_point_humid
            }
        } else {
            when {
                dewPoint < 10 -> R.string.omnijaws_dew_point_dry
                dewPoint < 16 -> R.string.omnijaws_dew_point_comfortable
                dewPoint < 21 -> R.string.omnijaws_dew_point_slightly_humid
                else -> R.string.omnijaws_dew_point_humid
            }
        }
    }
    val fraction = remember(dewPoint, isFahrenheit) {
        if (isFahrenheit) ((dewPoint - 32f) / 47f).coerceIn(0f, 1f)   // 32..79 F
        else ((dewPoint - 0f) / 26f).coerceIn(0f, 1f)                 // 0..26 C
    }
    val gradient = listOf(
        Color(0xFFFFB74D),
        Color(0xFF66BB6A),
        Color(0xFF29B6F6)
    )

    DetailCard(
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.omnijaws_detail_dew_point),
        modifier = modifier
    ) {
        Text(
            text = "${dewPoint.toInt()}${tempUnits}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(levelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        ScaleBar(
            fraction = fraction,
            gradient = gradient,
            modifier = Modifier.fillMaxWidth().height(10.dp)
        )
    }
}
