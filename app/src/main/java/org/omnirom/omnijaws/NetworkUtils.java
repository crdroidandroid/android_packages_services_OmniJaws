/*
 *  Copyright (C) 2018 The OmniROM Project
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

import android.content.Context;
import android.net.http.HttpResponseCache;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class NetworkUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = "WeatherService::NetworkUtils";

    private static final int HTTP_READ_TIMEOUT = 30000;
    private static final int HTTP_CONNECTION_TIMEOUT = 30000;
    private static final long HTTP_CACHE_SIZE = 4 * 1024 * 1024;
    private static final String CONTACT_URL =
            "https://github.com/crdroidandroid/android_packages_services_OmniJaws";

    private static String sUserAgent = "OmniJaws " + CONTACT_URL;

    // HTTP status of the last request on this thread, -1 if none was received
    private static final ThreadLocal<Integer> sLastStatus = ThreadLocal.withInitial(() -> -1);

    public static int getLastStatus() {
        return sLastStatus.get();
    }

    public static void resetLastStatus() {
        sLastStatus.set(-1);
    }

    public static void init(Context context) {
        try {
            String version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            sUserAgent = "OmniJaws/" + version + " " + CONTACT_URL;
        } catch (Exception e) {
            Log.w(TAG, "Failed to read version", e);
        }
        try {
            if (HttpResponseCache.getInstalled() == null) {
                HttpResponseCache.install(new File(context.getCacheDir(), "http"), HTTP_CACHE_SIZE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to install HTTP cache", e);
        }
    }

    public static HttpURLConnection setupHttpRequest(String urlStr) {
        URL url;
        HttpURLConnection urlConnection = null;
        sLastStatus.set(-1);
        try {
            url = new URL(urlStr);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.addRequestProperty("User-Agent", sUserAgent);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            sLastStatus.set(code);
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "response:" + code);
                urlConnection.disconnect();
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to server", e);
            return null;
        }
    }

    public static String downloadUrlMemoryAsString(String url) {
        if (DEBUG) Log.d(TAG, "download: " + url);

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = setupHttpRequest(url);
            if (urlConnection == null) {
                return null;
            }

            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (InputStream is = urlConnection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) >= 0) {
                    byteArray.write(buffer, 0, read);
                }
            }
            return new String(byteArray.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            sLastStatus.set(-1);
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
