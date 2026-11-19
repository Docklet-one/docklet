package com.docklet.shell.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/** User-level notification switch, separate from the Android permission. */
public final class NotificationPrefs {

    private static final String PREFS = "docklet_shell";
    private static final String KEY_MODULE = "notification_module:";

    private NotificationPrefs() {
    }

    public static boolean moduleEnabled(Context context, String moduleId) {
        if (context == null || moduleId == null) return true;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_MODULE + moduleId, true);
    }

    public static void setModuleEnabled(Context context, String moduleId, boolean enabled) {
        if (context == null || moduleId == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MODULE + moduleId, enabled).apply();
    }

    public static boolean enabledFor(Context context, String moduleId) {
        return moduleEnabled(context, moduleId);
    }
}
