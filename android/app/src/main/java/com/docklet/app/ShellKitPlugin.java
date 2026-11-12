package com.docklet.app;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.URLUtil;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 壳能力：开系统浏览器、把文件下到「下载」目录。
 * 由 AppHost 的 api.openExternal / api.download 调用，应用拿不到别的入口。
 */
@CapacitorPlugin(name = "ShellKit")
public class ShellKitPlugin extends Plugin {
  private boolean isHttp(String url) {
    return url != null && (url.startsWith("http://") || url.startsWith("https://"));
  }

  private void openInBrowser(String url) {
    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(intent);
  }

  @PluginMethod
  public void openExternal(PluginCall call) {
    String url = call.getString("url", "");
    if (!isHttp(url)) {
      call.reject("只支持 http/https 链接");
      return;
    }
    try {
      openInBrowser(url);
      call.resolve();
    } catch (Exception error) {
      call.reject("打不开链接: " + error.getMessage());
    }
  }

  @PluginMethod
  public void download(PluginCall call) {
    String url = call.getString("url", "");
    String rawName = call.getString("filename", "");
    if (!isHttp(url)) {
      call.reject("只支持 http/https 链接");
      return;
    }

    // Android 10 以前写公共下载目录需要存储权限；没申请就交给浏览器下载
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      try {
        openInBrowser(url);
        call.resolve();
      } catch (Exception error) {
        call.reject("下载失败: " + error.getMessage());
      }
      return;
    }

    try {
      String name = rawName == null ? "" : rawName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
      if (name.isEmpty()) name = URLUtil.guessFileName(url, null, null);
      DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
      request.setTitle(name);
      request.setDescription("像素盒子");
      request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
      DownloadManager manager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
      if (manager == null) throw new IllegalStateException("系统下载服务不可用");
      manager.enqueue(request);
      call.resolve();
    } catch (Exception error) {
      // 系统下载服务拒绝时退回浏览器
      try {
        openInBrowser(url);
        call.resolve();
      } catch (Exception fallbackError) {
        call.reject("下载失败: " + error.getMessage());
      }
    }
  }
}
