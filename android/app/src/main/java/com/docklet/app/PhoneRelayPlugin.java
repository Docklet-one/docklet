package com.docklet.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

/** 壳 <-> 后台监听服务 的桥：应用提交监听策略、启停、查状态、要通知权限。 */
@CapacitorPlugin(name = "PhoneRelay")
public class PhoneRelayPlugin extends Plugin {
  private SharedPreferences prefs() {
    return getContext().getSharedPreferences("phone_relay", Context.MODE_PRIVATE);
  }

  private JSONArray policies() {
    try {
      String raw = prefs().getString("watchPolicies", "[]");
      return new JSONArray(raw == null || raw.isEmpty() ? "[]" : raw);
    } catch (Exception error) {
      return new JSONArray();
    }
  }

  @PluginMethod
  public void watchStart(PluginCall call) {
    String id = call.getString("id", "");
    String endpoint = call.getString("endpoint", "");
    if (id == null || id.trim().isEmpty()) {
      call.reject("缺少 id");
      return;
    }
    if (endpoint == null || !(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
      call.reject("endpoint 必须是 http/https");
      return;
    }
    long interval = Math.max(
        PhoneRelayService.MIN_INTERVAL_MS, call.getLong("intervalMs", 2500L));
    JSONArray current = policies();
    JSONArray next = new JSONArray();
    boolean replaced = false;
    for (int i = 0; i < current.length(); i++) {
      JSONObject item = current.optJSONObject(i);
      if (item == null) continue;
      if (id.equals(item.optString("id", ""))) {
        next.put(buildPolicy(call, interval));
        replaced = true;
      } else {
        next.put(item);
      }
    }
    if (!replaced) {
      if (next.length() >= PhoneRelayService.MAX_WATCHERS) {
        call.reject("最多同时监听 " + PhoneRelayService.MAX_WATCHERS + " 个应用");
        return;
      }
      next.put(buildPolicy(call, interval));
    }
    prefs().edit().putString("watchPolicies", next.toString()).apply();
    Context context = getContext();
    ContextCompat.startForegroundService(context, new Intent(context, PhoneRelayService.class));
    JSObject result = new JSObject();
    result.put("ok", true);
    result.put("count", next.length());
    call.resolve(result);
  }

  private JSONObject buildPolicy(PluginCall call, long interval) {
    JSONObject policy = new JSONObject();
    try {
      policy.put("id", call.getString("id", ""));
      policy.put("endpoint", call.getString("endpoint", ""));
      policy.put("token", call.getString("token", ""));
      policy.put("intervalMs", interval);
      policy.put("field", trim(call.getString("field", "running")));
      policy.put("from", call.getBoolean("from", true));
      policy.put("to", call.getBoolean("to", false));
      policy.put("failField", trim(call.getString("failField", "")));
      policy.put("title", trim(call.getString("title", "提醒")));
      policy.put("doneText", trim(call.getString("doneText", "已完成")));
      policy.put("failText", trim(call.getString("failText", "执行失败")));
      policy.put("openApp", trim(call.getString("openApp", "")));
      policy.put("suppressApp", trim(call.getString("suppressApp", "")));
      policy.put("enabled", call.getBoolean("enabled", true));
    } catch (Exception ignored) {
      /* 字段类型不对时用默认值 */
    }
    return policy;
  }

  private String trim(String value) {
    if (value == null) return "";
    return value.length() > 80 ? value.substring(0, 80) : value;
  }

  @PluginMethod
  public void watchStop(PluginCall call) {
    String id = call.getString("id", "");
    JSONArray next = new JSONArray();
    JSONArray current = policies();
    for (int i = 0; i < current.length(); i++) {
      JSONObject item = current.optJSONObject(i);
      if (item == null) continue;
      if (id == null || !id.equals(item.optString("id", ""))) next.put(item);
    }
    prefs().edit().putString("watchPolicies", next.toString()).apply();
    if (next.length() == 0) {
      getContext().stopService(new Intent(getContext(), PhoneRelayService.class));
    }
    JSObject result = new JSObject();
    result.put("count", next.length());
    call.resolve(result);
  }

  @PluginMethod
  public void watchStatus(PluginCall call) {
    String id = call.getString("id", "");
    JSONArray current = policies();
    boolean exists = false;
    for (int i = 0; i < current.length(); i++) {
      JSONObject item = current.optJSONObject(i);
      if (item != null && id != null && id.equals(item.optString("id", ""))) exists = true;
    }
    JSObject result = new JSObject();
    result.put("running", PhoneRelayService.running);
    result.put("count", current.length());
    result.put("exists", exists);
    result.put("activeApp", prefs().getString("activeApp", ""));
    boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    result.put("granted", granted);
    call.resolve(result);
  }

  @PluginMethod
  public void setActive(PluginCall call) {
    prefs().edit().putString("activeApp", call.getString("appId", "")).apply();
    call.resolve();
  }

  @PluginMethod
  public void testNotification(PluginCall call) {
    try {
      NotificationManager manager = (NotificationManager) getContext()
          .getSystemService(Context.NOTIFICATION_SERVICE);
      if (manager == null) {
        call.reject("没有通知服务");
        return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel channel = new NotificationChannel(
            PhoneRelayService.CHANNEL_ALERT, "结果提醒", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("任务结束时弹横幅并响铃");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] { 0, 250, 150, 250 });
        manager.createNotificationChannel(channel);
      }
      manager.notify(2002, new NotificationCompat.Builder(getContext(), PhoneRelayService.CHANNEL_ALERT)
          .setSmallIcon(R.drawable.ic_stat_docklet)
          .setContentTitle("测试提醒")
          .setContentText("通知通道正常")
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setDefaults(NotificationCompat.DEFAULT_ALL)
          .setAutoCancel(true)
          .build());
      call.resolve();
    } catch (Exception error) {
      call.reject("发送失败: " + error.getMessage());
    }
  }

  @PluginMethod
  public void readOpenChat(PluginCall call) {
    JSObject result = new JSObject();
    result.put("open", prefs().getBoolean("openChat", false));
    result.put("app", prefs().getString("openApp", "chat"));
    call.resolve(result);
  }

  @PluginMethod
  public void consumeOpenChat(PluginCall call) {
    boolean open = prefs().getBoolean("openChat", false);
    String app = prefs().getString("openApp", "chat");
    if (open) prefs().edit().putBoolean("openChat", false).remove("openApp").apply();
    JSObject result = new JSObject();
    result.put("open", open);
    result.put("app", app);
    call.resolve(result);
  }

  @PluginMethod
  public void requestPermission(PluginCall call) {
    JSObject result = new JSObject();
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      result.put("granted", true);
      call.resolve(result);
      return;
    }
    boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED;
    if (!granted) {
      ActivityCompat.requestPermissions(
          getActivity(), new String[] { Manifest.permission.POST_NOTIFICATIONS }, 3101);
    }
    result.put("granted", granted);
    result.put("pending", !granted);
    call.resolve(result);
  }
}
