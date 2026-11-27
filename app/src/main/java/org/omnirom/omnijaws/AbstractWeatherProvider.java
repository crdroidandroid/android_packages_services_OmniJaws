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
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
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

    // Last resolved locality, keyed by coordinates rounded to ~1 km and the language, so
    // regular updates at the same place don't query the geocoder or geonames again (the
    // shared geonames account is rate limited) and a failed lookup doesn't show Unknown.
    private static final String PREF_KEY_LOCALITY_CACHE = "locality_cache";
    private static final char LOCALITY_SEPARATOR = '\u001F';

    private String mSubLocality;

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

    public String getSubLocality() {
        return mSubLocality;
    }

    private String[] getCoordinatesLocalityWithGoogle(double latitude, double longitude) {
        if (!Geocoder.isPresent()) {
            return null;
        }
        Geocoder geocoder = new Geocoder(mContext.getApplicationContext(), Locale.getDefault());
        try {
            List<Address> listAddresses = geocoder.getFromLocation(latitude, longitude, 1);
            if(listAddresses != null && listAddresses.size() > 0){
                Address a = listAddresses.get(0);
                String city = TextUtils.isEmpty(a.getLocality()) ? a.getAdminArea() : a.getLocality();
                return new String[] { city, a.getSubLocality() };
            }
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "Geocoder lookup failed", e);
        }
        return null;
    }

    protected String getCoordinatesLocality(String coordinate) {
        mSubLocality = null;
        double latitude, longitude;
        try {
            latitude = Double.parseDouble(coordinate.substring(4, coordinate.indexOf("&")));
            longitude = Double.parseDouble(coordinate.substring(coordinate.indexOf("lon=") + 4));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not parse coordinates '" + coordinate + "'", e);
            return null;
        }
        String lang = Locale.getDefault().getLanguage();
        String cacheKey = String.format(Locale.US, "%.2f,%.2f,%s|", latitude, longitude, lang);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String cached = prefs.getString(PREF_KEY_LOCALITY_CACHE, null);
        if (cached != null && cached.startsWith(cacheKey)) {
            String value = cached.substring(cacheKey.length());
            int sep = value.indexOf(LOCALITY_SEPARATOR);
            if (sep < 0) {
                return value;
            }
            mSubLocality = cleanSubLocality(value.substring(0, sep), value.substring(sep + 1));
            return value.substring(0, sep);
        }

        String[] locality = getCoordinatesLocalityWithGoogle(latitude, longitude);
        if (locality == null || TextUtils.isEmpty(locality[0])) {
            locality = getCoordinatesLocalityWithGeonames(latitude, longitude, lang);
        }
        if (locality == null || TextUtils.isEmpty(locality[0])) {
            return null;
        }
        String city = locality[0];
        mSubLocality = cleanSubLocality(city, locality[1]);
        prefs.edit().putString(PREF_KEY_LOCALITY_CACHE, cacheKey + city
                + (mSubLocality != null ? LOCALITY_SEPARATOR + mSubLocality : "")).apply();
        return city;
    }

    // Null when the sub-locality is blank or just repeats the city.
    private static String cleanSubLocality(String city, String subLocality) {
        if (subLocality == null) {
            return null;
        }
        subLocality = subLocality.trim();
        if (subLocality.isEmpty() || subLocality.equalsIgnoreCase(city == null ? "" : city.trim())) {
            return null;
        }
        return subLocality;
    }

    private String[] getCoordinatesLocalityWithGeonames(double latitude, double longitude, String lang) {
        log(TAG, "getCoordinatesLocalityWithGeonames " + latitude + " " + longitude);
        String url = String.format(Locale.US, URL_LOCALITY, latitude, longitude, lang);
        String response = retrieve(url);
        if (response == null) {
            return null;
        }
        log(TAG, "URL = " + url + " returning a response of " + response);

        try {
            return parseLocality(response);
        } catch (Exception e) {
            Log.w(TAG, "Received malformed location data (" + latitude + "," + longitude + ")", e);
        }
        return null;
    }

    // US coordinates yield an <address> block, elsewhere a <geoname> hierarchy
    // ordered from Earth down to the nearest place, or an <ocean> at sea.
    // Errors (e.g. the hourly limit of the shared account) come back as <status>.
    private static String[] parseLocality(String xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new StringReader(xml));
        String placename = null, adminName2 = null, ocean = null;
        String name = null, fcode = null;
        List<String[]> geonames = new ArrayList<>();
        boolean inAddress = false, inGeoname = false, inOcean = false;
        for (int ev = parser.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = parser.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("status".equals(tag)) {
                    Log.w(TAG, "geonames error " + parser.getAttributeValue(null, "value")
                            + ": " + parser.getAttributeValue(null, "message"));
                    return null;
                } else if ("address".equals(tag)) {
                    inAddress = true;
                } else if ("ocean".equals(tag)) {
                    inOcean = true;
                } else if (inOcean && "name".equals(tag)) {
                    ocean = parser.nextText();
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
                } else if ("ocean".equals(parser.getName())) {
                    inOcean = false;
                } else if ("geoname".equals(parser.getName())) {
                    inGeoname = false;
                    geonames.add(new String[] { name, fcode });
                }
            }
        }
        if (!TextUtils.isEmpty(placename)) {
            return new String[] { placename, null };
        }
        if (!TextUtils.isEmpty(adminName2)) {
            return new String[] { adminName2, null };
        }
        // Deepest first: the nearest town when the hierarchy ends in one, else the
        // smallest of the usual administrative levels.
        int cityIndex = -1;
        for (int i = geonames.size() - 1; i >= 0; i--) {
            String n = geonames.get(i)[0];
            String f = geonames.get(i)[1];
            if (TextUtils.isEmpty(n)) {
                continue;
            }
            if (isPopulatedPlace(f) || "ADM3".equals(f) || "ADM2".equals(f) || "ADM1".equals(f)) {
                cityIndex = i;
                break;
            }
        }
        if (cityIndex < 0) {
            return ocean != null ? new String[] { ocean, null } : null;
        }
        String city = geonames.get(cityIndex)[0];
        String subLocality = null;
        for (int i = geonames.size() - 1; i >= 0; i--) {
            String n = geonames.get(i)[0];
            String f = geonames.get(i)[1];
            if (i == cityIndex || TextUtils.isEmpty(n) || n.equalsIgnoreCase(city)) {
                continue;
            }
            if ("PPLX".equals(f) || "ADM5".equals(f) || "ADM4".equals(f) || "ADM3".equals(f)) {
                subLocality = n;
                break;
            }
        }
        return new String[] { city, subLocality };
    }

    // PPL, PPLA..PPLA4, PPLC, PPLL, PPLS, ...; not historical, abandoned, destroyed or a
    // section of a place (PPLH gives names like "Paris 09 Ancien - Quartier Hotel-de-Ville").
    private static boolean isPopulatedPlace(String fcode) {
        return fcode != null && fcode.startsWith("PPL")
                && !"PPLH".equals(fcode) && !"PPLQ".equals(fcode)
                && !"PPLW".equals(fcode) && !"PPLX".equals(fcode);
    }

    protected String getWeatherDataLocality(String coordinates) {
        String city;
        mSubLocality = null;
        if (Config.isCustomLocation(mContext)) {
            city = Config.getLocationName(mContext);
            if (TextUtils.isEmpty(city)) {
                city = getCoordinatesLocality(coordinates);
            }
            // A custom location is the place the user picked: no sub-locality.
            mSubLocality = null;
        } else {
            city = getCoordinatesLocality(coordinates);
        }
        if (TextUtils.isEmpty(city)) {
            city = mContext.getResources().getString(R.string.omnijaws_city_unkown);
            mSubLocality = null;
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

    protected String getDay(int i, long epochSec, String timeZone) {
        if (epochSec <= 0 || TextUtils.isEmpty(timeZone)) {
            return getDay(i);
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone(timeZone));
        return fmt.format(new Date(epochSec * 1000L));
    }
}
