/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

import androidx.preference.PreferenceManager

import com.android.internal.util.crdroid.OmniJawsClient

import org.omnirom.omnijaws.Config
import org.omnirom.omnijaws.R
import org.omnirom.omnijaws.icon.IconPack
import org.omnirom.omnijaws.icon.IconProvider
import org.omnirom.omnijaws.ui.WeatherDashboardActivity
import org.omnirom.omnijaws.ui.WeatherSettingsActivity

/** Current temperature and condition on a square Material 3 Expressive pebble. */
class WeatherPebbleAppWidgetProvider : AppWidgetProvider() {

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            WidgetConfig.clearPrefs(context, id)
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.forEachIndexed { i, oldWidgetId ->
            WidgetConfig.remapPrefs(context, oldWidgetId, newWidgetIds[i])
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_CONFIGURATION_CHANGED == action
            || Intent.ACTION_LOCALE_CHANGED == action
        ) {
            updateAllWidgets(context)
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWeather(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle
    ) {
        updateWeather(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val TAG = "WeatherPebbleAppWidgetProvider"

        // breezy-weather's proportions, as fractions of its 182dp pebble
        private const val TEMP_TEXT_RATIO = 70f / 182f
        private const val TEMP_MARGIN_TOP_RATIO = 18f / 182f
        private const val ICON_RATIO = 60f / 182f
        private const val ICON_MARGIN_RATIO = 37f / 182f
        private const val INFO_TEXT_RATIO = 14f / 182f

        private const val DEFAULT_SIZE_DP = 182f

        @JvmStatic
        fun updateAfterConfigure(context: Context, appWidgetId: Int) {
            updateWeather(context, AppWidgetManager.getInstance(context), appWidgetId)
        }

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, WeatherPebbleAppWidgetProvider::class.java)
            for (appWidgetId in appWidgetManager.getAppWidgetIds(componentName)) {
                updateWeather(context, appWidgetManager, appWidgetId)
            }
        }

        @JvmStatic
        fun updateWeather(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            OmniJawsClient.get().queryWeather(context)
            appWidgetManager.updateAppWidget(
                appWidgetId, createRemoteViews(context, appWidgetManager, appWidgetId)
            )
        }

        @JvmStatic
        fun createRemoteViews(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ): RemoteViews {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val theme = prefs.getInt(
                WidgetConfig.KEY_COLOR_THEME + "_" + appWidgetId, WidgetConfig.COLOR_THEME_DEFAULT
            )
            val bgTrans = prefs.getInt(
                WidgetConfig.KEY_BG_TRANS + "_" + appWidgetId, WidgetConfig.BG_TRANS_DEFAULT
            )
            val widgetIconTheme = prefs.getInt(
                WidgetConfig.KEY_ICON_THEME + "_" + appWidgetId,
                IconProvider.WIDGET_ICON_THEME_DEFAULT
            )

            val iconPack = IconPack.fromConfig(context)
            val iconNightMode = IconProvider.getWidgetIconNightMode(
                widgetIconTheme, Config.getIconTheme(context), theme
            )
            val useResourceIcon = iconPack != null
                    && iconPack.supportsTheming
                    && iconPack.canUseLocalResources(context)

            val build = { sizeDp: Float ->
                val views = newThemedRemoteViews(context, theme)
                scaleToSquare(views, sizeDp)
                setupRemoteView(
                    context, views, bgTrans, iconPack, useResourceIcon, iconNightMode
                )
                views
            }

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val sizes = options.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java
            )
            if (sizes.isNullOrEmpty()) {
                return RemoteViews(
                    build(squareSizeDp(options, portrait = false)),
                    build(squareSizeDp(options, portrait = true))
                )
            }
            val mapping = LinkedHashMap<SizeF, RemoteViews>()
            for (size in sizes) {
                mapping[size] = build(minOf(size.width, size.height))
            }
            return RemoteViews(mapping)
        }

