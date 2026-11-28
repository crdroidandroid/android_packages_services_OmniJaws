/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package org.omnirom.omnijaws.ui.components

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.omnirom.omnijaws.R

private const val DISABLED_ALPHA = 0.38f

@Composable
fun OmniJawsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (isSystemInDarkTheme()) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(colorScheme = colorScheme, motionScheme = MotionScheme.expressive()) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = content
        )
    }
}

@Composable
fun OmniJawsIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(painterResource(id), contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun dialogWidth(): Dp {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp.dp * when (configuration.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> 0.65f
        else -> 0.85f
    }
}

@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val container = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = container,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp),
                        shape = IconButtonDefaults.smallRoundShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        OmniJawsIcon(
                            R.drawable.ic_arrow_back,
                            contentDescription = stringResource(R.string.omnijaws_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = container,
                    scrolledContainerColor = container
                ),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content
    )
}

private enum class PreferencePosition { Single, Top, Middle, Bottom }

private val LocalPreferencePosition = compositionLocalOf { PreferencePosition.Single }

// roomy corners on the outside of a group, tight ones between its rows
private fun Shapes.segmented(first: Boolean, last: Boolean): CornerBasedShape {
    val top = if (first) largeIncreased.topStart else extraSmall.topStart
    val bottom = if (last) largeIncreased.bottomStart else extraSmall.bottomStart
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
private fun preferenceShape(position: PreferencePosition): Shape = MaterialTheme.shapes.segmented(
    first = position == PreferencePosition.Single || position == PreferencePosition.Top,
    last = position == PreferencePosition.Single || position == PreferencePosition.Bottom
)

class PreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

@Composable
fun PreferenceGroup(title: String? = null, content: PreferenceGroupScope.() -> Unit) {
    val items = PreferenceGroupScope().apply(content).items
    Column {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                val position = when {
                    items.size == 1 -> PreferencePosition.Single
                    index == 0 -> PreferencePosition.Top
                    index == items.lastIndex -> PreferencePosition.Bottom
                    else -> PreferencePosition.Middle
                }
                CompositionLocalProvider(LocalPreferencePosition provides position, content = item)
            }
        }
    }
}

@Composable
fun BasePreference(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    iconDrawable: Drawable? = null,
    widget: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(preferenceShape(LocalPreferencePosition.current))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .alpha(contentAlpha),
            contentAlignment = Alignment.Center
        ) {
            if (iconDrawable != null) {
                Image(
                    painter = rememberDrawablePainter(iconDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                OmniJawsIcon(
                    id = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(contentAlpha)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (widget != null) {
            Spacer(Modifier.width(16.dp))
            widget()
        }
    }
}

@Composable
fun ClickablePreference(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    iconDrawable: Drawable? = null
) {
    BasePreference(
        title = title,
        icon = icon,
        summary = summary,
        enabled = enabled,
        iconDrawable = iconDrawable,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun PreferenceSwitch(
    checked: Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource
) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = {
            Crossfade(checked, animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()) {
                OmniJawsIcon(
                    id = if (it) R.drawable.ic_check else R.drawable.ic_close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}

@Composable
fun SwitchPreference(
    title: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasePreference(
        title = title,
        icon = icon,
        summary = summary,
        enabled = enabled,
        modifier = Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ),
        widget = { PreferenceSwitch(checked, enabled, interactionSource) }
    )
}

@Composable
fun MasterSwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val container = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        checked -> colors.primaryContainer
        else -> colors.surfaceContainerHighest
    }
    val content = if (checked) colors.onPrimaryContainer else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(CircleShape)
            .background(container)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = content
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        PreferenceSwitch(checked, enabled, interactionSource)
    }
}

@Composable
fun ListPreference(
    title: String,
    @DrawableRes icon: Int,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    summary: String? = null
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    ClickablePreference(
        title = title,
        icon = icon,
        summary = summary ?: options.firstOrNull { it.first == value }?.second,
        onClick = { showDialog = true }
    )
    if (!showDialog) return
    AlertDialog(
        onDismissRequest = { showDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = { OmniJawsIcon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(
                                selected = key == value,
                                role = Role.RadioButton,
                                onClick = {
                                    showDialog = false
                                    if (key != value) onValueChange(key)
                                }
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = key == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
