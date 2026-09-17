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
            "https://api.pirateweather.net/forecast/%s/%s?units=%s&exclude=minutely,alerts,flags";

    private static final int MIN_FORECAST_DAYS = 5;
    private static final int HOURLY_FORECAST_COUNT = 24;

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
            Log.w(TAG, "could not parse coordinates from '" + selection + "'");
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
            JSONArray dailyData = conditions.getJSONObject("daily").getJSONArray("data");
            ArrayList<DayForecast> forecasts = parseForecasts(dailyData, metric);

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext, selection, city,
                    /* condition */ "",
                    /* conditionCode */ mapConditionIconToCode(conditionData.optString("icon", "")),
                    /* temperature */ (float) conditionData.getDouble("temperature"),
                    /* humidity */ (float) (conditionData.optDouble("humidity", Double.NaN) * 100),
                    /* wind */ convertWindSpeed(conditionData.optDouble("windSpeed", Double.NaN), metric),
                    /* windDir */ (int) conditionData.optDouble("windBearing", 0),
                    metric,
                    forecasts,
                    System.currentTimeMillis());

            if (conditionData.has("apparentTemperature")) {
                w.setFeelsLike((float) conditionData.getDouble("apparentTemperature"));
            }
            if (conditionData.has("pressure")) {
                // si and us both report hPa / mb
                w.setPressure((float) conditionData.getDouble("pressure"));
            }
            if (conditionData.has("uvIndex")) {
                w.setUvi((float) conditionData.getDouble("uvIndex"));
            }
            if (conditionData.has("dewPoint")) {
                w.setDewPoint((float) conditionData.getDouble("dewPoint"));
            }
            if (conditionData.has("visibility")) {
                // already km (si) or miles (us) - no conversion needed
                w.setVisibility((float) conditionData.getDouble("visibility"));
            }

            if (dailyData.length() > 0) {
                JSONObject today = dailyData.getJSONObject(0);
                if (today.has("sunriseTime")) {
                    w.setSunrise(today.getLong("sunriseTime") * 1000L);
                }
                if (today.has("sunsetTime")) {
                    w.setSunset(today.getLong("sunsetTime") * 1000L);
                }
            }

            if (conditions.has("hourly")) {
                JSONObject hourly = conditions.getJSONObject("hourly");
                if (hourly.has("data")) {
                    w.setHourlyForecasts(parseHourlyForecasts(hourly.getJSONArray("data"), metric));
                }
            }

            log(TAG, "Weather updated: " + w);

            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection + ")", e);
        }

        return null;
    }

    private String getCoords(String coordinate) {
        try {
            int latStart = coordinate.indexOf("lat=");
            int lonStart = coordinate.indexOf("lon=");
            if (latStart == -1 || lonStart == -1) {
                return null;
            }
            latStart += 4;
            int latEnd = coordinate.indexOf("&", latStart);
            if (latEnd == -1) {
                latEnd = coordinate.length();
            }
            double latitude = Double.parseDouble(coordinate.substring(latStart, latEnd));
            double longitude = Double.parseDouble(coordinate.substring(lonStart + 4));
            return String.format(Locale.US, "%f,%f", latitude, longitude);
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
                double low = forecast.has("temperatureLow")
                        ? forecast.getDouble("temperatureLow")
                        : forecast.getDouble("temperatureMin");
                double high = forecast.has("temperatureHigh")
                        ? forecast.getDouble("temperatureHigh")
                        : forecast.getDouble("temperatureMax");
                item = new DayForecast(
                        /* low */ (float) low,
                        /* high */ (float) high,
                        /* condition */ "",
                        /* conditionCode */ mapConditionIconToCode(forecast.optString("icon", "")),
                        day,
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i, e);
                continue;
            }
            result.add(item);
        }
        // clients assume there are 5 entries
        if (result.size() < MIN_FORECAST_DAYS) {
            for (int i = result.size(); i < MIN_FORECAST_DAYS; i++) {
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

    private ArrayList<WeatherInfo.HourlyForecast> parseHourlyForecasts(JSONArray hourly, boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();
        final long startSec = (System.currentTimeMillis() / 1000L) - 3600L;

        for (int i = 0; i < hourly.length() && result.size() < HOURLY_FORECAST_COUNT; i++) {
            try {
                JSONObject hour = hourly.getJSONObject(i);
                long time = hour.getLong("time");
                if (time < startSec) {
                    continue;
                }
                result.add(new WeatherInfo.HourlyForecast(
                        (float) hour.getDouble("temperature"),
                        mapConditionIconToCode(hour.optString("icon", "")),
                        "",
                        time * 1000L,
                        (float) (hour.optDouble("humidity", Double.NaN) * 100),
                        convertWindSpeed(hour.optDouble("windSpeed", Double.NaN), metric),
                        metric));
            } catch (JSONException e) {
                Log.w(TAG, "Invalid hourly forecast for index " + i, e);
            }
        }
        return result;
    }

    private static float convertWindSpeed(double value, boolean metric) {
        return (float) (metric ? value * 3.6 : value);
    }

    private int mapConditionIconToCode(String icon) {
        if (TextUtils.isEmpty(icon)) {
            return -1;
        }
        switch (icon) {
            case "clear-day":
                return 32;
            case "clear-night":
                return 31;
            case "rain":
                return 11;
            case "snow":
                return 16;
            case "sleet":
                return 18;
            case "wind":
                return 24;
            case "fog":
                return 20;
            case "cloudy":
                return 26;
            case "partly-cloudy-day":
                return 30;
            case "partly-cloudy-night":
                return 29;
            case "hail":
                return 17;
            case "thunderstorm":
                return 4;
            case "tornado":
                return 0;
            default:
                return -1;
        }
    }

    public boolean shouldRetry() {
        return false;
    }
}
