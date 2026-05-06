package org.omnirom.omnijaws.icon;
import org.omnirom.omnijaws.Config;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;

import com.android.internal.util.crdroid.OmniJawsClient;


public class IconProvider {

    public static final int ICON_THEME_SYSTEM = 1;
    public static final int ICON_THEME_DARK = 2;
    public static final int ICON_THEME_LIGHT = 3;
    public static final int ICON_THEME_DEFAULT = ICON_THEME_SYSTEM;

    public static final int WIDGET_ICON_THEME_DARK = ICON_THEME_DARK;
    public static final int WIDGET_ICON_THEME_LIGHT = ICON_THEME_LIGHT;
    public static final int WIDGET_ICON_THEME_FOLLOW_APP = 4;
    public static final int WIDGET_ICON_THEME_FOLLOW_WIDGET = 5;
    public static final int WIDGET_ICON_THEME_DEFAULT = WIDGET_ICON_THEME_FOLLOW_APP;

    public static int getAppIconNightMode(int appIconTheme) {
        return getThemeMode(appIconTheme);
    }

    public static int getWidgetIconNightMode(
            int widgetIconTheme, int appIconTheme, int widgetColorTheme) {
        int iconTheme;
        switch (widgetIconTheme) {
            case WIDGET_ICON_THEME_FOLLOW_APP:
                iconTheme = appIconTheme;
                break;

            case WIDGET_ICON_THEME_FOLLOW_WIDGET:
                iconTheme = widgetColorTheme;
                break;

            case WIDGET_ICON_THEME_LIGHT:
                return Configuration.UI_MODE_NIGHT_NO;
            case WIDGET_ICON_THEME_DARK:
                return Configuration.UI_MODE_NIGHT_YES;
            default:
                iconTheme = widgetIconTheme;
                break;
        }
        return getThemeMode(iconTheme);
    }

    private static int getThemeMode(int theme) {
        switch (theme) {
            case ICON_THEME_LIGHT:
                return Configuration.UI_MODE_NIGHT_NO;
            case ICON_THEME_DARK:
                return Configuration.UI_MODE_NIGHT_YES;
            case ICON_THEME_SYSTEM:
            default:
                return Configuration.UI_MODE_NIGHT_UNDEFINED;
        }
    }

    public static Drawable getConditionDrawable(Context context, int conditionCode) {
        IconPack iconPack = IconPack.fromConfig(context);
        int iconNightMode = getAppIconNightMode(Config.getIconTheme(context));

        if (iconPack != null
                && iconPack.supportsTheming
                && iconPack.canUseLocalResources(context)) {
            Context iconContext = getIconContext(context, iconNightMode);
            int resId = iconPack.getLocalResId(iconContext, conditionCode);
            if (resId != IconPack.RESOURCE_NOT_FOUND) {
                return iconContext.getDrawable(resId);
            }
        }

        return OmniJawsClient.get().getWeatherConditionImage(context, conditionCode);
    }

    public static Context getIconContext(Context context, int nightMode) {
        if (nightMode == Configuration.UI_MODE_NIGHT_UNDEFINED) {
            return context;
        }

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return context.createConfigurationContext(config);
    }
}
