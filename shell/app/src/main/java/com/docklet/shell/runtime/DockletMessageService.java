package com.docklet.shell.runtime;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.docklet.shell.ModuleRouterActivity;
import com.docklet.shell.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background notification relay. It owns only transport and notification
 * state; it never stores message bodies or runs module JavaScript.
 */
public class DockletMessageService extends Service {

    public static final String ACTION_SUBSCRIBE = "com.docklet.shell.message.SUBSCRIBE";
    public static final String ACTION_UNSUBSCRIBE = "com.docklet.shell.message.UNSUBSCRIBE";
    public static final String ACTION_ACTIVE_SESSION =
            "com.docklet.shell.message.ACTIVE_SESSION";
    public static final String ACTION_FOREGROUND =
            "com.docklet.shell.message.FOREGROUND";
    public static final String ACTION_BACKGROUND =
            "com.docklet.shell.message.BACKGROUND";

    public static final String EXTRA_MODULE_ID = "moduleId";
    public static final String EXTRA_MODULE_NAME = "moduleName";
    public static final String EXTRA_SUBSCRIPTION = "subscription";
    public static final String EXTRA_SESSION = "session";
    public static final String EXTRA_FOREGROUND = "foreground";

    private static final String TAG = "DockletMessage";
    private static final String SERVICE_CHANNEL = "docklet_message_service";
    private static final String MESSAGE_CHANNEL = "docklet_message_events";
    private static final int SERVICE_NOTIFICATION_ID = 7401;

    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> seenEvents = new HashMap<>();
    private static final String PREFS_SEEN = "docklet_message_seen";
    private volatile String foregroundModuleId = "";
    private volatile boolean foregroundStarted;
    private List<MessageSubscription> subscriptions = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        subscriptions = MessageSubscriptionStore.load(this);
        for (MessageSubscription item : subscriptions) {
            activeSessions.put(item.moduleId, item.activeSession == null ? "" : item.activeSession);
        }
        if (!subscriptions.isEmpty()) {
            startForegroundCompat();
            startAllWorkers();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            subscriptions = MessageSubscriptionStore.load(this);
            if (subscriptions.isEmpty()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            startForegroundCompat();
            startAllWorkers();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) return START_STICKY;

        if (ACTION_SUBSCRIBE.equals(action)) {
            String raw = intent.getStringExtra(EXTRA_SUBSCRIPTION);
            MessageSubscription item = null;
            try {
                item = MessageSubscription.fromJson(new JSONObject(raw == null ? "{}" : raw));
            } catch (Exception ignored) {
                // Invalid subscription is rejected below.
            }
            if (item == null) return START_STICKY;
            MessageSubscriptionStore.upsert(this, item);
            subscriptions = MessageSubscriptionStore.load(this);
            activeSessions.put(item.moduleId, item.activeSession == null ? "" : item.activeSession);
            foregroundModuleId = item.moduleId;
            cancelModuleNotification(item.moduleId);
            startForegroundCompat();
            restartWorker(item.moduleId);
            updateServiceNotification();
            return START_STICKY;
        }

        if (ACTION_UNSUBSCRIBE.equals(action)) {
            String moduleId = intent.getStringExtra(EXTRA_MODULE_ID);
            if (moduleId != null) {
                stopWorker(moduleId);
                MessageSubscriptionStore.remove(this, moduleId);
                activeSessions.remove(moduleId);
                if (moduleId.equals(foregroundModuleId)) foregroundModuleId = "";
                cancelModuleNotification(moduleId);
            }
            subscriptions = MessageSubscriptionStore.load(this);
            if (subscriptions.isEmpty()) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
            updateServiceNotification();
            return START_STICKY;
        }

        if (ACTION_ACTIVE_SESSION.equals(action)) {
            String moduleId = intent.getStringExtra(EXTRA_MODULE_ID);
            String session = intent.getStringExtra(EXTRA_SESSION);
            if (moduleId != null) {
                if (session == null) session = "";
                activeSessions.put(moduleId, session);
                MessageSubscriptionStore.setActiveSession(this, moduleId, session);
            }
            if (subscriptions.isEmpty()) {
                // Nothing subscribed: do not keep an idle service alive.
                stopSelf();
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }

        if (ACTION_FOREGROUND.equals(action)) {
            String moduleId = intent.getStringExtra(EXTRA_MODULE_ID);
            if (moduleId != null && MessageSubscriptionStore.has(this, moduleId)) {
                foregroundModuleId = moduleId;
                cancelModuleNotification(moduleId);
                updateServiceNotification();
            }
            return START_STICKY;
        }

        if (ACTION_BACKGROUND.equals(action)) {
            String moduleId = intent.getStringExtra(EXTRA_MODULE_ID);
            if (moduleId != null && moduleId.equals(foregroundModuleId)) {
                foregroundModuleId = "";
                updateServiceNotification();
            }
            return START_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        for (Worker worker : workers.values()) worker.stopWorker();
        workers.clear();
        super.onDestroy();
    }

    private void startAllWorkers() {
        for (MessageSubscription item : subscriptions) {
            if (workers.containsKey(item.moduleId)) continue;
            Worker worker = new Worker(item);
            workers.put(item.moduleId, worker);
            worker.start();
        }
    }

    private void restartWorker(String moduleId) {
        stopWorker(moduleId);
        MessageSubscription item = MessageSubscriptionStore.find(this, moduleId);
        if (item == null) return;
        Worker worker = new Worker(item);
        workers.put(moduleId, worker);
        worker.start();
    }

    private void stopWorker(String moduleId) {
        Worker worker = workers.remove(moduleId);
        if (worker != null) worker.stopWorker();
    }

    private void startForegroundCompat() {
        if (foregroundStarted) {
            updateServiceNotification();
            return;
        }
        Notification notification = buildServiceNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
    }

    private Notification buildServiceNotification() {
        Intent open = new Intent(this, com.docklet.shell.MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                7401,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        int count = subscriptions == null ? 0 : subscriptions.size();
        return new Notification.Builder(this, SERVICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_docklet)
                .setContentTitle("Docklet 后台消息")
                .setContentText(count <= 0 ? "等待订阅" : count + " 个模块已订阅")
                .setContentIntent(pending)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void updateServiceNotification() {
        if (!foregroundStarted) return;
        try {
            Notification notification = buildServiceNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                );
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {
            // Notification permission can change while the service is running.
        }
    }

    private void createChannels() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel service = new NotificationChannel(
                SERVICE_CHANNEL,
                "后台消息服务",
                NotificationManager.IMPORTANCE_LOW
        );
        service.setDescription("保持模块消息连接");
        manager.createNotificationChannel(service);
        NotificationChannel messages = new NotificationChannel(
                MESSAGE_CHANNEL,
                "模块消息",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        messages.setDescription("模块有新消息");
        manager.createNotificationChannel(messages);
    }

    private boolean shouldSuppress(MessageSubscription item, String session) {
        if (!item.moduleId.equals(foregroundModuleId)) return false;
        if ("silent".equalsIgnoreCase(item.foreground)) return true;
        String active = activeSessions.get(item.moduleId);
        return active != null && !active.isEmpty() && session != null && active.equals(session);
    }

    private void postMessageNotification(MessageSubscription item, String session) {
        if (!NotificationPrefs.moduleEnabled(this, item.moduleId)) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        try {
            Intent open = new Intent(this, ModuleRouterActivity.class);
            open.putExtra(ModuleRouterActivity.EXTRA_MODULE_ID, item.moduleId);
            if (session != null && !session.isEmpty()) {
                open.putExtra(ModuleRouterActivity.EXTRA_SESSION_ID, session);
            }
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pending = PendingIntent.getActivity(
                    this,
                    item.moduleId.hashCode(),
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification notification = new Notification.Builder(this, MESSAGE_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_docklet)
                    .setContentTitle(item.moduleName)
                    .setContentText("有新消息")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(false)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setShowWhen(true)
                    .build();
            manager.notify("docklet-message:" + item.moduleId, item.moduleId.hashCode(), notification);
        } catch (SecurityException ignored) {
            // Notification permission may have been revoked.
        }
    }

    private void cancelModuleNotification(String moduleId) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel("docklet-message:" + moduleId, moduleId.hashCode());
    }

    private void recordSeen(String moduleId, String key) {
        synchronized (seenEvents) {
            Set<String> seen = seenFor(moduleId);
            if (!seen.add(key)) return;
            while (seen.size() > 200) {
                java.util.Iterator<String> iterator = seen.iterator();
                if (!iterator.hasNext()) break;
                iterator.remove();
            }
            // Persist so a service restart or reboot does not re-alert an event.
            getSharedPreferences(PREFS_SEEN, MODE_PRIVATE)
                    .edit()
                    .putStringSet(moduleId, new LinkedHashSet<>(seen))
                    .apply();
        }
    }

    private boolean hasSeen(String moduleId, String key) {
        synchronized (seenEvents) {
            return seenFor(moduleId).contains(key);
        }
    }

    private Set<String> seenFor(String moduleId) {
        Set<String> seen = seenEvents.get(moduleId);
        if (seen != null) return seen;
        Set<String> loaded = new LinkedHashSet<>();
        Set<String> stored = getSharedPreferences(PREFS_SEEN, MODE_PRIVATE)
                .getStringSet(moduleId, null);
        if (stored != null) loaded.addAll(stored);
        seenEvents.put(moduleId, loaded);
        return loaded;
    }

    private void setState(String moduleId, String state, String error) {
        MessageSubscriptionStore.setState(this, moduleId, state, error);
    }

    /** Removes one module's subscription and stops its worker. */
    public static void unsubscribe(Context context, String moduleId) {
        if (context == null || moduleId == null) return;
        MessageSubscriptionStore.remove(context, moduleId);
        Intent intent = new Intent(context, DockletMessageService.class);
        intent.setAction(ACTION_UNSUBSCRIBE);
        intent.putExtra(EXTRA_MODULE_ID, moduleId);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
            // The service may already be stopped; the stored entry is gone.
        }
    }

    /** Called by ModuleActivity; keeps foreground suppression in sync with the page. */
    public static void setModuleForeground(Context context, String moduleId, boolean foreground) {
        if (context == null || moduleId == null) return;
        if (!MessageSubscriptionStore.has(context, moduleId)) return;
        Intent intent = new Intent(context, DockletMessageService.class);
        intent.setAction(foreground ? ACTION_FOREGROUND : ACTION_BACKGROUND);
        intent.putExtra(EXTRA_MODULE_ID, moduleId);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
            // The activity may be leaving; the next foreground call will repair it.
        }
    }

    private final class Worker extends Thread {
        private final MessageSubscription item;
        private volatile boolean running = true;
        private HttpURLConnection connection;

        Worker(MessageSubscription item) {
            super("docklet-message-" + item.moduleId);
            this.item = item;
        }

        void stopWorker() {
            running = false;
            HttpURLConnection current = connection;
            if (current != null) {
                try {
                    current.disconnect();
                } catch (Exception ignored) {
                    // Best effort.
                }
            }
            interrupt();
        }

        @Override
        public void run() {
            if (item.isSessionsPolling()) {
                runSessionsPolling();
            } else {
                runSse();
            }
        }

        private void runSse() {
            while (running) {
                try {
                    HttpURLConnection current = open(item.endpoint);
                    connection = current;
                    current.setRequestProperty("Accept", "text/event-stream");
                    current.setRequestProperty("Cache-Control", "no-cache");
                    applyHeaders(current, item.headers);
                    int status = current.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("HTTP " + status);
                    }
                    setState(item.moduleId, "online", "");
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                        StringBuilder data = new StringBuilder();
                        String eventName = "";
                        String line;
                        while (running && (line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                dispatchSse(data.toString(), eventName);
                                data.setLength(0);
                                eventName = "";
                            } else if (line.startsWith("data:")) {
                                if (data.length() > 0) data.append('\n');
                                data.append(line.substring(5).trim());
                                if (data.length() > 32 * 1024) data.setLength(0);
                            } else if (line.startsWith("event:")) {
                                eventName = line.substring(6).trim();
                            }
                        }
                        dispatchSse(data.toString(), eventName);
                    }
                } catch (Exception error) {
                    if (!running) break;
                    setState(item.moduleId, "offline", safeMessage(error));
                } finally {
                    if (connection != null) connection.disconnect();
                    connection = null;
                }
                sleepBeforeRetry();
            }
        }

        private void runSessionsPolling() {
            Map<String, Long> previous = new HashMap<>();
            boolean primed = false;
            while (running) {
                try {
                    HttpURLConnection current = open(item.endpoint);
                    connection = current;
                    current.setRequestProperty("Accept", "application/json");
                    current.setRequestProperty("Cache-Control", "no-cache");
                    applyHeaders(current, item.headers);
                    int status = current.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("HTTP " + status);
                    }
                    StringBuilder raw = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8)
                    )) {
                        String line;
                        while (running && (line = reader.readLine()) != null) {
                            raw.append(line);
                            if (raw.length() > 512 * 1024) break;
                        }
                    }
                    JSONObject root = new JSONObject(raw.toString());
                    JSONArray sessions = root.optJSONArray("sessions");
                    Map<String, Long> next = new HashMap<>();
                    String pendingSession = null;
                    if (sessions != null) {
                        for (int i = 0; i < sessions.length(); i += 1) {
                            JSONObject session = sessions.optJSONObject(i);
                            if (session == null) continue;
                            String id = session.optString("id", "");
                            if (id.isEmpty()) continue;
                            long seq = session.has("lastAssistantSeq")
                                    ? session.optLong("lastAssistantSeq", -1L)
                                    : session.optLong("lastSeq", -1L);
                            if (seq < 0L) continue;
                            next.put(id, seq);
                            long before = previous.containsKey(id) ? previous.get(id) : -1L;
                            boolean done = !session.optBoolean("running", false);
                            if (!primed) {
                                next.put(id, seq);
                            } else if (done) {
                                if (seq > before && !shouldSuppress(item, id)) {
                                    pendingSession = id;
                                }
                                next.put(id, seq);
                            } else {
                                // Keep the old cursor while a turn is running. The
                                // finished poll must still see seq > cursor once.
                                next.put(id, before >= 0L ? before : seq);
                            }
                        }
                    }
                    previous = next;
                    primed = true;
                    setState(item.moduleId, "online", "");
                    if (pendingSession != null) {
                        MessageSubscriptionStore.markEvent(
                                DockletMessageService.this, item.moduleId
                        );
                        postMessageNotification(item, pendingSession);
                    }
                } catch (Exception error) {
                    if (!running) break;
                    setState(item.moduleId, "offline", safeMessage(error));
                } finally {
                    if (connection != null) connection.disconnect();
                    connection = null;
                }
                sleepBeforeRetry();
            }
        }

        private void dispatchSse(String data, String eventName) {
            if (data == null || data.trim().isEmpty()) return;
            JSONObject envelope;
            try {
                envelope = new JSONObject(data.trim());
            } catch (Exception ignored) {
                return;
            }
            String type = envelope.optString("type", eventName == null ? "" : eventName);
            if (type.isEmpty() || !item.allowsType(type)) return;

            JSONObject payload = envelope.optJSONObject("payload");
            if (payload == null) payload = envelope.optJSONObject("data");
            if (payload == null) payload = envelope;

            if ("turn.end".equals(type)) {
                String kind = payload.optString("status", "");
                if (kind.isEmpty()) {
                    JSONObject reason = payload.optJSONObject("reason");
                    if (reason != null) kind = reason.optString("kind", "");
                }
                if (!kind.isEmpty() && !"completed".equals(kind)) return;
            }

            String session = envelope.optString(item.sessionField, "");
            if (session.isEmpty()) session = envelope.optString("session", "");
            if (session.isEmpty()) session = payload.optString(item.sessionField, "");
            if (session.isEmpty()) session = payload.optString("session", "");

            String eventId = envelope.optString("eventId", "");
            if (eventId.isEmpty()) eventId = envelope.optString("id", "");
            if (eventId.isEmpty()) {
                JSONObject reason = payload.optJSONObject("reason");
                if (reason != null) eventId = reason.optString("eventId", "");
            }
            if (eventId.isEmpty()) eventId = Integer.toHexString(data.hashCode());

            String dedupe = type + "|" + session + "|" + eventId;
            if (hasSeen(item.moduleId, dedupe)) return;
            recordSeen(item.moduleId, dedupe);
            MessageSubscriptionStore.markEvent(
                    DockletMessageService.this, item.moduleId
            );
            if (shouldSuppress(item, session)) return;
            postMessageNotification(item, session);
        }

        private void sleepBeforeRetry() {
            if (!running) return;
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }

        private HttpURLConnection open(String endpoint) throws Exception {
            HttpURLConnection current = (HttpURLConnection) new URL(endpoint).openConnection();
            current.setConnectTimeout(15_000);
            current.setReadTimeout(65_000);
            current.setUseCaches(false);
            return current;
        }

        private void applyHeaders(HttpURLConnection current, JSONObject headers) {
            if (headers == null) return;
            java.util.Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.optString(key, "");
                if (!key.isEmpty() && !value.isEmpty()) current.setRequestProperty(key, value);
            }
        }

        private String safeMessage(Exception error) {
            String message = error.getMessage();
            return message == null || message.isEmpty()
                    ? error.getClass().getSimpleName()
                    : message;
        }
    }
}
