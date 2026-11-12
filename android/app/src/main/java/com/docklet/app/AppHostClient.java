package com.docklet.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 拦截 https://<id>.app.local/*，映射到 filesDir/apps/<id>/。
 * 其它请求原样交回 Capacitor 的 WebViewClient。
 */
public class AppHostClient extends WebViewClient {
  private static final String HOST_SUFFIX = ".app.local";
  private final Context context;
  private final WebViewClient delegate;

  public AppHostClient(Context context, WebViewClient delegate) {
    this.context = context;
    this.delegate = delegate;
  }

  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    Uri uri = request.getUrl();
    String host = uri.getHost();
    if (host != null && host.endsWith(HOST_SUFFIX)) {
      if (!AppHostPlugin.isWebMessageOk()) return unsafeWebView();
      return serve(host, uri);
    }
    return delegate != null ? delegate.shouldInterceptRequest(view, request) : null;
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    return delegate != null && delegate.shouldOverrideUrlLoading(view, request);
  }

  @Override
  public void onPageStarted(WebView view, String url, Bitmap favicon) {
    if (delegate != null) delegate.onPageStarted(view, url, favicon);
  }

  @Override
  public void onPageFinished(WebView view, String url) {
    if (delegate != null) delegate.onPageFinished(view, url);
  }

  private WebResourceResponse serve(String host, Uri uri) {    String id = host.substring(0, host.length() - HOST_SUFFIX.length());
    if (!id.matches("[a-z][a-z0-9-]{1,31}")) return notFound(id);

    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) path = "/index.html";

    try {
      File base = new File(context.getFilesDir(), "apps/" + id).getCanonicalFile();
      if (path.contains("/.") || path.startsWith("/.")) return notFound(id);
      if ("/__docklet.html".equals(path)) {
        return moduleWrapper(base);
      }
      File file = new File(base, path).getCanonicalFile();
      if (!file.equals(base) && !file.getPath().startsWith(base.getPath() + File.separator)) {
        return notFound(id);
      }
      if (file.isDirectory()) file = new File(file, "index.html");
      if (!file.isFile()) return notFound(id);

      String mime = guessMime(file.getName());
      if ("text/html".equals(mime)) {
        return htmlResponse(readText(file));
      }
      String encoding = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("svg") ? "UTF-8" : null;
      InputStream stream = new FileInputStream(file);
      WebResourceResponse response = new WebResourceResponse(mime, encoding, stream);
      Map<String, String> headers = new HashMap<>();
      headers.put("Cache-Control", "no-cache");
      headers.put("Content-Security-Policy",
          "default-src 'self' data: blob:; "
              + "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; "
              + "style-src 'self' 'unsafe-inline'; "
              + "img-src 'self' data: blob:; "
              + "media-src 'self' data: blob:; "
              + "connect-src 'self' http: https:; "
              + "font-src 'self' data:;");
      response.setResponseHeaders(headers);
      return response;
    } catch (Exception error) {
      return notFound(id);
    }
  }

  /** .js 入口：生成 iframe 内的极简包装页，import 入口并调用 mount。 */
  private WebResourceResponse moduleWrapper(File base) {
    try {
      File manifest = new File(base, "app.json");
      String entry = "app.js";
      if (manifest.isFile()) {
        org.json.JSONObject json = new org.json.JSONObject(readText(manifest));
        String declared = json.optString("entry", "app.js");
        if (declared.endsWith(".js") && !declared.contains("..") && !declared.startsWith("/")) entry = declared;
      }
      boolean hasSdk = new File(base, "docklet-sdk.js").isFile();
      String sdkTag = hasSdk ? "<script src=\"docklet-sdk.js\"></script>" : "";
      String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
          + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
          + "<style>html,body{margin:0;height:100%;overflow:hidden;background:transparent}"
          + "#docklet-root{height:100%;overflow:hidden}</style></head><body>"
          + "<div id=\"docklet-root\"></div>" + sdkTag + "<script type=\"module\">"
          + "const mod = await import('./" + entry + "');"
          + "const api = await (window.__dockletApiReady || Promise.resolve(undefined));"
          + "const root=document.getElementById('docklet-root');"
          + "try{ if(typeof mod.default==='function') mod.default(root, api); else root.textContent='入口不是 module'; }"
          + "catch(error){ root.textContent='加载失败: '+error.message; }"
          + "</script></body></html>";
      return secure(new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes("UTF-8"))));
    } catch (Exception error) {
      return notFound(base.getName());
    }
  }

  /** 统一给 HTML 响应加安全头。 */
  private WebResourceResponse secure(WebResourceResponse response) {
    java.util.Map<String, String> headers = new java.util.HashMap<>();
    headers.put("X-Content-Type-Options", "nosniff");
    headers.put(
        "Content-Security-Policy",
        "default-src 'self' 'unsafe-inline' 'unsafe-eval' data: blob:; "
            + "connect-src 'self' https: http:; "
            + "img-src 'self' data: blob: https:; "
            + "media-src 'self' blob: https:; "
            + "frame-ancestors https://localhost https://*.localhost;");
    response.setResponseHeaders(headers);
    return response;
  }

  private WebResourceResponse htmlResponse(String html) throws Exception {
    return secure(new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes("UTF-8"))));
  }

  private String readText(File file) throws Exception {
    try (java.io.InputStream in = new java.io.FileInputStream(file)) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
      return out.toString("UTF-8");
    }
  }

  private String guessMime(String name) {
    int dot = name.lastIndexOf('.');
    String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    if ("mjs".equals(ext)) return "text/javascript";
    if ("wasm".equals(ext)) return "application/wasm";
    if ("webmanifest".equals(ext)) return "application/manifest+json";
    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    return mime != null ? mime : "application/octet-stream";
  }

  private WebResourceResponse unsafeWebView() {
    String html = "<!doctype html><html><head><meta charset=\"utf-8\"/>"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/></head>"
        + "<body style=\"margin:0;padding:32px;font:15px/1.8 -apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#33261c\">"
        + "<b>系统 WebView 过旧</b><p>这台设备缺少安全隔离所需的 WebView 能力，应用已停止运行。"
        + "请到应用商店更新「Android System WebView」或 Chrome 后重试。</p></body></html>";
    return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes()));
  }

  private WebResourceResponse notFound(String id) {
    String html = "<!doctype html><html><head><meta charset=\"utf-8\"/></head><body>"
        + "<p style=\"font:15px/1.8 -apple-system,'PingFang SC','Microsoft YaHei',sans-serif;padding:32px;color:#33261c\">"
        + "找不到应用入口</p><script>parent.postMessage({type:'docklet:load-error',id:'" + id
        + "',reason:'NOT_FOUND'},'*')<\\/script></body></html>";
    WebResourceResponse response =
        new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes()));
    response.setStatusCodeAndReasonPhrase(404, "Not Found");
    java.util.Map<String, String> headers = new java.util.HashMap<>();
    headers.put("X-Content-Type-Options", "nosniff");
    response.setResponseHeaders(headers);
    return response;
  }
}
