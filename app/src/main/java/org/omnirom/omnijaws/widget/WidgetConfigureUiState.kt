/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import org.omnirom.omnijaws.icon.IconProvider

data class WidgetConfigureUiState(
    val colorTheme: String = WidgetConfig.COLOR_THEME_DEFAULT.toString(),
    val bgTransparency: String = WidgetConfig.BG_TRANS_DEFAULT.toString(),
    val iconTheme: String = IconProvider.WIDGET_ICON_THEME_DEFAULT.toString(),
    /** Whether the active icon pack supports light/dark theming (mirrors IconPack.supportsThemes). */
    val showIconTheme: Boolean = false
)
