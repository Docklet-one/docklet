package com.docklet.shell.runtime;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Serves one module from {@code https://<id>.module.local}. */
final class ModuleWebViewClient extends WebViewClient {

    interface Listener {
        void onModulePageError(String message);

        void onModuleProcessGone();

        void onExternalBlocked(String url);
    }

    private static final String SDK_PATH = "/__docklet/sdk.js";

    private final Activity activity;
    private final ModuleManager manager;
    private final InstalledModule module;
    private final Listener listener;

    ModuleWebViewClient(
            Activity activity,
            ModuleManager manager,
            InstalledModule module,
            Listener listener
    ) {
        this.activity = activity;
        this.manager = manager;
        this.module = module;
        this.listener = listener;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (!isModuleOrigin(uri)) {
            return null;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return errorResponse(405, "Method Not Allowed", "只支持 GET 请求");
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/" + module.entry();
        }
        if (SDK_PATH.equals(path)) {
            return sdkResponse();
        }
        try {
            File root = manager.currentDir(module.id());
            if (root == null || !root.isDirectory()) {
                return errorResponse(404, "Not Found", "当前模块目录不存在");
            }
            File base = root.getCanonicalFile();
            File file = new File(base, path).getCanonicalFile();
            if (!file.equals(base) && !file.getPath().startsWith(base.getPath() + File.separator)) {
                return errorResponse(403, "Forbidden", "路径越界");
            }
            if (file.isDirectory()) file = new File(file, "index.html");
            if (!file.isFile()) {
                return errorResponse(404, "Not Found", "找不到模块资源");
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            String mime = mimeOf(name);
            String charset = isText(mime) ? "UTF-8" : null;
            InputStream stream = new FileInputStream(file);
            WebResourceResponse response = new WebResourceResponse(mime, charset, stream);
            response.setResponseHeaders(securityHeaders(mime));
            return response;
        } catch (Exception error) {
            return errorResponse(500, "Internal Error", "读取模块资源失败");
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (isModuleOrigin(uri)) return false;
        listener.onExternalBlocked(uri.toString());
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
    }

    @Override
    public void onReceivedError(
            WebView view,
            WebResourceRequest request,
            WebResourceError error
    ) {
        if (request != null && request.isForMainFrame()) {
            listener.onModulePageError(
                    error == null ? "页面加载失败" : error.getDescription().toString()
            );
        }
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        listener.onModuleProcessGone();
        return true;
    }

    private boolean isModuleOrigin(Uri uri) {
        if (uri == null) return false;
        return "https".equalsIgnoreCase(uri.getScheme())
                && (module.id() + ".module.local").equalsIgnoreCase(uri.getHost());
    }

    private WebResourceResponse sdkResponse() {
        try {
            InputStream stream = activity.getAssets().open("docklet-sdk.js");
            WebResourceResponse response = new WebResourceResponse(
                    "application/javascript", "UTF-8", stream
            );
            response.setResponseHeaders(securityHeaders("application/javascript"));
            return response;
        } catch (IOException error) {
            return errorResponse(500, "Internal Error", "SDK 资源缺失");
        }
    }

    private Map<String, String> securityHeaders(String mime) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store, max-age=0");
        headers.put("Pragma", "no-cache");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Referrer-Policy", "no-referrer");
        headers.put("Cross-Origin-Resource-Policy", "same-origin");
        headers.put("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        String network = module.capabilities().contains("network")
                ? " https: http:"
                : "";
        headers.put(
                "Content-Security-Policy",
                "default-src 'self' data: blob:; "
                        + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: blob:; "
                        + "media-src 'self' data: blob:; "
                        + "font-src 'self' data:; "
                        + "connect-src 'self' data: blob:" + network + "; "
                        + "frame-ancestors 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'none'"
        );
        return headers;
    }

    private WebResourceResponse errorResponse(int status, String reason, String message) {
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;height:100%;background:#f6f1e8;color:#241f19;"
                + "font:14px/1.7 -apple-system,'PingFang SC','Microsoft YaHei',sans-serif}"
                + "main{max-width:360px;margin:0 auto;padding:80px 28px}"
                + "b{display:block;font-size:20px;margin-bottom:8px}"
                + "p{color:#7d7367;margin:0}</style></head><body><main>"
                + "<b>页面打不开</b><p>" + escapeHtml(message) + "</p>"
                + "<p style=\"margin-top:18px;color:#a99e90\">Docklet · " + status + " " + reason + "</p>"
                + "</main></body></html>";
        WebResourceResponse response = new WebResourceResponse(
                "text/html",
                "UTF-8",
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))
        );
        response.setStatusCodeAndReasonPhrase(status, reason);
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("X-Content-Type-Options", "nosniff");
        response.setResponseHeaders(headers);
        return response;
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        switch (ext) {
            case "html":
            case "htm":
                return "text/html";
            case "js":
            case "mjs":
                return "application/javascript";
            case "css":
                return "text/css";
            case "json":
                return "application/json";
            case "svg":
                return "image/svg+xml";
            case "wasm":
                return "application/wasm";
            case "woff2":
                return "font/woff2";
            case "woff":
                return "font/woff";
            case "ttf":
                return "font/ttf";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "webp":
                return "image/webp";
            case "gif":
                return "image/gif";
            case "ico":
                return "image/x-icon";
            case "txt":
                return "text/plain";
            default:
                String guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                return guessed == null ? "application/octet-stream" : guessed;
        }
    }

    private boolean isText(String mime) {
        return mime.startsWith("text/")
                || mime.contains("javascript")
                || mime.contains("json")
                || mime.contains("svg")
                || mime.contains("xml");
    }
}
