package com.docklet.shell.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persisted background-message subscriptions and connection state. */
public final class MessageSubscriptionStore {

    private static final String PREFS = "docklet_message_subscriptions";
    private static final String KEY_ITEMS = "items";
    private static final String PREFS_STATE = "docklet_message_state";

    private MessageSubscriptionStore() {
    }

    public static synchronized List<MessageSubscription> load(Context context) {
        List<MessageSubscription> result = new ArrayList<>();
        if (context == null) return result;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray items = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < items.length(); i += 1) {
                MessageSubscription item = MessageSubscription.fromJson(
                        items.optJSONObject(i)
                );
                if (item != null) result.add(item);
            }
        } catch (Exception ignored) {
            // A corrupt list must not keep the whole service down.
        }
        return result;
    }

    public static synchronized void save(Context context, List<MessageSubscription> items) {
        if (context == null) return;
        JSONArray array = new JSONArray();
        for (MessageSubscription item : items) {
            if (item != null) array.put(item.toJson());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    public static synchronized void upsert(Context context, MessageSubscription item) {
        if (context == null || item == null) return;
        List<MessageSubscription> items = load(context);
        boolean replaced = false;
        for (int i = 0; i < items.size(); i += 1) {
            if (items.get(i).moduleId.equals(item.moduleId)) {
                items.set(i, item);
                replaced = true;
                break;
            }
        }
        if (!replaced) items.add(item);
        save(context, items);
    }

    public static synchronized void remove(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        List<MessageSubscription> items = load(context);
        for (int i = items.size() - 1; i >= 0; i -= 1) {
            if (items.get(i).moduleId.equals(moduleId)) items.remove(i);
        }
        save(context, items);
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
                .edit().remove(moduleId).apply();
    }

    public static synchronized boolean has(Context context, String moduleId) {
        if (moduleId == null) return false;
        for (MessageSubscription item : load(context)) {
            if (item.moduleId.equals(moduleId)) return true;
        }
        return false;
    }

    public static synchronized MessageSubscription find(Context context, String moduleId) {
        if (moduleId == null) return null;
        for (MessageSubscription item : load(context)) {
            if (item.moduleId.equals(moduleId)) return item;
        }
        return null;
    }

    public static synchronized void setActiveSession(
            Context context, String moduleId, String session
    ) {
        MessageSubscription item = find(context, moduleId);
        if (item == null) return;
        item.activeSession = session == null ? "" : session;
        upsert(context, item);
    }

    public static synchronized void setState(
            Context context, String moduleId, String state, String error
    ) {
        if (context == null || moduleId == null) return;
        JSONObject value = getState(context, moduleId);
        try {
            value.put("state", state == null ? "" : state);
            value.put("error", error == null ? "" : error);
            value.put("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) {
            // Simple values only.
        }
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
                .edit().putString(moduleId, value.toString()).apply();
    }

    public static synchronized void markEvent(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        JSONObject value = getState(context, moduleId);
        try {
            value.put("lastEventAt", System.currentTimeMillis());
        } catch (Exception ignored) {
            // Simple values only.
        }
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
                .edit().putString(moduleId, value.toString()).apply();
    }

    public static synchronized JSONObject getState(Context context, String moduleId) {
        if (context == null || moduleId == null) return new JSONObject();
        String raw = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
                .getString(moduleId, "");
        try {
            return raw == null || raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
