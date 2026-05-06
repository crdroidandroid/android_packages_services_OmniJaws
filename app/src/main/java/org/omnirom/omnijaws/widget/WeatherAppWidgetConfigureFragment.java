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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.omnirom.omnijaws.R;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.omnirom.omnijaws.icon.IconProvider;

public class WeatherAppWidgetConfigureFragment extends PreferenceFragmentCompat
        implements OnPreferenceChangeListener {

    public static final String KEY_COLOR_THEME = "color_theme";
    public static final String KEY_ICON_THEME = "widget_icon_theme";
    public static final int COLOR_THEME_TRANSPARENT = 0;
    public static final int COLOR_THEME_SYSTEM = 1;
    public static final int COLOR_THEME_DARK = 2;
    public static final int COLOR_THEME_LIGHT = 3;
    public static final int COLOR_THEME_DEFAULT = COLOR_THEME_SYSTEM;

    public static final String KEY_BG_TRANS = "bg_transparency";
    public static final int BG_TRANS_SEMI = 1;
    public static final int BG_TRANS_FULL = 2;
    public static final int BG_TRANS_SOLID = 3;
    public static final int BG_TRANS_DEFAULT = BG_TRANS_SEMI;

    private int mAppWidgetId;
    private ListPreference mColorTheme;
    private ListPreference mBgTrans;
    private ListPreference mIconTheme;

    public WeatherAppWidgetConfigureFragment(int appWidgetId) {
        super();
        mAppWidgetId = appWidgetId;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.weather_appwidget_configure);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int value = prefs.getInt(KEY_COLOR_THEME + "_" + mAppWidgetId, COLOR_THEME_DEFAULT);
        mColorTheme = (ListPreference) findPreference(KEY_COLOR_THEME);
        mColorTheme.setValue(String.valueOf(value));
        int idx = mColorTheme.findIndexOfValue(String.valueOf(value));
        mColorTheme.setSummary(mColorTheme.getEntries()[idx]);
        mColorTheme.setOnPreferenceChangeListener(this);

        value = prefs.getInt(KEY_BG_TRANS + "_" + mAppWidgetId, BG_TRANS_DEFAULT);
        mBgTrans = (ListPreference) findPreference(KEY_BG_TRANS);
        mBgTrans.setValue(String.valueOf(value));
        idx = mBgTrans.findIndexOfValue(String.valueOf(value));
        mBgTrans.setSummary(mBgTrans.getEntries()[idx]);
        mBgTrans.setOnPreferenceChangeListener(this);

        value = prefs.getInt(KEY_ICON_THEME + "_" + mAppWidgetId, IconProvider.WIDGET_ICON_THEME_DEFAULT);
        mIconTheme = (ListPreference) findPreference(KEY_ICON_THEME);
        mIconTheme.setValue(String.valueOf(value));
        idx = mIconTheme.findIndexOfValue(String.valueOf(value));
        mIconTheme.setSummary(mIconTheme.getEntries()[idx]);
        mIconTheme.setOnPreferenceChangeListener(this);
    }

    public static void clearPrefs(Context context, int id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .remove(KEY_COLOR_THEME + "_" + id)
                .remove(KEY_BG_TRANS + "_" + id)
                .remove(KEY_ICON_THEME + "_" + id)
                .apply();
    }

    public static void remapPrefs(Context context, int oldId, int newId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int oldThemeValue = prefs.getInt(KEY_COLOR_THEME + "_" + oldId, COLOR_THEME_DEFAULT);
        int oldBgValue = prefs.getInt(KEY_BG_TRANS + "_" + oldId, BG_TRANS_DEFAULT);
        int oldIconThemeValue = prefs.getInt(KEY_ICON_THEME + "_" + oldId, IconProvider.WIDGET_ICON_THEME_DEFAULT);

        prefs.edit()
                .putInt(KEY_COLOR_THEME + "_" + newId, oldThemeValue)
                .remove(KEY_COLOR_THEME + "_" + oldId)
                .putInt(KEY_BG_TRANS + "_" + newId, oldBgValue)
                .remove(KEY_BG_TRANS + "_" + oldId)
                .putInt(KEY_ICON_THEME + "_" + newId, oldIconThemeValue)
                .remove(KEY_ICON_THEME + "_" + oldId)
                .apply();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(mColorTheme)) {
            String newTheme = (String) newValue;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putInt(KEY_COLOR_THEME + "_" + mAppWidgetId, Integer.valueOf(newTheme)).apply();
            int idx = mColorTheme.findIndexOfValue(newTheme);
            mColorTheme.setSummary(mColorTheme.getEntries()[idx]);
            WeatherAppWidgetProvider.updateAfterConfigure(getContext(), mAppWidgetId);
            return true;
        } else if (preference.equals(mBgTrans)) {
            String newTheme = (String) newValue;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putInt(KEY_BG_TRANS + "_" + mAppWidgetId, Integer.valueOf(newTheme)).apply();
            int idx = mBgTrans.findIndexOfValue(newTheme);
            mBgTrans.setSummary(mBgTrans.getEntries()[idx]);
            return true;
        } else if (preference.equals(mIconTheme)) {
            String newTheme = (String) newValue;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putInt(KEY_ICON_THEME + "_" + mAppWidgetId, Integer.valueOf(newTheme)).commit();
            int idx = mIconTheme.findIndexOfValue(newTheme);
            mIconTheme.setSummary(mIconTheme.getEntries()[idx]);
            WeatherAppWidgetProvider.updateAfterConfigure(getContext(), mAppWidgetId);
            return true;
        }
        return false;
    }
}
