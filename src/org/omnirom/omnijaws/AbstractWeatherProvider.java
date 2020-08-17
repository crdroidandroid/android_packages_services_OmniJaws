/*
 *  Copyright (C) 2015 The OmniROM Project
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

import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class AbstractWeatherProvider {
    private static final String TAG = "AbstractWeatherProvider";
    private static final boolean DEBUG = true;
    protected Context mContext;

    public AbstractWeatherProvider(Context context) {
        mContext = context;
    }

    protected String retrieve(String url) {
        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(url).openConnection();
            int code = request.getResponseCode();
            if (code != HTTP_OK) {
                log(TAG, "HttpStatus: " + code + " for url: " + url);
                return null;
            }
            BufferedReader response = new BufferedReader(
                    new InputStreamReader(request.getInputStream()));
            String inputLine;
            StringBuilder entity = new StringBuilder();
            while ((inputLine = response.readLine()) != null) {
                entity.append(inputLine);
            }
            response.close();
            return entity.toString();
        } catch (MalformedURLException m) {
            Log.e(TAG, "Got malformed url " + url);
            // return null;
        } catch (IOException e) {
            Log.e(TAG, "Couldn't retrieve data from url " + url, e);
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
        return null;
    }

    public abstract WeatherInfo getCustomWeather(String id, boolean metric);

    public abstract WeatherInfo getLocationWeather(Location location, boolean metric);

    public abstract List<WeatherInfo.WeatherLocation> getLocations(String input);

    public abstract boolean shouldRetry();

    protected void log(String tag, String msg) {
        if (DEBUG) Log.d("WeatherService:" + tag, msg);
    }
}
