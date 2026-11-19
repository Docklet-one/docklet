package com.docklet.shell.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

/** One module's background-message subscription. Metadata only, never message bodies. */
public final class MessageSubscription {

    public final String moduleId;
    public final String moduleName;
    public final String endpoint;
    public final String transport;
    public final JSONObject headers;
    public final JSONArray eventTypes;
    public final String foreground;
    public final String sessionField;
    public final String dedupeBy;
    public final boolean persist;
    public String activeSession;

    public MessageSubscription(
            String moduleId,
            String moduleName,
            String endpoint,
            String transport,
            JSONObject headers,
            JSONArray eventTypes,
            String foreground,
            String sessionField,
            String dedupeBy,
            boolean persist,
            String activeSession
    ) {
        this.moduleId = moduleId;
        this.moduleName = moduleName;
        this.endpoint = endpoint;
        this.transport = transport;
        this.headers = headers == null ? new JSONObject() : headers;
        this.eventTypes = eventTypes == null ? new JSONArray() : eventTypes;
        this.foreground = foreground;
        this.sessionField = sessionField;
        this.dedupeBy = dedupeBy;
        this.persist = persist;
        this.activeSession = activeSession;
    }

    public boolean isSessionsPolling() {
        return "sessions".equalsIgnoreCase(transport)
                || endpoint.contains("/sessions");
    }

    public boolean allowsType(String type) {
        if (eventTypes.length() == 0) return true;
        for (int i = 0; i < eventTypes.length(); i += 1) {
            if (type.equals(eventTypes.optString(i))) return true;
        }
        return false;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("moduleId", moduleId);
            json.put("moduleName", moduleName);
            json.put("endpoint", endpoint);
            json.put("transport", transport);
            json.put("headers", headers);
            json.put("eventTypes", eventTypes);
            json.put("foreground", foreground);
            json.put("sessionField", sessionField);
            json.put("dedupeBy", dedupeBy);
            json.put("persist", persist);
            json.put("activeSession", activeSession == null ? "" : activeSession);
        } catch (Exception ignored) {
            // Values are simple strings and booleans.
        }
        return json;
    }

    public static MessageSubscription fromJson(JSONObject json) {
        if (json == null) return null;
        String moduleId = json.optString("moduleId", "");
        String endpoint = json.optString("endpoint", "");
        if (moduleId.isEmpty() || endpoint.isEmpty()) return null;
        return new MessageSubscription(
                moduleId,
                json.optString("moduleName", "模块"),
                endpoint,
                json.optString("transport", "sse"),
                json.optJSONObject("headers"),
                json.optJSONArray("eventTypes"),
                json.optString("foreground", "silent"),
                json.optString("sessionField", "session"),
                json.optString("dedupeBy", "eventId"),
                json.optBoolean("persist", true),
                json.optString("activeSession", "")
        );
    }
}
