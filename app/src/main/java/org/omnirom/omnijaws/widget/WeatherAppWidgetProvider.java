/*
 * Copyright (C) 2017 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SizeF;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.RemoteViews;

import org.omnirom.omnijaws.Config;
import org.omnirom.omnijaws.R;
import org.omnirom.omnijaws.SettingsActivity;
import org.omnirom.omnijaws.WeatherActivity;
import com.android.internal.util.crdroid.OmniJawsClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class WeatherAppWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "WeatherAppWidgetProvider";
    private static final boolean LOGGING = false;
    private static final int EXTRA_ERROR_DISABLED = 2;

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        if (LOGGING) {
            Log.i(TAG, "onEnabled");
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (LOGGING) {
            Log.i(TAG, "onDisabled");
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        for (int id : appWidgetIds) {
            if (LOGGING) {
                Log.i(TAG, "onDeleted: " + id);
            }
            WeatherAppWidgetConfigure.clearPrefs(context, id);
        }
    }

    @Override
    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        int i = 0;
        for (int oldWidgetId : oldWidgetIds) {
            if (LOGGING) {
                Log.i(TAG, "onRestored " + oldWidgetId + " " + newWidgetIds[i]);
            }
            WeatherAppWidgetConfigure.remapPrefs(context, oldWidgetId, newWidgetIds[i]);
            i++;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (LOGGING) {
            Log.i(TAG, "onReceive: " + action);
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (LOGGING) {
            Log.i(TAG, "onUpdate");
        }
        updateAllWeather(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        if (LOGGING) {
            Log.i(TAG, "onAppWidgetOptionsChanged");
            ArrayList<SizeF> sizes =
                    newOptions.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            Log.d(TAG, "size = " + sizes);
        }

        updateWeather(context, appWidgetManager, appWidgetId);
    }

    public static void updateAfterConfigure(Context context, int appWidgetId) {
        if (LOGGING) {
            Log.i(TAG, "updateAfterConfigure");
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        updateWeather(context, appWidgetManager, appWidgetId);
    }

    public static void updateAllWeather(Context context) {
        if (LOGGING) {
            Log.i(TAG, "updateAllWeather at = " + new Date());
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetManager != null) {
            ComponentName componentName = new ComponentName(context, WeatherAppWidgetProvider.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            for (int appWidgetId : appWidgetIds) {
                updateWeather(context, appWidgetManager, appWidgetId);
            }
        }
    }

    private static PendingIntent getSettingsIntent(Context context) {
        Intent configureIntent = new Intent(context, SettingsActivity.class);
        return PendingIntent.getActivity(context, 0, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent getWeatherActivityIntent(Context context) {
        Intent configureIntent = new Intent(context, WeatherActivity.class);
        return PendingIntent.getActivity(context, 0, configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void updateWeather(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        if (LOGGING) {
            Log.i(TAG, "updateWeather " + appWidgetId);
        }

        OmniJawsClient weatherClient = new OmniJawsClient(context.getApplicationContext());
        weatherClient.queryWeather();

        appWidgetManager.updateAppWidget(appWidgetId, createRemoteViews(context, appWidgetManager, appWidgetId, weatherClient));
    }

    private static void setupRemoteView(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                                        RemoteViews widget, OmniJawsClient weatherClient, boolean withForecast) {
        if (!Config.isEnabled(context)) {
            showError(context, appWidgetManager, appWidgetId, EXTRA_ERROR_DISABLED, widget);
            return;
        }

        OmniJawsClient.WeatherInfo weatherData = weatherClient.getWeatherInfo();

        initWidget(widget);
        setWidgetBackground(context, appWidgetManager, widget, appWidgetId);
        if (withForecast) {
            widget.setOnClickPendingIntent(R.id.weather_data, getSettingsIntent(context));
        } else {
            widget.setOnClickPendingIntent(R.id.weather_data, getWeatherActivityIntent(context));
        }
        int textColor = getForegroundColor(context, appWidgetId);

        if (weatherData == null) {
            Log.e(TAG, "updateWeather weatherData == null");
            widget.setViewVisibility(R.id.condition_line, View.GONE);
            widget.setViewVisibility(R.id.current_weather_line, View.GONE);
            widget.setViewVisibility(R.id.info_container, View.VISIBLE);
            widget.setTextViewText(R.id.info_text, context.getResources().getString(R.string.omnijaws_service_waiting));
            widget.setTextColor(R.id.info_text, textColor);
            return;
        }

        Long timeStamp = weatherData.timeStamp;
        String format = DateFormat.is24HourFormat(context) ? "HH:mm" : "hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        widget.setTextViewText(R.id.current_weather_timestamp, sdf.format(timeStamp));
        widget.setTextColor(R.id.current_weather_timestamp, textColor);

        sdf = new SimpleDateFormat("EE");
        Calendar cal = Calendar.getInstance();
        String dayShort = sdf.format(new Date(cal.getTimeInMillis()));
        String forecastData = getWeatherDataString(weatherData.forecasts.get(0).low, weatherData.forecasts.get(0).high,
                weatherData.tempUnits);

        Drawable d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(0).conditionCode);
        BitmapDrawable bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.forecast_image_0, bd.getBitmap());
        widget.setTextViewText(R.id.forecast_text_0, dayShort);
        widget.setTextColor(R.id.forecast_text_0, textColor);
        widget.setTextViewText(R.id.forecast_data_0, forecastData);
        widget.setTextColor(R.id.forecast_data_0, textColor);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));
        forecastData = getWeatherDataString(weatherData.forecasts.get(1).low, weatherData.forecasts.get(1).high,
                weatherData.tempUnits);
        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(1).conditionCode);
        bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.forecast_image_1, bd.getBitmap());
        widget.setTextViewText(R.id.forecast_text_1, dayShort);
        widget.setTextColor(R.id.forecast_text_1, textColor);
        widget.setTextViewText(R.id.forecast_data_1, forecastData);
        widget.setTextColor(R.id.forecast_data_1, textColor);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));
        forecastData = getWeatherDataString(weatherData.forecasts.get(2).low, weatherData.forecasts.get(2).high,
                weatherData.tempUnits);
        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(2).conditionCode);
        bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.forecast_image_2, bd.getBitmap());
        widget.setTextViewText(R.id.forecast_text_2, dayShort);
        widget.setTextColor(R.id.forecast_text_2, textColor);
        widget.setTextViewText(R.id.forecast_data_2, forecastData);
        widget.setTextColor(R.id.forecast_data_2, textColor);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));
        forecastData = getWeatherDataString(weatherData.forecasts.get(3).low, weatherData.forecasts.get(3).high,
                weatherData.tempUnits);
        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(3).conditionCode);
        bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.forecast_image_3, bd.getBitmap());
        widget.setTextViewText(R.id.forecast_text_3, dayShort);
        widget.setTextColor(R.id.forecast_text_3, textColor);
        widget.setTextViewText(R.id.forecast_data_3, forecastData);
        widget.setTextColor(R.id.forecast_data_3, textColor);

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));
        forecastData = getWeatherDataString(weatherData.forecasts.get(4).low, weatherData.forecasts.get(4).high,
                weatherData.tempUnits);
        d = weatherClient.getWeatherConditionImage(weatherData.forecasts.get(4).conditionCode);
        bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.forecast_image_4, bd.getBitmap());
        widget.setTextViewText(R.id.forecast_text_4, dayShort);
        widget.setTextColor(R.id.forecast_text_4, textColor);
        widget.setTextViewText(R.id.forecast_data_4, forecastData);
        widget.setTextColor(R.id.forecast_data_4, textColor);

        String currentData = getWeatherDataString(weatherData.temp, null, weatherData.tempUnits);
        d = weatherClient.getWeatherConditionImage(weatherData.conditionCode);
        bd = getTintedBitmapDrawable(context, d, textColor);
        widget.setImageViewBitmap(R.id.current_image, bd.getBitmap());
        widget.setTextViewText(R.id.current_text, context.getResources().getText(R.string.omnijaws_current_text));
        widget.setTextColor(R.id.current_text, textColor);
        widget.setTextColor(R.id.current_data, textColor);
        widget.setTextViewText(R.id.current_data, currentData);

        ColorStateList iconTint = ColorStateList.valueOf(textColor);
        widget.setTextViewText(R.id.current_weather_city, weatherData.city);
        widget.setTextColor(R.id.current_weather_city, textColor);

        widget.setTextViewText(R.id.current_humidity, weatherData.humidity);
        widget.setTextColor(R.id.current_humidity, textColor);
        widget.setImageViewBitmap(R.id.current_humidity_image,
                getTintedBitmapDrawable(context, context.getResources().getDrawable(R.drawable.ic_humidity_symbol_small, null), textColor).getBitmap());

        widget.setTextViewText(R.id.current_wind, weatherData.windSpeed + " " + weatherData.windUnits);
        widget.setTextColor(R.id.current_wind, textColor);
        widget.setImageViewBitmap(R.id.current_wind_image,
                getTintedBitmapDrawable(context, context.getResources().getDrawable(R.drawable.ic_wind_symbol_small, null), textColor).getBitmap());

        widget.setTextViewText(R.id.current_wind_direction, weatherData.pinWheel);
        widget.setTextColor(R.id.current_wind_direction, textColor);
        widget.setImageViewBitmap(R.id.current_wind_direction_image,
                getTintedBitmapDrawable(context, context.getResources().getDrawable(R.drawable.ic_wind_direction_symbol_small, null), textColor).getBitmap());
    }

    public static RemoteViews createRemoteViews(Context context, AppWidgetManager appWidgetManager, int appWidgetId, OmniJawsClient weatherClient) {
        if (LOGGING) {
            Log.i(TAG, "createRemoteViews");
        }

        RemoteViews smallView = new RemoteViews(context.getPackageName(), R.layout.weather_appwidget_small);
        setupRemoteView(context, appWidgetManager, appWidgetId, smallView, weatherClient, false);
        RemoteViews largeView = new RemoteViews(context.getPackageName(), R.layout.weather_appwidget_large);
        setupRemoteView(context, appWidgetManager, appWidgetId, largeView, weatherClient, true);
        RemoteViews wideView = new RemoteViews(context.getPackageName(), R.layout.weather_appwidget_wide);
        setupRemoteView(context, appWidgetManager, appWidgetId, wideView, weatherClient, true);
        Map<SizeF, RemoteViews> viewMapping = new ArrayMap<>();
        viewMapping.put(new SizeF(50f, 50f), smallView);
        viewMapping.put(new SizeF(260f, 150f), largeView);
        viewMapping.put(new SizeF(260f, 50f), wideView);
        RemoteViews remoteViews = new RemoteViews(viewMapping);
        return remoteViews;
    }

    private static void showError(
            Context context, AppWidgetManager appWidgetManager, int appWidgetId, int errorReason, RemoteViews widget) {

        if (LOGGING) {
            Log.i(TAG, "showError " + appWidgetId + " errorReason = " + errorReason);
        }
        int textColor = getForegroundColor(context, appWidgetId);

        initWidget(widget);
        setWidgetBackground(context, appWidgetManager, widget, appWidgetId);
        widget.setOnClickPendingIntent(R.id.weather_data, getSettingsIntent(context));

        if (errorReason == EXTRA_ERROR_DISABLED) {
            widget.setViewVisibility(R.id.condition_line, View.GONE);
            widget.setViewVisibility(R.id.current_weather_line, View.GONE);
            widget.setViewVisibility(R.id.info_container, View.VISIBLE);
            widget.setTextViewText(R.id.info_text, context.getResources().getString(R.string.omnijaws_service_disabled));
            widget.setTextColor(R.id.info_text, textColor);
        } else {
            // should never happen
            widget.setViewVisibility(R.id.condition_line, View.GONE);
            widget.setViewVisibility(R.id.current_weather_line, View.GONE);
            widget.setViewVisibility(R.id.info_container, View.VISIBLE);
            widget.setTextViewText(R.id.info_text, context.getResources().getString(R.string.omnijaws_service_unkown));
            widget.setTextColor(R.id.info_text, textColor);
        }
    }

    private static void initWidget(RemoteViews widget) {
        widget.setViewVisibility(R.id.condition_line, View.VISIBLE);
        widget.setViewVisibility(R.id.current_weather_line, View.VISIBLE);
        widget.setViewVisibility(R.id.info_container, View.GONE);
    }

    private static void setWidgetBackground(Context context, AppWidgetManager appWidgetManager, RemoteViews widget, int appWidgetId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int theme = prefs.getInt(WeatherAppWidgetConfigure.KEY_COLOR_THEME + "_" + appWidgetId, WeatherAppWidgetConfigure.COLOR_THEME_DEFAULT);
        widget.setColorInt(R.id.background_image, "setColorFilter", getBackgroundColor(context, appWidgetId), getBackgroundColor(context, appWidgetId));
        widget.setInt(R.id.background_image, "setAlpha", theme == WeatherAppWidgetConfigure.COLOR_THEME_TRANSPARENT ? 96 : 255);
    }

    private static BitmapDrawable getTintedBitmapDrawable(Context context, Drawable image, int textColor) {
        if (image instanceof VectorDrawable) {
            image = applyTint(context, image, textColor);
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final int imageWidth = image.getIntrinsicWidth();
        final int imageHeight = image.getIntrinsicHeight();

        final Bitmap bmp = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        image.setBounds(0, 0, imageWidth, imageHeight);
        image.draw(canvas);

        return new BitmapDrawable(context.getResources(), bmp);
    }

    private static String getWeatherDataString(String min, String max, String tempUnits) {
        if (max != null) {
            return min + "/" + max + tempUnits;
        } else {
            return min + tempUnits;
        }
    }

    private static Drawable applyTint(Context context, Drawable icon, int color) {
        icon = icon.mutate();
        icon.setTint(color);
        return icon;
    }

    private static BitmapDrawable shadow(Resources resources, Bitmap b) {
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        BlurMaskFilter blurFilter = new BlurMaskFilter(5, BlurMaskFilter.Blur.OUTER);
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setMaskFilter(blurFilter);

        int[] offsetXY = new int[2];
        Bitmap b2 = b.extractAlpha(shadowPaint, offsetXY);

        Bitmap bmResult = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                Bitmap.Config.ARGB_8888);

        canvas.setBitmap(bmResult);
        canvas.drawBitmap(b2, offsetXY[0], offsetXY[1], null);
        canvas.drawBitmap(b, 0, 0, null);

        return new BitmapDrawable(resources, bmResult);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidgetComponentName = new ComponentName(context, WeatherAppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName);
        for (int appWidgetId : appWidgetIds) {
            WeatherAppWidgetProvider.updateWeather(context, appWidgetManager, appWidgetId);
        }
    }

    public static void disableAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisAppWidgetComponentName = new ComponentName(context, WeatherAppWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName);
        for (int appWidgetId : appWidgetIds) {
            WeatherAppWidgetProvider.updateWeather(context, appWidgetManager, appWidgetId);
        }
    }

    private static int getSystemColor(Context context, String colorName) {
        Resources system = context.getResources();
        return system.getColor(
                system.getIdentifier(
                        colorName,
                        "color", "android"
                ), null
        );
    }

    private static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }

    private static int getBackgroundColor(Context context, int appWidgetId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int theme = prefs.getInt(WeatherAppWidgetConfigure.KEY_COLOR_THEME + "_" + appWidgetId, WeatherAppWidgetConfigure.COLOR_THEME_DEFAULT);
        if (theme == WeatherAppWidgetConfigure.COLOR_THEME_TRANSPARENT) {
            return Color.BLACK;
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_SYSTEM) {
            ContextThemeWrapper c = new ContextThemeWrapper(context, R.style.Theme_Widget);
            return getAttrColor(c, android.R.attr.colorBackground);
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_DARK) {
            return getSystemColor(context, "system_neutral1_900");
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_LIGHT) {
            return getSystemColor(context, "system_neutral1_50");
        }
        return Color.WHITE;
    }

    private static int getForegroundColor(Context context, int appWidgetId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int theme = prefs.getInt(WeatherAppWidgetConfigure.KEY_COLOR_THEME + "_" + appWidgetId, WeatherAppWidgetConfigure.COLOR_THEME_DEFAULT);
        if (theme == WeatherAppWidgetConfigure.COLOR_THEME_TRANSPARENT) {
            return Color.WHITE;
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_SYSTEM) {
            ContextThemeWrapper c = new ContextThemeWrapper(context, R.style.Theme_Widget);
            return getAttrColor(c, android.R.attr.textColorPrimary);
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_DARK) {
            return getSystemColor(context, "text_color_primary_device_default_dark");
        } else if (theme == WeatherAppWidgetConfigure.COLOR_THEME_LIGHT) {
            return getSystemColor(context, "text_color_primary_device_default_light");
        }
        return Color.BLACK;
    }

}
