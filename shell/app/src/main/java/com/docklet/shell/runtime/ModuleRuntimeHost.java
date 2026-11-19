package com.docklet.shell.runtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Insets;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleFiles;
import com.docklet.platform.ModuleManager;
import com.docklet.shell.BuildConfig;
import com.docklet.shell.storage.StorageService;
import com.docklet.shell.ui.IconView;
import com.docklet.shell.ui.UiKit;

import java.util.UUID;

/** One foreground WebView, one module, one origin. */
public final class ModuleRuntimeHost implements DockletBridge.Host {

    public interface Listener {
        void onModuleReady();

        void onModuleFailure(String reason);

        void onModuleClosed();

        void onRequestNotificationPermission();
    }

    private static final long READY_TIMEOUT_MS = 10_000L;
    private static final long SAVE_TIMEOUT_MS = 550L;
    private static final int REQUEST_FILE_CHOOSER = 1006;

    private final Activity activity;
    private final ModuleManager manager;
    private final StorageService storage;
    private final ModuleFiles files;
    private final InstalledModule module;
    private String sessionId;
    private final Listener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout root;
    private final FrameLayout webHost;
    private View toolbar;
    private int safeTop;
    private int safeBottom;
    private int safeLeft;
    private int safeRight;
    private int safeIme;
    private boolean insetsApplied;
    private final View loadingOverlay;
    private final LinearLayout errorOverlay;
    private TextView errorTitle;
    private TextView errorMessage;

    private WebView webView;
    private DockletBridge bridge;
    private boolean ready;
    private boolean closing;
    private boolean closed;
    private boolean destroyed;
    private boolean failureRecorded;
    private String pendingSaveId;
    private ValueCallback<Uri[]> fileChooserCallback;
    private final Runnable readyTimeout = () -> showError(
            "模块没有响应",
            "等待 ready 超过 10 秒。请检查入口脚本是否调用了 docklet.ready()。"
    );
    private final Runnable saveTimeout = this::finishClose;

    @SuppressLint("SetJavaScriptEnabled")
    public ModuleRuntimeHost(
            Activity activity,
            ModuleManager manager,
            StorageService storage,
            ModuleFiles files,
            InstalledModule module,
            Listener listener
    ) {
        this.activity = activity;
        this.manager = manager;
        this.storage = storage;
        this.files = files;
        this.module = module;
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (!"none".equals(module.chrome())) {
            toolbar = buildToolbar();
            root.addView(toolbar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        webHost = new FrameLayout(activity);
        webHost.setBackgroundColor(UiKit.BG);
        LinearLayout.LayoutParams webHostParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(webHost, webHostParams);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applySystemInsets(insets);
            return insets;
        });

        loadingOverlay = buildLoadingOverlay();
        webHost.addView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        errorOverlay = buildErrorOverlay();
        errorOverlay.setVisibility(View.GONE);
        webHost.addView(errorOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void applySystemInsets(WindowInsets insets) {
        if (insets == null) return;
        int top;
        int bottom;
        int left = 0;
        int right = 0;
        int ime = 0;
        boolean stableSafeArea = module.display().safeContent()
                && !module.display().isSafe();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets bars = stableSafeArea
                    ? insets.getInsetsIgnoringVisibility(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    )
                    : insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    );
            top = bars.top;
            bottom = bars.bottom;
            left = bars.left;
            right = bars.right;
            ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
        } else {
            top = stableSafeArea
                    ? insets.getStableInsetTop()
                    : insets.getSystemWindowInsetTop();
            bottom = stableSafeArea
                    ? insets.getStableInsetBottom()
                    : insets.getSystemWindowInsetBottom();
            left = insets.getSystemWindowInsetLeft();
            right = insets.getSystemWindowInsetRight();
        }
        safeTop = top;
        safeBottom = bottom;
        safeLeft = left;
        safeRight = right;
        safeIme = ime;
        insetsApplied = true;
        if (module.display().isSafe()) {
            // Activity content already fits system bars in this mode; adding
            // insets here would double-pad the toolbar and page.
            if (toolbar != null) {
                toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
            }
            webHost.setPadding(0, 0, 0, 0);
        } else if (toolbar != null) {
            toolbar.setPadding(dp(12), dp(8) + top, dp(12), dp(8));
            webHost.setPadding(0, 0, 0, bottom);
        } else if (module.display().safeContent() && !module.display().isSafe()) {
            // Full screen, but the module asked for content to stay inside
            // the cutout/system-bar safe rectangle.
            webHost.setPadding(0, top, 0, bottom);
            injectSafeArea();
        } else {
            // Full-bleed page: the module background extends behind the
            // status/navigation bars. Expose insets for pages that want to
            // keep their own content clear of the system bars.
            webHost.setPadding(0, 0, 0, 0);
            injectSafeArea();
        }
    }

