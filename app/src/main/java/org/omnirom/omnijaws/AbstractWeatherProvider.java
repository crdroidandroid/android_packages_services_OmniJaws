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

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractWeatherProvider {
    private static final String TAG = "AbstractWeatherProvider";
    private static final boolean DEBUG = false;
    protected Context mContext;
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final String URL_PLACES =
            "https://secure.geonames.org/searchJSON?name_startsWith=%s&lang=%s&username=omnijaws&maxRows=20";
    private static final String URL_LOCALITY =
            "https://secure.geonames.org/extendedFindNearby?lat=%f&lng=%f&lang=%s&username=omnijaws";
    protected static final String PART_COORDINATES =
            "lat=%f&lon=%f";

    public AbstractWeatherProvider(Context context) {
        mContext = context;
    }

    protected String retrieve(String url) {
        return NetworkUtils.downloadUrlMemoryAsString(url);
    }

    public abstract WeatherInfo getCustomWeather(String id, boolean metric);

    public abstract WeatherInfo getLocationWeather(Location location, boolean metric);

    public abstract boolean shouldRetry();

    protected void log(String tag, String msg) {
        if (DEBUG) Log.d("WeatherService:" + tag, msg);
    }

    private String getCoordinatesLocalityWithGoogle(String coordinate) {
        double latitude = Double.valueOf(coordinate.substring(4, coordinate.indexOf("&")));
        double longitude = Double.valueOf(coordinate.substring(coordinate.indexOf("lon=") + 4));
        Geocoder geocoder = new Geocoder(mContext.getApplicationContext(), Locale.getDefault());
        try {
            List<Address> listAddresses = geocoder.getFromLocation(latitude, longitude, 1);
            if(listAddresses != null && listAddresses.size() > 0){
                Address a = listAddresses.get(0);
                return TextUtils.isEmpty(a.getLocality()) ? a.getAdminArea() : a.getLocality();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getCoordinatesLocality(String coordinate) {
        String cityGoogle = getCoordinatesLocalityWithGoogle(coordinate);
        if (!TextUtils.isEmpty(cityGoogle)) {
            return cityGoogle;
        }
        double latitude = Double.valueOf(coordinate.substring(4, coordinate.indexOf("&")));
        double longitude = Double.valueOf(coordinate.substring(coordinate.indexOf("lon=") + 4));
        Log.d(TAG, "getCoordinatesLocality " + latitude + " " + longitude);

        String lang = Locale.getDefault().getLanguage().replaceFirst("_", "-");
        String url = String.format(Locale.US, URL_LOCALITY, latitude, longitude, lang);
        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            return parseLocality(response);
        } catch (Exception e) {
            Log.w(TAG, "Received malformed location data (coordinate=" + coordinate + ")", e);
        }
        return null;
    }

    // US coordinates yield an <address> block, elsewhere a <geoname> hierarchy
    // ordered from Earth down to the nearest place.
    private static String parseLocality(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        String placename = null, adminName2 = null;
        String name = null, fcode = null;
        List<String[]> geonames = new ArrayList<>();
        boolean inAddress = false, inGeoname = false;
        for (int ev = parser.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = parser.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("address".equals(tag)) {
                    inAddress = true;
                } else if ("geoname".equals(tag)) {
                    inGeoname = true;
                    name = fcode = null;
                } else if (inAddress && "placename".equals(tag)) {
                    placename = parser.nextText();
                } else if (inAddress && "adminName2".equals(tag)) {
                    adminName2 = parser.nextText();
                } else if (inGeoname && "name".equals(tag)) {
                    name = parser.nextText();
                } else if (inGeoname && "fcode".equals(tag)) {
                    fcode = parser.nextText();
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if ("address".equals(parser.getName())) {
                    inAddress = false;
                } else if ("geoname".equals(parser.getName())) {
                    inGeoname = false;
                    geonames.add(new String[] { name, fcode });
                }
            }
        }
        if (!TextUtils.isEmpty(placename)) {
            return placename;
        }
        if (!TextUtils.isEmpty(adminName2)) {
            return adminName2;
        }
        for (int i = geonames.size() - 1; i >= 0; i--) {
            String n = geonames.get(i)[0];
            String f = geonames.get(i)[1];
            if (TextUtils.isEmpty(n)) {
                continue;
            }
            if ("ADM3".equals(f) || "ADM2".equals(f) || "ADM1".equals(f)) {
                return n;
            }
        }
        return null;
    }

    protected String getWeatherDataLocality(String coordinates) {
        String city;
        if (Config.isCustomLocation(mContext)) {
            city = Config.getLocationName(mContext);
            if (TextUtils.isEmpty(city)) {
                city = getCoordinatesLocality(coordinates);
            }
        } else {
            city = getCoordinatesLocality(coordinates);
        }
        if (TextUtils.isEmpty(city)) {
            city = mContext.getResources().getString(R.string.omnijaws_city_unkown);
        }
        log(TAG, "getWeatherDataLocality = " + city);
        return city;
    }

    protected String getDay(int i) {
        Calendar calendar = Calendar.getInstance();
        if (i > 0) {
            calendar.add(Calendar.DATE, i);
        }
        return dayFormat.format(calendar.getTime());
    }
}
