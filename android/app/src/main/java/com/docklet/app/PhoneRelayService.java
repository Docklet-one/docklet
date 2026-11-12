package com.docklet.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 通用后台监听：按应用提交的「监听策略」轮询 endpoint，
 * 命中字段状态迁移（如 running true→false）就发本地提醒。
 * 壳不知道具体应用语义，只认策略。
 */
public class PhoneRelayService extends Service {
  public static final String CHANNEL_SERVICE = "phone_relay_service";
  /** 高重要性通道：横幅 + 声音 */
  public static final String CHANNEL_ALERT = "phone_relay_alert_v1";
  public static final int FOREGROUND_ID = 1001;
  /** 每个应用最多 1 条策略，最多 4 个应用 */
  public static final int MAX_WATCHERS = 4;
  public static final int MIN_INTERVAL_MS = 2000;
  public static volatile boolean running = false;

  private Thread worker;
  private volatile boolean active = false;
  private SharedPreferences prefs;
  private final Map<String, Boolean> lastValues = new HashMap<>();
  private final Map<String, String> lastTexts = new HashMap<>();
  private final Map<String, Long> lastPolls = new HashMap<>();
  private final Map<String, String> lastErrors = new HashMap<>();

  @Override
  public void onCreate() {
    super.onCreate();
    prefs = getSharedPreferences("phone_relay", MODE_PRIVATE);
    createChannels();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null && "stop".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    startForeground(FOREGROUND_ID, buildForeground());
    running = true;
    if (worker == null || !worker.isAlive()) {
      active = true;
      worker = new Thread(this::loop, "phone-relay");
      worker.start();
    }
    return START_NOT_STICKY;
  }

  private String activeApp() {
    String app = prefs.getString("activeApp", "");
    return app == null ? "" : app;
  }

  private void loop() {
    while (active) {
      boolean any = false;
      try {
        JSONArray policies = policies();
        for (int i = 0; i < policies.length(); i++) {
          JSONObject policy = policies.optJSONObject(i);
          if (policy == null) continue;
          if (!policy.optBoolean("enabled", true)) continue;
          any = true;
          poll(policy);
        }
      } catch (Exception ignored) {
        /* 配置坏了就本轮跳过 */
      }
      if (!any) {
        // 没有策略时停掉自己，避免无意义常驻
        stopSelf();
        return;
      }
      sleep(1000);
    }
  }

  private JSONArray policies() {
    String raw = prefs.getString("watchPolicies", "[]");
    try {
      return new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
    } catch (Exception error) {
      return new JSONArray();
    }
  }

  private void poll(JSONObject policy) {
    String id = policy.optString("id", "");
    if (id.isEmpty()) return;
    long interval = Math.max(MIN_INTERVAL_MS, policy.optLong("intervalMs", 2500));
    long now = System.currentTimeMillis();
    Long last = lastPolls.get(id);
    if (last != null && now - last < interval) return;
    lastPolls.put(id, now);
    String endpoint = policy.optString("endpoint", "");
    if (endpoint.isEmpty()) return;
    // 壳设置里按应用关掉提醒时，策略照常轮询但不弹通知
    int split = id.indexOf(':');
    boolean notifyAllowed = prefs.getBoolean("notify." + (split > 0 ? id.substring(0, split) : id), true);
    try {
      JSONObject json = new JSONObject(get(endpoint, policy.optString("token", "")));
      String field = policy.optString("field", "running");
      boolean failed = json.optBoolean(policy.optString("failField", ""), false);
      // 每条消息提醒：字段值变化就提醒（relay 的 lastAssistantSeq）
      if ("change".equals(policy.optString("mode"))) {
        String text = json.optString(field, "");
        String previousText = lastTexts.get(id);
        if (previousText != null
            && !previousText.isEmpty()
            && !text.isEmpty()
            && !text.equals(previousText)) {
          String suppress = policy.optString("suppressApp", "");
          boolean hidden = !suppress.isEmpty() && suppress.equals(activeApp());
          if (!hidden && notifyAllowed) {
            notifyResult(
                id,
                policy.optString("title", "提醒"),
                failed ? policy.optString("failText", "执行失败") : policy.optString("doneText", "新消息"),
                policy.optString("openApp", suppress));
          }
        }
        lastTexts.put(id, text);
        lastErrors.remove(id);
        return;
      }
      boolean value = json.optBoolean(field, false);
      Boolean previous = lastValues.get(id);
      if (previous != null
          && previous == policy.optBoolean("from", true)
          && value == policy.optBoolean("to", false)) {
        String suppress = policy.optString("suppressApp", "");
        boolean hidden = !suppress.isEmpty() && suppress.equals(activeApp());
        if (!hidden && notifyAllowed) {
          notifyResult(
              id,
              policy.optString("title", "提醒"),
              failed ? policy.optString("failText", "执行失败") : policy.optString("doneText", "已完成"),
              policy.optString("openApp", suppress));
        }
      }
      lastValues.put(id, value);
      lastErrors.remove(id);
    } catch (Exception error) {
      lastErrors.put(id, String.valueOf(error.getMessage()));
    }
  }

  private String get(String endpoint, String token) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setConnectTimeout(4000);
    connection.setReadTimeout(4000);
    if (token != null && !token.isEmpty()) {
      connection.setRequestProperty("Authorization", "Bearer " + token);
    }
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) builder.append(line);
      return builder.toString();
    } finally {
      connection.disconnect();
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      /* 退出时被打断 */
    }
  }

  private void notifyResult(String appId, String title, String text, String openApp) {
    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager == null) return;
    Intent open = new Intent(this, MainActivity.class);
    open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
    if (openApp != null && !openApp.isEmpty()) {
      open.putExtra("openChat", true);
      open.putExtra("openApp", openApp);
    }
    PendingIntent content = PendingIntent.getActivity(
        this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ALERT)
        .setSmallIcon(R.drawable.ic_stat_docklet)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setVibrate(new long[] { 0, 250, 150, 250 })
        .setAutoCancel(true)
        .setContentIntent(content)
        .build();
    manager.notify(2000 + Math.abs(appId.hashCode() % 500), notification);
  }

  private Notification buildForeground() {
    Intent stop = new Intent(this, PhoneRelayService.class);
    stop.setAction("stop");
    PendingIntent stopIntent = PendingIntent.getService(
        this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new NotificationCompat.Builder(this, CHANNEL_SERVICE)
        .setSmallIcon(R.drawable.ic_stat_docklet)
        .setContentTitle("后台提醒运行中")
        .setContentText("有结果时会提醒，点开回到应用")
        .setOngoing(true)
        .addAction(0, "停止", stopIntent)
        .build();
  }

  private void createChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
    NotificationManager manager = getSystemService(NotificationManager.class);
    if (manager == null) return;
    NotificationChannel service = new NotificationChannel(
        CHANNEL_SERVICE, "后台提醒", NotificationManager.IMPORTANCE_LOW);
    service.setDescription("保持与电脑的连接，有结果时提醒");
    manager.createNotificationChannel(service);
    NotificationChannel alert = new NotificationChannel(
        CHANNEL_ALERT, "结果提醒", NotificationManager.IMPORTANCE_HIGH);
    alert.setDescription("任务结束时弹横幅并响铃");
    alert.enableVibration(true);
    alert.setVibrationPattern(new long[] { 0, 250, 150, 250 });
    manager.createNotificationChannel(alert);
  }

  @Override
  public void onDestroy() {
    active = false;
    running = false;
    if (worker != null) worker.interrupt();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
