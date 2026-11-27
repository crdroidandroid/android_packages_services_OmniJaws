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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import android.content.Context;
import android.location.Location;
import android.util.Log;

public class OpenMeteoProvider extends AbstractWeatherProvider {
    private static final String TAG = "OpenMeteoProvider";

    private static final int MIN_FORECAST_DAYS = 5;
    private static final int HOURLY_FORECAST_COUNT = 24;
    private static final long ONE_HOUR_SEC = 3600L;

    private static final double SNOW_WET_BULB_MAX_C = 1.0;

    private static final String URL_WEATHER =
            "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f"
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
            + "weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,is_day"
            + "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,visibility,"
            + "uv_index,weather_code,wind_speed_10m,is_day,precipitation_probability,precipitation"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
            + "precipitation_probability_max,precipitation_sum"
            + "&timeformat=unixtime&timezone=auto&forecast_days=7"
            + "&temperature_unit=%s&wind_speed_unit=%s&precipitation_unit=%s";

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
        String precipUnit = metric ? "mm" : "inch";
        String url = String.format(Locale.US, URL_WEATHER,
                coords[0], coords[1], tempUnit, windUnit, precipUnit);

        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject root = new JSONObject(response);

            if (root.optBoolean("error", false)) {
                Log.w(TAG, "API error: " + root.optString("reason", "unknown"));
                return null;
            }

            JSONObject current = root.getJSONObject("current");
            JSONObject hourly = root.optJSONObject("hourly");
            JSONObject daily = root.getJSONObject("daily");

