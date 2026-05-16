/*
 *  Copyright (C) 2021 The OmniROM Project
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
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import org.omnirom.omnijaws.BaseActivity;
import org.omnirom.omnijaws.R;

import androidx.core.view.WindowCompat;

public class WeatherAppWidgetConfigureActivity extends BaseActivity {

    private int appWidgetId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.widget_configure_activity);
        setSupportActionBar(findViewById(R.id.toolbar));

        setupInsets(findViewById(R.id.main_layout));

        setTitle(R.string.weather_widget);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            // If this activity was started with an intent without an app widget ID,
            // finish with an error.
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(RESULT_CANCELED);
                finish();
            }
        }

        findViewById(R.id.ok_button).setOnClickListener(v -> {
            WeatherAppWidgetProvider.updateAfterConfigure(getApplicationContext(), appWidgetId);
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });

        findViewById(R.id.cancel_button).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new WeatherAppWidgetConfigureFragment(appWidgetId)).commit();
    }
}
