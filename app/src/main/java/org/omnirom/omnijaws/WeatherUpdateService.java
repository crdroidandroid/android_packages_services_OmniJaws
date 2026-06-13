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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import org.omnirom.omnijaws.widget.WeatherAppWidgetProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// we dont need an explicit wakelock JobScheduler takes care of that
public class WeatherUpdateService extends JobService {
    private static final String TAG = "WeatherUpdateService";
    private static final boolean DEBUG = false;
    private static final String ACTION_BROADCAST = "org.omnirom.omnijaws.WEATHER_UPDATE";
    private static final String ACTION_ERROR = "org.omnirom.omnijaws.WEATHER_ERROR";

    private static final String EXTRA_ERROR = "error";

    private static final int EXTRA_ERROR_LOCATION = 1;
    private static final int EXTRA_ERROR_DISABLED = 2;

    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes
    private static final long LOCATION_REQUEST_TIMEOUT_MS = 15L * 1000L; // 15 seconds
    private static final int RETRY_DELAY_MS = 5000;
    private static final int RETRY_MAX_NUM = 5;

    public static final int PERIODIC_UPDATE_JOB_ID = 0;
    public static final int ONCE_UPDATE_JOB_ID = 1;

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private volatile CancellationSignal mLocationCancellationSignal;
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStopJob " + params.getJobId());
        CancellationSignal signal = mLocationCancellationSignal;
        if (signal != null) {
            signal.cancel();
        }
        return true;
    }

    @Override
    public void onNetworkChanged(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onNetworkChanged " + params.getJobId());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (DEBUG) Log.d(TAG, "onStartJob " + params.getJobId());
        updateWeatherFromAlarm(params);
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");
        mHandlerThread = new HandlerThread("WeatherService Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        mHandler.removeCallbacksAndMessages(null);
        mHandlerThread.quitSafely();
    }

    private void updateWeatherFromAlarm(JobParameters params) {
        Config.setUpdateError(this, false);

        if (!Config.isEnabled(this)) {
            Log.w(TAG, "Service started, but not enabled ... stopping");
            Intent errorIntent = new Intent(ACTION_ERROR);
            errorIntent.putExtra(EXTRA_ERROR, EXTRA_ERROR_DISABLED);
            sendBroadcast(errorIntent);
            jobFinished(params, false);
            return;
        }

        Config.clearLastUpdateTime(this);

        Log.d(TAG, "updateWeather");
        updateWeather(params);
    }

    private boolean doCheckLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }
        return lm.isLocationEnabled();
    }

    @SuppressLint("MissingPermission")
    private Location getCurrentLocation() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!doCheckLocationEnabled()) {
            Log.w(TAG, "location services are OFF on the device");
            return null;
        }

        Location location = getBestLastKnownLocation(lm);
        Log.d(TAG, "getCurrentLocation: best last known = " + describe(location));

        // Use a recent, accurate last-known fix as-is (weather doesn't change
        // meaningfully over a few minutes / a short distance). Only pay for a
        // fresh fix when we have nothing usable.
        boolean needsUpdate = location == null;
        if (location != null) {
            if (location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
                Log.w(TAG, "Ignoring inaccurate last known location");
                needsUpdate = true;
            } else {
                long delta = System.currentTimeMillis() - location.getTime();
                if (delta > OUTDATED_LOCATION_THRESHOLD_MILLIS) {
                    Log.w(TAG, "Last known location is too old (" + dayFormat.format(location.getTime()) + ")");
                    needsUpdate = true;
                }
            }
        }

        Log.d(TAG, "getCurrentLocation: needsFreshFix = " + needsUpdate);

        if (needsUpdate) {
            Location fresh = requestCurrentLocationBlocking(lm);
            if (fresh != null) {
                location = fresh;
            } else {
                Log.w(TAG, "Could not obtain a fresh location"
                        + (location != null ? ", falling back to last known" : ""));
                // location stays as the (possibly stale) last known, or null.
            }
        }

        Log.d(TAG, "getCurrentLocation: returning " + describe(location));
        return location;
    }

    private static String describe(Location l) {
        if (l == null) return "null";
        return l.getProvider() + " (" + l.getLatitude() + "," + l.getLongitude()
                + ") acc=" + l.getAccuracy() + "m age="
                + ((System.currentTimeMillis() - l.getTime()) / 1000) + "s";
    }

    @SuppressLint("MissingPermission")
    private Location getBestLastKnownLocation(LocationManager lm) {
        Location best = null;
        List<String> providers = lm.getProviders(true);
        for (String provider : providers) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l == null) {
                    continue;
                }
                if (l.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
                    continue;
                }
                if (best == null || l.getTime() > best.getTime()) {
                    best = l;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // provider not usable, skip
            }
        }
        return best;
    }

    @SuppressLint("MissingPermission")
    private Location requestCurrentLocationBlocking(LocationManager lm) {
        String provider = null;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        } else if (lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
            provider = LocationManager.FUSED_PROVIDER;
        } else {
            String best = lm.getBestProvider(sLocationCriteria, true);
            if (!TextUtils.isEmpty(best)) {
                provider = best;
            }
        }

        if (provider == null) {
            Log.e(TAG, "No enabled location provider (network/gps/fused all off)");
            return null;
        }

        Log.d(TAG, "Requesting fresh location from provider '" + provider + "'");

        final CountDownLatch latch = new CountDownLatch(1);
        final Location[] result = new Location[1];
        final CancellationSignal cancel = new CancellationSignal();
        mLocationCancellationSignal = cancel;

        try {
            lm.getCurrentLocation(provider, cancel, getMainExecutor(), location -> {
                if (DEBUG) Log.d(TAG, "getCurrentLocation callback: " + describe(location));
                result[0] = location;
                latch.countDown();
            });
        } catch (SecurityException e) {
            Log.e(TAG, "Missing runtime permission for getCurrentLocation", e);
            mLocationCancellationSignal = null;
            return null;
        }

        try {
            if (!latch.await(LOCATION_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timed out after " + (LOCATION_REQUEST_TIMEOUT_MS / 1000)
                        + "s waiting for a fix from '" + provider + "'");
                cancel.cancel();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel.cancel();
        } finally {
            mLocationCancellationSignal = null;
        }
        return result[0];
    }

    public static void scheduleUpdatePeriodic(Context context) {
        cancelUpdatePeriodic(context);

        if (DEBUG) Log.d(TAG, "scheduleUpdatePeriodic");
        final long interval = TimeUnit.HOURS.toMillis(Config.getUpdateInterval(context));
        final long due = System.currentTimeMillis() + interval;

        if (DEBUG) Log.d(TAG, "Scheduling next update at " + new Date(due));

        ComponentName component = new ComponentName(context, WeatherUpdateService.class);
        JobInfo job = new JobInfo.Builder(PERIODIC_UPDATE_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(interval)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    public static void scheduleUpdateOnce(Context context, long timeoutMillis, int jobId) {
        if (DEBUG) Log.d(TAG, "scheduleUpdateOnce");

        ComponentName component = new ComponentName(context, WeatherUpdateService.class);
        JobInfo job = new JobInfo.Builder(jobId, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(timeoutMillis)
                .setOverrideDeadline(timeoutMillis)
                .build();
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    public static void scheduleUpdateNow(Context context) {
        if (DEBUG) Log.d(TAG, "scheduleUpdateNow");

        ComponentName component = new ComponentName(context, WeatherUpdateService.class);
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo expedited = new JobInfo.Builder(ONCE_UPDATE_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExpedited(true)
                .build();

        int result = jobScheduler.schedule(expedited);
        if (result != JobScheduler.RESULT_SUCCESS) {
            if (DEBUG) Log.d(TAG, "expedited schedule failed, using normal job");
            JobInfo normal = new JobInfo.Builder(ONCE_UPDATE_JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(TimeUnit.SECONDS.toMillis(30))
                    .build();
            jobScheduler.schedule(normal);
        }
    }

    private static void cancelUpdate(Context context) {
        if (DEBUG) Log.d(TAG, "cancelUpdate");
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(PERIODIC_UPDATE_JOB_ID);
    }

    public static void cancelUpdatePeriodic(Context context) {
        cancelUpdate(context);
    }

    public static void cancelAllUpdate(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }

    public static void disabledCall(Context context) {
        Intent errorIntent = new Intent(ACTION_ERROR);
        errorIntent.putExtra(EXTRA_ERROR, EXTRA_ERROR_DISABLED);
        context.sendBroadcast(errorIntent);
    }

    private void updateWeather(final JobParameters params) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                WeatherInfo w = null;
                boolean locationError = false;
                try {
                    AbstractWeatherProvider provider = Config.getProvider(WeatherUpdateService.this);
                    int i = 0;
                    // retry max RETRY_MAX_NUM times
                    while (i < RETRY_MAX_NUM) {
                        if (!Config.isCustomLocation(WeatherUpdateService.this)) {
                            if (checkPermissions()) {
                                Location location = getCurrentLocation();
                                if (location != null) {
                                    w = provider.getLocationWeather(location, Config.isMetric(WeatherUpdateService.this));
                                } else {
                                    Log.w(TAG, "no location available");
                                    locationError = true;
                                    // we are outa here
                                    break;
                                }
                            } else {
                                Log.w(TAG, "no location permission granted (FINE="
                                        + (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                                        + " COARSE="
                                        + (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                                        + ")");
                                locationError = true;
                                // we are outa here
                                break;
                            }
                        } else if (Config.getLocationId(WeatherUpdateService.this) != null) {
                            w = provider.getCustomWeather(Config.getLocationId(WeatherUpdateService.this), Config.isMetric(WeatherUpdateService.this));
                        } else {
                            Log.w(TAG, "no valid custom location");
                            // we are outa here
                            break;
                        }
                        if (w != null) {
                            Config.setWeatherData(WeatherUpdateService.this, w);
                            WeatherContentProvider.updateCachedWeatherInfo(WeatherUpdateService.this);
                            WeatherAppWidgetProvider.updateAllWidgets(WeatherUpdateService.this);
                            // we are outa here
                            break;
                        } else {
                            if (!provider.shouldRetry()) {
                                // some other error
                                break;
                            } else {
                                Log.w(TAG, "retry count =" + i);
                                try {
                                    Thread.sleep(RETRY_DELAY_MS);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                        i++;
                    }
                } finally {
                    if (w == null) {
                        // error
                        Log.d(TAG, "clear weather data");
                        Config.setUpdateError(WeatherUpdateService.this, true);
                        Config.clearWeatherData(WeatherUpdateService.this);
                        WeatherContentProvider.updateCachedWeatherInfo(WeatherUpdateService.this);
                        WeatherAppWidgetProvider.updateAllWidgets(WeatherUpdateService.this);
                        if (locationError) {
                            Intent errorIntent = new Intent(ACTION_ERROR);
                            errorIntent.putExtra(EXTRA_ERROR, EXTRA_ERROR_LOCATION);
                            sendBroadcast(errorIntent);
                        }
                    }
                    // send broadcast that something has changed
                    Intent updateIntent = new Intent(ACTION_BROADCAST);
                    sendBroadcast(updateIntent);
                    if (params != null) {
                        jobFinished(params, false);
                    }
                }
            }
        });
    }

    private boolean checkPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
