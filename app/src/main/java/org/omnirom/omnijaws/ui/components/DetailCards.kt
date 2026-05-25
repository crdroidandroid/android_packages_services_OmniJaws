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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailCardsGrid(weather: OmniJawsClient.WeatherInfo) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2
    ) {
        val cardModifier = Modifier.weight(1f)

        if (!weather.feelsLike.isNaN()) {
            FeelsLikeCard(
                feelsLike = weather.feelsLike,
                tempUnits = weather.tempUnits ?: "",
                modifier = cardModifier
            )
        }

        if (!weather.uvi.isNaN()) {
            UvIndexCard(
                uvi = weather.uvi,
                modifier = cardModifier
            )
        }

        HumidityCard(
            humidity = weather.humidity ?: "",
            modifier = cardModifier
        )

        WindCard(
            speed = weather.windSpeed ?: "",
            direction = weather.pinWheel ?: "",
            windUnits = weather.windUnits ?: "",
            windDeg = weather.windDirection?.replace("°", "")?.toIntOrNull() ?: 0,
            modifier = cardModifier
        )

        if (!weather.pressure.isNaN()) {
            PressureCard(
                pressure = weather.pressure,
                modifier = cardModifier
            )
        }

        if (!weather.visibility.isNaN()) {
            VisibilityCard(
                visibility = weather.visibility,
                modifier = cardModifier
            )
        }

        if (weather.sunrise > 0 && weather.sunset > 0) {
            SunriseSunsetCard(
                sunrise = weather.sunrise,
                sunset = weather.sunset,
                modifier = cardModifier
            )
        }

        if (!weather.dewPoint.isNaN()) {
            DewPointCard(
                dewPoint = weather.dewPoint,
                tempUnits = weather.tempUnits ?: "",
                modifier = cardModifier
            )
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
private fun FeelsLikeCard(feelsLike: Float, tempUnits: String, modifier: Modifier) {
    DetailCard(
        icon = Icons.Outlined.DeviceThermostat,
        title = stringResource(R.string.detail_feels_like),
        modifier = modifier
    ) {
        Text(
            text = "${feelsLike.toInt()}${tempUnits}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UvIndexCard(uvi: Float, modifier: Modifier) {
    val levelRes = remember(uvi) {
        when {
            uvi <= 2 -> R.string.uv_level_low
            uvi <= 5 -> R.string.uv_level_moderate
            uvi <= 7 -> R.string.uv_level_high
            uvi <= 10 -> R.string.uv_level_very_high
            else -> R.string.uv_level_extreme
        }
    }

    DetailCard(
        icon = Icons.Outlined.WbSunny,
        title = stringResource(R.string.detail_uv_index),
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
private fun HumidityCard(humidity: String, modifier: Modifier) {
    DetailCard(
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.detail_humidity),
        modifier = modifier
    ) {
        Text(
            text = humidity,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        title = stringResource(R.string.detail_wind),
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

        val arrowAngle = (degrees - 90) * PI / 180
        val arrowTipX = cx + (radius - 8.dp.toPx()) * cos(arrowAngle).toFloat()
        val arrowTipY = cy + (radius - 8.dp.toPx()) * sin(arrowAngle).toFloat()
        drawCircle(primaryColor, 4.dp.toPx(), Offset(arrowTipX, arrowTipY))

        drawCircle(outlineColor, 2.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
private fun PressureCard(pressure: Float, modifier: Modifier) {
    DetailCard(
        icon = Icons.Outlined.Compress,
        title = stringResource(R.string.detail_pressure),
        modifier = modifier
    ) {
        Text(
            text = "${pressure.toInt()}",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.unit_hpa),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VisibilityCard(visibility: Float, modifier: Modifier) {
    val levelRes = remember(visibility) {
        when {
            visibility >= 10 -> R.string.visibility_level_clear
            visibility >= 4 -> R.string.visibility_level_good
            visibility >= 1 -> R.string.visibility_level_moderate
            else -> R.string.visibility_level_poor
        }
    }

    DetailCard(
        icon = Icons.Outlined.Visibility,
        title = stringResource(R.string.detail_visibility),
        modifier = modifier
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%.1f", visibility),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.visibility_subtitle, stringResource(levelRes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        val diff = sunset - sunrise
        val hours = diff / 3600000
        val minutes = (diff % 3600000) / 60000
        context.getString(R.string.daylight_duration_format, hours, minutes)
    }

    DetailCard(
        icon = Icons.Outlined.WbTwilight,
        title = stringResource(R.string.detail_sunrise_sunset),
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
    val arcColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val dotColor = MaterialTheme.colorScheme.primary

    val now = remember { System.currentTimeMillis() }
    val progress = remember(now, sunrise, sunset) {
        if (now < sunrise) 0f
        else if (now > sunset) 1f
        else ((now - sunrise).toFloat() / (sunset - sunrise).toFloat()).coerceIn(0f, 1f)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val arcPad = 4.dp.toPx()

        val path = Path()
        val steps = 50
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = PI * (1 - t)
            val x = arcPad + (w - 2 * arcPad) * t
            val y = h - (h - arcPad) * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, trackColor, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))

        if (progress > 0f) {
            val activePath = Path()
            val activeSteps = (steps * progress).toInt()
            for (i in 0..activeSteps) {
                val t = i.toFloat() / steps
                val angle = PI * (1 - t)
                val x = arcPad + (w - 2 * arcPad) * t
                val y = h - (h - arcPad) * sin(angle).toFloat()
                if (i == 0) activePath.moveTo(x, y) else activePath.lineTo(x, y)
            }
            drawPath(activePath, arcColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        val dotAngle = PI * (1 - progress)
        val dotX = arcPad + (w - 2 * arcPad) * progress
        val dotY = h - (h - arcPad) * sin(dotAngle).toFloat()
        drawCircle(dotColor, 4.dp.toPx(), Offset(dotX, dotY))
    }
}

@Composable
private fun DewPointCard(dewPoint: Float, tempUnits: String, modifier: Modifier) {
    val levelRes = remember(dewPoint) {
        when {
            dewPoint < 10 -> R.string.dew_point_dry
            dewPoint < 16 -> R.string.dew_point_comfortable
            dewPoint < 21 -> R.string.dew_point_slightly_humid
            else -> R.string.dew_point_humid
        }
    }

    DetailCard(
        icon = Icons.Outlined.WaterDrop,
        title = stringResource(R.string.detail_dew_point),
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
    }
}
