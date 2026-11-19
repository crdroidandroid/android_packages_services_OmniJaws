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
 * Fetched for every weather provider so AQI is the same scale everywhere.
 */
public class AirQualityProvider {
    private static final String TAG = "AirQualityProvider";
    private static final boolean DEBUG = false;

    private static final String URL_AIR_QUALITY =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%f&longitude=%f"
            + "&current=us_aqi,pm2_5,pm10&timezone=auto";

    /** Fills in the US AQI and particulates of {@code w}; leaves them unset on failure. */
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
            JSONObject current = new JSONObject(response).getJSONObject("current");
            int aqi = current.isNull("us_aqi") ? -1 : (int) Math.round(current.getDouble("us_aqi"));
            float pm25 = current.isNull("pm2_5") ? Float.NaN : (float) current.getDouble("pm2_5");
            float pm10 = current.isNull("pm10") ? Float.NaN : (float) current.getDouble("pm10");
            w.setAirQuality(aqi, pm25, pm10);
        } catch (Exception e) {
            Log.w(TAG, "Received malformed air quality data", e);
        }
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
