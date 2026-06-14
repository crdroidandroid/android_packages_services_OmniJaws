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
import android.util.Log;

public class OpenMeteoProvider extends AbstractWeatherProvider {
    private static final String TAG = "OpenMeteoProvider";

    private static final String URL_WEATHER =
            "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,is_day"
            + "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,visibility,"
            + "uv_index,weather_code,wind_speed_10m,is_day"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&timeformat=unixtime&timezone=auto&forecast_days=7"
            + "&temperature_unit=%s&wind_speed_unit=%s";

    public OpenMeteoProvider(Context context) {
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
        double[] coords = parseCoords(selection);
        if (coords == null) {
            Log.w(TAG, "could not parse coordinates from '" + selection + "'");
            return null;
        }

        String tempUnit = metric ? "celsius" : "fahrenheit";
        String windUnit = metric ? "kmh" : "mph";
        String url = String.format(Locale.US, URL_WEATHER,
                coords[0], coords[1], tempUnit, windUnit);

        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject root = new JSONObject(response);
            JSONObject current = root.getJSONObject("current");
            JSONObject hourly = root.getJSONObject("hourly");
            JSONObject daily = root.getJSONObject("daily");

            boolean isDay = current.optInt("is_day", 1) == 1;
            int weatherCode = current.getInt("weather_code");

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext,
                    /* id */ selection,
                    /* cityId */ city,
                    /* condition */ wmoDescription(weatherCode),
                    /* conditionCode */ mapWmoToCode(weatherCode, isDay),
                    /* temperature */ (float) current.getDouble("temperature_2m"),
                    /* humidity */ (float) current.getDouble("relative_humidity_2m"),
                    /* wind */ (float) current.getDouble("wind_speed_10m"),
                    /* windDir */ current.optInt("wind_direction_10m", 0),
                    metric,
                    parseForecasts(daily, metric),
                    System.currentTimeMillis());

            if (current.has("apparent_temperature")) {
                w.setFeelsLike((float) current.getDouble("apparent_temperature"));
            }
            if (current.has("pressure_msl")) {
                w.setPressure((float) current.getDouble("pressure_msl"));
            }

            JSONArray hTimes = hourly.optJSONArray("time");
            if (hTimes != null && hTimes.length() > 0) {
                int idx = nearestHourIndex(hTimes, System.currentTimeMillis() / 1000L);
                w.setDewPoint(floatAt(hourly, "dew_point_2m", idx));
                w.setUvi(floatAt(hourly, "uv_index", idx));
                float visMeters = floatAt(hourly, "visibility", idx);
                if (!Float.isNaN(visMeters)) {
                    w.setVisibility(metric ? visMeters / 1000f : visMeters / 1609.34f);
                }
                w.setHourlyForecasts(parseHourlyForecasts(hourly, idx, metric));
            }

            JSONArray sunrise = daily.optJSONArray("sunrise");
            JSONArray sunset = daily.optJSONArray("sunset");
            if (sunrise != null && sunrise.length() > 0) {
                w.setSunrise(sunrise.getLong(0) * 1000L);
            }
            if (sunset != null && sunset.length() > 0) {
                w.setSunset(sunset.getLong(0) * 1000L);
            }

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection + ")", e);
        }
        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONObject daily, boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>();
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        int count = codes.length();

        for (int i = 0; i < count; i++) {
            try {
                int code = codes.getInt(i);
                result.add(new DayForecast(
                        /* low */ (float) lows.getDouble(i),
                        /* high */ (float) highs.getDouble(i),
                        /* condition */ wmoDescription(code),
                        /* conditionCode */ mapWmoToCode(code, true),
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

    private ArrayList<WeatherInfo.HourlyForecast> parseHourlyForecasts(
            JSONObject hourly, int startIdx, boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();
        try {
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray codes = hourly.getJSONArray("weather_code");
            JSONArray hums = hourly.optJSONArray("relative_humidity_2m");
            JSONArray winds = hourly.optJSONArray("wind_speed_10m");
            JSONArray isDays = hourly.optJSONArray("is_day");

            int end = Math.min(times.length(), startIdx + 24);
            for (int i = startIdx; i < end; i++) {
                int code = codes.getInt(i);
                boolean isDay = isDays == null || isDays.optInt(i, 1) == 1;
                result.add(new WeatherInfo.HourlyForecast(
                        (float) temps.getDouble(i),
                        mapWmoToCode(code, isDay),
                        wmoDescription(code),
                        times.getLong(i) * 1000L,
                        hums != null ? (float) hums.optDouble(i, Double.NaN) : Float.NaN,
                        winds != null ? (float) winds.optDouble(i, Double.NaN) : Float.NaN,
                        metric));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Invalid hourly forecast data", e);
        }
        return result;
    }

    private static double[] parseCoords(String selection) {
        try {
            int latStart = selection.indexOf("lat=") + 4;
            int amp = selection.indexOf("&", latStart);
            int lonStart = selection.indexOf("lon=") + 4;
            double lat = Double.parseDouble(selection.substring(latStart, amp));
            double lon = Double.parseDouble(selection.substring(lonStart));
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    private static int nearestHourIndex(JSONArray times, long nowSec) {
        int idx = 0;
        long best = Long.MAX_VALUE;
        for (int i = 0; i < times.length(); i++) {
            long diff = Math.abs(times.optLong(i) - nowSec);
            if (diff < best) {
                best = diff;
                idx = i;
            }
        }
        return idx;
    }

    private static float floatAt(JSONObject obj, String key, int idx) {
        JSONArray arr = obj.optJSONArray(key);
        if (arr == null || idx < 0 || idx >= arr.length() || arr.isNull(idx)) {
            return Float.NaN;
        }
        return (float) arr.optDouble(idx, Double.NaN);
    }

    private static int mapWmoToCode(int wmo, boolean isDay) {
        switch (wmo) {
            case 0:  return isDay ? 32 : 31; // clear
            case 1:  return isDay ? 34 : 33; // mainly clear
            case 2:  return isDay ? 30 : 29; // partly cloudy
            case 3:  return 26;              // overcast
            case 45:
            case 48: return 20;              // fog
            case 51:
            case 53:
            case 55: return 9;               // drizzle
            case 56:
            case 57:
            case 66:
            case 67: return 18;              // freezing drizzle / freezing rain -> sleet
            case 61:
            case 63:
            case 80:
            case 81: return 11;              // rain / showers
            case 65:
            case 82: return 12;              // heavy rain
            case 71:
            case 85: return 14;              // light snow (showers)
            case 73:
            case 75:
            case 77:
            case 86: return 16;              // snow
            case 95:
            case 96:
            case 99: return 4;               // thunderstorm
            default: return -1;
        }
    }

    private static String wmoDescription(int wmo) {
        switch (wmo) {
            case 0:  return "Clear";
            case 1:  return "Mainly clear";
            case 2:  return "Partly cloudy";
            case 3:  return "Overcast";
            case 45: return "Fog";
            case 48: return "Depositing rime fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Dense drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66:
            case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Violent showers";
            case 85: return "Light snow showers";
            case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96:
            case 99: return "Thunderstorm with hail";
            default: return "";
        }
    }

    public boolean shouldRetry() {
        return false;
    }
}
