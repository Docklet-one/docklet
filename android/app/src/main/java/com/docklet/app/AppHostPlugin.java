package com.docklet.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.webkit.WebStorage;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@CapacitorPlugin(
    name = "AppHost",
    permissions = {
      @Permission(alias = "notifications", strings = { android.Manifest.permission.POST_NOTIFICATIONS })
    })
public class AppHostPlugin extends com.getcapacitor.Plugin {

  private static final long MAX_DOWNLOAD = 100L * 1024 * 1024;
  private static final long MAX_TOTAL = 200L * 1024 * 1024;
  private static final long MAX_FILE = 50L * 1024 * 1024;
  private static final long MAX_FS_FILE = 20L * 1024 * 1024;
  private static final long MAX_DATA = 200L * 1024 * 1024;
  private static final int MAX_ENTRIES = 5000;
  private static final String ID_PATTERN = "[a-z][a-z0-9-]{1,31}";

  @Override
  public void load() {
    webMessageOk = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
    recoverInterruptedInstalls();
    seed();
  }

  /** 启动时修复上次安装中断留下的 .old- 目录，并清理残留临时目录。 */
  private void recoverInterruptedInstalls() {
    try {
      File apps = new File(getContext().getFilesDir(), "apps");
      File[] olds = apps.listFiles((dir, name) -> name.startsWith(".old-"));
      if (olds != null) {
        for (File old : olds) {
          String id = old.getName().substring(".old-".length());
          File target = new File(apps, id);
          if (!target.exists()) {
            if (!old.renameTo(target)) {
              copyDir(old, target);
              deleteRecursive(old);
            }
          } else {
            deleteRecursive(old);
          }
        }
      }
      File cache = getContext().getCacheDir();
      File[] temps = cache.listFiles((dir, name) -> name.startsWith("install-") || name.startsWith("docklet-local-"));
      if (temps != null) {
        for (File temp : temps) deleteRecursive(temp);
      }
    } catch (Exception ignored) {
      /* 启动恢复失败不阻塞壳 */
    }
  }

  /** 旧 WebView 回退到 addJavascriptInterface 时，iframe 也能直接调原生，必须禁用应用托管。 */
  private static volatile boolean webMessageOk = true;

  public static boolean isWebMessageOk() {
    return webMessageOk;
  }

  @PluginMethod
  public void security(PluginCall call) {
    JSObject result = new JSObject();
    result.put("webMessageListener", webMessageOk);
    call.resolve(result);
  }