    private void injectSafeArea() {
        if (webView == null || !insetsApplied) return;
        float density = activity.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1f;
        int topCss = Math.round(safeTop / density);
        int bottomCss = Math.round(safeBottom / density);
        int leftCss = Math.round(safeLeft / density);
        int rightCss = Math.round(safeRight / density);
        int imeCss = Math.round(safeIme / density);
        String js = "(function(){var d=document.documentElement;"
                + "d.style.setProperty('--safe-top','" + topCss + "px');"
                + "d.style.setProperty('--safe-bottom','" + bottomCss + "px');"
                + "d.style.setProperty('--safe-left','" + leftCss + "px');"
                + "d.style.setProperty('--safe-right','" + rightCss + "px');"
                + "d.style.setProperty('--safe-ime','" + imeCss + "px');"
                + "window.dispatchEvent(new MessageEvent('message',{data:{type:'docklet:insets',"
                + "detail:{top:" + topCss + ",bottom:" + bottomCss + ",left:" + leftCss
                + ",right:" + rightCss + ",ime:" + imeCss + "}}}));"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    public View view() {
        return root;
    }

    public InstalledModule module() {
        return module;
    }

    public void setSessionId(String id) {
        sessionId = id;
        if (bridge != null) bridge.setSessionId(id);
    }

    public void openSession(String id) {
        setSessionId(id);
        if (webView == null || id == null) return;
        String escaped = id.replace("\\", "\\\\").replace("'", "\\'");
        webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('docklet:session',{detail:{sessionId:'"
                        + escaped + "'}}));",
                null
        );
    }

    public void open() {
        if (destroyed || webView != null) return;
        failureRecorded = false;
        try {
            webView = new WebView(activity);
            webView.setBackgroundColor(UiKit.BG);
            webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            configure(webView);
            webHost.addView(webView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
            loadingOverlay.setVisibility(View.VISIBLE);
            loadingOverlay.setAlpha(1f);
            webView.loadUrl(module.origin() + "/" + module.entry());
            handler.removeCallbacks(readyTimeout);
            handler.postDelayed(readyTimeout, READY_TIMEOUT_MS);
        } catch (Exception error) {
            showError("无法创建 WebView", safeMessage(error));
        }
    }

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(UiKit.BG);

        FrameLayout back = UiKit.iconButton(
                activity,
                new IconView(activity).type(IconView.Type.BACK),
                "返回模块库",
                view -> close()
        );
        bar.addView(back);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        titlesParams.rightMargin = dp(8);
        bar.addView(titles, titlesParams);

        TextView title = UiKit.text(activity, module.name(), 15.5f, UiKit.INK, true);
        title.setMaxLines(1);
        titles.addView(title);

        TextView origin = UiKit.text(
                activity,
                module.id() + ".module.local · v" + module.version(),
                9.5f,
                UiKit.FAINT,
                false
        );
        origin.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams originParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        originParams.topMargin = dp(2);
        titles.addView(origin, originParams);

        FrameLayout close = UiKit.iconButton(
                activity,
                new IconView(activity).type(IconView.Type.CLOSE),
                "关闭模块",
                view -> close()
        );
        bar.addView(close);

        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        wrap.addView(UiKit.divider(activity));
        return wrap;
    }

    private View buildLoadingOverlay() {
        LinearLayout overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(UiKit.BG);
        overlay.setClickable(true);

        ProgressBar progress = new ProgressBar(activity);
        progress.getIndeterminateDrawable().setTint(UiKit.ACCENT);
        overlay.addView(progress, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView title = UiKit.text(
                activity,
                "正在启动 " + module.name(),
                15f,
                UiKit.INK,
                true
        );
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(16);
        overlay.addView(title, titleParams);

        TextView origin = UiKit.text(activity, module.origin(), 10.5f, UiKit.FAINT, false);
        origin.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams originParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        originParams.topMargin = dp(6);
        overlay.addView(origin, originParams);

        TextView chip = UiKit.pill(activity, "独立 origin · 单前台槽", UiKit.MUTED, UiKit.SURFACE_SOFT);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        chipParams.topMargin = dp(18);
        overlay.addView(chip, chipParams);
        return overlay;
    }

    private LinearLayout buildErrorOverlay() {
        LinearLayout overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(UiKit.BG);
        overlay.setClickable(true);
        overlay.setPadding(dp(30), dp(30), dp(30), dp(30));

        FrameLayout badge = new FrameLayout(activity);
        badge.setBackground(UiKit.rounded(activity, UiKit.DANGER_SOFT, 20f));
        IconView icon = new IconView(activity).type(IconView.Type.INFO);
        icon.setColor(UiKit.DANGER);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        overlay.addView(badge, new LinearLayout.LayoutParams(dp(58), dp(58)));

        errorTitle = UiKit.text(activity, "模块没有启动", 21f, UiKit.INK, true);
        errorTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(18);
        overlay.addView(errorTitle, titleParams);

        errorMessage = UiKit.text(
                activity,
                "请检查模块入口和 ready 调用。",
                12.5f,
                UiKit.MUTED,
                false
        );
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setLineSpacing(dp(2), 1.25f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(8);
        messageParams.leftMargin = dp(18);
        messageParams.rightMargin = dp(18);
        overlay.addView(errorMessage, messageParams);

        TextView back = UiKit.primaryButton(activity, "返回模块库");
        back.setOnClickListener(view -> closeImmediately());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        backParams.topMargin = dp(24);
        backParams.leftMargin = dp(34);
        backParams.rightMargin = dp(34);
        overlay.addView(back, backParams);

        TextView retry = UiKit.textButton(activity, "重新加载", UiKit.ACCENT_DEEP);
        retry.setOnClickListener(view -> reload());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        retryParams.topMargin = dp(2);
        overlay.addView(retry, retryParams);
        return overlay;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        // network is an explicit manifest capability. Modules that do not
        // declare it keep the strict mixed-content policy.
        settings.setMixedContentMode(module.capabilities().contains("network")
                ? WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        settings.setUserAgentString(settings.getUserAgentString() + " DockletShell/" + UiKit.SHELL_VERSION);

        bridge = new DockletBridge(activity, module, storage, files, this);
        bridge.setSessionId(sessionId);
        webView.addJavascriptInterface(bridge, "DockletNative");
        webView.setWebViewClient(new ModuleWebViewClient(
                activity,
                manager,
                module,
                new ModuleWebViewClient.Listener() {
                    @Override
                    public void onModulePageError(String message) {
                        showError("页面没有加载完成", message);
                    }

                    @Override
                    public void onModuleProcessGone() {
                        destroyWebView();
                        showError(
                                "模块进程已退出",
                                "当前模块的 WebView 渲染进程被系统回收，Docklet 仍然正常。点击“重新加载”可重建 WebView。"
                        );
                    }

                    @Override
                    public void onExternalBlocked(String url) {
                        // External navigation is intentionally blocked in the minimal shell.
                    }
                }
        ));
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                android.util.Log.d(
                        "DockletModule",
                        "[" + module.id() + "] " + consoleMessage.message()
                );
                return true;
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (callback == null) return false;
                cancelFileChooser();
                fileChooserCallback = callback;
                Intent intent;
                try {
                    intent = params == null ? null : params.createIntent();
                } catch (Exception error) {
                    intent = null;
                }
                if (intent == null) {
                    intent = fallbackFileChooserIntent(params);
                }
                if (intent == null) {
                    cancelFileChooser();
                    return false;
                }
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    activity.startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                } catch (ActivityNotFoundException error) {
                    cancelFileChooser();
                    return false;
                } catch (Exception error) {
                    cancelFileChooser();
                    return false;
                }
                return true;
            }
        });
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private Intent fallbackFileChooserIntent(WebChromeClient.FileChooserParams params) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String type = "*/*";
        String[] accept = params == null ? null : params.getAcceptTypes();
        if (accept != null && accept.length > 0 && accept[0] != null && !accept[0].isEmpty()) {
            type = accept[0];
        }
        intent.setType(type);
        if (accept != null && accept.length > 1) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, accept);
        }
        if (params != null
                && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    private void cancelFileChooser() {
        ValueCallback<Uri[]> callback = fileChooserCallback;
        fileChooserCallback = null;
        if (callback != null) callback.onReceiveValue(null);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE_CHOOSER) {
            ValueCallback<Uri[]> callback = fileChooserCallback;
            fileChooserCallback = null;
            if (callback == null) return true;
            Uri[] uris = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    uris = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i += 1) {
                        uris[i] = clip.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            callback.onReceiveValue(uris);
            return true;
        }
        return bridge != null && bridge.onActivityResult(requestCode, resultCode, data);
    }

    public void evaluateJavascript(String script) {
        handler.post(() -> {
            WebView view = webView;
            if (view == null || destroyed) return;
            try {
                view.evaluateJavascript(script, null);
            } catch (Exception ignored) {
                // The renderer may be gone while the page is closing.
            }
        });
    }

    public void onBridgeReady() {
        if (destroyed || closed || closing || ready) return;
        ready = true;
        handler.removeCallbacks(readyTimeout);
        loadingOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> loadingOverlay.setVisibility(View.GONE))
                .start();
        if (listener != null) listener.onModuleReady();
        injectSafeArea();
    }

