/*
 *  Copyright (C) 2017 The OmniROM Project
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

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.internal.util.crdroid.OmniJawsClient;
import org.omnirom.omnijaws.widget.WeatherAppWidgetProvider;

import java.util.ArrayList;
import java.util.List;

import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

public class SettingsFragment extends PreferenceFragment implements OnPreferenceChangeListener,
        WeatherLocationTask.Callback, OmniJawsClient.OmniJawsObserver {

    private static final String CHRONUS_ICON_PACK_INTENT = "com.dvtonder.chronus.ICON_PACK";
    private static final String DEFAULT_WEATHER_ICON_PACKAGE = "org.omnirom.omnijaws.outline";

    private SharedPreferences mPrefs;
    private ListPreference mProvider;
    private CheckBoxPreference mCustomLocation;
    private ListPreference mUnits;
    private SwitchPreference mEnable;
    private boolean mTriggerPermissionCheck;
    private ListPreference mUpdateInterval;
    private CustomLocationPreference mLocation;
    private ListPreference mWeatherIconPack;
    private Preference mUpdateStatus;
    private Handler mHandler = new Handler();
    protected boolean mShowIconPack = true;
    private EditTextPreference mOwmKey;
    private OmniJawsClient mWeatherClient;

    private static final String PREF_KEY_CUSTOM_LOCATION_CITY = "weather_custom_location_city";
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private static final String WEATHER_ICON_PACK = "weather_icon_pack";
    private static final String PREF_KEY_UPDATE_STATUS = "update_status";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mWeatherClient = new OmniJawsClient(getContext());

        doLoadPreferences();
    }

    private void doLoadPreferences() {
        addPreferencesFromResource(R.xml.settings);

        final PreferenceScreen prefScreen = getPreferenceScreen();
        mEnable = (SwitchPreference) findPreference(Config.PREF_KEY_ENABLE);
        mEnable.setChecked(Config.isEnabled(getContext()));
        mEnable.setOnPreferenceChangeListener(this);

        mCustomLocation = (CheckBoxPreference) findPreference(Config.PREF_KEY_CUSTOM_LOCATION);

        mProvider = (ListPreference) findPreference(Config.PREF_KEY_PROVIDER);
        mProvider.setOnPreferenceChangeListener(this);
        int idx = mProvider.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_PROVIDER, "0"));
        if (idx == -1) {
            idx = 0;
        }
        mProvider.setValueIndex(idx);
        mProvider.setSummary(mProvider.getEntries()[idx]);

        mUnits = (ListPreference) findPreference(Config.PREF_KEY_UNITS);
        mUnits.setOnPreferenceChangeListener(this);
        idx = mUnits.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UNITS, "0"));
        if (idx == -1) {
            idx = 0;
        }
        mUnits.setValueIndex(idx);
        mUnits.setSummary(mUnits.getEntries()[idx]);

        mUpdateInterval = (ListPreference) findPreference(Config.PREF_KEY_UPDATE_INTERVAL);
        mUpdateInterval.setOnPreferenceChangeListener(this);
        idx = mUpdateInterval.findIndexOfValue(mPrefs.getString(Config.PREF_KEY_UPDATE_INTERVAL, "2"));
        if (idx == -1) {
            idx = 0;
        }
        mUpdateInterval.setValueIndex(idx);
        mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);

        mLocation = (CustomLocationPreference) findPreference(PREF_KEY_CUSTOM_LOCATION_CITY);
        if (mPrefs.getBoolean(Config.PREF_KEY_ENABLE, false)
                && !mPrefs.getBoolean(Config.PREF_KEY_CUSTOM_LOCATION, false)) {
            checkLocationEnabled();
        }
        initCustomLocationPreference();

        mWeatherIconPack = (ListPreference) findPreference(WEATHER_ICON_PACK);

        if (mShowIconPack) {
            String settingHeaderPackage = Config.getIconPack(getContext());
            List<String> entries = new ArrayList<String>();
            List<String> values = new ArrayList<String>();
            getAvailableWeatherIconPacks(entries, values);
            mWeatherIconPack.setEntries(entries.toArray(new String[entries.size()]));
            mWeatherIconPack.setEntryValues(values.toArray(new String[values.size()]));

            int valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            if (valueIndex == -1) {
                // no longer found
                settingHeaderPackage = DEFAULT_WEATHER_ICON_PACKAGE;
                Config.setIconPack(getContext(), settingHeaderPackage);
                valueIndex = mWeatherIconPack.findIndexOfValue(settingHeaderPackage);
            }
            mWeatherIconPack.setValueIndex(valueIndex >= 0 ? valueIndex : 0);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntry());
            mWeatherIconPack.setOnPreferenceChangeListener(this);
        } else {
            prefScreen.removePreference(mWeatherIconPack);
        }
        mUpdateStatus = findPreference(PREF_KEY_UPDATE_STATUS);

        mOwmKey = (EditTextPreference) findPreference(Config.PREF_KEY_OWM_KEY);
        final String customKey = Config.getOwmKey(getContext());
        mOwmKey.setSummary(TextUtils.isEmpty(customKey) ?
                getResources().getString(R.string.service_disabled) : customKey);
        mOwmKey.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mWeatherClient.addObserver(this);
        // values can be changed from outside
        getPreferenceScreen().removeAll();
        doLoadPreferences();
        if (mTriggerPermissionCheck) {
            checkLocationPermissions(true);
            mTriggerPermissionCheck = false;
        }
        queryAndUpdateWeather();
    }

    @Override
    public void onPause() {
        super.onPause();
        mWeatherClient.removeObserver(this);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mCustomLocation) {
            if (!mCustomLocation.isChecked()) {
                checkLocationEnabled();
            }
            forceRefreshWeatherSettings();
            return true;
        } else if (preference == mUpdateStatus) {
            forceRefreshWeatherSettings();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mProvider) {
            String value = (String) newValue;
            int idx = mProvider.findIndexOfValue(value);
            mProvider.setSummary(mProvider.getEntries()[idx]);
            mProvider.setValueIndex(idx);
            if (mCustomLocation.isChecked() && Config.getLocationName(getContext()) != null) {
                // city ids are provider specific - so we need to recheck
                new WeatherLocationTask(getContext(), Config.getLocationName(getContext()), this).execute();
            } else {
                forceRefreshWeatherSettings();
            }
            return true;
        } else if (preference == mUnits) {
            String value = (String) newValue;
            int idx = mUnits.findIndexOfValue(value);
            mUnits.setSummary(mUnits.getEntries()[idx]);
            mUnits.setValueIndex(idx);
            forceRefreshWeatherSettings();
            return true;
        } else if (preference == mUpdateInterval) {
            String value = (String) newValue;
            int idx = mUpdateInterval.findIndexOfValue(value);
            mUpdateInterval.setSummary(mUpdateInterval.getEntries()[idx]);
            mUpdateInterval.setValueIndex(idx);
            forceRefreshWeatherSettings();
            return true;
        } else if (preference == mWeatherIconPack) {
            String value = (String) newValue;
            Config.setIconPack(getContext(), value);
            int valueIndex = mWeatherIconPack.findIndexOfValue(value);
            mWeatherIconPack.setSummary(mWeatherIconPack.getEntries()[valueIndex]);
            forceRefreshWeatherSettings();
            return true;
        } else if (preference == mOwmKey) {
            String value = (String) newValue;
            mOwmKey.setSummary(TextUtils.isEmpty(value) ?
                    getResources().getString(R.string.service_disabled) : value);
            forceRefreshWeatherSettings();
            return true;
        } else if (preference == mEnable) {
            boolean value = (Boolean) newValue;
            Config.setEnabled(getContext(), value);
            if (value) {
                enableService();
                if (!mCustomLocation.isChecked()) {
                    checkLocationEnabledInitial();
                } else {
                    forceRefreshWeatherSettings();
                }
            } else {
                disableService();
            }
            return true;
        }
        return false;
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        mTriggerPermissionCheck = true;
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    private void checkLocationPermissions(boolean force) {
        if (getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || getContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        } else {
            if (force) {
                forceRefreshWeatherSettings();
            }
            queryAndUpdateWeather();
        }
    }

    private boolean doCheckLocationEnabled() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, -1) != Settings.Secure.LOCATION_MODE_OFF;
    }

    private void checkLocationEnabled() {
        if (!doCheckLocationEnabled()) {
            showDialog();
        } else {
            checkLocationPermissions(false);
        }
    }

    private void checkLocationEnabledInitial() {
        if (!doCheckLocationEnabled()) {
            showDialog();
        } else {
            checkLocationPermissions(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    forceRefreshWeatherSettings();
                }
                break;
            }
        }
    }

    private void disableService() {
        // stop any pending
        WeatherUpdateService.cancelAllUpdate(getContext());
        WeatherAppWidgetProvider.disableAllWidgets(getContext());
    }

    private void enableService() {
        WeatherUpdateService.scheduleUpdatePeriodic(getContext());
    }

    @Override
    public void applyLocation(WeatherInfo.WeatherLocation result) {
        Config.setLocationId(getContext(), result.id);
        Config.setLocationName(getContext(), result.city);
        mLocation.setText(result.city);
        mLocation.setSummary(result.city);
        forceRefreshWeatherSettings();
    }

    private void initCustomLocationPreference() {
        String city = Config.getLocationName(getContext());
        if (!TextUtils.isEmpty(city)) {
            mLocation.setText(city);
            mLocation.setSummary(city);
        } else {
            mLocation.setSummary(R.string.weather_custom_location_missing);
        }
    }

    private void getAvailableWeatherIconPacks(List<String> entries, List<String> values) {
        Intent i = new Intent();
        PackageManager packageManager = getContext().getPackageManager();
        i.setAction("org.omnirom.WeatherIconPack");
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                values.add(0, r.activityInfo.name);
            } else {
                values.add(r.activityInfo.name);
            }
            String label = r.activityInfo.loadLabel(packageManager).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            if (packageName.equals(DEFAULT_WEATHER_ICON_PACKAGE)) {
                entries.add(0, label);
            } else {
                entries.add(label);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(CHRONUS_ICON_PACK_INTENT);
        for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
            String packageName = r.activityInfo.packageName;
            values.add(packageName + ".weather");
            String label = r.activityInfo.loadLabel(packageManager).toString();
            if (label == null) {
                label = r.activityInfo.packageName;
            }
            entries.add(label);
        }
    }

    @Override
    public void weatherUpdated() {
        queryAndUpdateWeather();
    }

    @Override
    public void weatherError(int errorReason) {
        String errorString = null;
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            errorString = getResources().getString(R.string.omnijaws_service_disabled);
        } else if (errorReason == OmniJawsClient.EXTRA_ERROR_LOCATION) {
            errorString = getResources().getString(R.string.omnijaws_service_error_location);
        } else if (errorReason == OmniJawsClient.EXTRA_ERROR_NETWORK) {
            errorString = getResources().getString(R.string.omnijaws_service_error_network);
        } else {
            errorString = getResources().getString(R.string.omnijaws_service_error_long);
        }
        if (errorString != null) {
            final String s = errorString;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mUpdateStatus != null) {
                        mUpdateStatus.setSummary(s);
                    }
                }
            });
        }
    }

    private void queryAndUpdateWeather() {
        mWeatherClient.queryWeather();
        if (mWeatherClient.getWeatherInfo() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mUpdateStatus != null) {
                        mUpdateStatus.setSummary(mWeatherClient.getWeatherInfo().getLastUpdateTime());
                    }
                }
            });
        }
    }

    private void forceRefreshWeatherSettings() {
        WeatherUpdateService.scheduleUpdateNow(getContext());
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        CustomLocationPreference.CustomLocationPreferenceDialogFragment f = null;
        if (preference instanceof CustomLocationPreference) {
            f = CustomLocationPreference.CustomLocationPreferenceDialogFragment
                    .newInstance(preference.getKey());
        } else {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        f.setTargetFragment(this, 0);
        f.show(getFragmentManager(), "dialog_preference");
    }
}
