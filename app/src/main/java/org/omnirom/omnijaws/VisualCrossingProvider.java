/*
 * Copyright (C) 2026 crDroid Android Project
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

public class VisualCrossingProvider extends AbstractWeatherProvider {
    private static final String TAG = "VisualCrossingProvider";

    private static final int MAX_FORECAST_DAYS = 7;
    private static final int MAX_HOURLY_ENTRIES = 24;

    // Timeline API: one call returns current conditions, daily forecast,
    // hourly forecast and astro data. unitGroup=metric -> degC / km/h / km,
    // unitGroup=us -> degF / mph / miles, so no unit conversion is needed.
    private static final String URL_WEATHER =
            "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
            + "%s?unitGroup=%s&include=current,days,hours&iconSet=icons2&contentType=json&key=%s";

    public VisualCrossingProvider(Context context) {
        super(context);
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        return handleWeatherRequest(id, metric);
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        String coordinates = String.format(Locale.US, PART_COORDINATES,
                location.getLatitude(), location.getLongitude());
        return handleWeatherRequest(coordinates, metric);
    }

    private WeatherInfo handleWeatherRequest(String selection, boolean metric) {
        String apiKey = Config.getVisualCrossingKey(mContext);
        if (TextUtils.isEmpty(apiKey)) {
            log(TAG, "no API key provided");
            return null;
        }

        String coordsForUrl = getCoords(selection);
        if (coordsForUrl == null) {
            Log.w(TAG, "could not parse coordinates from '" + selection + "'");
            return null;
        }

        String units = metric ? "metric" : "us";
        String url = String.format(Locale.US, URL_WEATHER, coordsForUrl, units, apiKey);
        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject root = new JSONObject(response);
            JSONObject current = root.getJSONObject("currentConditions");
            JSONArray days = root.getJSONArray("days");

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext,
                    /* id */ selection,
                    /* cityId */ city,
                    /* condition */ "",
                    /* conditionCode */ mapIconToCode(current.optString("icon", "")),
                    /* temperature */ (float) current.getDouble("temp"),
                    /* humidity */ (float) current.getDouble("humidity"),
                    /* wind */ (float) current.optDouble("windspeed", 0),
                    /* windDir */ (int) current.optDouble("winddir", 0),
                    metric,
                    parseForecasts(days, metric),
                    System.currentTimeMillis());

            if (current.has("feelslike")) {
                w.setFeelsLike((float) current.getDouble("feelslike"));
            }
            if (current.has("pressure") && !current.isNull("pressure")) {
                w.setPressure((float) current.getDouble("pressure"));
            }
            if (current.has("uvindex") && !current.isNull("uvindex")) {
                w.setUvi((float) current.getDouble("uvindex"));
            }
            if (current.has("visibility") && !current.isNull("visibility")) {
                // already km (metric) or miles (us)
                w.setVisibility((float) current.getDouble("visibility"));
            }
            if (current.has("dew") && !current.isNull("dew")) {
                w.setDewPoint((float) current.getDouble("dew"));
            }
            if (current.has("sunriseEpoch")) {
                w.setSunrise(current.getLong("sunriseEpoch") * 1000L);
            }
            if (current.has("sunsetEpoch")) {
                w.setSunset(current.getLong("sunsetEpoch") * 1000L);
            }

            w.setHourlyForecasts(parseHourlyForecasts(days, metric));

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection + ")", e);
        }
        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray days, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>();
        int count = Math.min(days.length(), MAX_FORECAST_DAYS);

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            try {
                JSONObject forecast = days.getJSONObject(i);
                result.add(new DayForecast(
                        /* low */ (float) forecast.getDouble("tempmin"),
                        /* high */ (float) forecast.getDouble("tempmax"),
                        /* condition */ "",
                        /* conditionCode */ mapIconToCode(forecast.optString("icon", "")),
                        getDay(i),
                        metric));
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i, e);
            }
        }
        // clients assume there are at least 5 entries - pad with dummies if needed
        if (result.size() < 5) {
            for (int i = result.size(); i < 5; i++) {
                Log.w(TAG, "Missing forecast for day " + i + " creating dummy");
                result.add(new DayForecast(0, 0, "", -1, "NaN", metric));
            }
        }
        return result;
    }

    private ArrayList<WeatherInfo.HourlyForecast> parseHourlyForecasts(JSONArray days, boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();
        // accept entries starting from the current (partially elapsed) hour
        long cutoffSec = (System.currentTimeMillis() / 1000L) - 3600L;

        try {
            for (int d = 0; d < days.length() && result.size() < MAX_HOURLY_ENTRIES; d++) {
                JSONArray hours = days.getJSONObject(d).optJSONArray("hours");
                if (hours == null) {
                    continue;
                }
                for (int i = 0; i < hours.length() && result.size() < MAX_HOURLY_ENTRIES; i++) {
                    JSONObject hour = hours.getJSONObject(i);
                    long ts = hour.optLong("datetimeEpoch", 0);
                    if (ts < cutoffSec) {
                        continue;
                    }
                    result.add(new WeatherInfo.HourlyForecast(
                            (float) hour.getDouble("temp"),
                            mapIconToCode(hour.optString("icon", "")),
                            "",
                            ts * 1000L,
                            (float) hour.optDouble("humidity", Double.NaN),
                            (float) hour.optDouble("windspeed", Double.NaN),
                            metric));
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "Invalid hourly forecast data", e);
        }
        return result;
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

    // Visual Crossing "icons2" set uses Dark Sky style icon names,
    // mapped the same way as PirateWeatherProvider
    private int mapIconToCode(String icon) {
        switch (icon) {
            case "clear-day":                return 32;
            case "clear-night":              return 31;
            case "partly-cloudy-day":        return 30;
            case "partly-cloudy-night":      return 29;
            case "cloudy":                   return 26;
            case "fog":                      return 20;
            case "wind":                     return 24;
            case "rain":
            case "showers-day":
            case "showers-night":            return 11;
            case "thunder":
            case "thunder-rain":             return 4;
            case "thunder-showers-day":      return 37;
            case "thunder-showers-night":    return 47;
            case "snow":                     return 16;
            case "snow-showers-day":
            case "snow-showers-night":       return 14;
            case "rain-snow":
            case "rain-snow-showers-day":
            case "rain-snow-showers-night":  return 5;
            case "sleet":                    return 18;
            case "hail":                     return 17;
            default:                         return -1;
        }
    }

    public boolean shouldRetry() {
        return false;
    }
}