    @Override
    public void onLifecycleResult(String requestId, boolean ok, String error) {
        if (closing && requestId != null && requestId.equals(pendingSaveId)) {
            finishClose();
        }
    }

    @Override
    public void requestNotificationPermission() {
        if (listener != null) listener.onRequestNotificationPermission();
    }

    public void pause() {
        sendLifecycle("pause", null);
        if (webView != null) webView.onPause();
    }

    public void resume() {
        if (webView != null) webView.onResume();
        sendLifecycle("resume", null);
    }

    public void onHostStop() {
        if (webView != null) {
            webView.onPause();
            sendLifecycle("save", null);
        }
    }

    public interface BackCallback {
        void onResult(boolean handled);
    }

    public void dispatchBack(BackCallback callback) {
        if (callback == null) return;
        if (destroyed || webView == null || closing || !ready) {
            callback.onResult(false);
            return;
        }
        final boolean[] done = {false};
        // The page may ask for more time before the shell forces its way out.
        webView.evaluateJavascript(
                "(function(){var n=Number(window.__dockletBackGrace);"
                        + "return isFinite(n)&&n>0?Math.min(n,2000):0})();",
                value -> {
                    int grace = 400;
                    try {
                        String text = value == null ? "" : value.replace("\"", "").trim();
                        int requested = Integer.parseInt(text);
                        if (requested > 0) grace = Math.min(requested, 2000);
                    } catch (Exception ignored) {
                    }
                    runBack(done, grace, callback);
                }
        );
    }