  @PluginMethod
  public void pinShortcut(PluginCall call) {
    String id = call.getString("id", "");
    if (!id.matches("[a-z][a-z0-9-]{1,31}")) {
      call.reject("INVALID_ID");
      return;
    }
    String name = call.getString("name", id);
    String icon = call.getString("icon", "");
    String color = call.getString("color", "#5b7f96");
    if (getActivity() == null) {
      call.reject("NO_ACTIVITY");
      return;
    }
    getActivity().runOnUiThread(() -> {
      try {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(getContext())) {
          call.reject("UNSUPPORTED");
          return;
        }
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("openApp", id);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(getContext(), "app-" + id)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithBitmap(shortcutIcon(icon, color)))
            .setIntent(intent)
            .build();
        boolean requested = ShortcutManagerCompat.requestPinShortcut(getContext(), shortcut, null);
        JSObject result = new JSObject();
        result.put("requested", requested);
        call.resolve(result);
      } catch (Exception error) {
        call.reject("PIN_FAILED", error.getMessage());
      }
    });
  }

  private Bitmap shortcutIcon(String icon, String color) {
    int size = 192;
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    int background;
    try {
      background = Color.parseColor(color);
    } catch (Exception ignored) {
      background = Color.rgb(91, 127, 150);
    }
    canvas.drawColor(background);
    String glyph = icon == null ? "" : icon.trim();
    // 清单里的 icon 一般是 emoji；包内图片路径暂不解析，回退到默认方块
    if (glyph.isEmpty() || glyph.contains("/") || glyph.contains(".")) glyph = "📦";
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(Typeface.DEFAULT);
    paint.setTextSize(size * 0.52f);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f;
    canvas.drawText(glyph, size / 2f, baseline, paint);
    return bitmap;
  }

  @PluginMethod
  public void list(PluginCall call) {
    try {
      seed();
      call.resolve(scanApps());
    } catch (Exception error) {
      call.reject("list failed: " + error.getMessage());
    }
  }

  private static volatile boolean cancelRequested = false;

  @PluginMethod
  public void install(PluginCall call) {
    String url = call.getString("url");
    if (url == null || !isAllowedInstallUrl(url)) {
      call.reject("INVALID_URL");
      return;
    }
    cancelRequested = false;
    new Thread(() -> {
      File tmpZip = null;
      try {
        tmpZip = File.createTempFile("docklet-", ".zip", getContext().getCacheDir());
        long size = download(url, tmpZip, "install");
        if (size <= 0 || size > MAX_DOWNLOAD) throw new IllegalStateException("TOO_LARGE");
        if (!isZip(tmpZip)) throw new IllegalStateException("DOWNLOAD_BLOCKED");
        call.resolve(performInstall(tmpZip));
      } catch (Exception error) {
        call.reject(error.getMessage() == null ? "INSTALL_FAILED" : error.getMessage());
      } finally {
        if (tmpZip != null) //noinspection ResultOfMethodCallIgnored
          tmpZip.delete();
      }
    }).start();
  }

  @PluginMethod
  public void installBase64(PluginCall call) {
    String data = call.getString("data", "");
    new Thread(() -> {
      File tmpZip = null;
      try {
        byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
        if (bytes.length <= 0 || bytes.length > MAX_DOWNLOAD) throw new IllegalStateException("TOO_LARGE");
        tmpZip = File.createTempFile("docklet-local-", ".zip", getContext().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(tmpZip)) {
          out.write(bytes);
        }
        call.resolve(performInstall(tmpZip));
      } catch (Exception error) {
        call.reject(error.getMessage() == null ? "INSTALL_FAILED" : error.getMessage());
      } finally {
        if (tmpZip != null) //noinspection ResultOfMethodCallIgnored
          tmpZip.delete();
      }
    }).start();
  }

  private static final java.util.Set<String> INSTALLING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

  private JSObject performInstall(File tmpZip) throws Exception {
    File tmpDir = new File(getContext().getCacheDir(), "install-" + System.currentTimeMillis());
    try {
      if (!tmpDir.mkdirs()) throw new IllegalStateException("TMP_FAILED");
      unzip(tmpZip, tmpDir);

      JSONObject manifest = readManifest(tmpDir);
      if (manifest == null) throw new IllegalStateException("MANIFEST_MISSING");

      String id = manifest.optString("id", "");
      if (!id.matches(ID_PATTERN)) throw new IllegalStateException("MANIFEST_INVALID");
      String version = manifest.optString("version", "");
      if (!version.matches("\\d+(\\.\\d+){0,2}")) throw new IllegalStateException("MANIFEST_INVALID");
      String entry = manifest.optString("entry", "index.html");
      if (!isSafeRelative(entry)) throw new IllegalStateException("MANIFEST_INVALID");
      if (!new File(tmpDir, entry).isFile()) throw new IllegalStateException("ENTRY_MISSING");

      File appsDir = new File(getContext().getFilesDir(), "apps");
      if (!appsDir.exists() && !appsDir.mkdirs()) throw new IllegalStateException("APPS_DIR_FAILED");
      File target = new File(appsDir, id);

      JSONObject oldState = readStateObject(id);
      boolean builtin = oldState != null && oldState.optBoolean("builtin", false);
      if (builtin) throw new IllegalStateException("BUILTIN_PROTECTED");
      String oldPublisher = oldState == null ? "" : oldState.optString("publisher", "");
      String newPublisher = manifest.optString("publisher", "");
      if (!oldPublisher.isEmpty() && !oldPublisher.equals(newPublisher)) {
        throw new IllegalStateException("PUBLISHER_MISMATCH");
      }

      String previous = readInstalledVersion(id);

      synchronized (INSTALLING) {
        if (!INSTALLING.add(id)) throw new IllegalStateException("INSTALL_IN_PROGRESS");
      }
      try {
      if (target.exists()) {
        File backup = new File(appsDir, ".old-" + id);
        deleteRecursive(backup);
        if (!target.renameTo(backup)) {
          copyDir(target, backup);
          deleteRecursive(target);
        }
        try {
          if (!tmpDir.renameTo(target)) {
            copyDir(tmpDir, target);
            deleteRecursive(tmpDir);
          }
        } catch (Exception replaceError) {
          deleteRecursive(target);
          //noinspection ResultOfMethodCallIgnored
          backup.renameTo(target);
          throw replaceError;
        }
        deleteRecursive(backup);
      } else {
        if (!tmpDir.renameTo(target)) {
          copyDir(tmpDir, target);
          deleteRecursive(tmpDir);
        }
      }

      writeState(target, manifest, id, version, entry, previous != null);
      clearRemoved(id);
      } finally {
        INSTALLING.remove(id);
      }

      JSObject result = new JSObject();
      result.put("id", id);
      result.put("name", manifest.optString("name", id));
      result.put("version", version);
      result.put("entry", entry);
      result.put("origin", "https://" + id + ".app.local/");
      return result;
    } finally {
      deleteRecursive(tmpDir);
    }
  }

  @PluginMethod
  public void inspect(PluginCall call) {
    String url = call.getString("url");
    if (url == null || !isAllowedInstallUrl(url)) {
      call.reject("INVALID_URL");
      return;
    }
    cancelRequested = false;
    new Thread(() -> {
      File tmpZip = null;
      File tmpDir = null;
      boolean keep = false;
      try {
        tmpZip = File.createTempFile("docklet-inspect-", ".zip", getContext().getCacheDir());
        long size = download(url, tmpZip, "inspect");
        if (size <= 0 || size > MAX_DOWNLOAD) throw new IllegalStateException("TOO_LARGE");
        if (!isZip(tmpZip)) throw new IllegalStateException("DOWNLOAD_BLOCKED");
        tmpDir = new File(getContext().getCacheDir(), "inspect-" + System.currentTimeMillis());
        if (!tmpDir.mkdirs()) throw new IllegalStateException("TMP_FAILED");
        unzip(tmpZip, tmpDir);

        JSONObject manifest = readManifest(tmpDir);
        if (manifest == null) throw new IllegalStateException("MANIFEST_MISSING");
        String id = manifest.optString("id", "");
        if (!id.matches(ID_PATTERN)) throw new IllegalStateException("MANIFEST_INVALID");
        String version = manifest.optString("version", "");
        if (!version.matches("\\d+(\\.\\d+){0,2}")) throw new IllegalStateException("MANIFEST_INVALID");
        String entry = manifest.optString("entry", "index.html");
        if (!isSafeRelative(entry) || !new File(tmpDir, entry).isFile()) throw new IllegalStateException("ENTRY_MISSING");

        JSObject result = new JSObject();
        result.put("id", id);
        result.put("name", manifest.optString("name", id));
        result.put("version", version);
        result.put("icon", manifest.optString("icon", ""));
        result.put("entry", entry);
        JSONArray caps = manifest.optJSONArray("capabilities");
        result.put("capabilities", caps == null ? new JSArray() : caps);
        JSONArray perms = manifest.optJSONArray("permissions");
        result.put("permissions", perms == null ? new JSArray() : perms);
        result.put("size", size);
        result.put("token", tmpZip.getName());
        keep = true;
        call.resolve(result);
      } catch (Exception error) {
        String message = error.getMessage() == null ? "INSPECT_FAILED" : error.getMessage();
        call.reject(message);
      } finally {
        if (!keep && tmpZip != null) //noinspection ResultOfMethodCallIgnored
          tmpZip.delete();
        if (tmpDir != null) deleteRecursive(tmpDir);
      }
    }).start();
  }

  @PluginMethod
  public void installToken(PluginCall call) {
    String token = call.getString("token");
    if (token == null || !token.matches("docklet-inspect-\\d+\\.zip")) {
      call.reject("INVALID_TOKEN");
      return;
    }
    File zip = new File(getContext().getCacheDir(), token);
    try {
      if (!zip.getCanonicalPath().startsWith(getContext().getCacheDir().getCanonicalPath())) {
        call.reject("INVALID_TOKEN");
        return;
      }
    } catch (Exception error) {
      call.reject("INVALID_TOKEN");
      return;
    }
    if (!zip.isFile()) {
      call.reject("TOKEN_EXPIRED");
      return;
    }
    cancelRequested = false;
    emitProgress("install", 0, 0);
    new Thread(() -> {
      try {
        JSObject result = performInstall(zip);
        call.resolve(result);
      } catch (Exception error) {
        String message = error.getMessage() == null ? "INSTALL_FAILED" : error.getMessage();
        call.reject(message);
      } finally {
        //noinspection ResultOfMethodCallIgnored
        zip.delete();
      }
    }).start();
  }

  @PluginMethod
  public void inspectBase64(PluginCall call) {
    String data = call.getString("data");
    if (data == null || data.isEmpty()) {
      call.reject("INVALID_DATA");
      return;
    }
    new Thread(() -> {
      File tmpZip = null;
      File tmpDir = null;
      boolean keep = false;
      try {
        byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
        if (bytes.length <= 0 || bytes.length > MAX_DOWNLOAD) throw new IllegalStateException("TOO_LARGE");
        tmpZip = File.createTempFile("docklet-inspect-", ".zip", getContext().getCacheDir());
        try (FileOutputStream out = new FileOutputStream(tmpZip)) {
          out.write(bytes);
        }
        tmpDir = new File(getContext().getCacheDir(), "inspect-" + System.currentTimeMillis());
        if (!tmpDir.mkdirs()) throw new IllegalStateException("TMP_FAILED");
        unzip(tmpZip, tmpDir);

        JSONObject manifest = readManifest(tmpDir);
        if (manifest == null) throw new IllegalStateException("MANIFEST_MISSING");
        String id = manifest.optString("id", "");
        if (!id.matches(ID_PATTERN)) throw new IllegalStateException("MANIFEST_INVALID");
        String version = manifest.optString("version", "");
        if (!version.matches("\\d+(\\.\\d+){0,2}")) throw new IllegalStateException("MANIFEST_INVALID");
        String entry = manifest.optString("entry", "index.html");
        if (!isSafeRelative(entry) || !new File(tmpDir, entry).isFile()) throw new IllegalStateException("ENTRY_MISSING");

        JSObject result = new JSObject();
        result.put("id", id);
        result.put("name", manifest.optString("name", id));
        result.put("version", version);
        result.put("icon", manifest.optString("icon", ""));
        result.put("entry", entry);
        JSONArray caps = manifest.optJSONArray("capabilities");
        result.put("capabilities", caps == null ? new JSArray() : caps);
        JSONArray perms = manifest.optJSONArray("permissions");
        result.put("permissions", perms == null ? new JSArray() : perms);
        result.put("size", bytes.length);
        result.put("token", tmpZip.getName());
        keep = true;
        call.resolve(result);
      } catch (Exception error) {
        String message = error.getMessage() == null ? "INSPECT_FAILED" : error.getMessage();
        call.reject(message);
      } finally {
        if (!keep && tmpZip != null) //noinspection ResultOfMethodCallIgnored
          tmpZip.delete();
        if (tmpDir != null) deleteRecursive(tmpDir);
      }
    }).start();
  }

  @PluginMethod
  public void remove(PluginCall call) {
    String id = call.getString("id");
    if (id == null || !id.matches(ID_PATTERN)) {
      call.reject("INVALID_ID");
      return;
    }
    deleteRecursive(new File(getContext().getFilesDir(), "apps/" + id));
    deleteRecursive(new File(getContext().getFilesDir(), "data/" + id));
    markRemoved(id);
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        WebStorage.getInstance().deleteOrigin("https://" + id + ".app.local");
      }
    } catch (Throwable ignored) {
      /* 老系统没有该方法 */
    }
    // 清掉该应用注册的后台监听策略，没有剩余策略时停掉服务
    try {
      SharedPreferences relay = getContext().getSharedPreferences("phone_relay", Context.MODE_PRIVATE);
      JSONArray policies = new JSONArray(relay.getString("watchPolicies", "[]"));
      JSONArray kept = new JSONArray();
      boolean changed = false;
      for (int i = 0; i < policies.length(); i += 1) {
        JSONObject policy = policies.optJSONObject(i);
        if (policy == null) continue;
        if (policy.optString("id", "").startsWith(id + ":")) changed = true;
        else kept.put(policy);
      }
      if (changed) {
        relay.edit().putString("watchPolicies", kept.toString()).apply();
        if (kept.length() == 0) getContext().stopService(new Intent(getContext(), PhoneRelayService.class));
      }
      relay.edit().remove("notify." + id).apply();
    } catch (Exception ignored) {
      /* 清不掉不影响卸载 */
    }
    call.resolve();
  }

  // ---------------------------------------------------------------- capabilities

  /** 后台提醒的应用列表：有 background 能力的已装应用 + 是否允许提醒（壳设置用） */
  @PluginMethod
  public void appNotifications(PluginCall call) {
    JSArray list = new JSArray();
    SharedPreferences relay = getContext().getSharedPreferences("phone_relay", Context.MODE_PRIVATE);
    File appsDir = new File(getContext().getFilesDir(), "apps");
    File[] dirs = appsDir.listFiles(File::isDirectory);
    if (dirs != null) {
      Arrays.sort(dirs);
      for (File dir : dirs) {
        JSONObject meta = readManifest(dir);
        if (meta == null) continue;
        boolean background = false;
        JSONArray caps = meta.optJSONArray("capabilities");
        for (int i = 0; caps != null && i < caps.length(); i += 1) {
          if ("background".equals(caps.optString(i))) background = true;
        }
        if (!background) continue;
        String id = dir.getName();
        JSObject item = new JSObject();
        item.put("id", id);
        item.put("name", meta.optString("name", id));
        item.put("icon", meta.optString("icon", ""));
        item.put("enabled", relay.getBoolean("notify." + id, true));
        list.put(item);
      }
    }
    JSObject result = new JSObject();
    result.put("apps", list);
    call.resolve(result);
  }

  /** 壳设置里按应用开关提醒；原生轮询提醒前会读这个开关 */
  @PluginMethod
  public void setAppNotification(PluginCall call) {
    String id = call.getString("id", "");
    if (id.isEmpty()) {
      call.reject("BAD_ID");
      return;
    }
    boolean enabled = !Boolean.FALSE.equals(call.getBoolean("enabled", true));
    getContext()
        .getSharedPreferences("phone_relay", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("notify." + id, enabled)
        .apply();
    call.resolve();
  }

  @PluginMethod
  public void notify(PluginCall call) {    if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
      String appId = call.getString("appId", "");
      JSObject event = new JSObject();
      event.put("appId", appId);
      event.put("name", appName(appId));
      event.put("reason", permissionReason(appId, "notifications"));
      notifyListeners("permissionRequest", event);
      call.reject("PERMISSION_REQUIRED");
      return;
    }
    sendNotification(call);
  }

  @PluginMethod
  public void requestNotifications(PluginCall call) {
    if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
      JSObject result = new JSObject();
      result.put("granted", true);
      call.resolve(result);
      return;
    }
    requestPermissionForAlias("notifications", call, "afterPermissionRequest");
  }

  @PermissionCallback
  private void afterPermissionRequest(PluginCall call) {
    JSObject result = new JSObject();
    result.put("granted", getPermissionState("notifications") == PermissionState.GRANTED);
    call.resolve(result);
  }

  private String appName(String appId) {
    JSONObject state = readStateObject(appId);
    return state == null ? appId : state.optString("name", appId);
  }

  private String permissionReason(String appId, String permission) {
    JSONObject state = readStateObject(appId);
    JSONArray perms = state == null ? null : state.optJSONArray("permissions");
    if (perms == null) return "";
    for (int i = 0; i < perms.length(); i += 1) {
      JSONObject item = perms.optJSONObject(i);
      if (item == null) continue;
      if (item.optString("name", "").toLowerCase(Locale.US).contains("notif")) {
        return item.optString("reason", "");
      }
    }
    return "";
  }

  private void sendNotification(PluginCall call) {
    try {
      String appId = call.getString("appId", "");
      if (!appId.matches(ID_PATTERN)) throw new IllegalStateException("INVALID_APP");
      JSONObject state = readStateObject(appId);
      String appName = state == null ? appId : state.optString("name", appId);
      boolean declared = false;
      JSONArray perms = state == null ? null : state.optJSONArray("permissions");
      if (perms != null) {
        for (int i = 0; i < perms.length(); i += 1) {
          JSONObject perm = perms.optJSONObject(i);
          String name = perm == null ? "" : perm.optString("name", "").toLowerCase(Locale.US);
          if (name.contains("notif")) {
            declared = true;
            break;
          }
        }
      }
      if (!declared) throw new IllegalStateException("NOT_DECLARED");
      long last = prefs().getLong("notifyAt_" + appId, 0L);
      if (System.currentTimeMillis() - last < 2000) throw new IllegalStateException("RATE_LIMITED");
      prefs().edit().putLong("notifyAt_" + appId, System.currentTimeMillis()).apply();
      String title = call.getString("title", "");
      String body = call.getString("body", "");
      if (title == null || title.isEmpty()) title = appName;
      else title = appName + "：" + title;
      android.app.NotificationManager manager =
          (android.app.NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
      if (manager == null) throw new IllegalStateException("NO_NOTIFICATION_SERVICE");
      String channelId = "docklet.apps";
      if (Build.VERSION.SDK_INT >= 26) {
        android.app.NotificationChannel channel =
            new android.app.NotificationChannel(channelId, "应用通知", android.app.NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);
      }
      android.content.Intent open =
          new android.content.Intent(getContext(), MainActivity.class)
              .putExtra("openApp", appId)
              .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
      android.app.PendingIntent pending =
          android.app.PendingIntent.getActivity(
              getContext(),
              appId.hashCode(),
              open,
              android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
      androidx.core.app.NotificationCompat.Builder builder =
          new androidx.core.app.NotificationCompat.Builder(getContext(), channelId)
              .setSmallIcon(getContext().getApplicationInfo().icon)
              .setContentTitle(title)
              .setContentText(body)
              .setContentIntent(pending)
              .setAutoCancel(true);
      manager.notify((int) (System.currentTimeMillis() % 100000), builder.build());
      call.resolve();
    } catch (Exception error) {
      call.reject(error.getMessage() == null ? "NOTIFY_FAILED" : error.getMessage());
    }
  }

  private String sanitizeFileName(String name) {
    String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("^\\.+", "").trim();
    if (clean.isEmpty()) clean = "download";
    if (clean.length() > 80) {
      int dot = clean.lastIndexOf('.');
      String ext = dot > 0 && clean.length() - dot <= 10 ? clean.substring(dot) : "";
      clean = clean.substring(0, 80 - ext.length()) + ext;
    }
    return clean;
  }

  @PluginMethod
  public void download(PluginCall call) {
    String url = call.getString("url", "");
    String name = call.getString("name", "");
    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
      call.reject("INVALID_URL");
      return;
    }
    try {
      android.app.DownloadManager manager =
          (android.app.DownloadManager) getContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE);
      if (manager == null) throw new IllegalStateException("NO_DOWNLOAD_SERVICE");
      String fileName = name == null || name.isEmpty() ? android.webkit.URLUtil.guessFileName(url, null, null) : name;
      fileName = sanitizeFileName(fileName);
      android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
      request.setTitle(fileName);
      request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
      manager.enqueue(request);
      call.resolve();
    } catch (Exception error) {
      call.reject("DOWNLOAD_FAILED");
    }
  }

  @PluginMethod
  public void permissionStatus(PluginCall call) {
    JSObject result = new JSObject();
    boolean granted = Build.VERSION.SDK_INT < 33
        || getContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    result.put("notifications", granted);
    call.resolve(result);
  }

  @PluginMethod
  public void openNotificationSettings(PluginCall call) {
    try {
      android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
      intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
      intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(intent);
      call.resolve();
    } catch (Exception error) {
      call.reject("SETTINGS_FAILED");
    }
  }

  @PluginMethod
  public void openExternal(PluginCall call) {
    String url = call.getString("url", "");
    if (!(url.startsWith("https://") || url.startsWith("http://"))) {
      call.reject("INVALID_URL");
      return;
    }
    try {
      android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
      intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(intent);
      call.resolve();
    } catch (Exception error) {
      call.reject("OPEN_FAILED");
    }
  }

  @PluginMethod
  public void clipboardWrite(PluginCall call) {
    try {
      String text = call.getString("text", "");
      android.content.ClipboardManager clipboard =
          (android.content.ClipboardManager) getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      if (clipboard == null) throw new IllegalStateException("NO_CLIPBOARD");
      clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Docklet", text));
      call.resolve();
    } catch (Exception error) {
      call.reject("CLIPBOARD_FAILED");
    }
  }

  @PluginMethod
  public void shareText(PluginCall call) {
    try {
      String text = call.getString("text", "");
      android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
      send.setType("text/plain");
      send.putExtra(android.content.Intent.EXTRA_TEXT, text);
      android.content.Intent chooser = android.content.Intent.createChooser(send, null);
      chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(chooser);
      call.resolve();
    } catch (Exception error) {
      call.reject("SHARE_FAILED");
    }
  }

  // ---------------------------------------------------------------- fs

  @PluginMethod
  public void fsWrite(PluginCall call) {
    try {
      String id = requireId(call);
      String path = requirePath(call);
      String base64 = call.getString("data", "");
      byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
      if (bytes.length > MAX_FS_FILE) throw new IllegalStateException("FS_TOO_LARGE");
      File file = dataFile(id, path, true);
      long used = dirSize(new File(getContext().getFilesDir(), "data/" + id));
      long delta = bytes.length - (file.isFile() ? file.length() : 0);
      if (used + delta > MAX_DATA) throw new IllegalStateException("FS_QUOTA");
      File parent = file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("MKDIR_FAILED");
      try (FileOutputStream out = new FileOutputStream(file)) {
        out.write(bytes);
      }
      call.resolve();
    } catch (Exception error) {
      call.reject(error.getMessage() == null ? "FS_WRITE_FAILED" : error.getMessage());
    }
  }

  @PluginMethod
  public void fsRead(PluginCall call) {
    try {
      String id = requireId(call);
      String path = requirePath(call);
      File file = dataFile(id, path, false);
      if (!file.isFile()) throw new IllegalStateException("NOT_FOUND");
      byte[] bytes = readBytes(file);
      JSObject result = new JSObject();
      result.put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
      call.resolve(result);
    } catch (Exception error) {
      call.reject(error.getMessage() == null ? "FS_READ_FAILED" : error.getMessage());
    }
  }

  @PluginMethod
  public void fsList(PluginCall call) {
    try {
      String id = requireId(call);
      String path = call.getString("path", "");
      File dir = dataFile(id, path, true);
      if (!dir.isDirectory()) throw new IllegalStateException("NOT_FOUND");
      JSArray items = new JSArray();
      File[] files = dir.listFiles();
      if (files != null) {
        Arrays.sort(files);
        for (File file : files) {
          JSObject item = new JSObject();
          item.put("name", file.getName());
          item.put("dir", file.isDirectory());
          item.put("size", file.length());
          items.put(item);
        }
      }
      JSObject result = new JSObject();
      result.put("items", items);
      call.resolve(result);
    } catch (Exception error) {
      call.reject(error.getMessage() == null ? "FS_LIST_FAILED" : error.getMessage());
    }
  }

  @PluginMethod
  public void fsDelete(PluginCall call) {
    try {
      String id = requireId(call);
      String path = requirePath(call);
      File file = dataFile(id, path, false);
      deleteRecursive(file);
      call.resolve();
    } catch (Exception error) {
      call.reject(error.getMessage() == null ? "FS_DELETE_FAILED" : error.getMessage());
    }
  }

  private String requireId(PluginCall call) {
    String id = call.getString("id", "");
    if (!id.matches(ID_PATTERN)) throw new IllegalStateException("INVALID_ID");
    return id;
  }

  private String requirePath(PluginCall call) {
    String path = call.getString("path", "");
    if (!isSafeRelative(path)) throw new IllegalStateException("INVALID_PATH");
    return path;
  }

  /** data/<id>/ 下的文件，禁止逃逸。 */
  private File dataFile(String id, String path, boolean allowDir) throws IOException {
    File base = new File(getContext().getFilesDir(), "data/" + id).getCanonicalFile();
    if (!base.exists() && !base.mkdirs()) throw new IllegalStateException("DATA_DIR_FAILED");
    File file = new File(base, path).getCanonicalFile();
    if (!file.equals(base) && !file.getPath().startsWith(base.getPath() + File.separator)) {
      throw new IllegalStateException("INVALID_PATH");
    }
    if (!allowDir && file.equals(base)) throw new IllegalStateException("INVALID_PATH");
    return file;
  }

  // ---------------------------------------------------------------- helpers

  private JSObject scanApps() throws Exception {
    File appsDir = new File(getContext().getFilesDir(), "apps");
    JSArray apps = new JSArray();
    File[] dirs = appsDir.listFiles(File::isDirectory);
    if (dirs != null) {
      Arrays.sort(dirs);
      for (File dir : dirs) {
        JSONObject meta = readManifest(dir);
        if (meta == null) continue;
        String id = dir.getName();
        JSONObject scanState = readStateObject(id);
        String stateId = scanState == null ? "" : scanState.optString("id", "");
        // 目录名必须与 state.json 的 id 一致，避免错位目录被当成可运行应用
        if (!stateId.isEmpty() && !stateId.equals(id)) continue;
        JSObject item = new JSObject();
        item.put("id", id);
        item.put("name", meta.optString("name", id));
        item.put("version", meta.optString("version", ""));
        item.put("icon", meta.optString("icon", ""));
        item.put("shortcutColor", meta.optString("shortcutColor", ""));
        item.put("entry", meta.optString("entry", "index.html"));
        item.put("origin", "https://" + id + ".app.local/");
        item.put("description", meta.optString("description", ""));
        item.put("tagline", meta.optString("tagline", ""));
        item.put("author", meta.optString("author", ""));
        item.put("authorContact", meta.optString("authorContact", ""));
        item.put("updatedAt", meta.optString("updatedAt", ""));
        item.put("immersive", meta.optBoolean("immersive", false));
        item.put("size", dirSize(dir));
      JSONObject appState = readStateObject(dir.getName());
      if (appState != null) {
        item.put("publishedAt", appState.optString("publishedAt", ""));
        item.put("releasedAt", appState.optString("releasedAt", ""));
      }
        JSONArray caps = meta.optJSONArray("capabilities");
        item.put("capabilities", caps == null ? new JSArray() : caps);
        JSONArray perms = meta.optJSONArray("permissions");
        item.put("permissions", perms == null ? new JSArray() : perms);
        apps.put(item);
      }
    }
    JSObject result = new JSObject();
    result.put("apps", apps);
    return result;
  }

  private long dirSize(File dir) {
    long total = 0;
    File[] files = dir.listFiles();
    if (files == null) return 0;
    for (File file : files) {
      total += file.isDirectory() ? dirSize(file) : file.length();
    }
    return total;
  }

  private String readInstalledVersion(String id) {
    JSONObject json = readStateObject(id);
    return json == null ? null : json.optString("version", null);
  }

  private JSONObject readStateObject(String id) {
    try {
      File state = new File(getContext().getFilesDir(), "apps/" + id + "/.docklet/state.json");
      if (!state.isFile()) return null;
      return new JSONObject(readText(state));
    } catch (Exception ignored) {
      return null;
    }
  }

  private void writeState(File dir, JSONObject manifest, String id, String version, String entry, boolean update) throws IOException {
    JSObject state = new JSObject();
    state.put("id", id);
    state.put("origin", "https://" + id + ".app.local");
    state.put("version", version);
    state.put("entry", entry);
    state.put("name", manifest.optString("name", id));
    state.put("icon", manifest.optString("icon", ""));
    state.put("installedAt", update ? readInstalledAt(id) : now());
    String updatedAt = update ? now() : null;
    if (updatedAt != null) state.put("updatedAt", updatedAt);
    JSONArray capabilities = manifest.optJSONArray("capabilities");
    state.put("capabilities", capabilities == null ? new JSArray() : capabilities);
    JSONArray manifestPerms = manifest.optJSONArray("permissions");
    state.put("permissions", manifestPerms == null ? new JSArray() : manifestPerms);
    String publisher = manifest.optString("publisher", "");
    if (!publisher.isEmpty()) state.put("publisher", publisher);
    state.put("publishedAt", manifest.optString("publishedAt", ""));
    state.put("releasedAt", manifest.optString("updatedAt", ""));

    File metaDir = new File(dir, ".docklet");
    if (!metaDir.exists() && !metaDir.mkdirs()) throw new IOException("STATE_DIR_FAILED");
    try (FileOutputStream out = new FileOutputStream(new File(metaDir, "state.json"))) {
      out.write(state.toString().getBytes(StandardCharsets.UTF_8));
    }
  }

  private String readInstalledAt(String id) {
    try {
      File state = new File(getContext().getFilesDir(), "apps/" + id + "/.docklet/state.json");
      if (!state.isFile()) return now();
      JSONObject json = new JSONObject(readText(state));
      return json.optString("installedAt", now());
    } catch (Exception ignored) {
      return now();
    }
  }

  private String now() {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format.format(new Date());
  }

  private int compareVersion(String a, String b) {
    String[] pa = a.split("\\.");
    String[] pb = b.split("\\.");
    int length = Math.max(pa.length, pb.length);
    for (int i = 0; i < length; i++) {
      int va = i < pa.length ? parseInt(pa[i]) : 0;
      int vb = i < pb.length ? parseInt(pb[i]) : 0;
      if (va != vb) return va - vb;
    }
    return 0;
  }

  private boolean isAllowedInstallUrl(String url) {
    if (url.startsWith("https://")) return true;
    if (!url.startsWith("http://")) return false;
    String host = android.net.Uri.parse(url).getHost();
    if (host == null) return false;
    if ("localhost".equals(host) || "127.0.0.1".equals(host)) return true;
    if (host.startsWith("192.168.") || host.startsWith("10.")) return true;
    return host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
  }

  private int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (Exception ignored) {
      return 0;
    }
  }

  private long download(String url, File target, String stage) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(30000);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Docklet/1.0");
    connection.setRequestProperty("Accept", "application/zip,application/octet-stream,*/*");
    int status = connection.getResponseCode();
    if (status < 200 || status >= 300) throw new IOException("HTTP_" + status);
    long total = 0;
    long lastEmit = 0;
    long expected = connection.getContentLengthLong();
    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) {
        if (cancelRequested) throw new IOException("CANCELLED");
        total += read;
        if (total > MAX_DOWNLOAD) throw new IOException("TOO_LARGE");
        out.write(buffer, 0, read);
        long now = System.currentTimeMillis();
        if (now - lastEmit > 200) {
          lastEmit = now;
          emitProgress(stage, total, expected);
        }
      }
    } finally {
      connection.disconnect();
    }
    emitProgress(stage, total, expected > 0 ? expected : total);
    return total;
  }

  private boolean isZip(File file) {
    try (InputStream in = new FileInputStream(file)) {
      return in.read() == 'P' && in.read() == 'K';
    } catch (Exception ignored) {
      return false;
    }
  }

  private void emitProgress(String stage, long received, long total) {
    JSObject data = new JSObject();
    data.put("stage", stage);
    data.put("received", received);
    data.put("total", total);
    notifyListeners("progress", data);
  }

  @PluginMethod
  public void cancelInstall(PluginCall call) {
    cancelRequested = true;
    call.resolve();
  }

  private void unzip(File zip, File dir) throws IOException {
    long total = 0;
    int count = 0;
    Set<String> seen = new HashSet<>();
    try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        count += 1;
        if (count > MAX_ENTRIES) throw new IOException("TOO_MANY_FILES");
        String name = entry.getName().replace('\\', '/');
        if (!isSafeRelative(name)) throw new IOException("ZIP_UNSAFE");
        if (!seen.add(name)) throw new IOException("ZIP_DUPLICATE");
        if (entry.getSize() > MAX_FILE) throw new IOException("TOO_LARGE");
        if (entry.getCompressedSize() > 0 && entry.getSize() > 0
            && entry.getSize() / Math.max(1, entry.getCompressedSize()) > 200) {
          throw new IOException("ZIP_UNSAFE");
        }
        File out = new File(dir, name);
        if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) {
          throw new IOException("ZIP_UNSAFE");
        }
        if (entry.isDirectory()) {
          if (!out.mkdirs() && !out.isDirectory()) throw new IOException("ZIP_UNSAFE");
          continue;
        }
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("ZIP_UNSAFE");
        try (FileOutputStream os = new FileOutputStream(out)) {
          byte[] buffer = new byte[8192];
          int read;
          while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > MAX_TOTAL || out.length() > MAX_FILE) throw new IOException("TOO_LARGE");
            os.write(buffer, 0, read);
          }
        }
      }
    }
  }

  private boolean isSafeRelative(String path) {
    if (path == null || path.isEmpty()) return false;
    if (path.startsWith("/") || path.startsWith("\\")) return false;
    if (path.contains("..")) return false;
    if (path.contains("\\")) return false;
    return true;
  }

  private JSONObject readManifest(File dir) {
    File file = new File(dir, "app.json");
    if (!file.isFile()) return null;
    try {
      return new JSONObject(readText(file));
    } catch (Exception ignored) {
      return null;
    }
  }

  private String readText(File file) throws IOException {
    try (InputStream in = new java.io.FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  /** 用户主动卸载过的内置应用，启动时不再从 assets 补回。 */
  private static final String PREFS = "docklet_apphost";
  private static final String KEY_REMOVED = "removed";

  private SharedPreferences prefs() {
    return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private boolean isRemoved(String id) {
    return prefs().getStringSet(KEY_REMOVED, new HashSet<>()).contains(id);
  }

  private void markRemoved(String id) {
    Set<String> ids = new HashSet<>(prefs().getStringSet(KEY_REMOVED, new HashSet<>()));
    ids.add(id);
    prefs().edit().putStringSet(KEY_REMOVED, ids).apply();
  }

  private void clearRemoved(String id) {
    Set<String> ids = new HashSet<>(prefs().getStringSet(KEY_REMOVED, new HashSet<>()));
    if (ids.remove(id)) prefs().edit().putStringSet(KEY_REMOVED, ids).apply();
  }

  /** 把 APK 内置的 apps/ 中尚未落地的应用复制到 filesDir/apps。 */
  private void seed() {
    File appsDir = new File(getContext().getFilesDir(), "apps");
    try {
      AssetManager assets = getContext().getAssets();
      String[] ids = assets.list("apps");
      if (ids == null) return;
      if (!appsDir.exists() && !appsDir.mkdirs()) return;
      for (String id : ids) {
        if (!id.matches(ID_PATTERN)) continue;
        if (isRemoved(id)) continue;
        File target = new File(appsDir, id);
        if (target.isDirectory()) {
          String assetVersion = assetVersion(assets, "apps/" + id);
          String installed = readInstalledVersion(id);
          boolean newer = assetVersion != null && (installed == null || compareVersion(assetVersion, installed) > 0);
          if (newer) {
            deleteRecursive(target);
            copyAssetDir(assets, "apps/" + id, target);
            writeBuiltinState(target, assetVersion);
          } else {
            // 同步 state（permissions/capabilities 可能随 APK 清单变化），保留 installedAt
            writeBuiltinState(target, installed != null ? installed : assetVersion);
          }
          continue;
        }
        copyAssetDir(assets, "apps/" + id, target);
        String copiedVersion = assetVersion(assets, "apps/" + id);
        if (copiedVersion != null) writeBuiltinState(target, copiedVersion);
      }
    } catch (Exception ignored) {
      /* 下次启动再试 */
    }
  }

  private String assetVersion(AssetManager assets, String dir) {
    try (InputStream in = assets.open(dir + "/app.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
      JSONObject json = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
      return json.optString("version", null);
    } catch (Exception ignored) {
      return null;
    }
  }

  /** 内置应用升级后补写 state，保证版本号对得上。 */
  private void writeBuiltinState(File dir, String version) {
    try {
      JSONObject manifest = readManifest(dir);
      if (manifest == null) return;
      String id = manifest.optString("id", dir.getName());
      String entry = manifest.optString("entry", "index.html");
      writeState(dir, manifest, id, version, entry, true);
      File stateFile = new File(dir, ".docklet/state.json");
      JSONObject state = new JSONObject(readText(stateFile));
      state.put("builtin", true);
      try (FileOutputStream out = new FileOutputStream(stateFile)) {
        out.write(state.toString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (Exception ignored) {
      /* 状态写失败不影响运行 */
    }
  }

  private void copyAssetDir(AssetManager assets, String from, File to) throws IOException {
    String[] children = assets.list(from);
    //noinspection ResultOfMethodCallIgnored
    to.mkdirs();
    if (children == null || children.length == 0) {
      copyAssetFile(assets, from, to);
      return;
    }
    for (String child : children) {
      String childPath = from + "/" + child;
      File target = new File(to, child);
      String[] grandChildren = assets.list(childPath);
      if (grandChildren != null && grandChildren.length > 0) {
        copyAssetDir(assets, childPath, target);
      } else {
        copyAssetFile(assets, childPath, target);
      }
    }
  }

  private void copyAssetFile(AssetManager assets, String from, File to) throws IOException {
    File parent = to.getParentFile();
    if (parent != null) //noinspection ResultOfMethodCallIgnored
      parent.mkdirs();
    try (InputStream in = assets.open(from); FileOutputStream out = new FileOutputStream(to)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
    }
  }

  private void copyDir(File from, File to) throws IOException {
    if (!to.exists() && !to.mkdirs()) throw new IOException("COPY_FAILED");
    File[] files = from.listFiles();
    if (files == null) return;
    for (File file : files) {
      File target = new File(to, file.getName());
      if (file.isDirectory()) {
        copyDir(file, target);
      } else {
        copyFile(file, target);
      }
    }
  }

  private byte[] readBytes(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
      return out.toByteArray();
    }
  }

  private void copyFile(File from, File to) throws IOException {
    File parent = to.getParentFile();
    if (parent != null) //noinspection ResultOfMethodCallIgnored
      parent.mkdirs();
    try (InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
    }
  }

  private void deleteRecursive(File file) {
    if (file == null || !file.exists()) return;
    File[] children = file.listFiles();
    if (children != null) for (File child : children) deleteRecursive(child);
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }
}
