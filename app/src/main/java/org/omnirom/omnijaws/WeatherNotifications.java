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

package org.omnirom.omnijaws;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import org.omnirom.omnijaws.icon.IconProvider;
import org.omnirom.omnijaws.ui.WeatherDashboardActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Daily forecast and persistent current weather notifications. */
public class WeatherNotifications extends BroadcastReceiver {
    private static final String CHANNEL_FORECAST = "forecast";
    private static final String CHANNEL_ONGOING = "ongoing";
    private static final int ID_ONGOING = 1;
    private static final int ID_FORECAST = 2;

    private static final String EXTRA_TOMORROW = "tomorrow";
    private static final int TODAY_HOUR = 7;
    private static final int TOMORROW_HOUR = 20;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean tomorrow = intent.getBooleanExtra(EXTRA_TOMORROW, false);
        if (Config.isEnabled(context) && Config.isForecastNotificationOn(context)) {
            showForecast(context, tomorrow);
            schedule(context, tomorrow);
        }
    }

    /** Re-arms the alarms and refreshes the persistent notification from the settings. */
    public static void update(Context context) {
        boolean enabled = Config.isEnabled(context);
        if (enabled && Config.isForecastNotificationOn(context)) {
            schedule(context, false);
            schedule(context, true);
        } else {
            AlarmManager am = context.getSystemService(AlarmManager.class);
            am.cancel(alarmIntent(context, false));
            am.cancel(alarmIntent(context, true));
            context.getSystemService(NotificationManager.class).cancel(ID_FORECAST);
        }
        updateOngoing(context);
    }

    public static void updateOngoing(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        WeatherInfo w = Config.getWeatherData(context);
        if (!Config.isEnabled(context) || !Config.isOngoingNotificationOn(context) || w == null) {
            nm.cancel(ID_ONGOING);
            return;
        }
        createChannels(context);
        String unit = "°" + (w.isMetric() ? "C" : "F");
        String title = Math.round(w.getTemperature()) + unit + " · " + w.getCondition();
        List<String> parts = new ArrayList<>();
        parts.add(w.getCity());
        WeatherInfo.DayForecast today = findDay(w, 0);
        if (today != null) {
            parts.add(Math.round(today.getLow()) + "° / " + Math.round(today.getHigh()) + "°");
        }
        Notification n = builder(context, CHANNEL_ONGOING, w.getConditionCode())
                .setContentTitle(title)
                .setContentText(String.join(" · ", parts))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
        nm.notify(ID_ONGOING, n);
    }

    private static void showForecast(Context context, boolean tomorrow) {
        WeatherInfo w = Config.getWeatherData(context);
        WeatherInfo.DayForecast day = w != null ? findDay(w, tomorrow ? 1 : 0) : null;
        if (day == null) {
            return;
        }
        createChannels(context);
        String label = context.getString(tomorrow
                ? R.string.omnijaws_forecast_tomorrow : R.string.omnijaws_forecast_today);
        String unit = "°" + (w.isMetric() ? "C" : "F");
        String text = Math.round(day.getLow()) + "° / " + Math.round(day.getHigh()) + unit;
        if (day.precipProbability >= 10) {
            text += " · " + context.getString(R.string.omnijaws_notification_precip_chance,
                    Math.round(day.precipProbability));
        }
        Notification n = builder(context, CHANNEL_FORECAST, day.getConditionCode())
                .setContentTitle(label + " · " + day.getCondition(context))
                .setContentText(text)
                .setSubText(w.getCity())
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(ID_FORECAST, n);
    }

    private static WeatherInfo.DayForecast findDay(WeatherInfo w, int offset) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DATE, offset);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
        for (WeatherInfo.DayForecast d : w.getForecasts()) {
            if (date.equals(d.date)) {
                return d;
            }
        }
        return null;
    }

    private static Notification.Builder builder(Context context, String channel, int conditionCode) {
        Intent open = new Intent(context, WeatherDashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_qs_weather_default_on)
                .setContentIntent(pi);
        Bitmap icon = toBitmap(IconProvider.getConditionDrawable(context, conditionCode));
        if (icon != null) {
            b.setLargeIcon(icon);
        }
        return b;
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d == null) {
            return null;
        }
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 128;
        int h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 128;
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, w, h);
        d.draw(canvas);
        return bitmap;
    }

    private static void createChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_FORECAST,
                context.getString(R.string.omnijaws_channel_forecast),
                NotificationManager.IMPORTANCE_DEFAULT));
        NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING,
                context.getString(R.string.omnijaws_channel_ongoing),
                NotificationManager.IMPORTANCE_LOW);
        ongoing.setShowBadge(false);
        nm.createNotificationChannel(ongoing);
    }

    private static void schedule(Context context, boolean tomorrow) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, tomorrow ? TOMORROW_HOUR : TODAY_HOUR);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DATE, 1);
        }
        context.getSystemService(AlarmManager.class).setWindow(AlarmManager.RTC_WAKEUP,
                c.getTimeInMillis(), WINDOW_MS, alarmIntent(context, tomorrow));
    }

    private static PendingIntent alarmIntent(Context context, boolean tomorrow) {
        Intent intent = new Intent(context, WeatherNotifications.class)
                .putExtra(EXTRA_TOMORROW, tomorrow);
        return PendingIntent.getBroadcast(context, tomorrow ? 1 : 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
