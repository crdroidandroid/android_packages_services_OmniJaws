/*
 *  Copyright (C) 2017 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws;

import java.util.Date;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

public class WeatherService extends Service {
    private static final String TAG = "WeatherService";
    private static final boolean DEBUG = true;
    private static final String ACTION_UPDATE = "org.omnirom.omnijaws.ACTION_UPDATE";
    private static final String ACTION_ALARM = "org.omnirom.omnijaws.ACTION_ALARM";
    private static final String ACTION_ENABLE = "org.omnirom.omnijaws.ACTION_ENABLE";
    private static final String ACTION_BROADCAST = "org.omnirom.omnijaws.WEATHER_UPDATE";

    private static final String EXTRA_FORCE = "force";
    private static final String EXTRA_ENABLE = "enable";

    static final String ACTION_CANCEL_LOCATION_UPDATE =
            "org.omnirom.omnijaws.CANCEL_LOCATION_UPDATE";

    public static final String BROADCAST_INTENT= "org.omnirom.omnijaws.BROADCAST_INTENT";
    public static final String STOP_INTENT= "org.omnirom.omnijaws.STOP_INTENT";

    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;
    public static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes
    private static final long ALARM_INTERVAL_BASE = AlarmManager.INTERVAL_HOUR;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private PowerManager.WakeLock mWakeLock;
    private boolean mRunning;
    private static PendingIntent mAlarm;

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }
    
    public WeatherService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread("WeatherService Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    public static void startUpdate(Context context, boolean force) {
        start(context, ACTION_UPDATE, force);
    }

    private static void start(Context context, String action, boolean force) {
        Intent i = new Intent(context, WeatherService.class);
        i.setAction(action);
        if (force) {
            i.putExtra(EXTRA_FORCE, force);
        }
        context.startService(i);
    }

    private static PendingIntent alarmPending(Context context) {
        Intent intent = new Intent(context, WeatherService.class);
        intent.setAction(ACTION_ALARM);
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean force = intent.getBooleanExtra(EXTRA_FORCE, false);
        boolean enable = intent.getBooleanExtra(EXTRA_ENABLE, false);

        if (mRunning) {
            Log.w(TAG, "Service running ... do nothing");
            return START_REDELIVER_INTENT;
        }

        if (ACTION_ENABLE.equals(intent.getAction())) {
            if (DEBUG) Log.d(TAG, "Set enablement " + enable);
            Config.setEnabled(this, enable);
            if (!enable) {
                cancelUpdate(this);
            } else {
                // force an immediate update
                force = true;
            }
        }

        if (!Config.isEnabled(this)) {
            Log.w(TAG, "Service started, but not enabled ... stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_CANCEL_LOCATION_UPDATE.equals(intent.getAction())) {
            Log.w(TAG, "Service started, but location timeout ... stopping");
            WeatherLocationListener.cancel(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isNetworkAvailable()) {
            if (DEBUG) Log.d(TAG, "Service started, but no network ... stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!force) {
            final long lastAlarm = Config.getLastAlarmTime(this);
            if (lastAlarm != 0) {
                final long now = System.currentTimeMillis();
                final long updateInterval = ALARM_INTERVAL_BASE * Config.getUpdateInterval(this);
                if (lastAlarm + updateInterval > now) {
                    if (DEBUG)  Log.d(TAG, "Service started, but next alarm is due at " + new Date(lastAlarm + updateInterval) + " ... stopping");
                    stopSelf();
                    return START_NOT_STICKY;
                }
            }
        }
        // force updates will not change the alarm schedule
        if (ACTION_ALARM.equals(intent.getAction())) {
            // normal regular alarm update
            Config.setLastAlarmTime(this);
        }
        if (DEBUG) Log.d(TAG, "updateWeather force =" + force);
        updateWeather();

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Intent result = new Intent(STOP_INTENT);
        sendBroadcast(result);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
    
    private Location getCurrentLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.w(TAG, "network locations disabled");
            return null;
        }
        Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        if (DEBUG) Log.d(TAG, "Current location is " + location);

        if (location != null && location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
            Log.w(TAG, "Ignoring inaccurate location");
            location = null;
        }

        // If lastKnownLocation is not present (because none of the apps in the
        // device has requested the current location to the system yet) or outdated,
        // then try to get the current location use the provider that best matches the criteria.
        boolean needsUpdate = location == null;
        if (location != null) {
            long delta = System.currentTimeMillis() - location.getTime();
            needsUpdate = delta > OUTDATED_LOCATION_THRESHOLD_MILLIS;
        }
        if (needsUpdate) {
            if (DEBUG) Log.d(TAG, "Getting best location provider");
            String locationProvider = lm.getBestProvider(sLocationCriteria, true);
            if (TextUtils.isEmpty(locationProvider)) {
                Log.e(TAG, "No available location providers matching criteria.");
            } else {
                WeatherLocationListener.registerIfNeeded(this, locationProvider);
            }
        }

        return location;
    }

    public static void scheduleUpdate(Context context) {
        cancelUpdate(context);

        final long interval = ALARM_INTERVAL_BASE * Config.getUpdateInterval(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        final long due = System.currentTimeMillis() + interval;
        Config.setLastAlarmTime(context);

        if (DEBUG) Log.d(TAG, "Scheduling next update at " + new Date(due));

        mAlarm = alarmPending(context);
        am.setInexactRepeating(AlarmManager.RTC, due, interval, mAlarm);
        startUpdate(context, true);
    }

    public static void cancelUpdate(Context context) {
        if (mAlarm != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (DEBUG) Log.d(TAG, "Cancel pending update");

            am.cancel(mAlarm);
            mAlarm = null;
        }
    }

    private void updateWeather() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mRunning = true;
                    mWakeLock.acquire();
                    AbstractWeatherProvider provider = Config.getProvider(WeatherService.this);
                    WeatherInfo w = null;
                    if (!Config.isCustomLocation(WeatherService.this)) {
                        if (checkPermissions()) {
                            Location location = getCurrentLocation();
                            if (location != null) {
                                w = provider.getLocationWeather(location, Config.isMetric(WeatherService.this));
                            }
                        } else {
                            Log.w(TAG, "no location permissions");
                        }
                    } else if (Config.getLocationId(WeatherService.this) != null){
                        w = provider.getCustomWeather(Config.getLocationId(WeatherService.this), Config.isMetric(WeatherService.this));
                    } else {
                        Log.w(TAG, "no valid custom location");
                    }
                    if (w != null) {
                        Config.setWeatherData(WeatherService.this, w);
                        WeatherContentProvider.updateCachedWeatherInfo(WeatherService.this);
                        Intent result = new Intent(BROADCAST_INTENT);
                        sendBroadcast(result);
                    }
                } finally {
                    // send broadcast that something has changed
                    Intent updateIntent = new Intent(ACTION_BROADCAST);
                    sendBroadcast(updateIntent);
                    mWakeLock.release();
                    mRunning = false;
                }
            }
         });
    }

    private boolean checkPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
