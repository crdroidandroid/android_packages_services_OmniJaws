/*
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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

private const val MIN_PRECIP_CHANCE = 10

internal fun Float.isNotablePrecipChance(): Boolean =
    isFinite() && roundToInt() >= MIN_PRECIP_CHANCE

/** Chance of precipitation, blank below the threshold so rows stay aligned. */
@Composable
internal fun PrecipitationChance(
    probability: Float,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = if (probability.isNotablePrecipChance()) "${probability.roundToInt()}%" else "",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        textAlign = textAlign,
        modifier = modifier
    )
}
