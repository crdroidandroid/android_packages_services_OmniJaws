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

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.omnirom.omnijaws.WeatherInfo.DayForecast;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.location.Address;
import android.location.Location;
import android.location.Geocoder;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

public class YahooWeatherProvider extends AbstractWeatherProvider  {
    private static final String TAG = "YahooWeatherProvider";

    private static final String URL_WEATHER =
            "https://query.yahooapis.com/v1/public/yql?q=" +
            Uri.encode("select * from weather.forecast where ");
    private static final String URL_LOCATION =
            "https://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select woeid, postal, admin1, admin2, admin3, " +
                    "locality1, locality2, country from geo.places where " +
                    "(placetype = 7 or placetype = 8 or placetype = 9 " +
                    "or placetype = 10 or placetype = 11 or placetype = 20) and text =");
    private static final String URL_PLACES =
            "https://query.yahooapis.com/v1/public/yql?format=json&q=" +
            Uri.encode("select * from geo.places where text =");

    private static final String[] LOCALITY_NAMES = new String[] {
        "locality1", "locality2", "admin3", "admin2", "admin1"
    };

    private static boolean metric;
    private static String todayShort;
    private static boolean addForecastDay;
    private static final boolean USE_GEOCODER = true;

    public YahooWeatherProvider(Context context) {
       super(context);
    }

    public List<WeatherInfo.WeatherLocation> getLocations(String input) {
        String language = getLanguage();
        String params = "\"" + input + "\" and lang = \"" + language + "\"";
        String url = URL_LOCATION + Uri.encode(params);
        JSONObject jsonResults = fetchResults(url);
        if (jsonResults == null) {
            return null;
        }

        try {
            JSONArray places = jsonResults.optJSONArray("place");
            if (places == null) {
                // Yahoo returns an object instead of an array when there's only one result
                places = new JSONArray();
                places.put(jsonResults.getJSONObject("place"));
            }

            ArrayList<WeatherInfo.WeatherLocation> results = new ArrayList<WeatherInfo.WeatherLocation>();
            for (int i = 0; i < places.length(); i++) {
                WeatherInfo.WeatherLocation result = parsePlace(places.getJSONObject(i));
                if (result != null) {
                    results.add(result);
                }
            }
            return results;
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed places data (input=" + input + ", lang=" + language + ")", e);
        }
        return null;
    }

    public WeatherInfo getCustomWeather(String id, boolean metric) {
        String params = "woeid=" + id + " and u='" + (metric ? "c" : "f") + "'";
        String url = URL_WEATHER + Uri.encode(params);
        String response = retrieve(url);
        this.metric = metric;
        if (response == null) {
            return null;
        }

        // yahoo delivers old forecast days in 5 day mode
        // so fetch 10 days and ignore all before today
        // use 3 letter day abbrev as index
        SimpleDateFormat sdf = new SimpleDateFormat("EEE", Locale.US);
        todayShort = sdf.format(new Date());
        log(TAG, "todayShort = " + todayShort);
        addForecastDay = false;

        log(TAG, "URL = " + url + " returning a response of " + response);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            StringReader reader = new StringReader(response);
            WeatherHandler handler = new WeatherHandler();
            parser.parse(new InputSource(reader), handler);

            if (handler.isComplete()) {
                // There are cases where the current condition is unknown, but the forecast
                // is not - using the (inaccurate) forecast is probably better than showing
                // the question mark
                if (handler.conditionCode == 3200) {
                    handler.condition = handler.forecasts.get(0).condition;
                    handler.conditionCode = handler.forecasts.get(0).conditionCode;
                }

                WeatherInfo w = new WeatherInfo(mContext, id,
                        handler.city,
                        handler.condition, handler.conditionCode, handler.temperature,
                        handler.humidity, handler.windSpeed,
                        handler.windDirection, metric, handler.forecasts,
                        System.currentTimeMillis());
                log(TAG, "Weather updated: " + w);
                return w;
            } else {
                Log.w(TAG, "Received incomplete weather XML (id=" + id + ")");
            }
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "Could not create XML parser", e);
        } catch (SAXException e) {
            Log.e(TAG, "Could not parse weather XML (id=" + id + ")", e);
        } catch (IOException e) {
            Log.e(TAG, "Could not parse weather XML (id=" + id + ")", e);
        }

        return null;
    }

    private static class WeatherHandler extends DefaultHandler {
        String city;
        String temperatureUnit, speedUnit;
        int windDirection, conditionCode;
        float humidity, temperature, windSpeed;
        String condition;
        ArrayList<DayForecast> forecasts = new ArrayList<DayForecast>();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            switch (qName) {
                case "yweather:location":
                    city = attributes.getValue("city");
                    break;
                case "yweather:units":
                    temperatureUnit = attributes.getValue("temperature");
                    speedUnit = attributes.getValue("speed");
                    break;
                case "yweather:wind":
                    windDirection = (int) stringToFloat(attributes.getValue("direction"), -1);
                    windSpeed = stringToFloat(attributes.getValue("speed"), -1);
                    break;
                case "yweather:atmosphere":
                    humidity = stringToFloat(attributes.getValue("humidity"), -1);
                    break;
                case "yweather:condition":
                    condition = attributes.getValue("text");
                    conditionCode = (int) stringToFloat(attributes.getValue("code"), -1);
                    temperature = stringToFloat(attributes.getValue("temp"), Float.NaN);
                    break;
                case "yweather:forecast":
                    String date = attributes.getValue("date");
                    String dayShort = attributes.getValue("day");
                    // is this forecaset days before today?
                    if (dayShort.equals(todayShort) && !addForecastDay) {
                        addForecastDay = true;
                    }
                    if (addForecastDay) {
                        DayForecast day = new DayForecast(
                            /* low */ stringToFloat(attributes.getValue("low"), Float.NaN),
                            /* high */ stringToFloat(attributes.getValue("high"), Float.NaN),
                            /* condition */ attributes.getValue("text"),
                            /* conditionCode */ (int) stringToFloat(attributes.getValue("code"), -1),
                                attributes.getValue("date"),
                                metric);
                        if (!Float.isNaN(day.low) && !Float.isNaN(day.high) && day.conditionCode >= 0) {
                            forecasts.add(day);
                        }
                    }
                    break;
            }
        }
        public boolean isComplete() {
            return temperatureUnit != null && speedUnit != null && conditionCode >= 0
                    && !Float.isNaN(temperature) && !forecasts.isEmpty();
        }
        private float stringToFloat(String value, float defaultValue) {
            try {
                if (value != null) {
                    return Float.parseFloat(value);
                }
            } catch (NumberFormatException e) {
                // fall through to the return line below
            }
            return defaultValue;
        }
    }

    private String locateCity(Location location) {
        try {
            Geocoder geocoder = new Geocoder(mContext, Locale.getDefault());
            List<Address> addresses =
                    geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.size() > 0) {
                return addresses.get(0).getLocality();
            } else {
                Log.e(TAG, "No city data");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to retrieve city", e);
        }
        return null;
    }

    public WeatherInfo getLocationWeather(Location location, boolean metric) {
        if (USE_GEOCODER) {
            // workaround broken yahoo geolocation API
            String city = locateCity(location);
            if (city != null) {
                List<WeatherInfo.WeatherLocation> locations = getLocations(city);
                if (locations != null && locations.size() > 0) {
                    WeatherInfo.WeatherLocation loction = locations.get(0);
                    log(TAG, "Resolved location " + location + " to " + city + " id " + loction.id);
                    return getCustomWeather(loction.id, metric);
                }
            }
        }
        String language = getLanguage();
        String params = String.format(Locale.US, "\"(%f,%f)\" and lang=\"%s\"",
                location.getLatitude(), location.getLongitude(), language);
        String url = URL_PLACES + Uri.encode(params);
        JSONObject results = fetchResults(url);
        if (results == null) {
            return null;
        }

        try {
            String queryCity = null;
            JSONObject place = results.getJSONObject("place");
            WeatherInfo.WeatherLocation result = parsePlace(place);
            String woeid = null;
            String city = null;
            if (result != null) {
                woeid = result.id;
                queryCity = result.city;
            }
            // The city name in the placefinder result is HTML encoded :-(
            if (queryCity != null) {
                queryCity = Html.fromHtml(queryCity).toString();
            } else {
                Log.w(TAG, "Can not resolve place name for " + location);
            }

            log(TAG, "Resolved location " + location + " to " + queryCity + " (" + woeid + ")");

            WeatherInfo info = getCustomWeather(woeid, metric);
            if (info != null) {
                return info;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received malformed placefinder data (location="
                    + location + ", lang=" + language + ")", e);
        }

        return null;
    }

    private WeatherInfo.WeatherLocation parsePlace(JSONObject place) throws JSONException {
        WeatherInfo.WeatherLocation result = new WeatherInfo.WeatherLocation();
        JSONObject country = place.getJSONObject("country");

        result.id = place.getString("woeid");
        result.country = country.getString("content");
        result.countryId = country.getString("code");
        if (!place.isNull("postal")) {
            result.postal = place.getJSONObject("postal").getString("content");
        }

        for (String name : LOCALITY_NAMES) {
            if (!place.isNull(name)) {
                JSONObject localeObject = place.getJSONObject(name);
                result.city = localeObject.getString("content");
                if (localeObject.optString("woeid") != null) {
                    result.id = localeObject.getString("woeid");
                }
                break;
            }
        }

        log(TAG, "JSON data " + place.toString() + " -> id=" + result.id
                    + ", city=" + result.city + ", country=" + result.countryId);

        if (result.id == null || result.city == null || result.countryId == null) {
            return null;
        }

        return result;
    }

    private JSONObject fetchResults(String url) {
        String response = retrieve(url);
        if (response == null) {
            return null;
        }

        log(TAG, "Request URL is " + url + ", response is " + response);

        try {
            JSONObject rootObject = new JSONObject(response);
            return rootObject.getJSONObject("query").getJSONObject("results");
        } catch (JSONException e) {
            Log.w(TAG, "Received malformed places data (url=" + url + ")", e);
        }

        return null;
    }

    private String getLanguage() {
        Locale locale = mContext.getResources().getConfiguration().locale;
        String country = locale.getCountry();
        String language = locale.getLanguage();

        if (TextUtils.isEmpty(country)) {
            return language;
        }
        return language + "-" + country;
    }
}
