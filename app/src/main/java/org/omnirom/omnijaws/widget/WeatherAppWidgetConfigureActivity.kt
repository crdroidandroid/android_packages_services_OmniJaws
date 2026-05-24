/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnijaws.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

import com.android.axion.compose.theme.AxionTheme

import org.omnirom.omnijaws.ui.WeatherSettingsActivity

class WeatherAppWidgetConfigureActivity : ComponentActivity() {

    private val viewModel: WidgetConfigureViewModel by viewModels()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        viewModel.init(appWidgetId)

        enableEdgeToEdge()
        setContent {
            AxionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    val state by viewModel.uiState.collectAsState()
                    WeatherAppWidgetConfigureScreen(
                        state = state,
                        onColorThemeChanged = viewModel::setColorTheme,
                        onBgTransparencyChanged = viewModel::setBgTransparency,
                        onIconThemeChanged = viewModel::setIconTheme,
                        onOpenWeatherSettings = {
                            startActivity(
                                Intent(this, WeatherSettingsActivity::class.java)
                            )
                        },
                        onConfirm = ::confirmAndFinish,
                        onCancel = ::cancelAndFinish
                    )
                }
            }
        }
    }

    private fun confirmAndFinish() {
        WeatherAppWidgetProvider.updateAfterConfigure(applicationContext, appWidgetId)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
