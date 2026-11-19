package com.docklet.shell.runtime;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Small recent-module ring stored in the shell preferences. */
public final class RecentModules {

    private static final String PREFS = "docklet_shell";
    private static final String KEY = "recent_modules";
    private static final int LIMIT = 12;

    private RecentModules() {
    }

    public static void record(Context context, String moduleId) {
        if (context == null || moduleId == null || moduleId.isEmpty()) return;
        List<String> current = list(context);
        current.remove(moduleId);
        current.add(0, moduleId);
        if (current.size() > LIMIT) current = new ArrayList<>(current.subList(0, LIMIT));
        StringBuilder joined = new StringBuilder();
        for (String id : current) {
            if (joined.length() > 0) joined.append('|');
            joined.append(id);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, joined.toString())
                .apply();
    }

    public static List<String> list(Context context) {
        List<String> result = new ArrayList<>();
        if (context == null) return result;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String id : raw.split("\\|")) {
            if (!id.isEmpty() && !result.contains(id)) result.add(id);
        }
        return result;
    }
}
