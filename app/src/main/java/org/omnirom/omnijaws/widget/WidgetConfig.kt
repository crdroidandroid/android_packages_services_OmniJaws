/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import android.content.Context
import androidx.preference.PreferenceManager

import org.omnirom.omnijaws.icon.IconProvider

object WidgetConfig {

    const val KEY_COLOR_THEME = "color_theme"
    const val KEY_ICON_THEME = "widget_icon_theme"
    const val COLOR_THEME_TRANSPARENT = 0
    const val COLOR_THEME_SYSTEM = 1
    const val COLOR_THEME_DARK = 2
    const val COLOR_THEME_LIGHT = 3
    const val COLOR_THEME_DEFAULT = COLOR_THEME_SYSTEM

    const val KEY_BG_TRANS = "bg_transparency"
    const val BG_TRANS_SEMI = 1
    const val BG_TRANS_FULL = 2
    const val BG_TRANS_SOLID = 3
    const val BG_TRANS_DEFAULT = BG_TRANS_SEMI

    @JvmStatic
    fun clearPrefs(context: Context, id: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(KEY_COLOR_THEME + "_" + id)
            .remove(KEY_BG_TRANS + "_" + id)
            .remove(KEY_ICON_THEME + "_" + id)
            .apply()
    }

    @JvmStatic
    fun remapPrefs(context: Context, oldId: Int, newId: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldThemeValue = prefs.getInt(KEY_COLOR_THEME + "_" + oldId, COLOR_THEME_DEFAULT)
        val oldBgValue = prefs.getInt(KEY_BG_TRANS + "_" + oldId, BG_TRANS_DEFAULT)
        val oldIconThemeValue =
            prefs.getInt(KEY_ICON_THEME + "_" + oldId, IconProvider.WIDGET_ICON_THEME_DEFAULT)

        prefs.edit()
            .putInt(KEY_COLOR_THEME + "_" + newId, oldThemeValue)
            .remove(KEY_COLOR_THEME + "_" + oldId)
            .putInt(KEY_BG_TRANS + "_" + newId, oldBgValue)
            .remove(KEY_BG_TRANS + "_" + oldId)
            .putInt(KEY_ICON_THEME + "_" + newId, oldIconThemeValue)
            .remove(KEY_ICON_THEME + "_" + oldId)
            .apply()
    }
}