        // MIN_* is portrait width and landscape height, MAX_* the reverse.
        private fun squareSizeDp(options: Bundle, portrait: Boolean): Float {
            val width = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            )
            val height = options.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            )
            val size = minOf(width, height)
            return if (size > 0) size.toFloat() else DEFAULT_SIZE_DP
        }

        private fun scaleToSquare(views: RemoteViews, sizeDp: Float) {
            val dip = TypedValue.COMPLEX_UNIT_DIP
            views.setViewLayoutWidth(R.id.pebble_root, sizeDp, dip)
            views.setViewLayoutHeight(R.id.pebble_root, sizeDp, dip)

            views.setTextViewTextSize(R.id.pebble_temp, dip, sizeDp * TEMP_TEXT_RATIO)
            views.setViewLayoutMargin(
                R.id.pebble_temp, RemoteViews.MARGIN_TOP, sizeDp * TEMP_MARGIN_TOP_RATIO, dip
            )

            views.setViewLayoutWidth(R.id.pebble_icon, sizeDp * ICON_RATIO, dip)
            views.setViewLayoutHeight(R.id.pebble_icon, sizeDp * ICON_RATIO, dip)
            views.setViewLayoutMargin(
                R.id.pebble_icon, RemoteViews.MARGIN_START, sizeDp * ICON_MARGIN_RATIO, dip
            )
            views.setViewLayoutMargin(
                R.id.pebble_icon, RemoteViews.MARGIN_BOTTOM, sizeDp * ICON_MARGIN_RATIO, dip
            )

            views.setTextViewTextSize(R.id.info_text, dip, sizeDp * INFO_TEXT_RATIO)
        }

        // Colours come from the root's theme; setting them here would stop the
        // launcher swapping roots via setLightBackgroundLayoutId.
        private fun newThemedRemoteViews(context: Context, colorTheme: Int): RemoteViews {
            val pkg = context.packageName
            return when (colorTheme) {
                WidgetConfig.COLOR_THEME_DARK ->
                    RemoteViews(pkg, R.layout.weather_appwidget_pebble_darkbg)

                WidgetConfig.COLOR_THEME_LIGHT ->
                    RemoteViews(pkg, R.layout.weather_appwidget_pebble_lightbg)

                else -> RemoteViews(pkg, R.layout.weather_appwidget_pebble_darkbg).apply {
                    setLightBackgroundLayoutId(R.layout.weather_appwidget_pebble_lightbg)
                }
            }
        }

        private fun setupRemoteView(
            context: Context, views: RemoteViews, bgTrans: Int,
            iconPack: IconPack?, useResourceIcon: Boolean, iconNightMode: Int
        ) {
            val alpha = when (bgTrans) {
                WidgetConfig.BG_TRANS_FULL -> 0f
                WidgetConfig.BG_TRANS_SOLID -> 1f
                else -> 0.8f // BG_TRANS_SEMI
            }
            views.setFloat(R.id.pebble_background, "setAlpha", alpha)

            if (!Config.isEnabled(context)) {
                showInfo(context, views, R.string.omnijaws_service_disabled)
                return
            }

            val weatherData = OmniJawsClient.get().weatherInfo
            if (weatherData == null) {
                Log.e(TAG, "updateWeather weatherData == null")
                showInfo(context, views, R.string.omnijaws_service_error_long)
                return
            }

            views.setViewVisibility(R.id.pebble_temp, View.VISIBLE)
            views.setViewVisibility(R.id.pebble_icon, View.VISIBLE)
            views.setViewVisibility(R.id.info_text, View.GONE)
            views.setOnClickPendingIntent(R.id.pebble_root, getWeatherActivityIntent(context))

            views.setTextViewText(R.id.pebble_temp, weatherData.temp + "°")
            WeatherAppWidgetProvider.setImageView(
                context, views, iconPack, R.id.pebble_icon,
                weatherData.conditionCode, useResourceIcon, iconNightMode
            )
        }

        private fun showInfo(context: Context, views: RemoteViews, textResId: Int) {
            views.setViewVisibility(R.id.pebble_temp, View.GONE)
            views.setViewVisibility(R.id.pebble_icon, View.GONE)
            views.setViewVisibility(R.id.info_text, View.VISIBLE)
            views.setTextViewText(R.id.info_text, context.resources.getString(textResId))
            views.setOnClickPendingIntent(R.id.pebble_root, getSettingsIntent(context))
        }

        private fun getSettingsIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context, 0, Intent(context, WeatherSettingsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getWeatherActivityIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context, 0, Intent(context, WeatherDashboardActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
