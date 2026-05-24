/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context

import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import org.omnirom.omnijaws.icon.IconPack
import org.omnirom.omnijaws.icon.IconProvider

class WidgetConfigureViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WidgetConfigureUiState())
    val uiState: StateFlow<WidgetConfigureUiState> = _uiState.asStateFlow()

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val context: Context get() = getApplication()

    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    /** Must be called once from the activity with the resolved widget id. Safe to call again on
     *  configuration changes - it simply reloads the persisted state, which is idempotent. */
    fun init(appWidgetId: Int) {
        this.appWidgetId = appWidgetId
        load()
    }

    private fun load() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val colorTheme = prefs.getInt(
            WidgetConfig.KEY_COLOR_THEME + "_" + appWidgetId, WidgetConfig.COLOR_THEME_DEFAULT
        )
        val bgTrans = prefs.getInt(
            WidgetConfig.KEY_BG_TRANS + "_" + appWidgetId, WidgetConfig.BG_TRANS_DEFAULT
        )
        val iconTheme = prefs.getInt(
            WidgetConfig.KEY_ICON_THEME + "_" + appWidgetId, IconProvider.WIDGET_ICON_THEME_DEFAULT
        )
        _uiState.value = WidgetConfigureUiState(
            colorTheme = colorTheme.toString(),
            bgTransparency = bgTrans.toString(),
            iconTheme = iconTheme.toString(),
            showIconTheme = IconPack.supportsThemes(context)
        )
    }

    fun setColorTheme(value: String) {
        prefs.edit()
            .putInt(WidgetConfig.KEY_COLOR_THEME + "_" + appWidgetId, value.toInt())
            .apply()
        _uiState.update { it.copy(colorTheme = value) }
        WeatherAppWidgetProvider.updateAfterConfigure(context, appWidgetId)
    }

    fun setBgTransparency(value: String) {
        prefs.edit()
            .putInt(WidgetConfig.KEY_BG_TRANS + "_" + appWidgetId, value.toInt())
            .apply()
        _uiState.update { it.copy(bgTransparency = value) }
    }

    fun setIconTheme(value: String) {
        prefs.edit()
            .putInt(WidgetConfig.KEY_ICON_THEME + "_" + appWidgetId, value.toInt())
            .commit()
        _uiState.update { it.copy(iconTheme = value) }
        WeatherAppWidgetProvider.updateAfterConfigure(context, appWidgetId)
    }
}