    /** Runs the page handler and closes the module if it does not answer in time. */
    private void runBack(final boolean[] done, int grace, final BackCallback callback) {
        if (destroyed || webView == null) {
            callback.onResult(false);
            return;
        }
        webView.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            callback.onResult(false);
        }, grace);
        webView.evaluateJavascript(
                "(function(){try{var r=window.__dockletBack?window.__dockletBack():'';"
                        + "return r==='handled'?'handled':(r==='exit'?'exit':'exit')}catch(e){return 'exit'}})();",
                value -> {
                    if (done[0] || destroyed) return;
                    done[0] = true;
                    if (value != null && value.contains("handled")) {
                        callback.onResult(true);
                        return;
                    }
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                        callback.onResult(true);
                        return;
                    }
                    callback.onResult(false);
                }
        );
    }

    public void close() {
        if (closing || closed || destroyed) return;
        closing = true;
        if (!ready || webView == null) {
            finishClose();
            return;
        }
        sendLifecycle("pause", null);
        pendingSaveId = "save-" + UUID.randomUUID();
        String js = "window.dockletLifecycle&&window.dockletLifecycle('save','" + pendingSaveId + "')";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            finishClose();
            return;
        }
        handler.removeCallbacks(saveTimeout);
        handler.postDelayed(saveTimeout, SAVE_TIMEOUT_MS);
    }

    public void closeImmediately() {
        if (closed || destroyed) return;
        closing = true;
        finishClose();
    }

    private void sendLifecycle(String action, String requestId) {
        if (webView == null || destroyed) return;
        String js = requestId == null
                ? "window.dockletLifecycle&&window.dockletLifecycle('" + action + "')"
                : "window.dockletLifecycle&&window.dockletLifecycle('" + action + "','" + requestId + "')";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            // WebView may already be detached.
        }
    }

    private void reload() {
        if (webView == null || destroyed) {
            open();
            return;
        }
        errorOverlay.setVisibility(View.GONE);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(1f);
        ready = false;
        failureRecorded = false;
        webView.loadUrl(module.origin() + "/" + module.entry());
        handler.removeCallbacks(readyTimeout);
        handler.postDelayed(readyTimeout, READY_TIMEOUT_MS);
    }

    private void showError(String title, String message) {
        if (destroyed) return;
        handler.removeCallbacks(readyTimeout);
        if (!failureRecorded) {
            failureRecorded = true;
            if (listener != null) {
                listener.onModuleFailure((title == null ? "" : title + "：")
                        + (message == null ? "" : message));
            }
        }
        errorTitle.setText(title);
        errorMessage.setText(message == null ? "" : message);
        errorOverlay.setVisibility(View.VISIBLE);
        errorOverlay.setAlpha(0f);
        errorOverlay.animate().alpha(1f).setDuration(180).start();
        loadingOverlay.setVisibility(View.GONE);
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        handler.removeCallbacks(saveTimeout);
        handler.removeCallbacks(readyTimeout);
        destroyWebView();
        if (listener != null) listener.onModuleClosed();
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        handler.removeCallbacks(readyTimeout);
        handler.removeCallbacks(saveTimeout);
        destroyWebView();
    }

    private void destroyWebView() {
        cancelFileChooser();
        if (bridge != null) {
            bridge.close();
            bridge = null;
        }
        WebView view = webView;
        webView = null;
        if (view == null) return;
        try {
            view.stopLoading();
            view.loadUrl("about:blank");
            view.clearHistory();
            view.clearFormData();
            view.clearCache(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebStorage.getInstance().deleteOrigin(module.origin());
            }
            view.removeJavascriptInterface("DockletNative");
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
            view.destroy();
        } catch (Exception ignored) {
            // A dead render process can throw while tearing down.
        }
    }

    private int dp(float value) {
        return UiKit.dp(activity, value);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
