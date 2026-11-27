package org.omnirom.omnijaws;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NetworkUtils.init(this);
        DynamicColors.applyToActivitiesIfAvailable(
                this,
                new DynamicColorsOptions.Builder().setThemeOverlay(R.style.AppTheme_Overlay).build()
        );
    }
}
