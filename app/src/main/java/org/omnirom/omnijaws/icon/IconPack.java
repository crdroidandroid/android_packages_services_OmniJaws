package org.omnirom.omnijaws.icon;

import android.content.Context;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

import org.omnirom.omnijaws.Config;

public class IconPack {
    public enum Type {
        OMNI,
        CHRONUS
    }
    public static final int RESOURCE_NOT_FOUND = 0;
    private static final int INDEX_NOT_FOUND = -1;

    private static final String CHRONUS_SUFFIX = ".weather";
    private static final String META_SUPPORTS_THEMES =
            "org.omnirom.omnijaws.SUPPORTS_THEMES";

    public final String value;
    public final String packageName;
    public final String prefix;
    public final Type type;
    public final boolean supportsTheming;

    private IconPack(String value, String packageName,
            String prefix, Type type, boolean supportsTheming) {
        this.value = value;
        this.packageName = packageName;
        this.prefix = prefix;
        this.type = type;
        this.supportsTheming = supportsTheming;
    }

    public static IconPack fromConfig(Context context) {
        return fromValue(context, Config.getIconPack(context));
    }

    private static IconPack fromValue(Context context, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // At this moment there are no themed Chronus packs and it would need additional work to
        // update them reliably, so supportsTheming is defaulted to false for now
        if (value.endsWith(CHRONUS_SUFFIX)) {
            String packageName = value.substring(0, value.length() - CHRONUS_SUFFIX.length());
            return new IconPack(value, packageName, "weather",
                    Type.CHRONUS, false);
        }

        int idx = value.lastIndexOf(".");
        if (idx == INDEX_NOT_FOUND) {
            return null;
        }

        String packageName = value.substring(0, idx);
        String prefix = value.substring(idx + 1);
        boolean supportsTheming = readSupportsTheming(context, packageName, value);

        return new IconPack(value, packageName, prefix,
                Type.OMNI, supportsTheming);
    }

    public boolean canUseLocalResources(Context context) {
        return type == Type.OMNI && packageName.equals(context.getPackageName());
    }

    public int getLocalResId(Context resourcesContext, int conditionCode) {
        return resourcesContext.getResources().getIdentifier(
                prefix + "_" + conditionCode,
                "drawable",
                resourcesContext.getPackageName());
    }

    private static boolean readSupportsTheming(Context context, String packageName, String className) {
        try {
            ComponentName component = new ComponentName(packageName, className);
            ActivityInfo info = context.getPackageManager().getActivityInfo(
                    component, PackageManager.GET_META_DATA);
            return info.metaData != null
                    && info.metaData.getBoolean(META_SUPPORTS_THEMES, false);
        } catch (Exception e) {
            return false;
        }
    }
}
