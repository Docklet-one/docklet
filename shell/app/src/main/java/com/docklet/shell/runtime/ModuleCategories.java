package com.docklet.shell.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Category ids are stable; names are only labels. */
public final class ModuleCategories {

    /** 预置项：收藏夹入口（最近 + 收藏），不是可归属的分类。 */
    public static final String FAVORITES_ID = "favorites";
    public static final String FAVORITES_NAME = "收藏";
    public static final String FAVORITES_ICON = "heart";
    /** 预置项：未归类模块的容器，新装模块默认落在这里。 */
    public static final String TEMPORARY_ID = "temporary";
    public static final String TEMPORARY_NAME = "临时";
    public static final String TEMPORARY_ICON = "inbox";
    public static final String FALLBACK_ICON = "folder";
    public static final int MAX_CATEGORIES = 7;

    private static final String PREFS = "docklet_shell";
    private static final String KEY_CUSTOM = "module_categories_v3";
    private static final String KEY_DEFAULT_NAME = "default_category_name";
    private static final String KEY_ASSIGN = "module_category:";
    private static final String KEY_ORDER = "module_category_order:";
    private static final String MIGRATED = "category_ids_renamed_v1";
    private static volatile boolean migrated;

    public static final class Category {
        public final String id;
        public final String name;
        public final String icon;

        Category(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon == null || icon.isEmpty() ? FALLBACK_ICON : icon;
        }
    }

    private ModuleCategories() {
    }