            boolean isDay = current.optInt("is_day", 1) == 1;
            float currentTemp = (float) current.getDouble("temperature_2m");
            float currentHumidity = (float) current.optDouble("relative_humidity_2m", Double.NaN);
            int weatherCode = snowIfCold(current.optInt("weather_code", -1),
                    toCelsius(currentTemp, metric), currentHumidity);
            String tzId = root.optString("timezone", "");
            TimeZone zone = tzId.isEmpty()
                    ? new SimpleTimeZone(root.optInt("utc_offset_seconds", 0) * 1000, "UTC")
                    : TimeZone.getTimeZone(tzId);

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext,
                    /* id */ selection,
                    /* cityId */ city,
                    /* condition */ "",
                    /* conditionCode */ mapWmoToCode(weatherCode, isDay),
                    /* temperature */ currentTemp,
                    /* humidity */ currentHumidity,
                    /* wind */ (float) current.optDouble("wind_speed_10m", Double.NaN),
                    /* windDir */ (int) current.optDouble("wind_direction_10m", 0),
                    metric,
                    parseForecasts(daily, zone, metric),
                    System.currentTimeMillis());

            if (current.has("apparent_temperature")) {
                w.setFeelsLike((float) current.getDouble("apparent_temperature"));
            }
            if (current.has("pressure_msl")) {
                w.setPressure((float) current.getDouble("pressure_msl"));
            }

            JSONArray hTimes = hourly != null ? hourly.optJSONArray("time") : null;
            if (hTimes != null && hTimes.length() > 0) {
                final long nowSec = System.currentTimeMillis() / 1000L;
                int idx = nearestHourIndex(hTimes, nowSec);
                w.setDewPoint(floatAt(hourly, "dew_point_2m", idx));
                w.setUvi(floatAt(hourly, "uv_index", idx));
                float visMeters = floatAt(hourly, "visibility", idx);
                if (!Float.isNaN(visMeters)) {
                    w.setVisibility(metric ? visMeters / 1000f : visMeters / 1609.34f);
                }
                int startIdx = firstIndexAtOrAfter(hTimes, nowSec - ONE_HOUR_SEC);
                w.setHourlyForecasts(parseHourlyForecasts(hourly, startIdx, metric));
            }

            JSONArray sunrise = daily.optJSONArray("sunrise");
            JSONArray sunset = daily.optJSONArray("sunset");
            if (sunrise != null && sunrise.length() > 0) {
                w.setSunrise(sunrise.optLong(0) * 1000L);
            }
            if (sunset != null && sunset.length() > 0) {
                w.setSunset(sunset.optLong(0) * 1000L);
            }

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection + ")", e);
        }
        return null;
    }

    private ArrayList<DayForecast> parseForecasts(JSONObject daily, TimeZone zone, boolean metric)
            throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<>();
        JSONArray times = daily.optJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");

        int count = Math.min(codes.length(), Math.min(highs.length(), lows.length()));
        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }

        for (int i = 0; i < count; i++) {
            try {
                if (codes.isNull(i) || highs.isNull(i) || lows.isNull(i)) {
                    Log.w(TAG, "Incomplete forecast for day " + i);
                    continue;
                }
                float high = (float) highs.getDouble(i);
                int code = snowIfCold(codes.getInt(i), toCelsius(high, metric), 100f);
                result.add(new DayForecast(
                        /* low */ (float) lows.getDouble(i),
                        /* high */ high,
                        /* condition */ "",
                        /* conditionCode */ mapWmoToCode(code, true),
                        dayAt(times, i, zone),
                        metric).setPrecipitation(
                        floatAt(daily, "precipitation_probability_max", i),
                        floatAt(daily, "precipitation_sum", i)));
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

    private ArrayList<WeatherInfo.HourlyForecast> parseHourlyForecasts(
            JSONObject hourly, int startIdx, boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();

        JSONArray times = hourly.optJSONArray("time");
        JSONArray temps = hourly.optJSONArray("temperature_2m");
        JSONArray codes = hourly.optJSONArray("weather_code");
        if (times == null || temps == null || codes == null) {
            Log.w(TAG, "Missing hourly arrays");
            return result;
        }
        JSONArray hums = hourly.optJSONArray("relative_humidity_2m");
        JSONArray winds = hourly.optJSONArray("wind_speed_10m");
        JSONArray isDays = hourly.optJSONArray("is_day");

        int end = Math.min(Math.min(times.length(), Math.min(temps.length(), codes.length())),
                startIdx + HOURLY_FORECAST_COUNT);

        for (int i = Math.max(startIdx, 0); i < end; i++) {
            try {
                if (times.isNull(i) || temps.isNull(i)) {
                    continue;
                }
                float temp = (float) temps.getDouble(i);
                float humidity = hums != null ? (float) hums.optDouble(i, Double.NaN) : Float.NaN;
                int code = codes.isNull(i) ? -1
                        : snowIfCold(codes.getInt(i), toCelsius(temp, metric), humidity);
                boolean isDay = isDays == null || isDays.optInt(i, 1) == 1;
                result.add(new WeatherInfo.HourlyForecast(
                        temp,
                        mapWmoToCode(code, isDay),
                        "",
                        times.getLong(i) * 1000L,
                        humidity,
                        winds != null ? (float) winds.optDouble(i, Double.NaN) : Float.NaN,
                        metric).setPrecipitation(
                        floatAt(hourly, "precipitation_probability", i),
                        floatAt(hourly, "precipitation", i)));
            } catch (JSONException e) {
                Log.w(TAG, "Invalid hourly forecast for index " + i, e);
            }
        }
        return result;
    }

    private static double[] parseCoords(String selection) {
        try {
            int latStart = selection.indexOf("lat=");
            int lonStart = selection.indexOf("lon=");
            if (latStart == -1 || lonStart == -1) {
                return null;
            }
            latStart += 4;
            int latEnd = selection.indexOf("&", latStart);
            if (latEnd == -1) {
                latEnd = selection.length();
            }
            double lat = Double.parseDouble(selection.substring(latStart, latEnd));
            double lon = Double.parseDouble(selection.substring(lonStart + 4));
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
            } else {
                break;
            }
        }
        return idx;
    }

    private static int firstIndexAtOrAfter(JSONArray times, long fromSec) {
        for (int i = 0; i < times.length(); i++) {
            if (times.optLong(i, Long.MIN_VALUE) >= fromSec) {
                return i;
            }
        }
        return Math.max(times.length() - 1, 0);
    }

    private static float floatAt(JSONObject obj, String key, int idx) {
        JSONArray arr = obj.optJSONArray(key);
        if (arr == null || idx < 0 || idx >= arr.length() || arr.isNull(idx)) {
            return Float.NaN;
        }
        return (float) arr.optDouble(idx, Double.NaN);
    }

    private String dayAt(JSONArray times, int i, TimeZone zone) {
        if (times != null && i < times.length() && !times.isNull(i)) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            fmt.setTimeZone(zone);
            return fmt.format(new Date(times.optLong(i) * 1000L));
        }
        return getDay(i);
    }

    private static double toCelsius(float value, boolean metric) {
        return metric ? value : (value - 32.0) / 1.8;
    }

    static double wetBulb(double t, double rh) {
        rh = Math.max(5.0, Math.min(99.0, rh));
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
                + Math.atan(t + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh)
                - 4.686035;
    }

    static int snowIfCold(int wmo, double tempC, float humidity) {
        if (Double.isNaN(tempC)) {
            return wmo;
        }
        double tw = Float.isNaN(humidity) || humidity >= 100f ? tempC : wetBulb(tempC, humidity);
        if (tw > SNOW_WET_BULB_MAX_C) {
            return wmo;
        }
        switch (wmo) {
            case 51:
            case 53:
            case 61: return 71; // light drizzle / rain -> slight snow
            case 55:
            case 63: return 73; // dense drizzle / moderate rain -> moderate snow
            case 65: return 75; // heavy rain -> heavy snow
            case 80: return 85; // slight showers -> slight snow showers
            case 81:
            case 82: return 86; // moderate / violent showers -> heavy snow showers
            default: return wmo;
        }
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

    public boolean shouldRetry() {
        return false;
    }
}
