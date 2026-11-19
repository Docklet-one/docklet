package com.docklet.shell.runtime;

import android.content.Context;

/** Last-open timestamps, separate from the home recent order. */
public final class ModuleUsage {

    private static final String PREFS = "docklet_shell";
    private static final String KEY_LAST_OPEN = "module_last_open:";

    private ModuleUsage() {
    }

    public static void markOpened(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_OPEN + moduleId, System.currentTimeMillis()).apply();
    }

    public static long lastOpened(Context context, String moduleId) {
        if (context == null || moduleId == null) return 0L;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_OPEN + moduleId, 0L);
    }
}
