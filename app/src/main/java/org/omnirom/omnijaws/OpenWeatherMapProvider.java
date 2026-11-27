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

import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

public class OpenWeatherMapProvider extends AbstractWeatherProvider {
    private static final String TAG = "OpenWeatherMapProvider";

    private static final int FORECAST_DAYS = 5;
    private static final String URL_WEATHER =
            "https://api.openweathermap.org/data/3.0/onecall?%s&mode=json&units=%s&lang=%s&cnt=" + FORECAST_DAYS + "&appid=%s";
    private static final String URL_CURRENT =
            "https://api.openweathermap.org/data/2.5/weather?%s&units=%s&lang=%s&appid=%s";
    private static final String URL_FORECAST =
            "https://api.openweathermap.org/data/2.5/forecast?%s&units=%s&lang=%s&appid=%s";

    // Key that One Call 3.0 rejected, so later updates go straight to 2.5
    private static String sOneCallDeniedKey;

    private List<String> mKeys = new ArrayList<String>();

    public OpenWeatherMapProvider(Context context) {
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
        String apiKey = Config.getOwmKey(mContext);
        if (TextUtils.isEmpty(apiKey)) {
            log(TAG, "no API key provided");
            return null;
        }

        String units = metric ? "metric" : "imperial";
        String locale = getLanguageCode();
        if (apiKey.equals(sOneCallDeniedKey)) {
            return handleLegacyRequest(selection, units, locale, apiKey, metric);
        }
        String conditionUrl = String.format(Locale.US, URL_WEATHER, selection, units, locale, apiKey);
        String conditionResponse = retrieve(conditionUrl);
        if (conditionResponse == null) {
            if (NetworkUtils.getLastStatus() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                Log.w(TAG, "One Call 3.0 not available for this key, using 2.5");
                sOneCallDeniedKey = apiKey;
                return handleLegacyRequest(selection, units, locale, apiKey, metric);
            }
            return null;
        }
        log(TAG, "Condition URL = " + conditionUrl + " returning a response of " + conditionResponse);

        try {
            JSONObject conditions = new JSONObject(conditionResponse);
            JSONObject conditionData = conditions.getJSONObject("current");
            JSONObject weather = conditionData.getJSONArray("weather").getJSONObject(0);
            ArrayList<DayForecast> forecasts =
                    parseForecasts(conditions.getJSONArray("daily"),
                            conditions.optString("timezone"), metric);
            float windSpeed = (float) conditionData.getDouble("wind_speed");
            if (metric) {
                // speeds are in m/s so convert to our common metric unit km/h
                windSpeed *= 3.6f;
            }

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext, selection, city,
                    /* condition */ "",
                    /* conditionCode */ mapConditionIconToCode(
                    weather.getString("icon"), weather.getInt("id")),
                    /* temperature */ sanitizeTemperature(conditionData.getDouble("temp"), metric),
                    /* humidity */ (float) conditionData.getDouble("humidity"),
                    /* wind */ windSpeed,
                    /* windDir */ conditionData.has("wind_deg") ? conditionData.getInt("wind_deg") : 0,
                    metric,
                    forecasts,
                    System.currentTimeMillis());

            if (conditionData.has("feels_like")) {
                w.setFeelsLike(sanitizeTemperature(conditionData.getDouble("feels_like"), metric));
            }
            if (conditionData.has("pressure")) {
                w.setPressure((float) conditionData.getDouble("pressure"));
            }
            if (conditionData.has("uvi")) {
                w.setUvi((float) conditionData.getDouble("uvi"));
            }
            if (conditionData.has("visibility")) {
                float vis = (float) conditionData.getDouble("visibility");
                w.setVisibility(metric ? vis / 1000f : vis / 1609.34f);
            }
            if (conditionData.has("dew_point")) {
                w.setDewPoint(sanitizeTemperature(conditionData.getDouble("dew_point"), metric));
            }

            JSONArray dailyArray = conditions.getJSONArray("daily");
            if (dailyArray.length() > 0) {
                JSONObject today = dailyArray.getJSONObject(0);
                if (today.has("sunrise")) {
                    w.setSunrise(today.getLong("sunrise") * 1000L);
                }
                if (today.has("sunset")) {
                    w.setSunset(today.getLong("sunset") * 1000L);
                }
            }

            if (conditions.has("hourly")) {
                w.setHourlyForecasts(parseHourlyForecasts(conditions.getJSONArray("hourly"), metric));
            }

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed weather data (selection = " + selection
                    + ", lang = " + locale + ")", e);
        }

