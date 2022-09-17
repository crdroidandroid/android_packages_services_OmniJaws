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

package org.omnirom.omnijaws.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;

import org.omnirom.omnijaws.R;

public class WeatherAppWidgetConfigure extends PreferenceActivity implements Preference.OnPreferenceChangeListener {

    public static final String KEY_COLOR_THEME = "color_theme";
    public static final int COLOR_THEME_DEFAULT = 1;
    public static final int COLOR_THEME_LIGHT = 3;
    public static final int COLOR_THEME_DARK = 2;
    public static final int COLOR_THEME_SYSTEM = 1;
    public static final int COLOR_THEME_TRANSPARENT = 0;

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ListPreference mColorTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID,
        // finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.weather_appwidget_configure);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int value = prefs.getInt(WeatherAppWidgetConfigure.KEY_COLOR_THEME + "_" + mAppWidgetId, WeatherAppWidgetConfigure.COLOR_THEME_DEFAULT);
        mColorTheme = (ListPreference) findPreference(WeatherAppWidgetConfigure.KEY_COLOR_THEME);
        mColorTheme.setValue(String.valueOf(value));
        int idx = mColorTheme.findIndexOfValue(String.valueOf(value));
        mColorTheme.setSummary(mColorTheme.getEntries()[idx]);
        mColorTheme.setOnPreferenceChangeListener(this);
    }

    public void handleOkClick(View v) {
        WeatherAppWidgetProvider.updateAfterConfigure(this, mAppWidgetId);
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    public static void clearPrefs(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(KEY_COLOR_THEME + "_" + id).commit();
    }

    public static void remapPrefs(Context context, int oldId, int newId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        int oldValue = prefs.getInt(KEY_COLOR_THEME + "_" + oldId, COLOR_THEME_DEFAULT);
        prefs.edit().putInt(KEY_COLOR_THEME + "_" + newId, oldValue).commit();
        prefs.edit().remove(KEY_COLOR_THEME + "_" + oldId).commit();
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(mColorTheme)) {
            String newTheme = (String) newValue;
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putInt(WeatherAppWidgetConfigure.KEY_COLOR_THEME + "_" + mAppWidgetId, Integer.valueOf(newTheme)).commit();
            int idx = mColorTheme.findIndexOfValue(newTheme);
            mColorTheme.setSummary(mColorTheme.getEntries()[idx]);
            return true;
        }
        return false;
    }
}
