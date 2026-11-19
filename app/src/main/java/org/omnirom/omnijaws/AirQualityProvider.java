/*
 * Copyright (C) 2026 crDroid Android project
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

import android.util.Log;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Air quality from the Open-Meteo Air Quality API (CAMS based, global, no key).
 * Fetched for every weather provider. The index follows the local scale like
 * other weather apps do: India's National AQI (CPCB) in India, US AQI elsewhere.
 */
public class AirQualityProvider {
    private static final String TAG = "AirQualityProvider";
    private static final boolean DEBUG = false;

    public static final String SCALE_US = "us";
    public static final String SCALE_IN = "in";

    private static final String URL_AIR_QUALITY =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%f&longitude=%f"
            + "&current=us_aqi,pm2_5,pm10,nitrogen_dioxide,sulphur_dioxide,ozone,"
            + "carbon_monoxide,ammonia&timezone=auto";

    private static boolean isIndia(String timezone) {
        return "Asia/Kolkata".equals(timezone) || "Asia/Calcutta".equals(timezone);
    }

    // CPCB National AQI breakpoints (ug/m3, CO in mg/m3) for the index bands
    // 0-50-100-200-300-400-500. The last concentration caps the open ended
    // Severe band. Evaluated on current concentrations, so the index follows the
    // present conditions instead of trailing a 24 h / 8 h average.
    private static final int[] NAQI_INDEX = {0, 50, 100, 200, 300, 400, 500};
    private static final double[] NAQI_PM25 = {0, 30, 60, 90, 120, 250, 380};
    private static final double[] NAQI_PM10 = {0, 50, 100, 250, 350, 430, 600};
    private static final double[] NAQI_NO2 = {0, 40, 80, 180, 280, 400, 800};
    private static final double[] NAQI_SO2 = {0, 40, 80, 380, 800, 1600, 2400};
    private static final double[] NAQI_O3 = {0, 50, 100, 168, 208, 748, 1000};
    private static final double[] NAQI_CO = {0, 1, 2, 10, 17, 34, 50};
    private static final double[] NAQI_NH3 = {0, 200, 400, 800, 1200, 1800, 2400};

    /** Fills in the AQI and particulates of {@code w}; leaves them unset on failure. */
    public static void fetchInto(WeatherInfo w, double latitude, double longitude) {
        if (w == null) {
            return;
        }
        String url = String.format(Locale.US, URL_AIR_QUALITY, latitude, longitude);
        String response = NetworkUtils.downloadUrlMemoryAsString(url);
        if (response == null) {
            Log.w(TAG, "no air quality data");
            return;
        }
        if (DEBUG) Log.d(TAG, "URL = " + url + " returning a response of " + response);

        try {
            JSONObject root = new JSONObject(response);
            JSONObject current = root.getJSONObject("current");
            float pm25 = getValue(current, "pm2_5");
            float pm10 = getValue(current, "pm10");

            int aqi = -1;
            String scale = SCALE_US;
            if (isIndia(root.optString("timezone"))) {
                aqi = computeNaqi(current, pm25, pm10);
                scale = SCALE_IN;
            }
            if (aqi < 0) {
                aqi = current.isNull("us_aqi") ? -1 : (int) Math.round(current.getDouble("us_aqi"));
                scale = SCALE_US;
            }
            w.setAirQuality(aqi, scale, pm25, pm10);
        } catch (Exception e) {
            Log.w(TAG, "Received malformed air quality data", e);
        }
    }

    private static float getValue(JSONObject o, String key) {
        return o.isNull(key) ? Float.NaN : (float) o.optDouble(key, Double.NaN);
    }

    /**
     * Highest sub-index of the available pollutants. CPCB needs at least three
     * of them including PM2.5 or PM10; returns -1 otherwise.
     */
    private static int computeNaqi(JSONObject current, float pm25, float pm10) {
        if (Float.isNaN(pm25) && Float.isNaN(pm10)) {
            return -1;
        }
        float co = getValue(current, "carbon_monoxide");
        double[] subIndices = {
                subIndex(pm25, NAQI_PM25),
                subIndex(pm10, NAQI_PM10),
                subIndex(getValue(current, "nitrogen_dioxide"), NAQI_NO2),
                subIndex(getValue(current, "sulphur_dioxide"), NAQI_SO2),
                subIndex(getValue(current, "ozone"), NAQI_O3),
                subIndex(Float.isNaN(co) ? Float.NaN : co / 1000f, NAQI_CO),
                subIndex(getValue(current, "ammonia"), NAQI_NH3)
        };
        int count = 0;
        double max = -1;
        for (double s : subIndices) {
            if (s >= 0) {
                count++;
                max = Math.max(max, s);
            }
        }
        return count >= 3 ? (int) Math.round(max) : -1;
    }

    private static double subIndex(float c, double[] breakpoints) {
        if (Float.isNaN(c) || c < 0) {
            return -1;
        }
        for (int i = 1; i < breakpoints.length; i++) {
            if (c <= breakpoints[i]) {
                return NAQI_INDEX[i - 1] + (c - breakpoints[i - 1])
                        * (NAQI_INDEX[i] - NAQI_INDEX[i - 1])
                        / (breakpoints[i] - breakpoints[i - 1]);
            }
        }
        return NAQI_INDEX[NAQI_INDEX.length - 1];
    }

    /** Parses "lat=..&lon=.." as used for weather selections and custom location ids. */
    public static double[] parseCoords(String selection) {
        if (selection == null) {
            return null;
        }
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
            int lonEnd = selection.indexOf("&", lonStart + 4);
            if (lonEnd == -1) {
                lonEnd = selection.length();
            }
            double lat = Double.parseDouble(selection.substring(latStart, latEnd));
            double lon = Double.parseDouble(selection.substring(lonStart + 4, lonEnd));
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }
}
