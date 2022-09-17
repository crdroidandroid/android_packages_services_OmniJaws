/*
 * Copyright (C) 2017-2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnijaws;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.crdroid.OmniJawsClient;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DetailedWeatherView extends FrameLayout {

    static final String TAG = "DetailedWeatherView";
    static final boolean DEBUG = false;

    private ImageView mCurrentImage;
    private ImageView mForecastImage0;
    private ImageView mForecastImage1;
    private ImageView mForecastImage2;
    private ImageView mForecastImage3;
    private ImageView mForecastImage4;
    private TextView mForecastText0;
    private TextView mForecastText1;
    private TextView mForecastText2;
    private TextView mForecastText3;
    private TextView mForecastText4;
    private OmniJawsClient mWeatherClient;
    private View mCurrentView;
    private TextView mCurrentText;
    private View mProgressContainer;
    private TextView mStatusMsg;
    private View mEmptyView;
    private ImageView mEmptyViewImage;
    private View mWeatherLine;
    private TextView mProviderName;
    private TextView mCurrentWind;
    private TextView mCurrentHumidity;
    private TextView mCurrentLocation;
    private TextView mCurrentProvider;
    private TextView mCurrentWindDirection;
    private TextView mLastUpdate;
    private WeatherActivity mActivity;
    private TextView mForecastData0;
    private TextView mForecastData1;
    private TextView mForecastData2;
    private TextView mForecastData3;
    private TextView mForecastData4;

    public DetailedWeatherView(Context context) {
        this(context, null);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetailedWeatherView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setWeatherClient(OmniJawsClient client) {
        mWeatherClient = client;
    }

    public void setActivity(WeatherActivity activity) {
        mActivity = activity;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mProgressContainer = findViewById(R.id.progress_container);
        mForecastImage0 = (ImageView) findViewById(R.id.forecast_image_0);
        mForecastImage1 = (ImageView) findViewById(R.id.forecast_image_1);
        mForecastImage2 = (ImageView) findViewById(R.id.forecast_image_2);
        mForecastImage3 = (ImageView) findViewById(R.id.forecast_image_3);
        mForecastImage4 = (ImageView) findViewById(R.id.forecast_image_4);
        mForecastText0 = (TextView) findViewById(R.id.forecast_text_0);
        mForecastText1 = (TextView) findViewById(R.id.forecast_text_1);
        mForecastText2 = (TextView) findViewById(R.id.forecast_text_2);
        mForecastText3 = (TextView) findViewById(R.id.forecast_text_3);
        mForecastText4 = (TextView) findViewById(R.id.forecast_text_4);
        mForecastData0 = (TextView) findViewById(R.id.forecast_data_0);
        mForecastData1 = (TextView) findViewById(R.id.forecast_data_1);
        mForecastData2 = (TextView) findViewById(R.id.forecast_data_2);
        mForecastData3 = (TextView) findViewById(R.id.forecast_data_3);
        mForecastData4 = (TextView) findViewById(R.id.forecast_data_4);
        mCurrentView = findViewById(R.id.current);
        mCurrentImage = (ImageView) findViewById(R.id.current_image);
        mCurrentText = (TextView) findViewById(R.id.current_text);
        mCurrentWind = (TextView) findViewById(R.id.current_wind);
        mCurrentHumidity = (TextView) findViewById(R.id.current_humidity);
        mCurrentLocation = (TextView) findViewById(R.id.current_location);
        mCurrentProvider = (TextView) findViewById(R.id.current_provider);
        mCurrentWindDirection = (TextView) findViewById(R.id.current_wind_direction);
        mLastUpdate = findViewById(R.id.last_update);
        mStatusMsg = (TextView) findViewById(R.id.status_msg);
        mEmptyView = findViewById(R.id.status_view);
        mEmptyViewImage = (ImageView) findViewById(R.id.empty_weather_image);
        mWeatherLine = findViewById(R.id.current_weather);
    }

    public void updateWeatherData(OmniJawsClient.WeatherInfo weatherData) {
        if (DEBUG) Log.d(TAG, "updateWeatherData");
        mActivity.updateHourColor();
        mProgressContainer.setVisibility(View.GONE);

        boolean serviceDisabled = !mWeatherClient.isOmniJawsEnabled();
        if (weatherData == null || serviceDisabled) {
            setErrorView();
            if (!serviceDisabled) {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
                mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_waiting));
            } else {
                mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
                mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_disabled));
            }
            return;
        }
        mEmptyView.setVisibility(View.GONE);
        mWeatherLine.setVisibility(View.VISIBLE);

        Long timeStamp = weatherData.timeStamp;
        String format = DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(format);

        mCurrentWind.setText(weatherData.windSpeed + " " + weatherData.windUnits);
        mCurrentWindDirection.setText(weatherData.pinWheel);
        mCurrentHumidity.setText(weatherData.humidity);
        mCurrentLocation.setText(weatherData.city);
        mCurrentProvider.setText(weatherData.provider);
        mLastUpdate.setText(sdf.format(timeStamp));

        sdf = new SimpleDateFormat("EE");
        Calendar cal = Calendar.getInstance();
        String dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        Drawable d = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(0).conditionCode);
        mForecastImage0.setImageDrawable(d);
        mForecastText0.setText(dayShort);
        mForecastData0.setText(getWeatherDataString(weatherData.forecasts.get(0).low, weatherData.forecasts.get(0).high,
                weatherData.tempUnits));

        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(1).conditionCode);
        mForecastImage1.setImageDrawable(d);
        mForecastText1.setText(dayShort);
        mForecastData1.setText(getWeatherDataString(weatherData.forecasts.get(1).low, weatherData.forecasts.get(1).high,
                weatherData.tempUnits));
        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(2).conditionCode);
        mForecastImage2.setImageDrawable(d);
        mForecastText2.setText(dayShort);
        mForecastData2.setText(getWeatherDataString(weatherData.forecasts.get(2).low, weatherData.forecasts.get(2).high,
                weatherData.tempUnits));
        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(3).conditionCode);
        mForecastImage3.setImageDrawable(d);
        mForecastText3.setText(dayShort);
        mForecastData3.setText(getWeatherDataString(weatherData.forecasts.get(3).low, weatherData.forecasts.get(3).high,
                weatherData.tempUnits));
        cal.add(Calendar.DATE, 1);
        dayShort = sdf.format(new Date(cal.getTimeInMillis()));

        d = mWeatherClient.getWeatherConditionImage(weatherData.forecasts.get(4).conditionCode);
        mForecastImage4.setImageDrawable(d);
        mForecastText4.setText(dayShort);
        mForecastData4.setText(getWeatherDataString(weatherData.forecasts.get(4).low, weatherData.forecasts.get(4).high,
                weatherData.tempUnits));
        d = mWeatherClient.getWeatherConditionImage(weatherData.conditionCode);
        mCurrentImage.setImageDrawable(d);
        mCurrentText.setText(weatherData.temp + " " + weatherData.tempUnits);
    }

    private String getWeatherDataString(String min, String max, String tempUnits) {
        if (max != null) {
            return min + "/" + max + tempUnits;
        } else {
            return min + tempUnits;
        }
    }

    private void setErrorView() {
        mEmptyView.setVisibility(View.VISIBLE);
        mWeatherLine.setVisibility(View.GONE);
    }

    public void weatherError(int errorReason) {
        if (DEBUG) Log.d(TAG, "weatherError " + errorReason);
        mProgressContainer.setVisibility(View.GONE);
        setErrorView();

        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_off);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_disabled));
        } else if (errorReason == OmniJawsClient.EXTRA_ERROR_LOCATION) {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_error_location));
        } else if (errorReason == OmniJawsClient.EXTRA_ERROR_NETWORK) {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_error_network));
        } else {
            mEmptyViewImage.setImageResource(R.drawable.ic_qs_weather_default_on);
            mStatusMsg.setText(getResources().getString(R.string.omnijaws_service_error_long));
        }
    }

    public void startProgress() {
        mEmptyView.setVisibility(View.GONE);
        mWeatherLine.setVisibility(View.GONE);
        mProgressContainer.setVisibility(View.VISIBLE);
    }

    public void stopProgress() {
        mProgressContainer.setVisibility(View.GONE);
    }

    public void forceRefresh() {
        if (mWeatherClient.isOmniJawsEnabled()) {
            startProgress();
            ContentValues values = new ContentValues();
            values.put(WeatherContentProvider.COLUMN_FORCE_REFRESH, true);
            getContext().getContentResolver().update(OmniJawsClient.CONTROL_URI,
                    values, "", null);

            //WeatherUpdateService.scheduleUpdateNow(getContext());
        }
    }
}
