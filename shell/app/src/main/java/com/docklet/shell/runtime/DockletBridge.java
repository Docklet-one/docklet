package com.docklet.shell.runtime;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;

import com.docklet.platform.InstalledModule;
import com.docklet.platform.Json;
import com.docklet.platform.ModuleException;
import com.docklet.platform.ModuleFiles;
import com.docklet.shell.ModuleActivity;
import com.docklet.shell.ModuleRouterActivity;
import com.docklet.shell.R;
import com.docklet.shell.storage.StorageService;
import com.docklet.shell.ui.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Minimal capability bridge exposed as {@code window.DockletNative}.
 *
 * <p>Storage and notifications are implemented now. Other capabilities are
 * declared in the manifest contract but deliberately denied until their stage.</p>
 */
public final class DockletBridge {

    public interface Host {
        void onBridgeReady();

        void onLifecycleResult(String requestId, boolean ok, String error);

        void requestNotificationPermission();

        void evaluateJavascript(String script);
    }

    public static final int REQUEST_PICK_FILE = 1003;
    private static final long MAX_REMOTE_DOWNLOAD_BYTES = 200L * 1024L * 1024L;
    private static final String CHANNEL_ID = "docklet_modules";
    private static final String CHANNEL_NAME = "模块消息";
    private static final long PICK_TIMEOUT_SECONDS = 120L;
    private static final int MAX_MESSAGE_SUBSCRIPTIONS = 8;

    private final Object pickLock = new Object();
    private volatile CountDownLatch pickLatch;
    private volatile Uri pickedUri;
    private volatile boolean pickActive;

    private final Activity activity;
    private final InstalledModule module;
    private final StorageService storage;
    private final ModuleFiles files;
    private final Host host;
    private String sessionId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> networkWatchIds = ConcurrentHashMap.newKeySet();
    private NetworkWatcher networkWatcher;

    public DockletBridge(
            Activity activity,
            InstalledModule module,
            StorageService storage,
            ModuleFiles files,
            Host host
    ) {
        this.activity = activity;
        this.module = module;
        this.storage = storage;
        this.files = files;
        this.host = host;
    }

    public void setSessionId(String id) {
        sessionId = id;
    }