    /**
     * Older builds stored the preset ids as "default" / "__unclassified__".
     * Rewrite them once so existing installs keep their categories.
     */
    private static synchronized void ensureMigrated(Context context) {
        if (migrated || context == null) return;
        migrated = true;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(MIGRATED, false)) return;
        Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof String)) continue;
            String text = (String) value;
            String nextKey = key
                    .replace(":default", ":" + FAVORITES_ID)
                    .replace(":__unclassified__", ":" + TEMPORARY_ID);
            String nextValue = "default".equals(text) ? FAVORITES_ID
                    : "__unclassified__".equals(text) ? TEMPORARY_ID
                    : text;
            if (!nextKey.equals(key)) {
                editor.remove(key);
                editor.putString(nextKey, nextValue);
            } else if (!nextValue.equals(text)) {
                editor.putString(key, nextValue);
            }
        }
        editor.remove(KEY_DEFAULT_NAME);
        editor.putBoolean(MIGRATED, true);
        editor.apply();
    }

    public static List<Category> list(Context context) {
        ensureMigrated(context);
        List<Category> result = new ArrayList<>();
        if (context == null) return result;
        result.add(new Category(FAVORITES_ID, FAVORITES_NAME, FAVORITES_ICON));
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                result.add(new Category(
                        parts[0],
                        parts[1],
                        parts.length >= 3 ? parts[2] : FALLBACK_ICON
                ));
            }
        }
        return result;
    }

    public static List<Category> customList(Context context) {
        List<Category> result = new ArrayList<>();
        for (Category category : list(context)) {
            if (!FAVORITES_ID.equals(category.id)) result.add(category);
        }
        return result;
    }

    public static boolean isCustom(Context context, String id) {
        if (id == null) return false;
        for (Category category : customList(context)) {
            if (category.id.equals(id)) return true;
        }
        return false;
    }

    public static String nameOf(Context context, String id) {
        if (TEMPORARY_ID.equals(id)) return TEMPORARY_NAME;
        for (Category category : list(context)) {
            if (category.id.equals(id)) return category.name;
        }
        return FAVORITES_NAME;
    }

    public static String add(Context context, String rawName) {
        String name = normalize(rawName);
        if (context == null || !validName(name)) return null;
        List<Category> categories = customList(context);
        if (categories.size() >= MAX_CATEGORIES) return null;
        for (Category category : categories) {
            if (category.name.equalsIgnoreCase(name)) return null;
        }
        String id = "c-" + Long.toString(System.currentTimeMillis(), 36);
        categories.add(new Category(id, name, FALLBACK_ICON));
        saveCustom(context, categories);
        return id;
    }

    public static boolean rename(Context context, String id, String rawName) {
        String name = normalize(rawName);
        if (context == null || !validName(name)) return false;
        List<Category> categories = list(context);
        boolean found = false;
        for (Category category : categories) {
            if (category.id.equals(id)) {
                found = true;
            } else if (category.name.equalsIgnoreCase(name)) {
                return false;
            }
        }
        if (!found) return false;
        if (FAVORITES_ID.equals(id)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_DEFAULT_NAME, name).apply();
        } else {
            List<Category> updated = new ArrayList<>();
            for (Category category : categories) {
                updated.add(category.id.equals(id)
                        ? new Category(category.id, name, category.icon)
                        : category);
            }
            saveCustom(context, updated);
        }
        return true;
    }

    public static int delete(Context context, String id) {
        if (context == null || FAVORITES_ID.equals(id)) return 0;
        List<Category> categories = list(context);
        boolean found = false;
        List<Category> kept = new ArrayList<>();
        for (Category category : categories) {
            if (category.id.equals(id)) {
                found = true;
            } else {
                kept.add(category);
            }
        }
        if (!found) return 0;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = 0;
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_ASSIGN) && id.equals(entry.getValue())) {
                editor.remove(key);
                count += 1;
            }
        }
        editor.remove(KEY_ORDER + id);
        editor.apply();
        saveCustom(context, kept);
        return count;
    }

    public static int count(Context context) {
        return list(context).size();
    }

    public static boolean moveUp(Context context, String id) {
        if (context == null || id == null || FAVORITES_ID.equals(id)) return false;
        List<Category> categories = list(context);
        int customIndex = 0;
        for (Category category : categories) {
            if (FAVORITES_ID.equals(category.id)) continue;
            if (category.id.equals(id)) {
                if (customIndex == 0) return false;
                break;
            }
            customIndex += 1;
        }
        List<Category> custom = new ArrayList<>();
        for (Category category : categories) {
            if (!FAVORITES_ID.equals(category.id)) custom.add(category);
        }
        int index = -1;
        for (int i = 0; i < custom.size(); i += 1) {
            if (custom.get(i).id.equals(id)) index = i;
        }
        if (index <= 0) return false;
        Category item = custom.remove(index);
        custom.add(index - 1, item);
        saveCustom(context, custom);
        return true;
    }

    public static void reorder(Context context, List<String> orderedIds) {
        if (context == null || orderedIds == null) return;
        List<Category> byId = list(context);
        List<Category> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            if (FAVORITES_ID.equals(id)) continue;
            for (Category category : byId) {
                if (category.id.equals(id) && !ordered.contains(category)) ordered.add(category);
            }
        }
        for (Category category : byId) {
            if (!FAVORITES_ID.equals(category.id) && !ordered.contains(category)) ordered.add(category);
        }
        saveCustom(context, ordered);
    }

    public static String categoryOf(Context context, String moduleId) {
        return categoryOf(context, moduleId, TEMPORARY_ID);
    }

    public static String categoryOf(Context context, String moduleId, String fallback) {
        ensureMigrated(context);
        if (context == null || moduleId == null) return TEMPORARY_ID;
        String id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ASSIGN + moduleId, TEMPORARY_ID);
        if (id == null || id.isEmpty() || FAVORITES_ID.equals(id) || TEMPORARY_ID.equals(id)) {
            return TEMPORARY_ID;
        }
        for (Category category : customList(context)) {
            if (category.id.equals(id)) return id;
        }
        return fallback == null ? TEMPORARY_ID : fallback;
    }

    public static void setCategory(Context context, String moduleId, String categoryId) {
        if (context == null || moduleId == null) return;
        if (categoryId == null || categoryId.isEmpty()
                || FAVORITES_ID.equals(categoryId) || TEMPORARY_ID.equals(categoryId)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_ASSIGN + moduleId).apply();
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ASSIGN + moduleId, categoryId).apply();
    }

    /** Newly assigned modules go to the front of the target category. */
    public static void moveToFront(Context context, String moduleId, String categoryId) {
        if (context == null || moduleId == null) return;
        List<String> order = order(context, categoryId);
        order.remove(moduleId);
        order.add(0, moduleId);
        saveOrder(context, categoryId, order);
    }

    public static List<String> order(Context context, String categoryId) {
        List<String> result = new ArrayList<>();
        if (context == null) return result;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ORDER + categoryId, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String id : raw.split("\\|")) {
            if (!id.isEmpty() && !result.contains(id)) result.add(id);
        }
        return result;
    }

    public static synchronized void clearModule(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().remove(KEY_ASSIGN + moduleId);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_ORDER) || !(entry.getValue() instanceof String)) continue;
            String raw = (String) entry.getValue();
            if (raw == null || raw.isEmpty()) continue;
            List<String> kept = new ArrayList<>();
            for (String id : raw.split("\\|")) {
                if (!id.isEmpty() && !id.equals(moduleId) && !kept.contains(id)) kept.add(id);
            }
            StringBuilder joined = new StringBuilder();
            for (String id : kept) {
                if (joined.length() > 0) joined.append('|');
                joined.append(id);
            }
            editor.putString(key, joined.toString());
        }
        editor.apply();
    }

    public static void saveOrder(Context context, String categoryId, List<String> ids) {
        if (context == null || categoryId == null) return;
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isEmpty() || joined.indexOf("|" + id + "|") >= 0) continue;
            if (joined.length() > 0) joined.append('|');
            joined.append(id);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ORDER + categoryId, joined.toString()).apply();
    }

    public static void sort(Context context, List<com.docklet.platform.InstalledModule> modules,
                            String categoryId) {
        List<String> order = order(context, categoryId);
        if (order.isEmpty()) return;
        final Map<String, Integer> rank = new java.util.HashMap<>();
        for (int i = 0; i < order.size(); i += 1) rank.put(order.get(i), i);
        modules.sort(Comparator.comparingInt(module -> {
            Integer value = rank.get(module.id());
            return value == null ? Integer.MAX_VALUE : value;
        }));
    }

    public static boolean setIcon(Context context, String id, String icon) {
        if (context == null || id == null || FAVORITES_ID.equals(id)) return false;
        List<Category> categories = customList(context);
        boolean found = false;
        List<Category> updated = new ArrayList<>();
        for (Category category : categories) {
            if (category.id.equals(id)) {
                found = true;
                updated.add(new Category(category.id, category.name, icon));
            } else {
                updated.add(category);
            }
        }
        if (!found) return false;
        saveCustom(context, updated);
        return true;
    }

    private static boolean validName(String name) {
        if (name.isEmpty()) return false;
        int length = name.codePointCount(0, name.length());
        return length <= 4 && name.indexOf('|') < 0 && name.indexOf('\n') < 0 && name.indexOf('\t') < 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void saveCustom(Context context, List<Category> categories) {
        StringBuilder joined = new StringBuilder();
        for (Category category : categories) {
            if (FAVORITES_ID.equals(category.id)) continue;
            if (joined.length() > 0) joined.append('\n');
            joined.append(category.id).append('\t').append(category.name)
                    .append('\t').append(category.icon);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM, joined.toString()).apply();
    }
}
