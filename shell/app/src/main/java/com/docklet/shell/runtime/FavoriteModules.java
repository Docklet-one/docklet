package com.docklet.shell.runtime;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Manually pinned modules, ordered by pin time. */
public final class FavoriteModules {

    private static final String PREFS = "docklet_shell";
    private static final String KEY = "favorite_modules";
    private static final int LIMIT = 24;

    private FavoriteModules() {
    }

    public static boolean contains(Context context, String moduleId) {
        return list(context).contains(moduleId);
    }

    public static boolean toggle(Context context, String moduleId) {
        if (context == null || moduleId == null || moduleId.isEmpty()) return false;
        List<String> current = list(context);
        boolean added;
        if (current.contains(moduleId)) {
            current.remove(moduleId);
            added = false;
        } else {
            current.add(0, moduleId);
            added = true;
            if (current.size() > LIMIT) current = new ArrayList<>(current.subList(0, LIMIT));
        }
        StringBuilder joined = new StringBuilder();
        for (String id : current) {
            if (joined.length() > 0) joined.append('|');
            joined.append(id);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, joined.toString())
                .apply();
        return added;
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
