/*
 * Copyright (C) 2021 The OmniROM project
 * Copyright (C) 2022-2026 crDroid Android project
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

package org.omnirom.omnijaws.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class OmniJawsClient private constructor() {

    @Volatile private var cachedInfo: WeatherInfo? = null
    @Volatile private var res: Resources? = null
    @Volatile private var packageName: String? = null
    @Volatile private var iconPrefix: String? = null
    @Volatile private var settingIconPackage: String? = null
    @Volatile private var metric = true

    private val observers = CopyOnWriteArrayList<WeakReference<OmniJawsObserver>>()
    private var receiver: WeatherUpdateReceiver? = null
    private var receiverContext: Context? = null
    private var weatherReceiverRegistered = false

    interface OmniJawsObserver {
        fun weatherUpdated()
        fun weatherError(errorReason: Int)
        fun updateSettings() {}
    }

    private inner class WeatherUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            pruneDeadObservers()
            for (ref in observers) {
                val obs = ref.get() ?: continue
                try {
                    if (WEATHER_UPDATE == action) {
                        obs.weatherUpdated()
                    } else if (WEATHER_ERROR == action) {
                        obs.weatherError(intent.getIntExtra(EXTRA_ERROR, 0))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "observer threw on $action", e)
                }
            }
        }
    }

    val settingsIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .setClassName(SERVICE_PACKAGE, "$SERVICE_PACKAGE.SettingsActivity")

    fun getWeatherActivityIntent(context: Context): Intent {
        if (isOmniJawsEnabled(context)) {
            return Intent(Intent.ACTION_MAIN)
                .setClassName(SERVICE_PACKAGE, "$SERVICE_PACKAGE.WeatherActivity")
        }
        return settingsIntent
    }

    val weatherInfo: WeatherInfo?
        get() = cachedInfo

    fun queryWeather(context: Context) {
        if (!isOmniJawsEnabled(context)) {
            Log.w(TAG, "queryWeather while disabled")
            cachedInfo = null
            return
        }

        var info: WeatherInfo? = null
        val resolver = context.contentResolver

        try {
            resolver.query(WEATHER_URI, WEATHER_PROJECTION, null, null, null)?.use { c ->
                if (c.count > 0) {
                    info = readWeather(c)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryWeather: weather", e)
            info = null
        }

        try {
            resolver.query(SETTINGS_URI, SETTINGS_PROJECTION, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    metric = getInt(c, c.getColumnIndex(COL_UNITS), 0) == 0
                    info?.let {
                        it.tempUnits = temperatureUnit
                        it.windUnits = windUnit
                        it.precipUnits = precipUnit
                        it.provider = getString(c, c.getColumnIndex(COL_PROVIDER))
                        it.iconPack = getString(c, c.getColumnIndex(COL_ICON_PACK))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryWeather: settings", e)
        }

        info?.let {
            try {
                resolver.query(HOURLY_URI, HOURLY_PROJECTION, null, null, null)?.use { c ->
                    if (c.count > 0) {
                        readHourly(c)?.let { hourly -> it.hourlyForecasts = hourly }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "queryWeather: hourly", e)
            }
        }

        cachedInfo = info

        updateSettings(context)
    }

    private fun readWeather(c: Cursor): WeatherInfo {
        val info = WeatherInfo()
        info.tempUnits = temperatureUnit
        info.windUnits = windUnit
        info.precipUnits = precipUnit
        val forecasts = ArrayList<DayForecast>()

        val colCity = c.getColumnIndex(COL_CITY)
        val colWindSpeed = c.getColumnIndex(COL_WIND_SPEED)
        val colWindDir = c.getColumnIndex(COL_WIND_DIRECTION)
        val colConditionCode = c.getColumnIndex(COL_CONDITION_CODE)
        val colTemperature = c.getColumnIndex(COL_TEMPERATURE)
        val colHumidity = c.getColumnIndex(COL_HUMIDITY)
        val colCondition = c.getColumnIndex(COL_CONDITION)
        val colTimeStamp = c.getColumnIndex(COL_TIME_STAMP)
        val colPinWheel = c.getColumnIndex(COL_PIN_WHEEL)
        val colFeelsLike = c.getColumnIndex(COL_FEELS_LIKE)
        val colPressure = c.getColumnIndex(COL_PRESSURE)
        val colUvi = c.getColumnIndex(COL_UVI)
        val colVisibility = c.getColumnIndex(COL_VISIBILITY)
        val colDewPoint = c.getColumnIndex(COL_DEW_POINT)
        val colSunrise = c.getColumnIndex(COL_SUNRISE)
        val colSunset = c.getColumnIndex(COL_SUNSET)
        val colCityId = c.getColumnIndex(COL_CITY_ID)
        val colAqi = c.getColumnIndex(COL_AQI)
        val colAqiScale = c.getColumnIndex(COL_AQI_SCALE)
        val colPm25 = c.getColumnIndex(COL_PM25)
        val colPm10 = c.getColumnIndex(COL_PM10)

        val colForecastLow = c.getColumnIndex(COL_FORECAST_LOW)
        val colForecastHigh = c.getColumnIndex(COL_FORECAST_HIGH)
        val colForecastCondition = c.getColumnIndex(COL_FORECAST_CONDITION)
        val colForecastConditionCode = c.getColumnIndex(COL_FORECAST_CONDITION_CODE)
        val colForecastDate = c.getColumnIndex(COL_FORECAST_DATE)
        val colForecastPrecipProbability = c.getColumnIndex(COL_FORECAST_PRECIP_PROBABILITY)
        val colForecastPrecipAmount = c.getColumnIndex(COL_FORECAST_PRECIP_AMOUNT)

        for (i in 0 until c.count) {
            c.moveToPosition(i)
            if (i == 0) {
                info.city = getString(c, colCity)
                info.cityId = getString(c, colCityId)
                info.windSpeed = getFormattedValue(getFloat(c, colWindSpeed))
                info.windDirection = getInt(c, colWindDir, 0).toString() + "°"
                info.conditionCode = getInt(c, colConditionCode, -1)
                info.temp = getFormattedValue(getFloat(c, colTemperature))
                info.humidity = getString(c, colHumidity)
                info.condition = getString(c, colCondition)
                info.timeStamp = getLong(c, colTimeStamp, 0L)
                info.pinWheel = getString(c, colPinWheel)

                info.feelsLike = getFloat(c, colFeelsLike)
                info.pressure = getFloat(c, colPressure)
                info.uvi = getFloat(c, colUvi)
                info.visibility = getFloat(c, colVisibility)
                info.dewPoint = getFloat(c, colDewPoint)
                info.sunrise = getLong(c, colSunrise, 0L)
                info.sunset = getLong(c, colSunset, 0L)
                info.aqi = getNullableInt(c, colAqi, -1)
                info.aqiScale = if (AQI_SCALE_IN == getString(c, colAqiScale)) {
                    AQI_SCALE_IN
                } else {
                    AQI_SCALE_US
                }
                info.pm25 = getFloat(c, colPm25)
                info.pm10 = getFloat(c, colPm10)
            } else {
                val day = DayForecast()
                day.low = getFormattedValue(getFloat(c, colForecastLow))
                day.high = getFormattedValue(getFloat(c, colForecastHigh))
                day.condition = getString(c, colForecastCondition)
                day.conditionCode = getInt(c, colForecastConditionCode, -1)
                day.date = getString(c, colForecastDate)
                day.precipProbability = getFloat(c, colForecastPrecipProbability)
                day.precipAmount = getFloat(c, colForecastPrecipAmount)
                forecasts.add(day)
            }
        }
        info.forecasts = forecasts
        return info
    }

    private fun readHourly(c: Cursor): List<HourlyForecast>? {
        val colTemp = c.getColumnIndex(COL_HOURLY_TEMPERATURE)
        val colCode = c.getColumnIndex(COL_HOURLY_CONDITION_CODE)
        val colCondition = c.getColumnIndex(COL_HOURLY_CONDITION)
        val colTimestamp = c.getColumnIndex(COL_HOURLY_TIMESTAMP)
        val colHumidity = c.getColumnIndex(COL_HOURLY_HUMIDITY)
        val colWindSpeed = c.getColumnIndex(COL_HOURLY_WIND_SPEED)
        val colPrecipProbability = c.getColumnIndex(COL_HOURLY_PRECIP_PROBABILITY)
        val colPrecipAmount = c.getColumnIndex(COL_HOURLY_PRECIP_AMOUNT)

        if (colTemp == -1 || colCode == -1 || colCondition == -1 ||
            colTimestamp == -1 || colHumidity == -1 || colWindSpeed == -1
        ) {
            Log.w(TAG, "queryWeather: hourly columns missing, skipping hourly forecasts")
            return null
        }
        val hourly = ArrayList<HourlyForecast>()
        while (c.moveToNext()) {
            val h = HourlyForecast()
            h.temperature = c.getFloat(colTemp)
            h.conditionCode = c.getInt(colCode)
            h.condition = c.getString(colCondition)
            h.timestamp = c.getLong(colTimestamp)
            h.humidity = c.getFloat(colHumidity)
            h.windSpeed = c.getFloat(colWindSpeed)
            h.precipProbability = getFloat(c, colPrecipProbability)
            h.precipAmount = getFloat(c, colPrecipAmount)
            hourly.add(h)
        }
        return hourly
    }

    private fun updateSettings(context: Context) {
        val iconPack = cachedInfo?.iconPack
        if (iconPack.isNullOrEmpty()) {
            loadDefaultIconsPackage(context)
        } else if (iconPack != settingIconPackage) {
            settingIconPackage = iconPack
            loadCustomIconPackage(context, iconPack)
        }
    }

    private fun loadDefaultIconsPackage(context: Context) {
        packageName = ICON_PACKAGE_DEFAULT
        iconPrefix = ICON_PREFIX_DEFAULT
        settingIconPackage = "$ICON_PACKAGE_DEFAULT.$ICON_PREFIX_DEFAULT"
        res = try {
            context.packageManager.getResourcesForApplication(ICON_PACKAGE_DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "No default icon package found")
            null
        }
    }

    private fun loadCustomIconPackage(context: Context, iconPack: String) {
        val idx = iconPack.lastIndexOf('.')
        if (idx == -1) {
            loadDefaultIconsPackage(context)
            return
        }
        val pkg = iconPack.substring(0, idx)
        packageName = pkg
        iconPrefix = iconPack.substring(idx + 1)
        try {
            res = context.packageManager.getResourcesForApplication(pkg)
        } catch (e: Exception) {
            Log.w(TAG, "Icon pack loading failed, fallback to default")
            loadDefaultIconsPackage(context)
        }
    }

    fun isOmniJawsServiceInstalled(context: Context): Boolean =
        isAvailableApp(context, SERVICE_PACKAGE)

    fun isOmniJawsEnabled(context: Context): Boolean {
        if (!isOmniJawsServiceInstalled(context)) return false
        return try {
            context.contentResolver.query(SETTINGS_URI, SETTINGS_PROJECTION, null, null, null)
                ?.use { c ->
                    c.moveToFirst() && getInt(c, c.getColumnIndex(COL_ENABLED), 0) == 1
                } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "isOmniJawsEnabled", e)
            false
        }
    }

    private fun isAvailableApp(context: Context, pkg: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            val state = pm.getApplicationEnabledSetting(pkg)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun addObserver(context: Context, observer: OmniJawsObserver?) {
        if (observer == null) return
        removeObserverInternal(observer)
        observers.add(WeakReference(observer))
        registerReceiverIfNeeded(context)
    }

    fun removeObserver(context: Context, observer: OmniJawsObserver?) {
        if (observer == null) return
        removeObserverInternal(observer)
        if (observers.isEmpty()) {
            unregisterReceiver()
        }
    }

    private fun removeObserverInternal(observer: OmniJawsObserver) {
        observers.removeIf { ref ->
            val o = ref.get()
            o == null || o === observer
        }
    }

    private fun pruneDeadObservers() {
        try {
            observers.removeIf { it.get() == null }
        } catch (e: Exception) {
            Log.w(TAG, "Exception occured while pruning, ignoring")
        }
    }

    @Synchronized
    private fun registerReceiverIfNeeded(context: Context) {
        if (weatherReceiverRegistered || observers.isEmpty()) {
            return
        }
        val appContext = context.applicationContext
        val r = WeatherUpdateReceiver()
        val filter = IntentFilter().apply {
            addAction(WEATHER_UPDATE)
            addAction(WEATHER_ERROR)
        }
        try {
            appContext.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
            receiverContext = appContext
            receiver = r
            weatherReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "registerReceiver failed", e)
            receiver = null
            receiverContext = null
        }
    }

    @Synchronized
    private fun unregisterReceiver() {
        val r = receiver
        val ctx = receiverContext
        if (weatherReceiverRegistered && r != null && ctx != null) {
            try {
                ctx.unregisterReceiver(r)
            } catch (ignored: Exception) {
            }
        }
        receiver = null
        receiverContext = null
        weatherReceiverRegistered = false
    }

    private val temperatureUnit: String
        get() = if (metric) "°C" else "°F"

    private val windUnit: String
        get() = if (metric) "km/h" else "mph"

    private val precipUnit: String
        get() = if (metric) "mm" else "in"

    fun getWeatherConditionImage(context: Context, conditionCode: Int): Drawable {
        if (res == null) {
            loadDefaultIconsPackage(context)
        }
        val r = res ?: return getDefaultConditionImage(context)
        return try {
            val resId = r.getIdentifier("${iconPrefix}_$conditionCode", "drawable", packageName)
            if (resId == 0) {
                getDefaultConditionImage(context)
            } else {
                r.getDrawable(resId, null) ?: getDefaultConditionImage(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWeatherConditionImage", e)
            getDefaultConditionImage(context)
        }
    }

    private fun getDefaultConditionImage(context: Context): Drawable {
        return try {
            val r = context.packageManager.getResourcesForApplication(ICON_PACKAGE_DEFAULT)
            val resId = r.getIdentifier("${ICON_PREFIX_DEFAULT}_na", "drawable", ICON_PACKAGE_DEFAULT)
            if (resId == 0) {
                ColorDrawable(Color.TRANSPARENT)
            } else {
                r.getDrawable(resId, null) ?: ColorDrawable(Color.TRANSPARENT)
            }
        } catch (e: Exception) {
            ColorDrawable(Color.TRANSPARENT)
        }
    }

    fun getResOmni(context: Context, iconOmni: String): Drawable {
        if (res == null) loadDefaultIconsPackage(context)
        val r = res ?: return ColorDrawable(Color.TRANSPARENT)
        return try {
            val resId = r.getIdentifier(iconOmni, "drawable", packageName)
            if (resId == 0) {
                ColorDrawable(Color.TRANSPARENT)
            } else {
                r.getDrawable(resId, null) ?: ColorDrawable(Color.TRANSPARENT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getResOmni", e)
            ColorDrawable(Color.TRANSPARENT)
        }
    }

    class WeatherInfo {
        @JvmField var city: String? = null
        @JvmField var cityId: String? = null
        @JvmField var windSpeed: String? = null
        @JvmField var windDirection: String? = null
        @JvmField var conditionCode: Int = 0
        @JvmField var temp: String? = null
        @JvmField var humidity: String? = null
        @JvmField var condition: String? = null
        @JvmField var timeStamp: Long? = null
        @JvmField var forecasts: List<DayForecast>? = null
        @JvmField var tempUnits: String? = null
        @JvmField var windUnits: String? = null
        @JvmField var precipUnits: String? = null
        @JvmField var provider: String? = null
        @JvmField var pinWheel: String? = null
        @JvmField var iconPack: String? = null

        @JvmField var feelsLike: Float = Float.NaN
        @JvmField var pressure: Float = Float.NaN
        @JvmField var uvi: Float = Float.NaN
        @JvmField var visibility: Float = Float.NaN
        @JvmField var dewPoint: Float = Float.NaN
        @JvmField var sunrise: Long = 0L
        @JvmField var sunset: Long = 0L
        /** US AQI (0-500), -1 when unknown. */
        @JvmField var aqi: Int = -1
        @JvmField var aqiScale: String = AQI_SCALE_US
        /** Particulates in ug/m3, NaN when unknown. */
        @JvmField var pm25: Float = Float.NaN
        @JvmField var pm10: Float = Float.NaN
        @JvmField var hourlyForecasts: List<HourlyForecast>? = null

        fun getLastUpdateTime(): String {
            val ts = timeStamp
            if (ts == null || ts == 0L) {
                return ""
            }
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
        }

        override fun toString(): String {
            val ts = timeStamp
            return "$city @ ${if (ts != null) Date(ts) else "never"} | $condition | $temp"
        }
    }

    class DayForecast {
        @JvmField var low: String? = null
        @JvmField var high: String? = null
        @JvmField var conditionCode: Int = 0
        @JvmField var condition: String? = null
        @JvmField var date: String? = null
        /** Chance of precipitation 0-100, NaN when unknown. */
        @JvmField var precipProbability: Float = Float.NaN
        /** Precipitation in mm (inches when imperial), NaN when unknown. */
        @JvmField var precipAmount: Float = Float.NaN

        override fun toString(): String = "[$date - $low/$high - $condition]"
    }

    class HourlyForecast {
        @JvmField var temperature: Float = 0f
        @JvmField var conditionCode: Int = 0
        @JvmField var condition: String? = null
        @JvmField var timestamp: Long = 0L
        @JvmField var humidity: Float = 0f
        @JvmField var windSpeed: Float = 0f
        @JvmField var precipProbability: Float = Float.NaN
        @JvmField var precipAmount: Float = Float.NaN

        override fun toString(): String = "[${Date(timestamp)} - $temperature - $condition]"
    }

    companion object {
        private const val TAG = "OmniJawsClient"

        const val SERVICE_PACKAGE = "org.omnirom.omnijaws"
        @JvmField val WEATHER_URI: Uri = Uri.parse("content://org.omnirom.omnijaws.provider/weather")
        @JvmField val SETTINGS_URI: Uri = Uri.parse("content://org.omnirom.omnijaws.provider/settings")
        @JvmField val HOURLY_URI: Uri = Uri.parse("content://org.omnirom.omnijaws.provider/hourly")
        const val WEATHER_UPDATE = "$SERVICE_PACKAGE.WEATHER_UPDATE"
        const val WEATHER_ERROR = "$SERVICE_PACKAGE.WEATHER_ERROR"

        private const val ICON_PACKAGE_DEFAULT = "org.omnirom.omnijaws"
        private const val ICON_PREFIX_DEFAULT = "google_new"
        private const val EXTRA_ERROR = "error"
        const val EXTRA_ERROR_NETWORK = 0
        const val EXTRA_ERROR_LOCATION = 1
        const val EXTRA_ERROR_DISABLED = 2
        const val EXTRA_ERROR_API_KEY = 3
        const val EXTRA_ERROR_RATE_LIMIT = 4

        private const val COL_CITY = "city"
        private const val COL_WIND_SPEED = "wind_speed"
        private const val COL_WIND_DIRECTION = "wind_direction"
        private const val COL_CONDITION_CODE = "condition_code"
        private const val COL_TEMPERATURE = "temperature"
        private const val COL_HUMIDITY = "humidity"
        private const val COL_CONDITION = "condition"
        private const val COL_FORECAST_LOW = "forecast_low"
        private const val COL_FORECAST_HIGH = "forecast_high"
        private const val COL_FORECAST_CONDITION = "forecast_condition"
        private const val COL_FORECAST_CONDITION_CODE = "forecast_condition_code"
        private const val COL_TIME_STAMP = "time_stamp"
        private const val COL_FORECAST_DATE = "forecast_date"
        private const val COL_FORECAST_PRECIP_PROBABILITY = "forecast_precip_probability"
        private const val COL_FORECAST_PRECIP_AMOUNT = "forecast_precip_amount"
        private const val COL_PIN_WHEEL = "pin_wheel"
        private const val COL_FEELS_LIKE = "feels_like"
        private const val COL_PRESSURE = "pressure"
        private const val COL_UVI = "uvi"
        private const val COL_VISIBILITY = "visibility"
        private const val COL_DEW_POINT = "dew_point"
        private const val COL_SUNRISE = "sunrise"
        private const val COL_SUNSET = "sunset"
        private const val COL_CITY_ID = "city_id"
        private const val COL_AQI = "aqi"
        private const val COL_AQI_SCALE = "aqi_scale"
        private const val COL_PM25 = "pm25"
        private const val COL_PM10 = "pm10"

        private const val COL_ENABLED = "enabled"
        private const val COL_UNITS = "units"
        private const val COL_PROVIDER = "provider"
        private const val COL_SETUP = "setup"
        private const val COL_ICON_PACK = "icon_pack"

        private const val COL_HOURLY_TEMPERATURE = "hourly_temperature"
        private const val COL_HOURLY_CONDITION_CODE = "hourly_condition_code"
        private const val COL_HOURLY_CONDITION = "hourly_condition"
        private const val COL_HOURLY_TIMESTAMP = "hourly_timestamp"
        private const val COL_HOURLY_HUMIDITY = "hourly_humidity"
        private const val COL_HOURLY_WIND_SPEED = "hourly_wind_speed"
        private const val COL_HOURLY_PRECIP_PROBABILITY = "hourly_precip_probability"
        private const val COL_HOURLY_PRECIP_AMOUNT = "hourly_precip_amount"

        @JvmField val WEATHER_PROJECTION = arrayOf(
            COL_CITY, COL_WIND_SPEED, COL_WIND_DIRECTION, COL_CONDITION_CODE, COL_TEMPERATURE,
            COL_HUMIDITY, COL_CONDITION, COL_FORECAST_LOW, COL_FORECAST_HIGH, COL_FORECAST_CONDITION,
            COL_FORECAST_CONDITION_CODE, COL_TIME_STAMP, COL_FORECAST_DATE, COL_PIN_WHEEL,
            COL_FEELS_LIKE, COL_PRESSURE, COL_UVI, COL_VISIBILITY, COL_DEW_POINT,
            COL_SUNRISE, COL_SUNSET, COL_CITY_ID, COL_AQI, COL_AQI_SCALE,
            COL_PM25, COL_PM10, COL_FORECAST_PRECIP_PROBABILITY, COL_FORECAST_PRECIP_AMOUNT
        )

        @JvmField val SETTINGS_PROJECTION = arrayOf(
            COL_ENABLED, COL_UNITS, COL_PROVIDER, COL_SETUP, COL_ICON_PACK
        )

        @JvmField val HOURLY_PROJECTION = arrayOf(
            COL_HOURLY_TEMPERATURE, COL_HOURLY_CONDITION_CODE, COL_HOURLY_CONDITION,
            COL_HOURLY_TIMESTAMP, COL_HOURLY_HUMIDITY, COL_HOURLY_WIND_SPEED,
            COL_HOURLY_PRECIP_PROBABILITY, COL_HOURLY_PRECIP_AMOUNT
        )

        const val AQI_SCALE_US = "us"
        const val AQI_SCALE_IN = "in"

        private val noDigitsFormat = DecimalFormat("0")

        @Volatile private var instance: OmniJawsClient? = null

        @JvmStatic
        fun get(): OmniJawsClient =
            instance ?: synchronized(this) {
                instance ?: OmniJawsClient().also { instance = it }
            }

        private fun getString(c: Cursor, col: Int): String? {
            if (col == -1) return null
            return try {
                c.getString(col)
            } catch (e: Exception) {
                null
            }
        }

        private fun getInt(c: Cursor, col: Int, def: Int): Int {
            if (col == -1) return def
            return try {
                c.getInt(col)
            } catch (e: Exception) {
                def
            }
        }

        // Null (unknown, or a service without the column) maps to def instead of 0.
        private fun getNullableInt(c: Cursor, col: Int, def: Int): Int {
            if (col == -1) return def
            return try {
                if (c.isNull(col)) def else c.getInt(col)
            } catch (e: Exception) {
                def
            }
        }

        private fun getFloat(c: Cursor, col: Int): Float {
            if (col == -1) return Float.NaN
            return try {
                if (c.isNull(col)) Float.NaN else c.getFloat(col)
            } catch (e: Exception) {
                Float.NaN
            }
        }

        private fun getLong(c: Cursor, col: Int, def: Long): Long {
            if (col == -1) return def
            return try {
                c.getLong(col)
            } catch (e: Exception) {
                def
            }
        }

        private fun getFormattedValue(value: Float): String {
            if (value.isNaN()) return "-"
            val result = noDigitsFormat.format(value)
            return if (result == "-0") "0" else result
        }
    }
}