        return null;
    }

    private WeatherInfo handleLegacyRequest(String selection, String units, String locale,
            String apiKey, boolean metric) {
        String currentResponse = retrieve(
                String.format(Locale.US, URL_CURRENT, selection, units, locale, apiKey));
        if (currentResponse == null) {
            return null;
        }
        String forecastResponse = retrieve(
                String.format(Locale.US, URL_FORECAST, selection, units, locale, apiKey));
        if (forecastResponse == null) {
            return null;
        }

        try {
            JSONObject current = new JSONObject(currentResponse);
            JSONObject main = current.getJSONObject("main");
            JSONObject weather = current.getJSONArray("weather").getJSONObject(0);
            JSONObject wind = current.optJSONObject("wind");
            JSONArray list = new JSONObject(forecastResponse).getJSONArray("list");
            TimeZone zone = new SimpleTimeZone(current.optInt("timezone", 0) * 1000, "UTC");

            float temperature = sanitizeTemperature(main.getDouble("temp"), metric);
            float windSpeed = wind != null ? (float) wind.optDouble("speed", 0) : 0f;
            if (metric) {
                windSpeed *= 3.6f;
            }

            String city = getWeatherDataLocality(selection);

            WeatherInfo w = new WeatherInfo(mContext, selection, city,
                    /* condition */ "",
                    /* conditionCode */ mapConditionIconToCode(
                    weather.getString("icon"), weather.getInt("id")),
                    /* temperature */ temperature,
                    /* humidity */ (float) main.getDouble("humidity"),
                    /* wind */ windSpeed,
                    /* windDir */ wind != null ? wind.optInt("deg", 0) : 0,
                    metric,
                    parseLegacyForecasts(list, zone, temperature, metric),
                    System.currentTimeMillis());

            if (main.has("feels_like")) {
                w.setFeelsLike(sanitizeTemperature(main.getDouble("feels_like"), metric));
            }
            if (main.has("pressure")) {
                w.setPressure((float) main.getDouble("pressure"));
            }
            if (current.has("visibility")) {
                float vis = (float) current.getDouble("visibility");
                w.setVisibility(metric ? vis / 1000f : vis / 1609.34f);
            }
            JSONObject sys = current.optJSONObject("sys");
            if (sys != null && sys.has("sunrise") && sys.has("sunset")) {
                w.setSunrise(sys.getLong("sunrise") * 1000L);
                w.setSunset(sys.getLong("sunset") * 1000L);
            }
            w.setHourlyForecasts(parseLegacyHourlyForecasts(list, metric));

            log(TAG, "Weather updated: " + w);
            return w;
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed 2.5 weather data (selection = " + selection
                    + ", lang = " + locale + ")", e);
        }
        return null;
    }

    // 2.5 has 3-hourly steps only: build days from their range, with the
    // condition of the step closest to noon
    private ArrayList<DayForecast> parseLegacyForecasts(JSONArray list, TimeZone zone,
            float currentTemp, boolean metric) throws JSONException {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(zone);
        Calendar cal = Calendar.getInstance(zone);
        String today = fmt.format(new Date());

        // day -> {low, high, condition code, hours from noon}
        LinkedHashMap<String, float[]> days = new LinkedHashMap<>();
        for (int i = 0; i < list.length(); i++) {
            try {
                JSONObject entry = list.getJSONObject(i);
                long timeMs = entry.getLong("dt") * 1000L;
                JSONObject main = entry.getJSONObject("main");
                JSONObject data = entry.getJSONArray("weather").getJSONObject(0);
                String day = fmt.format(new Date(timeMs));
                float[] d = days.get(day);
                if (d == null) {
                    d = new float[] { Float.MAX_VALUE, -Float.MAX_VALUE, -1, Float.MAX_VALUE };
                    if (day.equals(today)) {
                        d[0] = currentTemp;
                        d[1] = currentTemp;
                    }
                    days.put(day, d);
                }
                d[0] = Math.min(d[0], sanitizeTemperature(main.getDouble("temp_min"), metric));
                d[1] = Math.max(d[1], sanitizeTemperature(main.getDouble("temp_max"), metric));
                cal.setTimeInMillis(timeMs);
                int fromNoon = Math.abs(cal.get(Calendar.HOUR_OF_DAY) - 12);
                if (fromNoon < d[3]) {
                    d[2] = mapConditionIconToCode(data.getString("icon"), data.getInt("id"));
                    d[3] = fromNoon;
                }
            } catch (JSONException e) {
                Log.w(TAG, "Invalid 2.5 forecast for index " + i, e);
            }
        }
        if (days.isEmpty()) {
            throw new JSONException("Empty forecasts array");
        }

        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        for (Map.Entry<String, float[]> entry : days.entrySet()) {
            if (result.size() == FORECAST_DAYS) {
                break;
            }
            float[] d = entry.getValue();
            result.add(new DayForecast(d[0], d[1], "", (int) d[2], entry.getKey(), metric));
        }
        return result;
    }

    private ArrayList<WeatherInfo.HourlyForecast> parseLegacyHourlyForecasts(JSONArray list,
            boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();
        int count = Math.min(list.length(), 8); // 3-hour steps
        for (int i = 0; i < count; i++) {
            try {
                JSONObject entry = list.getJSONObject(i);
                JSONObject main = entry.getJSONObject("main");
                JSONObject data = entry.getJSONArray("weather").getJSONObject(0);
                JSONObject wind = entry.optJSONObject("wind");
                float windSpeed = wind != null ? (float) wind.optDouble("speed", 0) : 0f;
                if (metric) {
                    windSpeed *= 3.6f;
                }
                result.add(new WeatherInfo.HourlyForecast(
                        sanitizeTemperature(main.getDouble("temp"), metric),
                        mapConditionIconToCode(data.getString("icon"), data.getInt("id")),
                        "",
                        entry.getLong("dt") * 1000L,
                        (float) main.getDouble("humidity"),
                        windSpeed,
                        metric));
            } catch (JSONException e) {
                Log.w(TAG, "Invalid 2.5 hourly forecast for index " + i, e);
            }
        }
        return result;
    }

    private ArrayList<DayForecast> parseForecasts(JSONArray forecasts, String timeZone,
            boolean metric) throws JSONException {
        ArrayList<DayForecast> result = new ArrayList<DayForecast>();
        int count = forecasts.length();

        if (count == 0) {
            throw new JSONException("Empty forecasts array");
        }
        for (int i = 0; i < count; i++) {
            DayForecast item = null;
            try {
                JSONObject forecast = forecasts.getJSONObject(i);
                String day = getDay(i, forecast.optLong("dt"), timeZone);
                JSONObject conditionData = forecast.getJSONObject("temp");
                JSONObject data = forecast.getJSONArray("weather").getJSONObject(0);
                item = new DayForecast(
                        /* low */ sanitizeTemperature(conditionData.getDouble("min"), metric),
                        /* high */ sanitizeTemperature(conditionData.getDouble("max"), metric),
                        /* condition */ "",
                        /* conditionCode */ mapConditionIconToCode(
                        data.getString("icon"), data.getInt("id")),
                        day,
                        metric);
            } catch (JSONException e) {
                Log.w(TAG, "Invalid forecast for day " + i, e);
                continue;
            }
            result.add(item);
        }
        // clients assume there are 5  entries - so fill with dummy if needed
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

    private ArrayList<WeatherInfo.HourlyForecast> parseHourlyForecasts(JSONArray hourly, boolean metric) {
        ArrayList<WeatherInfo.HourlyForecast> result = new ArrayList<>();
        int count = Math.min(hourly.length(), 24);
        for (int i = 0; i < count; i++) {
            try {
                JSONObject hour = hourly.getJSONObject(i);
                JSONObject hourWeather = hour.getJSONArray("weather").getJSONObject(0);
                float hWindSpeed = (float) hour.getDouble("wind_speed");
                if (metric) {
                    hWindSpeed *= 3.6f;
                }
                result.add(new WeatherInfo.HourlyForecast(
                        sanitizeTemperature(hour.getDouble("temp"), metric),
                        mapConditionIconToCode(hourWeather.getString("icon"), hourWeather.getInt("id")),
                        "",
                        hour.getLong("dt") * 1000L,
                        (float) hour.getDouble("humidity"),
                        hWindSpeed,
                        metric));
            } catch (JSONException e) {
                Log.w(TAG, "Invalid hourly forecast for index " + i, e);
            }
        }
        return result;
    }

    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private static float sanitizeTemperature(double value, boolean metric) {
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (value > 170) {
            // K -> deg C
            value -= 273.15;
            if (!metric) {
                // deg C -> deg F
                value = (value * 1.8) + 32;
            }
        }
        return (float) value;
    }

    private static final HashMap<String, String> LANGUAGE_CODE_MAPPING = new HashMap<String, String>();

    static {
        LANGUAGE_CODE_MAPPING.put("bg-", "bg");
        LANGUAGE_CODE_MAPPING.put("de-", "de");
        LANGUAGE_CODE_MAPPING.put("es-", "sp");
        LANGUAGE_CODE_MAPPING.put("fi-", "fi");
        LANGUAGE_CODE_MAPPING.put("fr-", "fr");
        LANGUAGE_CODE_MAPPING.put("it-", "it");
        LANGUAGE_CODE_MAPPING.put("nl-", "nl");
        LANGUAGE_CODE_MAPPING.put("pl-", "pl");
        LANGUAGE_CODE_MAPPING.put("pt-", "pt");
        LANGUAGE_CODE_MAPPING.put("ro-", "ro");
        LANGUAGE_CODE_MAPPING.put("ru-", "ru");
        LANGUAGE_CODE_MAPPING.put("se-", "se");
        LANGUAGE_CODE_MAPPING.put("tr-", "tr");
        LANGUAGE_CODE_MAPPING.put("uk-", "ua");
        LANGUAGE_CODE_MAPPING.put("zh-CN", "zh_cn");
        LANGUAGE_CODE_MAPPING.put("zh-TW", "zh_tw");
    }

    private String getLanguageCode() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String selector = locale.getLanguage() + "-" + locale.getCountry();

        for (Map.Entry<String, String> entry : LANGUAGE_CODE_MAPPING.entrySet()) {
            if (selector.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "en";
    }

    private int mapConditionIconToCode(String icon, int conditionId) {

        // First, use condition ID for specific cases
        switch (conditionId) {
            // Thunderstorms
            case 202:   // thunderstorm with heavy rain
            case 232:   // thunderstorm with heavy drizzle
            case 211:   // thunderstorm
                return 4;
            case 212:   // heavy thunderstorm
                return 3;
            case 221:   // ragged thunderstorm
            case 231:   // thunderstorm with drizzle
            case 201:   // thunderstorm with rain
                return 38;
            case 230:   // thunderstorm with light drizzle
            case 200:   // thunderstorm with light rain
            case 210:   // light thunderstorm
                return 37;

            // Drizzle
            case 300:    // light intensity drizzle
            case 301:    // drizzle
            case 302:    // heavy intensity drizzle
            case 310:    // light intensity drizzle rain
            case 311:    // drizzle rain
            case 312:    // heavy intensity drizzle rain
            case 313:    // shower rain and drizzle
            case 314:    // heavy shower rain and drizzle
            case 321:    // shower drizzle
                return 9;

            // Rain
            case 500:    // light rain
            case 501:    // moderate rain
            case 520:    // light intensity shower rain
            case 521:    // shower rain
            case 531:    // ragged shower rain
                return 11;
            case 502:    // heavy intensity rain
            case 503:    // very heavy rain
            case 504:    // extreme rain
            case 522:    // heavy intensity shower rain
                return 12;
            case 511:    // freezing rain
                return 10;

            // Snow
            case 600:
            case 620:
                return 14; // light snow
            case 601:
            case 621:
                return 16; // snow
            case 602:
            case 622:
                return 41; // heavy snow
            case 611:
            case 612:
            case 613:
                return 18; // sleet (incl. shower sleet)
            case 615:
            case 616:
                return 5;  // rain and snow

            // Atmosphere
            case 741:    // fog
                return 20;
            case 711:    // smoke
            case 762:    // volcanic ash
                return 22;
            case 701:    // mist
            case 721:    // haze
                return 21;
            case 731:    // sand/dust whirls
            case 751:    // sand
            case 761:    // dust
                return 19;
            case 771:    // squalls
                return 23;
            case 781:    // tornado
                return 0;

            // clouds
            case 800:     // clear sky
                return icon.endsWith("n") ? 31 : 32; // day or night
            case 801:     // few clouds
                return icon.endsWith("n") ? 33 : 34; // day or night
            case 802:     // scattered clouds
                return icon.endsWith("n") ? 29 : 30; // day or night
            case 803:     // broken clouds
                return icon.endsWith("n") ? 27 : 28; // day or night
            case 804:     // overcast clouds
                return 26;

            // Extreme
            case 900:
                return 0;  // tornado
            case 901:
                return 1;  // tropical storm
            case 902:
                return 2;  // hurricane
            case 903:
                return 25; // cold
            case 904:
                return 36; // hot
            case 905:
                return 24; // windy
            case 906:
                return 17; // hail
        }

        return -1;
    }

    public boolean shouldRetry() {
        return false;
    }
}