    @JavascriptInterface
    public String request(String rawRequest) {
        try {
            Map<String, Object> request = Json.parseObject(rawRequest);
            String method = Json.string(request, "method");
            if (method == null || method.isEmpty()) {
                return error("BAD_REQUEST", "缺少 method");
            }
            Map<String, Object> params = Json.asObject(request.get("params"));
            if (params == null) params = new LinkedHashMap<>();

            if (method.startsWith("storage.")) {
                requireCapability("storage");
            } else if (method.startsWith("download.")) {
                requireCapability("download");
            } else if (method.startsWith("files.")) {
                requireCapability("files");
            } else if (method.startsWith("clipboard.")) {
                requireCapability("clipboard");
            } else if (method.startsWith("backgroundMessages.")) {
                requireCapability("background");
            } else if (method.startsWith("network.")) {
                requireCapability("network");
            } else if (method.equals("notify.show")) {
                requireCapability("notify");
            } else if (method.equals("openExternal")) {
                requireCapability("openExternal");
            }

            switch (method) {
                case "shell.info":
                    return success(shellInfo());
                case "storage.open":
                    return success(storage.open(module.id()).toMap());
                case "storage.query": {
                    String sql = requireSql(params);
                    StorageService.QueryResult result = storage.query(
                            module.id(),
                            sql,
                            objectList(params.get("params"))
                    );
                    return success(result.rows());
                }
                case "storage.exec": {
                    String sql = requireSql(params);
                    return success(storage.exec(
                            module.id(),
                            sql,
                            objectList(params.get("params"))
                    ).toMap());
                }
                case "storage.transaction":
                    return success(storage.transaction(
                            module.id(),
                            parseStatements(params)
                    ).toMap());
                case "storage.backup":
                    return success(storage.backup(module.id()).toMap());
                case "files.write": {
                    String path = requirePath(params);
                    String data = Json.string(params, "data", "");
                    return success(files.write(
                            module.id(),
                            path,
                            data,
                            Json.string(params, "encoding", "utf8")
                    ));
                }
                case "files.read": {
                    String path = requirePath(params);
                    return success(files.read(
                            module.id(),
                            path,
                            Json.string(params, "encoding", "utf8")
                    ));
                }
                case "files.list": {
                    String path = Json.string(params, "path", "");
                    return success(files.list(module.id(), path == null ? "" : path));
                }
                case "files.delete": {
                    String path = requirePath(params);
                    return success(files.delete(
                            module.id(),
                            path,
                            Json.bool(params, "recursive", false)
                    ));
                }
                case "files.exists": {
                    String path = Json.string(params, "path", "");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", path == null ? "" : path);
                    result.put("exists", files.exists(module.id(), path == null ? "" : path));
                    return success(result);
                }
                case "download.save":
                    return downloadRemote(params);
                case "files.pick":
                    return pickFile(params);
                case "files.share":
                    return shareFile(params);
                case "files.download":
                    return downloadFile(params);
                case "clipboard.read":
                    return success(clipboardRead());
                case "clipboard.write":
                    return success(clipboardWrite(Json.string(params, "text", "")));
                case "openExternal":
                    return openExternal(Json.string(params, "url", ""));
                case "backgroundMessages.subscribe":
                    return backgroundMessagesSubscribe(params);
                case "backgroundMessages.unsubscribe":
                    return backgroundMessagesUnsubscribe();
                case "backgroundMessages.setActiveSession":
                    return backgroundMessagesSetActiveSession(params);
                case "backgroundMessages.getState":
                    return backgroundMessagesGetState();
                case "display.set":
                    return displaySet(params);
                case "display.getState":
                    return displayGetState();
                case "network.getState":
                    return networkGetState();
                case "network.watch":
                    return networkWatch(params);
                case "network.unwatch":
                    return networkUnwatch(params);
                case "notify.show":
                    return notifyShow(params);
                default:
                    return error("UNKNOWN_METHOD", "未知方法: " + method);
            }
        } catch (ModuleException error) {
            return error(error.getCode(), error.getMessage());
        } catch (Json.JsonException error) {
            return error("BAD_REQUEST", "请求不是合法 JSON: " + error.getMessage());
        } catch (Exception error) {
            return error("BRIDGE_ERROR", safeMessage(error));
        }
    }

    @JavascriptInterface
    public void ready() {
        mainHandler.post(host::onBridgeReady);
    }

    @JavascriptInterface
    public void lifecycleResult(String requestId, boolean ok, String error) {
        mainHandler.post(() -> host.onLifecycleResult(requestId, ok, error));
    }

    @JavascriptInterface
    public void log(String message) {
        if (message != null && message.length() < 2000) {
            android.util.Log.d("DockletModule", "[" + module.id() + "] " + message);
        }
    }

