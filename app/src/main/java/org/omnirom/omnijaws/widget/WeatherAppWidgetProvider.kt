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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.ArrayMap
import android.util.Log
import android.util.SizeF
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

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class WeatherAppWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (LOGGING) {
            Log.i(TAG, "onEnabled")
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (LOGGING) {
            Log.i(TAG, "onDisabled")
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            if (LOGGING) {
                Log.i(TAG, "onDeleted: $id")
            }
            WidgetConfig.clearPrefs(context, id)
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        var i = 0
        for (oldWidgetId in oldWidgetIds) {
            if (LOGGING) {
                Log.i(TAG, "onRestored $oldWidgetId ${newWidgetIds[i]}")
            }
            WidgetConfig.remapPrefs(context, oldWidgetId, newWidgetIds[i])
            i++
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (Intent.ACTION_CONFIGURATION_CHANGED == action
            || Intent.ACTION_LOCALE_CHANGED == action
        ) {
            updateAllWidgets(context)
        }

        if (LOGGING) {
            Log.i(TAG, "onReceive: $action")
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        if (LOGGING) {
            Log.i(TAG, "onUpdate")
        }
        updateAllWeather(context)
    }

    @Suppress("DEPRECATION")
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager,
        appWidgetId: Int, newOptions: Bundle
    ) {
        if (LOGGING) {
            Log.i(TAG, "onAppWidgetOptionsChanged")
            val sizes = newOptions.getParcelableArrayList<SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES
            )
            Log.d(TAG, "size = $sizes")
        }

        updateWeather(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private const val TAG = "WeatherAppWidgetProvider"
        private const val LOGGING = false
        private const val EXTRA_ERROR_DISABLED = 2

        @JvmStatic
        fun updateAfterConfigure(context: Context, appWidgetId: Int) {
            if (LOGGING) {
                Log.i(TAG, "updateAfterConfigure")
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            updateWeather(context, appWidgetManager, appWidgetId)
        }

        @JvmStatic
        fun updateAllWeather(context: Context) {
            if (LOGGING) {
                Log.i(TAG, "updateAllWeather at = " + Date())
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (appWidgetManager != null) {
                val componentName = ComponentName(context, WeatherAppWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                for (appWidgetId in appWidgetIds) {
                    updateWeather(context, appWidgetManager, appWidgetId)
                }
            }
        }

        private fun getSettingsIntent(context: Context): PendingIntent {
            val configureIntent = Intent(context, WeatherSettingsActivity::class.java)
            return PendingIntent.getActivity(
                context, 0, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getWeatherActivityIntent(context: Context): PendingIntent {
            val configureIntent = Intent(context, WeatherDashboardActivity::class.java)
            return PendingIntent.getActivity(
                context, 0, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        @JvmStatic
        fun updateWeather(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ) {
            if (LOGGING) {
                Log.i(TAG, "updateWeather $appWidgetId")
            }

            OmniJawsClient.get().queryWeather(context)

            appWidgetManager.updateAppWidget(
                appWidgetId, createRemoteViews(context, appWidgetManager, appWidgetId)
            )
        }

        private fun setupRemoteView(
            context: Context, appWidgetManager: AppWidgetManager,
            appWidgetId: Int, widget: RemoteViews,
            bgTrans: Int, iconPack: IconPack?, useResourceIcon: Boolean, iconNightMode: Int
        ) {
            if (!Config.isEnabled(context)) {
                showError(context, appWidgetManager, appWidgetId, EXTRA_ERROR_DISABLED, widget)
                return
            }

            val weatherData = OmniJawsClient.get().weatherInfo

            initWidget(widget)
            widget.setOnClickPendingIntent(R.id.weather_data, getWeatherActivityIntent(context))

            if (weatherData == null) {
                Log.e(TAG, "updateWeather weatherData == null")
                widget.setViewVisibility(R.id.forecast_line, View.GONE)
                widget.setViewVisibility(R.id.current_weather_line, View.GONE)
                widget.setViewVisibility(R.id.current_condition_line, View.GONE)
                widget.setViewVisibility(R.id.info_container, View.VISIBLE)
                widget.setTextViewText(
                    R.id.info_text,
                    context.resources.getString(R.string.omnijaws_service_error_long)
                )
                return
            }

            val sdf = SimpleDateFormat("EE")
            val cal = Calendar.getInstance()
            var dayShort = sdf.format(Date(cal.timeInMillis))
            var forecastData = getWeatherDataString(
                weatherData.forecasts[0].low, weatherData.forecasts[0].high, weatherData.tempUnits
            )

            setImageView(
                context, widget, iconPack, R.id.forecast_image_0,
                weatherData.forecasts[0].conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.forecast_text_0, dayShort)
            widget.setTextViewText(R.id.forecast_data_0, forecastData)

            cal.add(Calendar.DATE, 1)
            dayShort = sdf.format(Date(cal.timeInMillis))
            forecastData = getWeatherDataString(
                weatherData.forecasts[1].low, weatherData.forecasts[1].high, weatherData.tempUnits
            )

            setImageView(
                context, widget, iconPack, R.id.forecast_image_1,
                weatherData.forecasts[1].conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.forecast_text_1, dayShort)
            widget.setTextViewText(R.id.forecast_data_1, forecastData)

            cal.add(Calendar.DATE, 1)
            dayShort = sdf.format(Date(cal.timeInMillis))
            forecastData = getWeatherDataString(
                weatherData.forecasts[2].low, weatherData.forecasts[2].high, weatherData.tempUnits
            )

            setImageView(
                context, widget, iconPack, R.id.forecast_image_2,
                weatherData.forecasts[2].conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.forecast_text_2, dayShort)
            widget.setTextViewText(R.id.forecast_data_2, forecastData)

            cal.add(Calendar.DATE, 1)
            dayShort = sdf.format(Date(cal.timeInMillis))
            forecastData = getWeatherDataString(
                weatherData.forecasts[3].low, weatherData.forecasts[3].high, weatherData.tempUnits
            )

            setImageView(
                context, widget, iconPack, R.id.forecast_image_3,
                weatherData.forecasts[3].conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.forecast_text_3, dayShort)
            widget.setTextViewText(R.id.forecast_data_3, forecastData)

            cal.add(Calendar.DATE, 1)
            dayShort = sdf.format(Date(cal.timeInMillis))
            forecastData = getWeatherDataString(
                weatherData.forecasts[4].low, weatherData.forecasts[4].high, weatherData.tempUnits
            )

            setImageView(
                context, widget, iconPack, R.id.forecast_image_4,
                weatherData.forecasts[4].conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.forecast_text_4, dayShort)
            widget.setTextViewText(R.id.forecast_data_4, forecastData)

            val currentData = getWeatherDataString(weatherData.temp, null, weatherData.tempUnits)
            setImageView(
                context, widget, iconPack, R.id.current_image,
                weatherData.conditionCode, useResourceIcon, iconNightMode
            )
            widget.setTextViewText(R.id.current_data, currentData)
            widget.setTextViewText(R.id.current_weather_city, weatherData.city)
            widget.setImageViewResource(
                R.id.current_humidity_image, R.drawable.ic_humidity_symbol_small
            )
            widget.setTextViewText(R.id.current_humidity, weatherData.humidity)
            widget.setImageViewResource(R.id.current_wind_image, R.drawable.ic_wind_symbol_small)
            widget.setTextViewText(
                R.id.current_wind, weatherData.windSpeed + " " + weatherData.windUnits
            )
            widget.setImageViewResource(
                R.id.current_wind_direction_image, R.drawable.ic_wind_direction_symbol_small
            )
            widget.setTextViewText(R.id.current_wind_direction, weatherData.pinWheel)

            val alpha = when (bgTrans) {
                WidgetConfig.BG_TRANS_FULL -> 0f
                WidgetConfig.BG_TRANS_SOLID -> 1f
                else -> 0.8f // BG_TRANS_SEMI
            }
            widget.setFloat(R.id.background, "setAlpha", alpha)

            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val showConditionLine = minWidth > 300
            widget.setViewVisibility(
                R.id.current_condition_line, if (showConditionLine) View.VISIBLE else View.GONE
            )
        }

        @JvmStatic
        fun createRemoteViews(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int
        ): RemoteViews {
            if (LOGGING) {
                Log.i(TAG, "createRemoteViews")
            }
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

            var smallWidgetResId = R.layout.weather_appwidget_small_system
            var largeWidgetResId = R.layout.weather_appwidget_large_system
            var wideWidgetResId = R.layout.weather_appwidget_wide_system

            val iconPack = IconPack.fromConfig(context)

            val appIconTheme = Config.getIconTheme(context)
            val iconNightMode = IconProvider.getWidgetIconNightMode(
                widgetIconTheme, appIconTheme, theme
            )

            val useResourceIcon = iconPack != null
                    && iconPack.supportsTheming
                    && iconPack.canUseLocalResources(context)

            when (theme) {
                WidgetConfig.COLOR_THEME_SYSTEM -> {
                    smallWidgetResId = R.layout.weather_appwidget_small_system
                    largeWidgetResId = R.layout.weather_appwidget_large_system
                    wideWidgetResId = R.layout.weather_appwidget_wide_system
                }

                WidgetConfig.COLOR_THEME_DARK -> {
                    smallWidgetResId = R.layout.weather_appwidget_small_dark
                    largeWidgetResId = R.layout.weather_appwidget_large_dark
                    wideWidgetResId = R.layout.weather_appwidget_wide_dark
                }

                WidgetConfig.COLOR_THEME_LIGHT -> {
                    smallWidgetResId = R.layout.weather_appwidget_small_light
                    largeWidgetResId = R.layout.weather_appwidget_large_light
                    wideWidgetResId = R.layout.weather_appwidget_wide_light
                }
            }

            val smallView = RemoteViews(context.packageName, smallWidgetResId)
            setupRemoteView(
                context, appWidgetManager, appWidgetId, smallView,
                bgTrans, iconPack, useResourceIcon, iconNightMode
            )
            val largeView = RemoteViews(context.packageName, largeWidgetResId)
            setupRemoteView(
                context, appWidgetManager, appWidgetId, largeView,
                bgTrans, iconPack, useResourceIcon, iconNightMode
            )
            val wideView = RemoteViews(context.packageName, wideWidgetResId)
            setupRemoteView(
                context, appWidgetManager, appWidgetId, wideView,
                bgTrans, iconPack, useResourceIcon, iconNightMode
            )

            val viewMapping = ArrayMap<SizeF, RemoteViews>()
            viewMapping[SizeF(50f, 50f)] = smallView
            viewMapping[SizeF(260f, 150f)] = largeView
            viewMapping[SizeF(200f, 50f)] = wideView
            return RemoteViews(viewMapping)
        }

        private fun showError(
            context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int,
            errorReason: Int, widget: RemoteViews
        ) {
            if (LOGGING) {
                Log.i(TAG, "showError $appWidgetId errorReason = $errorReason")
            }

            initWidget(widget)
            widget.setOnClickPendingIntent(R.id.weather_data, getSettingsIntent(context))

            if (errorReason == EXTRA_ERROR_DISABLED) {
                widget.setViewVisibility(R.id.forecast_line, View.GONE)
                widget.setViewVisibility(R.id.current_weather_line, View.GONE)
                widget.setViewVisibility(R.id.current_condition_line, View.GONE)
                widget.setViewVisibility(R.id.info_container, View.VISIBLE)
                widget.setTextViewText(
                    R.id.info_text,
                    context.resources.getString(R.string.omnijaws_service_disabled)
                )
            } else {
                // should never happen
                widget.setViewVisibility(R.id.forecast_line, View.GONE)
                widget.setViewVisibility(R.id.current_weather_line, View.GONE)
                widget.setViewVisibility(R.id.current_condition_line, View.GONE)
                widget.setViewVisibility(R.id.info_container, View.VISIBLE)
                widget.setTextViewText(
                    R.id.info_text,
                    context.resources.getString(R.string.omnijaws_service_unkown)
                )
            }
        }

        private fun initWidget(widget: RemoteViews) {
            widget.setViewVisibility(R.id.forecast_line, View.VISIBLE)
            widget.setViewVisibility(R.id.current_weather_line, View.VISIBLE)
            widget.setViewVisibility(R.id.current_condition_line, View.VISIBLE)
            widget.setViewVisibility(R.id.info_container, View.GONE)
        }

        private fun getBitmapDrawable(context: Context, image: Drawable): BitmapDrawable {
            val canvas = Canvas()
            canvas.drawFilter = PaintFlagsDrawFilter(
                Paint.ANTI_ALIAS_FLAG, Paint.FILTER_BITMAP_FLAG
            )
            val imageWidth = image.intrinsicWidth
            val imageHeight = image.intrinsicHeight

            val bmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
            canvas.setBitmap(bmp)
            image.setBounds(0, 0, imageWidth, imageHeight)
            image.draw(canvas)

            return BitmapDrawable(context.resources, bmp)
        }

        @JvmStatic
        fun setImageView(
            context: Context, widget: RemoteViews, iconPack: IconPack?, viewId: Int,
            conditionCode: Int, useResourceIcon: Boolean, iconNightMode: Int
        ) {
            if (useResourceIcon && iconPack != null) {
                val iconContext = IconProvider.getIconContext(context, iconNightMode)
                val resId = iconPack.getLocalResId(iconContext, conditionCode)

                if (resId != IconPack.RESOURCE_NOT_FOUND) {
                    if (iconNightMode == Configuration.UI_MODE_NIGHT_UNDEFINED) {
                        widget.setImageViewResource(viewId, resId)
                    } else {
                        val d = iconContext.getDrawable(resId)
                        widget.setImageViewBitmap(
                            viewId, getBitmapDrawable(iconContext, d!!).bitmap
                        )
                    }
                    return
                }
            }

            val d = OmniJawsClient.get().getWeatherConditionImage(context, conditionCode)
            widget.setImageViewBitmap(viewId, getBitmapDrawable(context, d).bitmap)
        }

        private fun getWeatherDataString(min: String, max: String?, tempUnits: String): String {
            return if (max != null) {
                "$min/$max$tempUnits"
            } else {
                "$min$tempUnits"
            }
        }

        @JvmStatic
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidgetComponentName =
                ComponentName(context, WeatherAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName)
            for (appWidgetId in appWidgetIds) {
                updateWeather(context, appWidgetManager, appWidgetId)
            }
        }

        @JvmStatic
        fun disableAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidgetComponentName =
                ComponentName(context, WeatherAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName)
            for (appWidgetId in appWidgetIds) {
                updateWeather(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
