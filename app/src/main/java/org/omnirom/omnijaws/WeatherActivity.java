/*
 *  Copyright (C) 2020 The OmniROM Project
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

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.android.internal.util.crdroid.OmniJawsClient;

import java.util.Calendar;

public class WeatherActivity extends Activity implements OmniJawsClient.OmniJawsObserver {
    private static final String TAG = "WeatherActivity";
    private static final boolean DEBUG = false;
    private DetailedWeatherView mDetailedView;
    private OmniJawsClient mWeatherClient;

    /** The background colors of the app, it changes thru out the day to mimic the sky. **/
    public static final String[] BACKGROUND_SPECTRUM = { "#212121", "#27232e", "#2d253a",
            "#332847", "#382a53", "#3e2c5f", "#442e6c", "#393a7a", "#2e4687", "#235395", "#185fa2",
            "#0d6baf", "#0277bd", "#0d6cb1", "#1861a6", "#23569b", "#2d4a8f", "#383f84", "#433478",
            "#3d3169", "#382e5b", "#322b4d", "#2c273e", "#272430" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_weather);
        mDetailedView = findViewById(R.id.weather_forecast);
        View settings = findViewById(R.id.settings);
        settings.setOnClickListener(v -> {
            startActivity(mWeatherClient.getSettingsIntent());
        });
        View statusView = findViewById(R.id.status_view);
        statusView.setOnClickListener(v -> {
            startActivity(mWeatherClient.getSettingsIntent());
        });
        View refresh = findViewById(R.id.refresh);
        refresh.setOnClickListener(v -> {
            mDetailedView.forceRefresh();
        });
        mWeatherClient = new OmniJawsClient(this);
        mDetailedView.setActivity(this);
        mDetailedView.setWeatherClient(mWeatherClient);
        updateHourColor();
    }

    @Override
    public void onResume() {
        super.onResume();
         mWeatherClient.addObserver(this);
         queryAndUpdateWeather();
    }

    @Override
    public void onPause() {
        super.onPause();
        mWeatherClient.removeObserver(this);
    }

    @Override
    public void weatherUpdated() {
        if (DEBUG) Log.d(TAG, "weatherUpdated");
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mDetailedView.weatherError(errorReason);
    }

    private void queryAndUpdateWeather() {
        mWeatherClient.queryWeather();
        mDetailedView.updateWeatherData(mWeatherClient.getWeatherInfo());
    }

    private int getCurrentHourColor() {
        final int hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return Color.parseColor(BACKGROUND_SPECTRUM[hourOfDay]);
    }

    protected void updateHourColor() {
        getWindow().getDecorView().setBackgroundColor(getCurrentHourColor());
        getWindow().setNavigationBarColor(getCurrentHourColor());
        getWindow().setStatusBarColor(getCurrentHourColor());
    }
}
