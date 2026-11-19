package com.docklet.shell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import com.docklet.platform.DisplayConfig;
import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleException;
import com.docklet.platform.ModuleFiles;
import com.docklet.platform.ModuleManager;
import com.docklet.shell.runtime.DockletMessageService;
import com.docklet.shell.runtime.ModuleRuntimeHost;
import com.docklet.shell.runtime.ModuleSessionStore;
import com.docklet.shell.runtime.WebViewProcess;
import com.docklet.shell.storage.StorageService;
import com.docklet.shell.ui.UiKit;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single V3 module-slot Activity. It runs in the :m1 process and owns the
 * WebView, module-local storage and files. The shell owns install, CrashGuard
 * and the activity result contract.
 */
public class ModuleActivity extends Activity implements ModuleRuntimeHost.Listener {

    public static final String EXTRA_MODULE_ID = "com.docklet.shell.MODULE_ID";
    public static final String EXTRA_READY = "com.docklet.shell.MODULE_READY";
    public static final String EXTRA_CANCELLED = "com.docklet.shell.MODULE_CANCELLED";
    public static final String EXTRA_REASON = "com.docklet.shell.MODULE_REASON";
    public static final String EXTRA_SESSION_ID = "com.docklet.shell.SESSION_ID";
    public static final String EXTRA_NEXT_MODULE = "com.docklet.shell.NEXT_MODULE";
    public static final String EXTRA_NEXT_SESSION = "com.docklet.shell.NEXT_SESSION";
    public static final String EXTRA_TARGET_MODULE = "com.docklet.shell.TARGET_MODULE";
    public static final String EXTRA_TARGET_SESSION = "com.docklet.shell.TARGET_SESSION";
    public static final String ACTION_MODULE_READY = "com.docklet.shell.MODULE_READY";
    public static final String ACTION_SWITCH_TO = "com.docklet.shell.SWITCH_TO";
    public static final String ACTION_STOP_REQUEST = "com.docklet.shell.STOP_REQUEST";

    private static final int REQUEST_NOTIFICATION = 2002;

