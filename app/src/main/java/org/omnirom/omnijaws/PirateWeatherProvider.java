/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

public class PirateWeatherProvider extends AbstractWeatherProvider {
    private static final String TAG = "PirateWeatherProvider";

    private static final String URL_WEATHER =
            "https://api.pirateweather.net/forecast/%s/%s?units=%s&exclude=minutely,hourly,alerts,flags";

    public PirateWeatherProvider(Context context) {
        super(context);
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return handleWeatherRequest(id, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String coordinates = String.format(Locale.US, PART_COORDINATES, location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(coordinates, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        String apiKey = Config.getPirateWeatherKey(mContext);
        if (TextUtils.isEmpty(apiKey)) {
            log(TAG, "no API key provided");
            return null;
        }

        String units = metric ? "si" : "us";
        String coordsForUrl = getCoords(selection);
        if (coordsForUrl == null) {
            return null;
        }

        String conditionUrl = String.format(Locale.US, URL_WEATHER, apiKey, coordsForUrl, units);
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            return null;
        }
        log(TAG, "Condition URL = " + conditionUrl + " returning a response of " + conditionResponse);

        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject conditionData = conditions.getJSONObject("currently");
            ArrayList<DayForecast> forecasts =
                    parseForecasts(conditions.getJSONObject("daily").getJSONArray("data"), metric);
            
            float windSpeed = (float) conditionData.getDouble("windSpeed");
            if (metric) {
                // speeds are in m/s so convert to our common metric unit km/h
                windSpeed *= 3.6f;
            } else {
                // Dark Sky US units gives mph
            }

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext, selection, city,
                    /* condition */ conditionData.getString("summary"),
                    /* conditionCode */ mapConditionIconToCode(conditionData.getString("icon")),
                    /* temperature */ (float) conditionData.getDouble("temperature"),
                    /* humidity */ (float) (conditionData.getDouble("humidity") * 100),
                    /* wind */ windSpeed,
                    /* windDir */ conditionData.has("windBearing") ? conditionData.getInt("windBearing") : 0,
                    metric,
                    forecasts,
                    System.currentTimeMillis());

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection + ")", e);
        }

        return null;
    }

    private String getCoords(String coordinate) {
        try {
            double latitude = Double.valueOf(coordinate.substring(4, coordinate.indexOf("&")));
            double longitude = Double.valueOf(coordinate.substring(coordinate.indexOf("lon=") + 4));
            return latitude + "," + longitude;
        } catch (Exception e) {
            return null;
        }
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            String day = getDay(i);
            DayForecast item = null;
            try {
                JSONObject forecast = forecasts.getJSONObject(i);
                item = new DayForecast(
                        /* low */ (float) forecast.getDouble("temperatureLow"),
                        /* high */ (float) forecast.getDouble("temperatureHigh"),
                        /* condition */ forecast.getString("summary"),
                        /* conditionCode */ mapConditionIconToCode(forecast.getString("icon")),
                        day,
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i + " creating dummy", e);
                item = new DayForecast(
                        /* low */ 0,
                        /* high */ 0,
                        /* condition */ "",
                        /* conditionCode */ -1,
                        "NaN",
                        metric);
            }
            result.add(item);
        }
        // clients assume there are 5 entries
        if (result.size() < 5) {
            for (int i = result.size(); i < 5; i++) {
                Log.w(TAG, "Missing forecast for day " + i + " creating dummy");
                DayForecast item = new DayForecast(
                        /* low */ 0,
                        /* high */ 0,
                        /* condition */ "",
                        /* conditionCode */ -1,
                        "NaN",
                        metric);
                result.add(item);
            }
        }
        return result;
    }

    private int mapConditionIconToCode(String icon) {
        if (icon.equals("clear-day")) {
            return 32;
        } else if (icon.equals("clear-night")) {
            return 31;
        } else if (icon.equals("rain")) {
            return 11;
        } else if (icon.equals("snow")) {
            return 16;
        } else if (icon.equals("sleet")) {
            return 18;
        } else if (icon.equals("wind")) {
            return 24;
        } else if (icon.equals("fog")) {
            return 20;
        } else if (icon.equals("cloudy")) {
            return 26;
        } else if (icon.equals("partly-cloudy-day")) {
            return 30;
        } else if (icon.equals("partly-cloudy-night")) {
            return 29;
        } else if (icon.equals("hail")) {
            return 17;
        } else if (icon.equals("thunderstorm")) {
            return 4;
        } else if (icon.equals("tornado")) {
            return 0;
        }

        return -1;
    }

    public boolean shouldRetry() {
        return false;
    }
}
