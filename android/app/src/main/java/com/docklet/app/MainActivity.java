package com.docklet.app;

import android.content.Intent;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

  /**
   * Edge-to-edge layout. The shell reports system insets to the page:
   * --safe-top / --safe-bottom in CSS px, plus a "docklet:keyboard" event with
   * the physical IME height (navigation bar excluded).
   */
  private void reportInsets() {
    try {
      WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
      final View content = findViewById(android.R.id.content);
      if (content == null) return;
      ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
        final Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        final int imeBottom = Math.max(0, ime.bottom);
        final int navPhysical = Math.max(0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
        final boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
        final float density = Math.max(1f, getResources().getDisplayMetrics().density);

        final int safeTop = Math.max(0, Math.round(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top / density));
        final int navBottom = Math.max(0, Math.round(navPhysical / density));
        final int safeBottom = imeBottom > 0 ? 0 : navBottom;
        final int keyboardHeight = imeBottom > 0 ? Math.max(0, imeBottom - navPhysical) : 0;

        final WebView webView = getBridge() == null ? null : getBridge().getWebView();
        if (webView != null) {
          webView.post(() -> webView.evaluateJavascript(
              "document.documentElement.style.setProperty('--safe-top','" + safeTop + "px');"
                  + "document.documentElement.style.setProperty('--safe-bottom','" + safeBottom + "px');"
                  + "window.dispatchEvent(new CustomEvent('docklet:keyboard',{detail:{height:" + keyboardHeight
                  + ",visible:" + (imeVisible ? "true" : "false") + ",safeTop:" + safeTop
                  + ",safeBottom:" + safeBottom + "}}))", null));
        }
        return insets;
      });
      ViewCompat.requestApplyInsets(content);
      content.postDelayed(() -> ViewCompat.requestApplyInsets(content), 1200);
    } catch (Exception ignored) {
      /* keep system defaults */
    }
  }

  /** Wrap the WebView client so *.app.local is served from filesDir/apps. */
  private void wrapAppHostClient() {
    try {
      WebView webView = getBridge() == null ? null : getBridge().getWebView();
      if (webView == null) return;
      if (webView.getWebViewClient() instanceof AppHostClient) return;
      webView.setWebViewClient(new AppHostClient(this, webView.getWebViewClient()));
    } catch (Exception ignored) {
      /* keep builtin mode */
    }
  }

  /** 目录应用里的 http(s) 下载交给系统下载器。 */
  private void installDownloadHandler() {
    try {
      final WebView webView = getBridge() == null ? null : getBridge().getWebView();
      if (webView == null) return;
      webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        try {
          DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
          if (mimetype != null) request.setMimeType(mimetype);
          if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
          request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
          String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
          request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
          DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
          if (manager != null) manager.enqueue(request);
        } catch (Exception ignored) {
          /* 下载失败时不打断页面 */
        }
      });
    } catch (Exception ignored) {
      /* keep default behavior */
    }
  }

  private void allowAutoplay() {
    try {
      WebView webView = getBridge().getWebView();
      if (webView == null) return;
      WebSettings settings = webView.getSettings();
      settings.setMediaPlaybackRequiresUserGesture(false);
    } catch (Exception ignored) {
      /* ignore */
    }
  }

  private void handleOpenAppIntent(Intent intent) {
    if (intent == null) return;
    String app = intent.getStringExtra("openApp");
    // 只有通知/深链明确带 openApp 才切应用；普通启动（桌面图标）不能覆盖上次页面
    if (app == null || !app.matches("[a-z][a-z0-9-]{1,31}")) return;
    try {
      // 与 PhoneRelayPlugin.consumeOpenChat 共用同一份 pending，冷启动/锁屏后再取
      getSharedPreferences("phone_relay", MODE_PRIVATE)
          .edit().putBoolean("openChat", true).putString("openApp", app).apply();
    } catch (Exception ignored) {
      /* ignore */
    }
    try {
      WebView webView = getBridge() == null ? null : getBridge().getWebView();
      if (webView != null) {
        String id = app.replace("\\", "\\\\").replace("'", "\\'");
        final String js =
            "window.dispatchEvent(new CustomEvent('docklet:open-app',{detail:{id:'" + id + "'}}))";
        webView.post(() -> webView.evaluateJavascript(js, null));
      }
    } catch (Exception ignored) {
      /* ignore */
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(PhoneRelayPlugin.class);
    registerPlugin(ShellKitPlugin.class);
    registerPlugin(AppHostPlugin.class);
    super.onCreate(savedInstanceState);
    wrapAppHostClient();
    installDownloadHandler();
    reportInsets();
    handleOpenAppIntent(getIntent());
    allowAutoplay();
    final WebView webView = getBridge().getWebView();
    if (webView != null) {
      webView.postDelayed(this::allowAutoplay, 300);
      webView.postDelayed(this::allowAutoplay, 1200);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleOpenAppIntent(intent);
  }

  @Override
  public void onBackPressed() {
    WebView webView = getBridge() == null ? null : getBridge().getWebView();
    if (webView == null) {
      super.onBackPressed();
      return;
    }
    webView.evaluateJavascript(
        "(function(){try{return String(!!(window.__dockletBack && window.__dockletBack()))}catch(e){return 'false'}})()",
        value -> {
          String result = value == null ? "" : value;
          if (!result.contains("true")) {
            finish();
          }
        });
  }

  @Override
  public void onStart() {
    super.onStart();
    allowAutoplay();
  }

  @Override
  public void onPause() {
    try {
      getSharedPreferences("phone_relay", MODE_PRIVATE)
          .edit().putBoolean("chatActive", false).apply();
    } catch (Exception ignored) {
      /* ignore */
    }
    try {
      WebView webView = getBridge().getWebView();
      if (webView != null) webView.onPause();
    } catch (Exception ignored) {
      /* ignore */
    }
    super.onPause();
  }

  @Override
  public void onResume() {
    super.onResume();
    try {
      WebView webView = getBridge().getWebView();
      if (webView != null) webView.onResume();
    } catch (Exception ignored) {
      /* ignore */
    }
    allowAutoplay();
  }
}