    private ModuleManager manager;
    private StorageService storage;
    private ModuleFiles files;
    private ModuleRuntimeHost runtime;
    private boolean backPending;
    private String moduleId;
    private String sessionId;
    private String nextSessionId;
    private boolean ready;
    private String failureReason;
    private String nextModuleId;
    private boolean resultSent;
    private BroadcastReceiver switchReceiver;
    private String displayMode = DisplayConfig.MODE_SAFE;
    private String displayStatusIcons = DisplayConfig.ICONS_AUTO;
    private boolean displaySafeContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWebViewProcess();
        moduleId = getIntent().getStringExtra(EXTRA_MODULE_ID);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (moduleId == null || moduleId.isEmpty()) {
            finishWithFailure("缺少模块 id");
            return;
        }
        try {
            File filesDir = getFilesDir();
            manager = new ModuleManager(
                    new File(filesDir, "modules"),
                    new File(filesDir, "data"),
                    new File(getCacheDir(), "module-packages"),
                    UiKit.PLATFORM_VERSION
            );
            storage = new StorageService(new File(filesDir, "data"));
            files = new ModuleFiles(new File(filesDir, "data"));
            InstalledModule module = manager.get(moduleId);
            setupWindow(module);
            runtime = new ModuleRuntimeHost(this, manager, storage, files, module, this);
            runtime.setSessionId(sessionId);
            setContentView(runtime.view());
            runtime.open();
        } catch (ModuleException error) {
            finishWithFailure(error.getCode() + "：" + error.getMessage());
        } catch (Exception error) {
            finishWithFailure(error.getMessage() == null ? "模块启动失败" : error.getMessage());
        }
        registerSwitchReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSwitchReceiver() {
        switchReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_STOP_REQUEST.equals(intent.getAction())) {
                    if (nextModuleId == null) closeRuntime();
                    return;
                }
                String target = intent.getStringExtra(EXTRA_TARGET_MODULE);
                String targetSession = intent.getStringExtra(EXTRA_TARGET_SESSION);
                if (target == null || target.isEmpty()) return;
                if (target.equals(moduleId)) {
                    if (targetSession != null && !targetSession.isEmpty()
                            && runtime != null) {
                        sessionId = targetSession;
                        runtime.setSessionId(targetSession);
                        runtime.openSession(targetSession);
                    }
                    return;
                }
                if (nextModuleId != null) return;
                nextModuleId = target;
                nextSessionId = targetSession;
                closeRuntime();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SWITCH_TO);
        filter.addAction(ACTION_STOP_REQUEST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(switchReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(switchReceiver, filter);
        }
    }

    private void configureWebViewProcess() {
        WebViewProcess.ensureSuffix(this instanceof ModuleActivity2 ? "docklet-m2" : "docklet-m1");
    }

    private void setupWindow(InstalledModule module) {
        DisplayConfig display = module.display();
        displayMode = display.mode();
        displayStatusIcons = display.statusBarIcons();
        displaySafeContent = display.safeContent();
        applyWindowChrome(display);
        applyOrientation(display.orientation());
    }

    private void applyWindowChrome(DisplayConfig display) {
        Window window = getWindow();
        boolean safe = display.isSafe();
        boolean immersive = display.isImmersive();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setStatusBarColor(safe ? UiKit.BG : Color.TRANSPARENT);
        window.setNavigationBarColor(safe ? UiKit.BG : Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = safe || display.safeContent()
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(safe);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
                if (immersive) {
                    controller.hide(
                            android.view.WindowInsets.Type.statusBars()
                                    | android.view.WindowInsets.Type.navigationBars()
                    );
                } else {
                    controller.show(
                            android.view.WindowInsets.Type.statusBars()
                                    | android.view.WindowInsets.Type.navigationBars()
                    );
                }
                setIconAppearance(controller, display.useDarkStatusIcons());
            }
        } else {
            int flags = 0;
            if (!safe) {
                flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
                if (immersive) {
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                }
            }
            if (display.useDarkStatusIcons()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private void setIconAppearance(WindowInsetsController controller, boolean darkIcons) {
        int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        controller.setSystemBarsAppearance(
                darkIcons ? appearance : 0,
                appearance
        );
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void applyOrientation(String orientation) {
        if (DisplayConfig.ORIENTATION_PORTRAIT.equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (DisplayConfig.ORIENTATION_LANDSCAPE.equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /** Runtime display changes from docklet.display.set(). */
    public void applyRuntimeDisplay(String mode, String icons) {
        if (DisplayConfig.MODE_SAFE.equals(mode)
                || DisplayConfig.MODE_EDGE_TO_EDGE.equals(mode)
                || DisplayConfig.MODE_IMMERSIVE.equals(mode)) {
            displayMode = mode;
        }
        if (DisplayConfig.ICONS_AUTO.equals(icons)
                || DisplayConfig.ICONS_LIGHT.equals(icons)
                || DisplayConfig.ICONS_DARK.equals(icons)) {
            displayStatusIcons = icons;
        }
        DisplayConfig current = new DisplayConfig(
                displayMode,
                DisplayConfig.ORIENTATION_AUTO,
                displayStatusIcons,
                displaySafeContent
        );
        applyWindowChrome(current);
    }

    public Map<String, Object> displayState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("mode", displayMode);
        state.put("statusBarIcons", displayStatusIcons);
        state.put("safeContent", displaySafeContent);
        return state;
    }

    private void closeRuntime() {
        if (runtime != null) runtime.close();
        else finish();
    }

    @Override
    public void onModuleReady() {
        ready = true;
        failureReason = null;
        ModuleSessionStore.writeReady(
                this,
                moduleId,
                this instanceof ModuleActivity2 ? ":m2" : ":m1"
        );
        Intent readyIntent = new Intent(ACTION_MODULE_READY)
                .setPackage(getPackageName())
                .putExtra(EXTRA_MODULE_ID, moduleId);
        sendBroadcast(readyIntent);
    }

    @Override
    public void onModuleFailure(String reason) {
        failureReason = reason == null || reason.isEmpty() ? "模块启动失败" : reason;
    }

    @Override
    public void onModuleClosed() {
        if (resultSent) return;
        resultSent = true;
        ModuleSessionStore.clear(this);
        Intent result = new Intent();
        result.putExtra(EXTRA_READY, ready);
        result.putExtra(EXTRA_CANCELLED, !ready && failureReason == null);
        if (failureReason != null) result.putExtra(EXTRA_REASON, failureReason);
        if (nextModuleId != null) result.putExtra(EXTRA_NEXT_MODULE, nextModuleId);
        if (nextSessionId != null) result.putExtra(EXTRA_NEXT_SESSION, nextSessionId);
        setResult(ready && failureReason == null ? RESULT_OK : RESULT_CANCELED, result);
        finish();
    }

    @Override
    public void onRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show();
                return;
            }
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(
                    this,
                    granted ? "通知权限已开启" : "通知权限被拒绝",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void finishWithFailure(String reason) {
        failureReason = reason;
        ready = false;
        onModuleClosed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (runtime != null && runtime.onActivityResult(requestCode, resultCode, data)) return;
    }

    private void handleBack() {
        if (runtime == null) {
            closeRuntime();
            return;
        }
        if (backPending) {
            closeRuntime();
            return;
        }
        backPending = true;
        runtime.dispatchBack(handled -> {
            backPending = false;
            if (!handled) closeRuntime();
        });
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onPause() {
        if (moduleId != null) {
            DockletMessageService.setModuleForeground(this, moduleId, false);
        }
        if (runtime != null) runtime.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (runtime != null) runtime.resume();
        if (moduleId != null) {
            DockletMessageService.setModuleForeground(this, moduleId, true);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && DisplayConfig.MODE_IMMERSIVE.equals(displayMode)) {
            applyWindowChrome(new DisplayConfig(
                    displayMode,
                    DisplayConfig.ORIENTATION_AUTO,
                    displayStatusIcons,
                    displaySafeContent
            ));
        }
    }

    @Override
    protected void onStop() {
        if (runtime != null) runtime.onHostStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (switchReceiver != null) {
            try {
                unregisterReceiver(switchReceiver);
            } catch (Exception ignored) {
                // Already unregistered during teardown.
            }
            switchReceiver = null;
        }
        if (moduleId != null) {
            DockletMessageService.setModuleForeground(this, moduleId, false);
        }
        if (runtime != null) runtime.destroy();
        runtime = null;
        if (storage != null) storage.closeAll();
        super.onDestroy();
    }
}