    private Map<String, Object> shellInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("moduleId", module.id());
        info.put("name", module.name());
        info.put("version", module.version());
        info.put("origin", module.origin());
        info.put("platformVersion", UiKit.PLATFORM_VERSION);
        info.put("shellVersion", UiKit.SHELL_VERSION);
        info.put("capabilities", module.capabilities());
        if (sessionId != null) {
            info.put("sessionId", sessionId);
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put("sessionId", sessionId);
            info.put("initial", initial);
        }
        return info;
    }

    private String backgroundMessagesSubscribe(Map<String, Object> params)
            throws ModuleException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            mainHandler.post(host::requestNotificationPermission);
            return error("PERMISSION_REQUESTED", "需要通知权限，请允许后重试");
        }
        String endpoint = Json.string(params, "endpoint", "");
        if (endpoint == null || endpoint.trim().isEmpty() || endpoint.length() > 4096) {
            return error("INVALID_ENDPOINT", "缺少合法的消息 Endpoint");
        }
        Uri uri = Uri.parse(endpoint.trim());
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return error("INVALID_ENDPOINT", "消息 Endpoint 只支持 http/https");
        }

        JSONObject headers = new JSONObject();
        Map<String, Object> headerMap = Json.asObject(params.get("headers"));
        if (headerMap != null) {
            for (Map.Entry<String, Object> entry : headerMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                try {
                    headers.put(entry.getKey(), String.valueOf(entry.getValue()));
                } catch (Exception ignored) {
                    // Ignore malformed header names.
                }
            }
        }

        JSONArray eventTypes = new JSONArray();
        List<String> eventTypeNames = new ArrayList<>();
        List<Object> types = Json.asArray(params.get("eventTypes"));
        if (types != null) {
            for (Object value : types) {
                if (value == null) continue;
                String name = String.valueOf(value);
                eventTypes.put(name);
                eventTypeNames.add(name);
            }
        }
        if (eventTypes.length() == 0) {
            eventTypes.put("turn.end");
            eventTypeNames.add("turn.end");
        }

        String foreground = Json.string(params, "foreground", "silent");
        if (!"notify".equals(foreground) && !"silent".equals(foreground)) {
            foreground = "silent";
        }
        String transport = Json.string(params, "transport", "sse");
        String sessionField = Json.string(params, "sessionField", "session");
        String dedupeBy = Json.string(params, "dedupeBy", "eventId");
        boolean persist = Json.bool(params, "persist", true);

        MessageSubscription existing = MessageSubscriptionStore.find(activity, module.id());
        if (existing == null
                && MessageSubscriptionStore.load(activity).size() >= MAX_MESSAGE_SUBSCRIPTIONS) {
            return error(
                    "TOO_MANY_MESSAGE_SUBSCRIPTIONS",
                    "后台消息订阅已达到上限（" + MAX_MESSAGE_SUBSCRIPTIONS + " 个）"
            );
        }
        MessageSubscription subscription = new MessageSubscription(
                module.id(),
                module.name(),
                endpoint.trim(),
                transport == null ? "sse" : transport,
                headers,
                eventTypes,
                foreground,
                sessionField == null ? "session" : sessionField,
                dedupeBy == null ? "eventId" : dedupeBy,
                persist,
                existing == null ? "" : existing.activeSession
        );
        MessageSubscriptionStore.upsert(activity, subscription);
        try {
            Intent intent = new Intent(activity, DockletMessageService.class);
            intent.setAction(DockletMessageService.ACTION_SUBSCRIBE);
            intent.putExtra(
                    DockletMessageService.EXTRA_SUBSCRIPTION,
                    subscription.toJson().toString()
            );
            activity.startForegroundService(intent);
        } catch (Exception error) {
            return error("MESSAGE_SUBSCRIBE_FAILED", safeMessage(error));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribed", true);
        result.put("moduleId", module.id());
        result.put("foreground", foreground);
        result.put("eventTypes", eventTypeNames);
        return success(result);
    }

    private String backgroundMessagesUnsubscribe() {
        MessageSubscriptionStore.remove(activity, module.id());
        try {
            Intent intent = new Intent(activity, DockletMessageService.class);
            intent.setAction(DockletMessageService.ACTION_UNSUBSCRIBE);
            intent.putExtra(DockletMessageService.EXTRA_MODULE_ID, module.id());
            activity.startService(intent);
        } catch (Exception ignored) {
            // The service may already be stopped.
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribed", false);
        return success(result);
    }

    private String backgroundMessagesSetActiveSession(Map<String, Object> params) {
        String session = Json.string(params, "session", "");
        if (session == null) session = "";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeSession", session);
        if (MessageSubscriptionStore.find(activity, module.id()) == null) {
            // Without a subscription there is nothing to suppress, and waking
            // the service here would leave an idle foreground-less service.
            return success(result);
        }
        MessageSubscriptionStore.setActiveSession(activity, module.id(), session);
        try {
            Intent intent = new Intent(activity, DockletMessageService.class);
            intent.setAction(DockletMessageService.ACTION_ACTIVE_SESSION);
            intent.putExtra(DockletMessageService.EXTRA_MODULE_ID, module.id());
            intent.putExtra(DockletMessageService.EXTRA_SESSION, session);
            activity.startService(intent);
        } catch (Exception ignored) {
            // The service may already be stopped.
        }
        return success(result);
    }

    private String backgroundMessagesGetState() {
        MessageSubscription subscription = MessageSubscriptionStore.find(activity, module.id());
        JSONObject state = MessageSubscriptionStore.getState(activity, module.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribed", subscription != null);
        result.put("activeSession", subscription == null ? "" : subscription.activeSession);
        result.put("state", state.optString("state", "offline"));
        result.put("lastError", state.optString("error", ""));
        result.put("lastEventAt", state.optLong("lastEventAt", 0L));
        return success(result);
    }

    private String displaySet(Map<String, Object> params) {
        if (!(activity instanceof ModuleActivity)) {
            return error("UNSUPPORTED", "display 只能由模块页面调用");
        }
        String mode = Json.string(params, "mode", "");
        String icons = Json.string(params, "statusBarIcons", "");
        ((ModuleActivity) activity).applyRuntimeDisplay(mode, icons);
        return success(((ModuleActivity) activity).displayState());
    }

    private String displayGetState() {
        if (!(activity instanceof ModuleActivity)) {
            return error("UNSUPPORTED", "display 只能由模块页面调用");
        }
        return success(((ModuleActivity) activity).displayState());
    }

    private String networkGetState() {
        NetworkWatcher watcher = new NetworkWatcher(activity, null);
        try {
            return success(networkStateMap(watcher.state()));
        } finally {
            watcher.stop();
        }
    }

    private String networkWatch(Map<String, Object> params) throws ModuleException {
        String id = Json.string(params, "id", "");
        if (id == null || id.isEmpty() || id.length() > 64) {
            throw new ModuleException("INVALID_ID", "network.watch 缺少订阅 id");
        }
        networkWatchIds.add(id);
        if (networkWatcher == null) {
            networkWatcher = new NetworkWatcher(activity, state -> {
                if (networkWatchIds.isEmpty()) return;
                String payload = state.toJson().toString();
                for (String watchId : networkWatchIds) {
                    host.evaluateJavascript(
                            "(function(){if(window.__dockletNetworkEvent)"
                                    + "window.__dockletNetworkEvent("
                                    + JSONObject.quote(watchId) + ","
                                    + payload + ");})();"
                    );
                }
            });
        }
        return success(networkStateMap(networkWatcher.state()));
    }

    private String networkUnwatch(Map<String, Object> params) {
        String id = Json.string(params, "id", "");
        if (id != null) networkWatchIds.remove(id);
        if (networkWatchIds.isEmpty() && networkWatcher != null) {
            networkWatcher.stop();
            networkWatcher = null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watching", !networkWatchIds.isEmpty());
        return success(result);
    }

    private Map<String, Object> networkStateMap(NetworkWatcher.NetworkState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("online", state.online);
        result.put("type", state.type);
        result.put("metered", state.metered);
        result.put("validated", state.validated);
        result.put("changedAt", state.changedAt);
        return result;
    }

    /** Stop per-page network subscriptions before the WebView is destroyed. */
    public void close() {
        networkWatchIds.clear();
        if (networkWatcher != null) {
            networkWatcher.stop();
            networkWatcher = null;
        }
    }

    private String notifyShow(Map<String, Object> params) {
        String title = stringOr(params, "title", module.name());
        String body = stringOr(params, "body", "");
        String session = stringOr(params, "sessionId", "");
        String eventId = stringOr(params, "eventId", "");
        String tag = stringOr(params, "tag", "module-" + module.id());
        if (!eventId.isEmpty()) {
            tag = "docklet-event-" + module.id() + "-" + session + "-" + eventId;
        }
        if (title.length() > 80) title = title.substring(0, 80);
        if (body.length() > 300) body = body.substring(0, 300);
        if (tag.length() > 64) tag = tag.substring(0, 64);

        if (!NotificationPrefs.enabledFor(activity, module.id())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shown", false);
            result.put("suppressed", true);
            return success(result);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            mainHandler.post(host::requestNotificationPermission);
            return error("PERMISSION_REQUESTED", "需要通知权限，请允许后重试");
        }

        try {
            NotificationManager manager =
                    (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return error("NOTIFY_FAILED", "系统通知服务不可用");

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Docklet 模块通知");
            manager.createNotificationChannel(channel);

            Intent intent = new Intent(activity, ModuleRouterActivity.class);
            intent.putExtra(ModuleRouterActivity.EXTRA_MODULE_ID, module.id());
            if (!session.isEmpty()) {
                intent.putExtra(ModuleRouterActivity.EXTRA_SESSION_ID, session);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    activity,
                    (module.id() + "|" + session + "|" + eventId).hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder builder = new Notification.Builder(activity, CHANNEL_ID);
            builder.setSmallIcon(R.drawable.ic_stat_docklet)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setShowWhen(true);
            Notification notification = builder.build();
            int notificationId = eventId.isEmpty()
                    ? (int) (System.currentTimeMillis() & 0x7FFFFFFF)
                    : (module.id() + "|" + session + "|" + eventId).hashCode();
            manager.notify(tag, notificationId, notification);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("shown", true);
            result.put("tag", tag);
            return success(result);
        } catch (SecurityException error) {
            return error("PERMISSION_REQUIRED", "没有通知权限");
        } catch (Exception error) {
            return error("NOTIFY_FAILED", safeMessage(error));
        }
    }

    private String requirePath(Map<String, Object> params) throws ModuleException {
        String path = Json.string(params, "path");
        if (path == null || path.trim().isEmpty()) {
            throw new ModuleException(ModuleException.PATH_REJECTED, "缺少文件 path");
        }
        return path;
    }

    private Map<String, Object> clipboardRead() throws ModuleException {
        String text = onMain(() -> {
            ClipboardManager manager =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) return "";
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence value = clip.getItemAt(0).coerceToText(activity);
            String result = value == null ? "" : value.toString();
            return result.length() > 200_000 ? result.substring(0, 200_000) : result;
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text == null ? "" : text);
        result.put("chars", text == null ? 0 : text.length());
        return result;
    }

    private Map<String, Object> clipboardWrite(String text) throws ModuleException {
        String value = text == null ? "" : text;
        if (value.length() > 200_000) {
            throw new ModuleException("CLIPBOARD_ERROR", "剪贴板内容不能超过 200,000 字符");
        }
        onMain(() -> {
            ClipboardManager manager =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null) throw new IllegalStateException("剪贴板服务不可用");
            manager.setPrimaryClip(ClipData.newPlainText("Docklet", value));
            return Boolean.TRUE;
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("chars", value.length());
        return result;
    }

    private String openExternal(String url) throws ModuleException {
        if (url == null || url.trim().isEmpty()) {
            throw new ModuleException("INVALID_URL", "缺少 url");
        }
        if (url.length() > 2048) {
            throw new ModuleException("INVALID_URL", "url 过长");
        }
        Uri uri;
        try {
            uri = Uri.parse(url.trim());
        } catch (Exception error) {
            throw new ModuleException("INVALID_URL", "url 不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new ModuleException("INVALID_URL", "url 必须带 scheme");
        }
        String lower = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(lower) && !"https".equals(lower)
                && !"mailto".equals(lower) && !"tel".equals(lower)) {
            throw new ModuleException("INVALID_URL", "不允许的 scheme: " + scheme);
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            throw new ModuleException("NO_HANDLER", "系统没有能打开该链接的应用");
        } catch (Exception error) {
            throw new ModuleException("OPEN_EXTERNAL_FAILED", safeMessage(error), error);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("opened", true);
        result.put("url", url);
        return success(result);
    }

    /** Called by MainActivity when the document picker returns. */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_FILE) return false;
        CountDownLatch latch;
        synchronized (pickLock) {
            if (!pickActive) return true;
            pickedUri = resultCode == Activity.RESULT_OK && data != null
                    ? data.getData()
                    : null;
            latch = pickLatch;
        }
        if (latch != null) latch.countDown();
        return true;
    }

    private String pickFile(Map<String, Object> params) throws ModuleException {
        CountDownLatch latch;
        synchronized (pickLock) {
            if (pickActive) {
                return error("PICK_BUSY", "已经有一个文件选择器打开");
            }
            pickActive = true;
            pickedUri = null;
            latch = new CountDownLatch(1);
            pickLatch = latch;
        }

        String mime = Json.string(params, "mime", "*/*");
        if (mime == null || mime.trim().isEmpty()) mime = "*/*";
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final Intent pickIntent = intent;
        mainHandler.post(() -> {
            try {
                activity.startActivityForResult(pickIntent, REQUEST_PICK_FILE);
            } catch (Exception error) {
                onActivityResult(REQUEST_PICK_FILE, Activity.RESULT_CANCELED, null);
            }
        });

        boolean completed;
        try {
            completed = latch.await(PICK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            completed = false;
        }
        Uri uri;
        synchronized (pickLock) {
            pickActive = false;
            uri = pickedUri;
            pickedUri = null;
        }
        if (!completed) {
            return error("PICK_TIMEOUT", "等待文件选择超时");
        }
        if (uri == null) {
            return error("PICK_CANCELLED", "已取消选择文件");
        }
        return importPickedFile(uri, Json.string(params, "path", ""));
    }

    private String importPickedFile(Uri uri, String requestedPath) throws ModuleException {
        String displayName = displayName(uri);
        String safeName = safeFileName(displayName);
        String target = requestedPath == null ? "" : requestedPath.trim();
        if (target.isEmpty()) {
            target = "picked/" + System.currentTimeMillis() + "-" + safeName;
        }

        byte[] bytes;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new ModuleException("PICK_FAILED", "无法读取所选文件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > ModuleFiles.MAX_FILE_BYTES) {
                    throw new ModuleException(
                            ModuleException.FILE_TOO_LARGE,
                            "所选文件超过 5 MB"
                    );
                }
                out.write(buffer, 0, read);
            }
            bytes = out.toByteArray();
        } catch (ModuleException error) {
            throw error;
        } catch (Exception error) {
            throw new ModuleException("PICK_FAILED", "读取文件失败: " + safeMessage(error), error);
        }

        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        Map<String, Object> written = files.write(module.id(), target, encoded, "base64");
        String mime = activity.getContentResolver().getType(uri);
        if (mime == null) mime = mimeFromName(safeName);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", written.get("path"));
        result.put("name", safeName);
        result.put("bytes", bytes.length);
        result.put("mime", mime);
        return success(result);
    }

    private String shareFile(Map<String, Object> params) throws ModuleException {
        String path = requirePath(params);
        File file = files.fileFor(module.id(), path);
        if (!file.isFile()) {
            throw new ModuleException(ModuleException.FILE_NOT_FOUND, "文件不存在");
        }
        Uri uri = ModuleFileProvider.uriFor(activity, module.id(), path);
        String mime = Json.string(params, "mime", "");
        if (mime == null || mime.trim().isEmpty()) {
            mime = activity.getContentResolver().getType(uri);
        }
        if (mime == null || mime.trim().isEmpty()) mime = mimeFromName(file.getName());

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, "分享 " + file.getName());
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (chooser.resolveActivity(activity.getPackageManager()) == null) {
            return error("NO_HANDLER", "没有可用的分享应用");
        }
        mainHandler.post(() -> {
            try {
                activity.startActivity(chooser);
            } catch (Exception ignored) {
                // The chooser may disappear while the activity is finishing.
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shared", true);
        result.put("path", path);
        result.put("uri", uri.toString());
        return success(result);
    }

    private String downloadRemote(Map<String, Object> params) throws ModuleException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return error("UNSUPPORTED", "下载到 Downloads 需要 Android 10+");
        }
        String urlText = Json.string(params, "url", "");
        if (urlText == null || urlText.trim().isEmpty() || urlText.length() > 2048) {
            return error("INVALID_URL", "缺少合法的下载 url");
        }
        Uri parsed;
        try {
            parsed = Uri.parse(urlText.trim());
        } catch (Exception error) {
            return error("INVALID_URL", "下载 url 不合法");
        }
        String scheme = parsed.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || parsed.getUserInfo() != null) {
            return error("INVALID_URL", "下载只支持 http/https，且不能带用户名密码");
        }
        String name = Json.string(params, "filename", "");
        if (name == null || name.trim().isEmpty()) {
            name = parsed.getLastPathSegment();
            if (name != null) {
                try {
                    name = URLDecoder.decode(name, "UTF-8");
                } catch (Exception ignored) {
                    // Fall back to the raw segment.
                }
            }
        }
        if (name == null || name.trim().isEmpty()) name = "download.bin";
        name = safeFileName(name);
        Map<String, Object> headerValues = Json.asObject(params.get("headers"));
        final String targetName = name;
        final String source = urlText.trim();
        Thread worker = new Thread(
                () -> performRemoteDownload(source, targetName, headerValues),
                "docklet-download-" + module.id()
        );
        worker.start();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started", true);
        result.put("name", targetName);
        return success(result);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private void performRemoteDownload(
            String source,
            String filename,
            Map<String, Object> headers
    ) {
        ContentResolver resolver = activity.getApplicationContext().getContentResolver();
        Uri item = null;
        HttpURLConnection connection = null;
        String errorMessage = null;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "*/*");
            if (headers != null) {
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue() == null ? null : String.valueOf(entry.getValue());
                    if (key == null || value == null) continue;
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (lower.equals("host") || lower.equals("content-length")
                            || lower.equals("connection")) {
                        continue;
                    }
                    connection.setRequestProperty(key, value);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            String mime = mimeFromName(filename);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Docklet");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            item = resolver.insert(collection, values);
            if (item == null) throw new IllegalStateException("系统没有创建下载文件");

            long total = 0L;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = resolver.openOutputStream(item)) {
                if (out == null) throw new IllegalStateException("无法打开下载目标");
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_REMOTE_DOWNLOAD_BYTES) {
                        throw new IllegalStateException("文件超过 200 MB");
                    }
                    out.write(buffer, 0, read);
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, done, null, null);
            showDownloadNotification(
                    filename,
                    "已下载到 Downloads/Docklet",
                    item
            );
        } catch (Exception error) {
            errorMessage = safeMessage(error);
            if (item != null) {
                try {
                    resolver.delete(item, null, null);
                } catch (Exception ignored) {
                    // Best effort.
                }
            }
        } finally {
            if (connection != null) connection.disconnect();
            if (errorMessage != null) {
                showDownloadNotification(filename, "下载失败：" + errorMessage, null);
            }
        }
    }

    private void showDownloadNotification(String name, String text, Uri uri) {
        if (!NotificationPrefs.enabledFor(activity, module.id())) return;
        try {
            NotificationManager manager =
                    (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(channel);
            Notification.Builder builder = new Notification.Builder(activity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_docklet)
                    .setContentTitle(name)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setShowWhen(true);
            if (uri != null) {
                Intent open = new Intent(Intent.ACTION_VIEW);
                open.setDataAndType(uri, mimeFromName(name));
                open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                PendingIntent pending = PendingIntent.getActivity(
                        activity,
                        name.hashCode(),
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                builder.setContentIntent(pending);
            }
            manager.notify("docklet-download-" + name, name.hashCode(), builder.build());
        } catch (Exception ignored) {
            // Download itself succeeded/failed; notification is best effort.
        }
    }

    private String downloadFile(Map<String, Object> params) throws ModuleException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return error("UNSUPPORTED", "下载到 Downloads 需要 Android 10+，可先使用 share");
        }
        String path = requirePath(params);
        File file = files.fileFor(module.id(), path);
        if (!file.isFile()) {
            throw new ModuleException(ModuleException.FILE_NOT_FOUND, "文件不存在");
        }
        String mime = Json.string(params, "mime", "");
        if (mime == null || mime.trim().isEmpty()) mime = mimeFromName(file.getName());

        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Docklet");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri item = resolver.insert(collection, values);
        if (item == null) {
            return error("DOWNLOAD_FAILED", "系统没有创建下载文件");
        }
        try {
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = resolver.openOutputStream(item)) {
                if (out == null) throw new IllegalStateException("无法打开下载目标");
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, done, null, null);
        } catch (Exception error) {
            resolver.delete(item, null, null);
            return error("DOWNLOAD_FAILED", safeMessage(error));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("downloaded", true);
        result.put("path", path);
        result.put("name", file.getName());
        result.put("uri", item.toString());
        return success(result);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
            // Fall back to the URI path.
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "picked.bin" : fallback;
    }

    private static String safeFileName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (value.isEmpty()) value = "picked.bin";
        if (value.length() > 80) {
            int dot = value.lastIndexOf('.');
            if (dot > 0 && value.length() - dot <= 12) {
                value = value.substring(0, 80 - (value.length() - dot)) + value.substring(dot);
            } else {
                value = value.substring(0, 80);
            }
        }
        return value;
    }

    private static String mimeFromName(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "application/octet-stream";
        String mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return mime == null ? "application/octet-stream" : mime;
    }

    private <T> T onMain(Callable<T> callable) throws ModuleException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return callable.call();
            } catch (Exception error) {
                throw new ModuleException("CLIPBOARD_ERROR", safeMessage(error), error);
            }
        }
        FutureTask<T> task = new FutureTask<>(callable);
        mainHandler.post(task);
        try {
            return task.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ModuleException("CLIPBOARD_ERROR", "剪贴板操作被中断", error);
        } catch (Exception error) {
            task.cancel(true);
            throw new ModuleException("CLIPBOARD_ERROR", "剪贴板操作超时或失败", error);
        }
    }

    private List<StorageService.Statement> parseStatements(Map<String, Object> params)
            throws ModuleException {
        List<Object> raw = Json.asArray(params.get("statements"));
        if (raw == null || raw.isEmpty()) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "statements 不能为空");
        }
        List<StorageService.Statement> statements = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> object = Json.asObject(item);
            if (object == null) {
                throw new ModuleException(ModuleException.STORAGE_ERROR, "事务语句必须是对象");
            }
            String sql = requireSql(object);
            statements.add(new StorageService.Statement(sql, objectList(object.get("params"))));
        }
        return statements;
    }

    private String requireSql(Map<String, Object> params) throws ModuleException {
        String sql = Json.string(params, "sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "缺少 sql");
        }
        return sql;
    }

    private List<Object> objectList(Object raw) {
        List<Object> values = Json.asArray(raw);
        return values == null ? new ArrayList<>() : values;
    }

    private String stringOr(Map<String, Object> params, String key, String fallback) {
        String value = Json.string(params, key);
        return value == null ? fallback : value;
    }

    private void requireCapability(String capability) throws ModuleException {
        if (!module.capabilities().contains(capability)) {
            throw new ModuleException(
                    "CAPABILITY_DENIED",
                    "模块未声明 " + capability + " 能力"
            );
        }
    }

    private String success(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("data", data);
        return Json.stringify(response);
    }

    private String error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? code : message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false);
        response.put("error", error);
        return Json.stringify(response);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}
