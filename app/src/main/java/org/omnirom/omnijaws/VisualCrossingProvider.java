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
    private static final int MIN_FORECAST_DAYS = 5;
    private static final int MAX_HOURLY_ENTRIES = 24;

    private static final String ELEMENTS =
            "datetime,datetimeEpoch,temp,tempmax,tempmin,feelslike,humidity,dew,"
            + "pressure,windspeed,winddir,uvindex,visibility,icon,sunriseEpoch,sunsetEpoch";

    // Timeline API: one call returns current conditions, daily forecast,
    // hourly forecast and astro data. unitGroup=metric -> degC / km/h / km,
    // unitGroup=us -> degF / mph / miles, so no unit conversion is needed.
    private static final String URL_WEATHER =
            "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/"
            + "%s/next" + MAX_FORECAST_DAYS + "days?unitGroup=%s"
            + "&include=current,days,hours&iconSet=icons2&contentType=json"
            + "&elements=" + ELEMENTS + "&key=%s";

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

        String trimmed = response.trim();
        if (!trimmed.startsWith("{")) {
            Log.w(TAG, "API returned a non JSON response: "
                    + trimmed.substring(0, Math.min(trimmed.length(), 200)));
            return null;
        }

        try {
            JSONObject root = new JSONObject(trimmed);
            JSONObject current = root.getJSONObject("currentConditions");
            JSONArray days = root.optJSONArray("days");
            if (days == null || days.length() == 0) {
                Log.w(TAG, "no daily data in response");
                return null;
            }
            JSONObject today = days.getJSONObject(0);

            if (root.has("queryCost")) {
                log(TAG, "queryCost = " + root.optInt("queryCost", -1));
            }

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext,
                    /* id */ selection,
                    /* cityId */ city,
                    /* condition */ "",
                    /* conditionCode */ mapIconToCode(current.optString("icon", "")),
                    /* temperature */ (float) current.getDouble("temp"),
                    /* humidity */ (float) current.optDouble("humidity", Double.NaN),
                    /* wind */ (float) current.optDouble("windspeed", Double.NaN),
                    /* windDir */ (int) current.optDouble("winddir", 0),
                    metric,
                    parseForecasts(days, metric),
                    System.currentTimeMillis());

            Float feelsLike = optFloat(current, "feelslike");
            if (feelsLike != null) {
                w.setFeelsLike(feelsLike);
            }
            Float pressure = optFloat(current, "pressure");
            if (pressure != null) {
                w.setPressure(pressure);
            }
            Float uvi = optFloat(current, "uvindex");
            if (uvi != null) {
                w.setUvi(uvi);
            }
            Float visibility = optFloat(current, "visibility");
            if (visibility != null) {
                // already km (metric) or miles (us)
                w.setVisibility(visibility);
            }
            Float dew = optFloat(current, "dew");
            if (dew != null) {
                w.setDewPoint(dew);
            }

            long sunrise = optEpochMillis(current, "sunriseEpoch");
            if (sunrise == 0L) {
                sunrise = optEpochMillis(today, "sunriseEpoch");
            }
            long sunset = optEpochMillis(current, "sunsetEpoch");
            if (sunset == 0L) {
                sunset = optEpochMillis(today, "sunsetEpoch");
            }
            if (sunrise > 0L) {
                w.setSunrise(sunrise);
            }
            if (sunset > 0L) {
                w.setSunset(sunset);
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
                if (forecast.isNull("tempmin") || forecast.isNull("tempmax")) {
                    Log.w(TAG, "Incomplete forecast for day " + i);
                    continue;
                }
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
        if (result.size() < MIN_FORECAST_DAYS) {
            for (int i = result.size(); i < MIN_FORECAST_DAYS; i++) {
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

        for (int d = 0; d < days.length() && result.size() < MAX_HOURLY_ENTRIES; d++) {
            JSONArray hours;
            try {
                hours = days.getJSONObject(d).optJSONArray("hours");
            } catch (JSONException e) {
                Log.w(TAG, "Invalid day entry at index " + d, e);
                continue;
            }
            if (hours == null) {
                continue;
            }
            for (int i = 0; i < hours.length() && result.size() < MAX_HOURLY_ENTRIES; i++) {
                // one bad hour must not discard the remaining ones
                try {
                    JSONObject hour = hours.getJSONObject(i);
                    long ts = hour.optLong("datetimeEpoch", 0);
                    if (ts < cutoffSec || hour.isNull("temp")) {
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
                } catch (JSONException e) {
                    Log.w(TAG, "Invalid hourly forecast at day " + d + " index " + i, e);
                }
            }
        }
        return result;
    }

    private static Float optFloat(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) {
            return null;
        }
        double value = obj.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? null : (float) value;
    }

    private static long optEpochMillis(JSONObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.isNull(key)) {
            return 0L;
        }
        return obj.optLong(key, 0L) * 1000L;
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

    // Visual Crossing "icons2" set uses Dark Sky style icon names,
    // mapped the same way as PirateWeatherProvider
    private int mapIconToCode(String icon) {
        if (TextUtils.isEmpty(icon)) {
            return -1;
        }
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
            default:
                Log.w(TAG, "unknown icon '" + icon + "'");
                return -1;
        }
    }

    public boolean shouldRetry() {
        return false;
    }
}
