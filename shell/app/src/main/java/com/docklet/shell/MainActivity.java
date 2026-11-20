package com.docklet.shell;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.docklet.platform.CrashGuard;
import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleManifest;
import com.docklet.platform.ModuleException;
import com.docklet.platform.ModuleManager;
import com.docklet.platform.PackageDownloader;
import com.docklet.platform.RemoteCatalog;
import com.docklet.platform.RemoteModule;
import com.docklet.platform.SemVer;
import com.docklet.shell.demo.DemoApp;
import com.docklet.shell.demo.DemoCatalog;
import com.docklet.shell.runtime.DockletMessageService;
import com.docklet.shell.runtime.MemoryUsage;
import com.docklet.shell.runtime.ModuleCategories;
import com.docklet.shell.runtime.ModuleInventory;
import com.docklet.shell.ui.InstallDialog;
import com.docklet.shell.runtime.ModuleSessionStore;
import com.docklet.shell.runtime.ModuleSlotManager;
import com.docklet.shell.runtime.ModuleUsage;
import com.docklet.shell.runtime.RecentModules;
import com.docklet.shell.runtime.SlotWarmupService;
import com.docklet.shell.storage.StorageService;
import com.docklet.shell.ui.AddModuleScreen;
import com.docklet.shell.ui.BackStep;
import com.docklet.shell.ui.HomeScreen;
import com.docklet.shell.ui.IconView;
import com.docklet.shell.ui.DemoDetailScreen;
import com.docklet.shell.ui.SheetDialog;
import com.docklet.shell.ui.UiKit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.net.URL;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Docklet shell: home, installation flows and slot launch. */
public final class MainActivity extends Activity implements HomeScreen.Listener {

    public static final String EXTRA_OPEN_MODULE = "com.docklet.shell.OPEN_MODULE";
    public static final String EXTRA_OPEN_SESSION = "com.docklet.shell.OPEN_SESSION";

    private static final int REQUEST_PICK_PACKAGE = 1001;
    private static final int REQUEST_NOTIFICATION = 1002;
    private static final int REQUEST_MODULE = 1004;

    private ModuleManager manager;
    private StorageService storage;
    private CrashGuard crashGuard;
    private ExecutorService io;
    private Handler main;

    private FrameLayout root;
    private FrameLayout screenHost;
    private FrameLayout overlayHost;
    private int addInputMode = -1;
    private TextView snack;
    private Runnable hideSnack;

    private HomeScreen home;
    private AddModuleScreen addScreen;
    private AddModuleScreen onlineScreen;
    private AddModuleScreen.Listener appListener;
    private AddModuleScreen pendingDemoAdd;
    /** The page that opened the detail, so install/uninstall refresh the right one. */
    private AddModuleScreen demoDetailOrigin;
    /** The detail page currently on screen (null when closed). */
    private DemoDetailScreen demoDetailScreen;
    private AddModuleScreen detailOriginAdd;
    private Runnable pendingModuleReturn;
    private boolean uninstallFromAdd;
    private List<DemoApp> demoApps;
    private boolean demoDetailReturnHome;
    private String activeModuleId;
    private String pendingOpenModuleId;
    private String pendingSessionId;
    private String pendingPackageHash;
    private ModuleInventory inventory;
    private List<InstalledModule> renderedModules;
    private boolean moduleAwaitingResult;
    private boolean detailReturnToAdd;
    private final ModuleSlotManager slots = new ModuleSlotManager();
    private ModuleSlotManager.Slot activeSlot;
    private Runnable afterStop;
    private int deathRetries;
    private boolean preloadWarmed;
    private static final String PRELOAD_PREF = "preload_enabled";
    private final Runnable stopTimeout = () -> {
        if (afterStop != null) {
            afterStop = null;
            showSnack("停止模块超时，操作已取消");
        }
    };
    private BroadcastReceiver moduleReadyReceiver;
    private final Runnable reclaimIfIdle = () -> {
        if (moduleAwaitingResult || activeModuleId != null || isPreloadEnabled()) return;
        reclaimInactiveSlots();
    };
    private final Runnable processDeathCheck = new Runnable() {
        @Override
        public void run() {
        if (!moduleAwaitingResult) return;
        if (moduleActivityOnTop()) return;
        String slot = activeSlot == ModuleSlotManager.Slot.M2 ? ":m2" : ":m1";
        if (activeSlot != null && slotProcessAlive(slot)) {
            if (deathRetries < 3) {
                deathRetries += 1;
                main.postDelayed(processDeathCheck, 1000L);
                return;
            }
        }
        String id = activeModuleId;
        moduleAwaitingResult = false;
        activeModuleId = null;
        if (activeSlot != null) {
            slots.release(activeSlot);
            activeSlot = null;
        }
        setActiveModule(null);
        main.removeCallbacks(stopTimeout);
        Runnable after = afterStop;
        afterStop = null;
        boolean wasReady = false;
        if (id != null) {
            String[] ready = ModuleSessionStore.readReady(MainActivity.this);
            wasReady = ready != null && id.equals(ready[0]);
        }
        if (id != null && crashGuard != null) {
            if (wasReady) {
                crashGuard.onReady(id);
                ModuleSessionStore.clear(MainActivity.this);
            } else {
                crashGuard.recordFailure(id, "模块进程已退出");
            }
        }
        if (after != null) {
            pendingModuleReturn = null;
            showHome();
            main.post(after);
        } else {
            restoreModuleOrigin();
            showSnack(wasReady ? "模块进程已退出，未计入启动失败" : "模块进程已退出，已记录一次失败");
        }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        main = new Handler(Looper.getMainLooper());
        io = Executors.newSingleThreadExecutor();
        setupWindow();
        setupRoot();

        try {
            File files = getFilesDir();
            manager = new ModuleManager(
                    new File(files, "modules"),
                    new File(files, "data"),
                    new File(getCacheDir(), "module-packages"),
                    UiKit.PLATFORM_VERSION
            );
            storage = new StorageService(new File(files, "data"));
            inventory = new ModuleInventory(manager, io, main);
            crashGuard = new CrashGuard(new File(files, "crash_guard.json"));
            String interrupted = recoverInterruptedLaunch();
            if (interrupted != null) {
                try {
                    manager.get(interrupted);
                } catch (ModuleException error) {
                    crashGuard.clear(interrupted);
                    interrupted = null;
                }
            }
            demoApps = DemoCatalog.load(this);
        maybeRefreshCatalog();
            registerModuleReadyReceiver();
            showHome();
                if (interrupted != null) {
                showSnack("检测到上次启动中断，已记录一次失败");
            }
        } catch (Exception error) {
            showFatal(error);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(EXTRA_OPEN_MODULE);
        if (id == null || id.isEmpty()) return;
        String session = intent.getStringExtra(EXTRA_OPEN_SESSION);
        intent.removeExtra(EXTRA_OPEN_MODULE);
        intent.removeExtra(EXTRA_OPEN_SESSION);
        pendingSessionId = session;
        openModuleById(id);
    }

    private void setupWindow() {
        Window window = getWindow();
        View decor = window.getDecorView();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(UiKit.BG);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
    }

    private void setupRoot() {
        root = new FrameLayout(this);
        root.setBackgroundColor(UiKit.BG);
        setContentView(root);

        screenHost = new FrameLayout(this);
        root.addView(screenHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        overlayHost = new FrameLayout(this);
        overlayHost.setVisibility(View.GONE);
        root.addView(overlayHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        snack = UiKit.text(this, "", 12.5f, Color.rgb(255, 250, 242), false);
        snack.setGravity(Gravity.CENTER);
        snack.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable snackBg = UiKit.rounded(this, 0xF2261F19, 14f);
        snack.setBackground(snackBg);
        snack.setElevation(dp(10));
        snack.setAlpha(0f);
        snack.setVisibility(View.GONE);
        FrameLayout.LayoutParams snackParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        snackParams.leftMargin = dp(22);
        snackParams.rightMargin = dp(22);
        snackParams.bottomMargin = dp(20);
        root.addView(snack, snackParams);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            int left;
            int right;
            int ime = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                top = bars.top;
                bottom = bars.bottom;
                left = bars.left;
                right = bars.right;
                ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            // With the keyboard up it covers the navigation bar area, so that
            // inset must not be reserved a second time: otherwise a strip of
            // page background shows between the content and the keyboard.
            if (ime > 0) bottom = 0;
            root.setPadding(left, top, right, bottom);
            if (home != null) home.setTabbarVisible(ime == 0);
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(float value) {
        return UiKit.dp(this, value);
    }

    private String recoverInterruptedLaunch() {
        String pending = crashGuard.pendingLaunch();
        String[] ready = ModuleSessionStore.readReady(this);
        if (pending != null && ready != null && pending.equals(ready[0])) {
            if (slotProcessAlive(ready[1])) {
                crashGuard.onReady(pending);
                ModuleSessionStore.clear(this);
                return null;
            }
        }
        if (pending == null) {
            ModuleSessionStore.clear(this);
        }
        return crashGuard.recoverInterruptedLaunch();
    }

    private boolean slotProcessAlive(String slot) {
        if (slot == null || slot.isEmpty()) return false;
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;
        String expected = getPackageName() + slot;
        try {
            List<ActivityManager.RunningAppProcessInfo> processes =
                    activityManager.getRunningAppProcesses();
            if (processes == null) return false;
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (expected.equals(process.processName)) return true;
            }
        } catch (Exception ignored) {
            // If process visibility is unavailable, fall back to the guard.
        }
        return false;
    }

    private boolean moduleActivityOnTop() {
        ActivityManager manager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info.topActivity != null) {
                    String name = info.topActivity.getClassName();
                    if ("com.docklet.shell.ModuleActivity".equals(name)
                            || "com.docklet.shell.ModuleActivity2".equals(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to treating a missing task list as still active.
        }
        return false;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerModuleReadyReceiver() {
        moduleReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (crashGuard == null) return;
                String id = intent.getStringExtra(ModuleActivity.EXTRA_MODULE_ID);
                if (id != null && moduleAwaitingResult && id.equals(activeModuleId)) {
                    crashGuard.onReady(id);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ModuleActivity.ACTION_MODULE_READY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(moduleReadyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(moduleReadyReceiver, filter);
        }
    }

    private void showHome() {
        if (screenHost == null || manager == null) return;
        List<InstalledModule> cached = inventory == null ? null : inventory.snapshot();
        if (cached != null) {
            renderHome(cached);
        }
        if (inventory == null) return;
        inventory.refresh(true, modules -> {
            if (isFinishing() || isDestroyed()) return;
            if (!sameModules(renderedModules, modules)) {
                renderHome(modules);
            }
        });
    }

    private void renderHome(List<InstalledModule> modules) {
        if (screenHost == null || manager == null) return;
        renderedModules = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
        Map<String, String> glyphs = new HashMap<>();
        if (demoApps != null) {
            for (DemoApp app : demoApps) glyphs.put(app.id(), app.glyph());
        }
        Map<String, CrashGuard.State> crashStates = crashGuard == null
                ? new HashMap<>()
                : crashGuard.states();
        if (home == null) {
            screenHost.removeAllViews();
            home = new HomeScreen(
                    this,
                    renderedModules,
                    glyphs,
                    crashStates,
                    this
            );
            screenHost.addView(home);
        } else {
            // Keep the instance: it owns the selected page and the app page.
            home.updateData(renderedModules, glyphs, crashStates);
        }
        maybePreload();
    }

    private boolean sameModules(List<InstalledModule> left, List<InstalledModule> right) {
        if (left == right) return true;
        if (left == null || right == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i += 1) {
            InstalledModule a = left.get(i);
            InstalledModule b = right.get(i);
            if (!a.id().equals(b.id()) || !a.version().equals(b.version())) return false;
        }
        return true;
    }

    @Override
    public boolean isPreloadEnabled() {
        return getSharedPreferences(ModuleRouterActivity.ACTIVE_PREFS, MODE_PRIVATE)
                .getBoolean(PRELOAD_PREF, false);
    }

    @Override
    public void onTogglePreload() {
        boolean enabled = !isPreloadEnabled();
        getSharedPreferences(ModuleRouterActivity.ACTIVE_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(PRELOAD_PREF, enabled)
                .apply();
        preloadWarmed = false;
        if (!enabled) {
            stopService(new Intent(this, SlotWarmupService.class));
        }
        showHome();
        if (home != null) home.showSettings();
        showSnack(enabled ? "预加载已开启，只预热 :m2 槽" : "预加载已关闭");
        if (enabled) maybePreload();
    }

    private void maybePreload() {
        if (!isPreloadEnabled() || preloadWarmed || moduleAwaitingResult) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return;
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memory);
        if (memory.availMem < 256L * 1024L * 1024L) return;
        try {
            startService(new Intent(this, SlotWarmupService.class));
            preloadWarmed = true;
        } catch (Exception ignored) {
            preloadWarmed = false;
        }
    }

    @Override
    public void onOpenModule(InstalledModule module) {
        if (crashGuard != null && crashGuard.hasFailure(module.id())) {
            showCrashSheet(module);
            return;
        }
        launchModule(module);
    }

    private void openModuleById(String id) {
        if (manager == null) return;
        try {
            InstalledModule module = manager.get(id);
            if (crashGuard != null && crashGuard.isBlocked(id)) {
                if (moduleAwaitingResult) {
                    showSnack("目标模块处于安全模式，请先从模块库重试");
                    return;
                }
                showCrashSheet(module);
                return;
            }
            if (moduleAwaitingResult) {
                if (id.equals(activeModuleId)) return;
                pendingOpenModuleId = id;
                showSnack("关闭当前模块后将打开目标");
                return;
            }
            launchModule(module);
        } catch (ModuleException error) {
            showSnack("模块未安装或已损坏：" + id);
        }
    }

    private void launchModule(InstalledModule module) {
        if (manager == null || module == null) return;
        if (moduleAwaitingResult) {
            pendingOpenModuleId = module.id();
            return;
        }
        ModuleSlotManager.Slot slot = slots.acquire();
        if (slot == null) {
            showSnack("模块槽位忙，请稍后重试");
            return;
        }
        activeSlot = slot;
        RecentModules.record(this, module.id());
        ModuleUsage.markOpened(this, module.id());
        if (crashGuard != null) crashGuard.beginLaunch(module.id());
        activeModuleId = module.id();
        setActiveModule(module.id());
        moduleAwaitingResult = true;
        deathRetries = 0;
        main.removeCallbacks(processDeathCheck);
        try {
            // Keep the shell from loading ModuleActivity/WebView classes here.
            // The target process loads the Activity class itself.
            String target = slot == ModuleSlotManager.Slot.M1
                    ? "com.docklet.shell.ModuleActivity"
                    : "com.docklet.shell.ModuleActivity2";
            Intent intent = new Intent().setClassName(this, target);
            intent.putExtra(ModuleActivity.EXTRA_MODULE_ID, module.id());
            if (pendingSessionId != null) {
                intent.putExtra(ModuleActivity.EXTRA_SESSION_ID, pendingSessionId);
            }
            pendingSessionId = null;
            startActivityForResult(intent, REQUEST_MODULE);
        } catch (Exception error) {
            moduleAwaitingResult = false;
            activeModuleId = null;
            slots.release(slot);
            activeSlot = null;
            setActiveModule(null);
            if (crashGuard != null) {
                crashGuard.recordFailure(module.id(), "无法启动模块进程：" + safeMessage(error));
            }
            restoreModuleOrigin();
            showErrorSheet(
                    "无法启动模块",
                    "LAUNCH_FAILED",
                    safeMessage(error)
            );
        }
    }

    @Override
    public void onBrowseDemos() {
        showAddScreen();
    }

    private void stopActiveModule(Runnable action) {
        if (!moduleAwaitingResult || activeModuleId == null) {
            action.run();
            return;
        }
        if (afterStop != null) return;
        afterStop = action;
        sendBroadcast(new Intent(ModuleActivity.ACTION_STOP_REQUEST)
                .setPackage(getPackageName()));
        main.removeCallbacks(stopTimeout);
        main.postDelayed(stopTimeout, 4000L);
    }

    private void setActiveModule(String id) {
        getSharedPreferences(ModuleRouterActivity.ACTIVE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(ModuleRouterActivity.ACTIVE_MODULE, id == null ? "" : id)
                .apply();
    }

    private void showOverlay(View view) {
        if (overlayHost == null || view == null) return;
        overlayHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        overlayHost.setVisibility(View.VISIBLE);
        overlayHost.bringToFront();
        if (snack != null) snack.bringToFront();
    }

    /** Leaves the app page by switching the host back to 首页. */
    private void leaveApp() {
        if (home != null) home.selectTab(0);
    }

    private void closeOverlay() {
        if (overlayHost == null) return;
        int count = overlayHost.getChildCount();
        if (count > 0) {
            View top = overlayHost.getChildAt(count - 1);
            overlayHost.removeView(top);
            if (top == onlineScreen) onlineScreen = null;
        }
        if (overlayHost.getChildCount() > 0) return;
        overlayHost.setVisibility(View.GONE);
        if (home != null && inventory != null && inventory.snapshot() != null) {
            home.refreshModules(inventory.snapshot());
        }
        if (addInputMode != -1) {
            Window window = getWindow();
            if (window != null) window.setSoftInputMode(addInputMode);
            addInputMode = -1;
        }
    }

    private void showAddScreen() {
        // Reuse the live page: no rebuild, no lost search state.
        if (addScreen != null && home != null && home.hasAppPage()) {
            List<InstalledModule> current = inventory != null && inventory.snapshot() != null
                    ? inventory.snapshot()
                    : new ArrayList<>();
            addScreen.refreshInstalled(current);
            home.showAppScreen(addScreen);
            addScreen.selectFirstCategory();
            return;
        }
        if (demoApps == null) demoApps = DemoCatalog.load(this);
        Window window = getWindow();
        if (window != null && addInputMode == -1) {
            addInputMode = window.getAttributes().softInputMode;
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        List<InstalledModule> installedForAdd = inventory != null && inventory.snapshot() != null
                ? inventory.snapshot()
                : new ArrayList<>();
        AddModuleScreen.Listener sharedListener = new AddModuleScreen.Listener() {
                    @Override
                    public void onClose() {
                        leaveApp();
                    }

                    @Override
                    public void onDemo(DemoApp app, String state) {
                        leaveApp();
                        handleDemoInstall(app, state);
                    }

                    @Override
                    public void onDemoDetails(DemoApp app, String state) {
                        demoDetailOrigin = addScreen;
                        pendingDemoAdd = addScreen;
                        showDemoDetail(app, state);
                    }

                    @Override
                    public void onRemote() {
                        leaveApp();
                        showRemoteCatalogSheet();
                    }

                    @Override
                    public void onPickFile(String sha256) {
                        openPackagePicker(sha256);
                    }

                    @Override
                    public void onUrl(String url, String sha256) {
                        leaveApp();
                        confirmUrlInstall(url, sha256);
                    }

                    @Override
                    public void onOpenInstalled(InstalledModule module) {
                        prepareAddModuleReturn();
                        leaveApp();
                        onOpenModule(module);
                    }

                    @Override
                    public void onInstalledDetails(InstalledModule module) {
                        detailReturnToAdd = true;
                        detailOriginAdd = addScreen;
                        leaveApp();
                        showDetailScreen(module);
                    }

                    @Override
                    public void onNavigate(int tab) {
                        if (home != null) home.selectTab(tab);
                    }

                    @Override
                    public void onAddToDesktop(InstalledModule module) {
                        addModuleShortcut(module);
                    }

                    @Override
                    public void onOpenOnline() {
                        openOnlinePage();
                    }

                    @Override
                    public void onOpenLocalInstall() {
                        openLocalInstallPage();
                    }

                    @Override
                    public void onRefreshCatalog() {
                        refreshCatalog();
                    }

                    @Override
                    public void onShowFullscreenPage(View page) {
                        if (overlayHost == null || page == null) return;
                        if (page.getParent() instanceof ViewGroup) {
                            ((ViewGroup) page.getParent()).removeView(page);
                        }
                        overlayHost.removeAllViews();
                        overlayHost.addView(page, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        ));
                        overlayHost.setVisibility(View.VISIBLE);
                        overlayHost.bringToFront();
                    }

                    @Override
                    public void onHideFullscreenPage() {
                        if (overlayHost == null) return;
                        overlayHost.removeAllViews();
                        overlayHost.setVisibility(View.GONE);
                    }

                    @Override
                    public void onUninstall(InstalledModule module) {
                        uninstallFromAdd = true;
                        confirmUninstall(module);
                    }
                };
        this.appListener = sharedListener;
        AddModuleScreen screen = new AddModuleScreen(
                this, demoApps, installedForAdd, sharedListener
        );
        addScreen = screen;
        if (home != null) {
            home.showAppScreen(screen);
        }
    }

    /** Pulls catalog.json from the repo and swaps the online catalog. */
    private void refreshCatalog() {
        refreshCatalog(false);
    }

    private void refreshCatalog(boolean silent) {
        if (!silent) showSnack("正在获取在线目录…");
        io.execute(() -> {
            String json = com.docklet.shell.demo.CatalogSource.fetch();
            if (json == null) {
                if (!silent) {
                    runOnUiThread(() -> showSnack("获取目录失败，已保留当前列表"));
                }
                return;
            }
            List<DemoApp> apps = DemoCatalog.parse(json);
            if (apps.isEmpty()) {
                if (!silent) {
                    runOnUiThread(() -> showSnack("目录为空或格式不对，已保留当前列表"));
                }
                return;
            }
            com.docklet.shell.demo.CatalogSource.save(this, json);
            runOnUiThread(() -> {
                demoApps = apps;
                if (addScreen != null) addScreen.setCatalog(apps);
                if (!silent) {
                    showSnack("在线目录已更新，共 " + apps.size() + " 个模块");
                }
            });
        });
    }

    /** Fetches the online catalog in the background when the cache is stale. */
    private void maybeRefreshCatalog() {
        java.io.File cache = new java.io.File(getFilesDir(), "catalog.json");
        long age = System.currentTimeMillis() - (cache.isFile() ? cache.lastModified() : 0L);
        if (age < 6L * 60L * 60L * 1000L) return;
        refreshCatalog(true);
    }

    /** Local install page: a full-screen layer, no bottom navigation. */
    private void openLocalInstallPage() {
        if (addScreen == null) showAddScreen();
        if (addScreen == null) return;
        View page = addScreen.buildLocalInstallPage(this::closeOverlay);
        if (page != null) pushOverlay(page);
    }

    /** The standalone 在线应用 page: its own screen instance on the overlay layer. */
    private void openOnlinePage() {
        if (demoApps == null) demoApps = DemoCatalog.load(this);
        if (appListener == null) showAddScreen();
        if (appListener == null) return;
        List<InstalledModule> current = inventory != null && inventory.snapshot() != null
                ? inventory.snapshot()
                : new ArrayList<>();
        AddModuleScreen online = new AddModuleScreen(
                this, demoApps, current, new OnlinePageListener(appListener)
        );
        online.setOnlineOnly(true);
        onlineScreen = online;
        pushOverlay(online);
    }

    /** Everything but onClose goes to the app page listener. */
    private final class OnlinePageListener implements AddModuleScreen.Listener {
        private final AddModuleScreen.Listener base;

        OnlinePageListener(AddModuleScreen.Listener base) {
            this.base = base;
        }

        @Override
        public void onClose() {
            closeOverlay();
        }

        @Override
        public void onDemo(DemoApp app, String state) {
            base.onDemo(app, state);
        }

        @Override
        public void onDemoDetails(DemoApp app, String state) {
            // The detail sits on top of the online overlay: remember that, not
            // the installed page, so returning lands back on 在线模块.
            demoDetailOrigin = onlineScreen;
            pendingDemoAdd = null;
            showDemoDetail(app, state);
        }

        @Override
        public void onRemote() {
            base.onRemote();
        }

        @Override
        public void onPickFile(String sha256) {
            base.onPickFile(sha256);
        }

        @Override
        public void onUrl(String url, String sha256) {
            base.onUrl(url, sha256);
        }

        @Override
        public void onOpenInstalled(InstalledModule module) {
            base.onOpenInstalled(module);
        }

        @Override
        public void onInstalledDetails(InstalledModule module) {
            base.onInstalledDetails(module);
        }

        @Override
        public void onNavigate(int tab) {
            closeOverlay();
            if (home != null) home.selectTab(tab);
        }

        @Override
        public void onAddToDesktop(InstalledModule module) {
            base.onAddToDesktop(module);
        }

        @Override
        public void onUninstall(InstalledModule module) {
            base.onUninstall(module);
        }

        @Override
        public void onOpenOnline() {
            // already here
        }

        @Override
        public void onOpenLocalInstall() {
            openLocalInstallPage();
        }

        @Override
        public void onRefreshCatalog() {
            refreshCatalog();
        }

        @Override
        public void onShowFullscreenPage(View page) {
            base.onShowFullscreenPage(page);
        }

        @Override
        public void onHideFullscreenPage() {
            base.onHideFullscreenPage();
        }
    }

    /** Adds a full-screen layer on top of the current one. */
    private void pushOverlay(View view) {
        if (overlayHost == null || view == null) return;
        overlayHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        overlayHost.setVisibility(View.VISIBLE);
        overlayHost.bringToFront();
    }

    private void showDemoDetail(DemoApp app, String state) {
        InstalledModule installed = installedModule(app.id());
        final DemoDetailScreen[] screenRef = new DemoDetailScreen[1];
        DemoDetailScreen screen = new DemoDetailScreen(
                this,
                app,
                state,
                installed == null ? null : installed.version(),
                installed == null ? null : installed.previousVersion(),
                new DemoDetailScreen.Listener() {
                    @Override
                    public void onClose() {
                        restoreDemoAdd();
                    }

                    @Override
                    public void onInstall() {
                        installDemoApp(app, module -> {
                            if (screenRef[0] != null) {
                                screenRef[0].setState(
                                        "已安装",
                                        module.version(),
                                        module.previousVersion()
                                );
                            }
                            if (inventory != null) {
                                inventory.refresh(true, modules -> refreshScreens(modules));
                            }
                        });
                    }

                    @Override
                    public void onOpen() {
                        InstalledModule module = installedModule(app.id());
                        if (module == null) {
                            showSnack("模块未安装");
                            return;
                        }
                        prepareDemoDetailReturn(screenRef[0], demoDetailOrigin);
                        closeOverlay();
                        onOpenModule(module);
                    }

                    @Override
                    public void onRollback() {
                        InstalledModule module = installedModule(app.id());
                        if (module == null || !module.canRollback()) {
                            showSnack("没有可回滚的版本");
                            return;
                        }
                        confirmRollback(module);
                    }

                    @Override
                    public void onUninstall() {
                        InstalledModule module = installedModule(app.id());
                        if (module == null) {
                            if (screenRef[0] != null) screenRef[0].setState("", null);
                            return;
                        }
                        uninstallFromAdd = false;
                        confirmUninstall(module, remaining -> {
                            if (screenRef[0] != null) {
                                screenRef[0].setState("", null);
                            }
                            refreshScreens(remaining);
                            if (inventory != null) {
                                inventory.refresh(true, modules -> refreshScreens(modules));
                            }
                        });
                    }
                }
        );
        screenRef[0] = screen;
        demoDetailScreen = screen;
        showOverlay(screen);
    }

    /** Shows the app page as a normal peer page (the add screen is not an overlay any more). */
    private void showAppPage(AddModuleScreen add) {
        if (add == null) {
            showAddScreen();
            return;
        }
        addScreen = add;
        if (home != null) {
            home.showAppScreen(add);
        }
    }

    /** Pushes a fresh install list into every live catalog/list page. */
    private void refreshScreens(List<InstalledModule> modules) {
        if (addScreen != null) addScreen.refreshInstalled(modules);
        if (onlineScreen != null) onlineScreen.refreshInstalled(modules);
    }

    private void restoreDemoAdd() {
        AddModuleScreen origin = demoDetailOrigin;
        demoDetailOrigin = null;
        demoDetailScreen = null;
        pendingDemoAdd = null;
        boolean backHome = demoDetailReturnHome;
        demoDetailReturnHome = false;
        closeOverlay();
        if (backHome) {
            showHome();
            return;
        }
        if (origin != null && origin == onlineScreen && onlineScreen.getParent() != null) {
            // Returning to 在线模块: the overlay is still underneath the detail.
            return;
        }
        if (origin == null) {
            showAddScreen();
            return;
        }
        showAppPage(origin);
    }

    private void prepareAddModuleReturn() {
        final AddModuleScreen add = addScreen;
        pendingModuleReturn = () -> {
            if (add == null) {
                showHome();
                return;
            }
            showAppPage(add);
        };
    }

    private void prepareDemoDetailReturn(DemoDetailScreen detail, AddModuleScreen add) {
        pendingModuleReturn = () -> {
            if (detail != null) {
                showOverlay(detail);
            } else if (add != null) {
                showAppPage(add);
            } else {
                showHome();
            }
        };
    }

    private void restoreModuleOrigin() {
        Runnable restore = pendingModuleReturn;
        pendingModuleReturn = null;
        if (restore != null) {
            restore.run();
        } else {
            showHome();
        }
    }

    private InstalledModule installedModule(String id) {
        if (inventory != null && inventory.snapshot() != null) {
            for (InstalledModule module : inventory.snapshot()) {
                if (module.id().equals(id)) return module;
            }
        }
        try {
            return manager == null ? null : manager.get(id);
        } catch (ModuleException ignored) {
            return null;
        }
    }

    private void handleDemoInstall(DemoApp app, String state) {
        if (state != null && state.startsWith("已安装")) {
            showSnack(app.name() + " " + state);
            return;
        }
        if (app.url() == null || app.url().isEmpty()) {
            showSnack("目录里没有这个模块的下载地址");
            return;
        }
        installFromUrl(app.url(), app.sha256());
    }

    @Override
    public void onInstallFromFile() {
        openPackagePicker(null);
    }

    private void openPackagePicker(String sha256) {
        pendingPackageHash = sha256 == null || sha256.isEmpty() ? null : sha256.trim();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_PACKAGE);
        } catch (Exception error) {
            pendingPackageHash = null;
            showSnack("没有可用的文件选择器");
        }
    }

    @Override
    public void onShowModuleDetails(InstalledModule module) {
        detailReturnToAdd = false;
        detailOriginAdd = null;
        showDetailScreen(module);
    }

    @Override
    public void onShowInstallMenu() {
        showAddScreen();
    }

    @Override
    public void onShowApp() {
        showAddScreen();
    }

    @Override
    public void onFavoriteChanged() {
        showHome();
    }

    @Override
    public void onShowMemory() {
        showMemorySheet(MemoryUsage.read(this));
    }

    private void showDetailScreen(InstalledModule module) {
        boolean returnToAdd = detailReturnToAdd;
        AddModuleScreen origin = detailOriginAdd;
        detailReturnToAdd = false;
        detailOriginAdd = null;
        demoDetailReturnHome = !returnToAdd;
        pendingDemoAdd = returnToAdd ? origin : null;
        demoDetailOrigin = returnToAdd ? origin : null;
        showDemoDetail(demoAppFor(module), "");
    }

    /** Every installed app has a catalog entry; fall back to its manifest when not. */
    private DemoApp demoAppFor(InstalledModule module) {
        if (demoApps != null) {
            for (DemoApp item : demoApps) {
                if (item.id().equals(module.id())) return item;
            }
        }
        List<DemoApp.Permission> permissions = new ArrayList<>();
        for (ModuleManifest.Permission permission : module.permissions()) {
            permissions.add(new DemoApp.Permission(permission.name(), permission.reason()));
        }
        return new DemoApp(
                module.id(),
                module.name(),
                module.kind(),
                module.glyph(),
                module.version(),
                module.description(),
                module.accent(),
                "",
                "",
                "",
                module.tagline(),
                module.author(),
                module.authorContact(),
                "",
                module.entry(),
                module.capabilities(),
                module.codeBytes(),
                "",
                permissions
        );
    }

    private void showMemorySheet(MemoryUsage.Snapshot snapshot) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(UiKit.text(this, "运行内存", 21f, UiKit.INK, true));
        TextView sub = UiKit.text(
                this,
                "PSS 统计 · 共享内存按比例计入 · " + snapshot.processCount + " 个进程",
                10.5f,
                UiKit.MUTED,
                false
        );
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(5);
        content.addView(sub, subParams);
        TextView build = UiKit.label(
                this,
                "BUILD " + BuildConfig.BUILD_STAMP,
                7.5f,
                UiKit.FAINT
        );
        LinearLayout.LayoutParams buildParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buildParams.topMargin = dp(3);
        content.addView(build, buildParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(UiKit.stroked(this, UiKit.SURFACE, UiKit.LINE, 18f));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.topMargin = dp(16);
        content.addView(panel, panelParams);
        addInfoRow(panel, "Docklet 进程", formatMemoryKb(snapshot.shellPssKb), false);
        addInfoRow(panel, "模块槽位", formatMemoryKb(snapshot.modulePssKb), false);
        if (snapshot.webViewPssKb > 0L) {
            addInfoRow(panel, "WebView 子进程", formatMemoryKb(snapshot.webViewPssKb), false);
        }
        addInfoRow(panel, "Docklet 总计", formatMemoryKb(snapshot.totalPssKb), true);

        LinearLayout system = new LinearLayout(this);
        system.setOrientation(LinearLayout.VERTICAL);
        system.setBackground(UiKit.rounded(this, UiKit.SURFACE_SOFT, 16f));
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        systemParams.topMargin = dp(12);
        content.addView(system, systemParams);
        addInfoRow(
                system,
                "系统可用",
                UiKit.formatBytes(snapshot.availableBytes) + " / "
                        + UiKit.formatBytes(snapshot.totalBytes),
                true
        );

        TextView close = UiKit.secondaryButton(this, "关闭");
        close.setOnClickListener(view -> sheet.dismiss());
        sheet.setBody(content);
        sheet.setFooter(close);
        sheet.show();
    }

    private String formatMemoryKb(long kb) {
        return String.format(Locale.ROOT, "%.1f MB", Math.max(0L, kb) / 1024.0);
    }

    @Override
    public void onStopBackground(InstalledModule module) {
        if (module != null) DockletMessageService.unsubscribe(this, module.id());
        main.postDelayed(() -> {
            showHome();
            showSnack("后台消息已停止");
        }, 250L);
    }

    private String remoteCatalogUrl() {
        SharedPreferences prefs = getSharedPreferences("docklet_remote", MODE_PRIVATE);
        String saved = prefs.getString("catalog_url", "");
        if (saved == null) saved = "";
        if (saved.isEmpty() && BuildConfig.DEBUG) {
            return "http://10.0.2.2:8789/catalog.json";
        }
        return saved;
    }

    private void showRemoteCatalogSheet() {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = UiKit.text(this, "远程模块", 21f, UiKit.INK, true);
        title.setLetterSpacing(-0.02f);
        content.addView(title);

        TextView subtitle = UiKit.text(
                this,
                "从 catalog 下载 .pkg；先校验 sha256，再原子安装。失败不会替换当前版本。",
                12f,
                UiKit.MUTED,
                false
        );
        subtitle.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(8);
        content.addView(subtitle, subtitleParams);

        LinearLayout urlRow = new LinearLayout(this);
        urlRow.setOrientation(LinearLayout.HORIZONTAL);
        urlRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams urlRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        urlRowParams.topMargin = dp(16);
        content.addView(urlRow, urlRowParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(12f);
        input.setTextColor(UiKit.INK);
        input.setHintTextColor(UiKit.FAINT);
        input.setHint("https://…/catalog.json");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setBackground(UiKit.rounded(this, UiKit.SURFACE_SOFT, 13f));
        input.setPadding(dp(13), dp(10), dp(13), dp(10));
        input.setText(remoteCatalogUrl());
        input.setSelection(input.getText().length());
        urlRow.addView(input, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));

        TextView load = UiKit.text(this, "加载", 13f, Color.WHITE, true);
        load.setGravity(Gravity.CENTER);
        load.setBackground(UiKit.ripple(UiKit.rounded(this, UiKit.ACCENT, 13f)));
        load.setClickable(true);
        load.setFocusable(true);
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(dp(68), dp(48));
        loadParams.leftMargin = dp(8);
        urlRow.addView(load, loadParams);

        TextView status = UiKit.text(
                this,
                BuildConfig.DEBUG
                        ? "调试默认地址指向 10.0.2.2:8789；真机请改局域网 IP。"
                        : "输入 catalog 地址后点击加载。",
                10.5f,
                UiKit.FAINT,
                false
        );
        status.setLineSpacing(dp(1), 1.15f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(8);
        content.addView(status, statusParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVisibility(View.GONE);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)
        );
        scrollParams.topMargin = dp(8);
        content.addView(scroll, scrollParams);

        TextView hint = UiKit.text(
                this,
                "catalog 需要提供每个模块的 sha256；HTTPS catalog 不能降级到 HTTP 包。",
                10f,
                UiKit.FAINT,
                false
        );
        hint.setLineSpacing(dp(1), 1.15f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = dp(7);
        content.addView(hint, hintParams);

        TextView cancel = UiKit.textButton(this, "取消", UiKit.MUTED);
        cancel.setOnClickListener(view -> sheet.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.topMargin = dp(2);
        content.addView(cancel, cancelParams);

        load.setOnClickListener(view ->
                loadRemoteCatalog(input, status, list, load, sheet, scroll)
        );

        sheet.setBody(content);
        sheet.show();
    }

    @SuppressLint("SetTextI18n")
    private void loadRemoteCatalog(
            EditText input,
            TextView status,
            LinearLayout list,
            TextView load,
            SheetDialog sheet,
            ScrollView scroll
    ) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            status.setTextColor(UiKit.DANGER);
            status.setText("请先填写 catalog 地址。");
            return;
        }
        URL url;
        try {
            url = new URL(raw);
        } catch (Exception error) {
            status.setTextColor(UiKit.DANGER);
            status.setText("catalog 地址不合法。");
            return;
        }
        String scheme = url.getProtocol();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            status.setTextColor(UiKit.DANGER);
            status.setText("catalog 只支持 http/https。");
            return;
        }

        status.setTextColor(UiKit.MUTED);
        status.setText("正在读取 catalog…");
        load.setEnabled(false);
        load.setAlpha(0.5f);
        list.removeAllViews();
        scroll.setVisibility(View.GONE);

        io.execute(() -> {
            try {
                RemoteCatalog catalog = RemoteCatalog.load(url);
                main.post(() -> {
                    load.setEnabled(true);
                    load.setAlpha(1f);
                    getSharedPreferences("docklet_remote", MODE_PRIVATE)
                            .edit()
                            .putString("catalog_url", raw)
                            .apply();
                    status.setTextColor(UiKit.MUTED);
                    status.setText("目录已加载 · " + catalog.modules().size() + " 个模块");
                    scroll.setVisibility(View.VISIBLE);
                    populateRemoteList(list, catalog, sheet);
                });
            } catch (ModuleException error) {
                main.post(() -> {
                    load.setEnabled(true);
                    load.setAlpha(1f);
                    status.setTextColor(UiKit.DANGER);
                    status.setText(friendly(error.getCode(), error.getMessage()));
                });
            }
        });
    }

    private void populateRemoteList(
            LinearLayout list,
            RemoteCatalog catalog,
            SheetDialog sheet
    ) {
        list.removeAllViews();
        Map<String, InstalledModule> installed = new HashMap<>();
        for (InstalledModule module : manager.listInstalled()) {
            installed.put(module.id(), module);
        }
        if (catalog.modules().isEmpty()) {
            TextView empty = UiKit.text(this, "catalog 里没有模块。", 12f, UiKit.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(28), dp(12), dp(28));
            list.addView(empty);
            return;
        }

        for (RemoteModule remote : catalog.modules()) {
            InstalledModule existing = installed.get(remote.id());
            int comparison = existing == null
                    ? -1
                    : SemVer.compare(remote.version(), existing.version());

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(11), dp(12), dp(11));
            card.setBackground(UiKit.rounded(this, UiKit.SURFACE_SOFT, 15f));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cardParams.topMargin = dp(7);
            list.addView(card, cardParams);

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            card.addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ));

            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            copy.addView(nameRow);

            TextView name = UiKit.text(this, remote.name(), 12.5f, UiKit.INK, true);
            nameRow.addView(name);
            TextView version = UiKit.pill(
                    this,
                    "v" + remote.version(),
                    UiKit.parseColor(remote.accent(), UiKit.ACCENT),
                    UiKit.blend(
                            UiKit.parseColor(remote.accent(), UiKit.ACCENT),
                            Color.WHITE,
                            0.86f
                    )
            );
            LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            versionParams.leftMargin = dp(7);
            nameRow.addView(version, versionParams);

            if (remote.description() != null && !remote.description().isEmpty()) {
                TextView description = UiKit.text(
                        this,
                        remote.description(),
                        10.5f,
                        UiKit.MUTED,
                        false
                );
                description.setMaxLines(2);
                description.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                descriptionParams.topMargin = dp(3);
                copy.addView(description, descriptionParams);
            }

            TextView meta = UiKit.text(
                    this,
                    remote.kind() + (remote.size() > 0
                            ? " · " + UiKit.formatBytes(remote.size())
                            : ""),
                    9.5f,
                    UiKit.FAINT,
                    false
            );
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            metaParams.topMargin = dp(4);
            copy.addView(meta, metaParams);

            String label;
            if (existing == null) {
                label = "安装";
            } else if (comparison > 0) {
                label = "更新";
            } else {
                label = "已安装";
            }
            TextView action = UiKit.pill(
                    this,
                    label,
                    comparison > 0 || existing == null ? Color.WHITE : UiKit.MUTED,
                    comparison > 0 || existing == null ? UiKit.ACCENT : UiKit.LINE
            );
            action.setClickable(existing == null || comparison > 0);
            action.setFocusable(existing == null || comparison > 0);
            action.setPadding(dp(11), dp(6), dp(11), dp(6));
            if (existing == null || comparison > 0) {
                action.setOnClickListener(view -> {
                    sheet.dismiss();
                    downloadRemoteModule(remote, existing != null);
                });
            } else {
                action.setOnClickListener(view ->
                        showSnack("当前已是 v" + existing.version())
                );
            }
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            actionParams.leftMargin = dp(8);
            card.addView(action, actionParams);
        }
    }

    private void downloadRemoteModule(RemoteModule remote, boolean update) {
        Dialog progress = showProgress(
                update ? "正在更新" : "正在安装",
                "下载 " + remote.name() + " v" + remote.version() + " · 校验 sha256"
        );
        io.execute(() -> {
            File packageFile = null;
            try {
                packageFile = PackageDownloader.download(
                        remote,
                        new File(getCacheDir(), "remote-modules")
                );
                InstalledModule installed = manager.install(
                        packageFile,
                        remote.id(),
                        remote.version()
                );
                main.post(() -> {
                    dismissQuietly(progress);
                    showHome();
                    showSnack(
                            (update ? "已更新 " : "已安装 ")
                                    + installed.name() + " v" + installed.version()
                    );
                });
            } catch (ModuleException error) {
                main.post(() -> {
                    dismissQuietly(progress);
                    showErrorSheet(
                            update ? "更新失败，旧版本保留" : "远程安装失败",
                            error.getCode(),
                            friendly(error.getCode(), error.getMessage())
                    );
                });
            } finally {
                if (packageFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    packageFile.delete();
                }
            }
        });
    }

    private void showInstallSheet() {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = UiKit.text(this, "添加模块", 21f, UiKit.INK, true);
        title.setLetterSpacing(-0.02f);
        content.addView(title);

        TextView subtitle = UiKit.text(
                this,
                "几个不同类型的示例，安装后各自拥有独立运行环境与数据。",
                12f,
                UiKit.MUTED,
                false
        );
        subtitle.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(6);
        content.addView(subtitle, subtitleParams);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sectionParams.topMargin = dp(18);
        sectionParams.bottomMargin = dp(2);
        content.addView(section, sectionParams);

        TextView sectionTitle = UiKit.label(this, "示例模块", 11.5f, UiKit.MUTED);
        sectionTitle.setLetterSpacing(0.12f);
        section.addView(sectionTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        section.addView(UiKit.pill(
                this,
                demoApps == null ? "0 个" : demoApps.size() + " 个",
                UiKit.MUTED,
                UiKit.SURFACE_SOFT
        ));

        if (demoApps == null || demoApps.isEmpty()) {
            TextView missing = UiKit.text(
                    this,
                    "示例目录缺失，请从本地 .pkg 安装。",
                    11.5f,
                    UiKit.DANGER,
                    false
            );
            content.addView(missing, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        } else {
            for (DemoApp app : demoApps) {
                String state = demoState(app);
                View row = demoOptionRow(app, state, () -> {
                    sheet.dismiss();
                    if (state.startsWith("已安装")) {
                        showSnack(app.name() + " " + state);
                        return;
                    }
                    installDemoApp(app, null);
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                );
                rowParams.topMargin = dp(9);
                content.addView(row, rowParams);
            }
        }

        TextView or = UiKit.text(this, "或者", 10.5f, UiKit.FAINT, false);
        or.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        orParams.topMargin = dp(16);
        content.addView(or, orParams);

        View file = optionRow(
                IconView.Type.FILE,
                "从文件安装 .pkg",
                "选择本地模块包，先校验再安装",
                () -> {
                    sheet.dismiss();
                    onInstallFromFile();
                }
        );
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fileParams.topMargin = dp(8);
        content.addView(file, fileParams);

        TextView cancel = UiKit.textButton(this, "取消", UiKit.MUTED);
        cancel.setOnClickListener(view -> sheet.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.topMargin = dp(6);
        content.addView(cancel, cancelParams);

        sheet.setBody(content);
        sheet.show();
    }

    private String demoState(DemoApp app) {
        try {
            InstalledModule installed = manager.get(app.id());
            int compare = SemVer.compare(installed.version(), app.version());
            if (compare == 0) return "已安装";
            if (compare < 0) return "可更新";
            return "已安装 " + installed.version();
        } catch (ModuleException error) {
            return "";
        }
    }

    private View demoOptionRow(DemoApp app, String state, Runnable action) {
        int accent = UiKit.parseColor(app.accent(), UiKit.ACCENT);
        int iconBg = UiKit.blend(accent, Color.WHITE, 0.15f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(UiKit.ripple(stroked(UiKit.SURFACE, UiKit.LINE, 18f)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(UiKit.rounded(this, iconBg, 14f));
        IconView icon = new IconView(this).type(iconFor(app.glyph()));
        icon.setColor(accent);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        textParams.leftMargin = dp(13);
        textParams.rightMargin = dp(8);
        row.addView(text, textParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(titleRow);

        titleRow.addView(UiKit.text(this, app.name(), 14.5f, UiKit.INK, true));
        TextView kind = UiKit.pill(this, app.kind(), accent, iconBg);
        LinearLayout.LayoutParams kindParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        kindParams.leftMargin = dp(8);
        titleRow.addView(kind, kindParams);

        TextView detailView = UiKit.text(
                this,
                app.description() + " · v" + app.version(),
                10.8f,
                UiKit.MUTED,
                false
        );
        detailView.setMaxLines(2);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(4);
        text.addView(detailView, detailParams);

        if (state.isEmpty()) {
            IconView chevron = new IconView(this).type(IconView.Type.CHEVRON_RIGHT);
            chevron.setColor(UiKit.FAINT);
            row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        } else {
            int stateColor = state.equals("可更新") ? accent : UiKit.GREEN;
            int stateBg = state.equals("可更新") ? iconBg : UiKit.GREEN_SOFT;
            row.addView(UiKit.pill(this, state, stateColor, stateBg));
        }
        return row;
    }

    private IconView.Type iconFor(String icon) {
        if (icon == null) return IconView.Type.SPARK;
        switch (icon) {
            case "list":
                return IconView.Type.LIST;
            case "timer":
                return IconView.Type.TIMER;
            case "brush":
                return IconView.Type.BRUSH;
            case "calc":
                return IconView.Type.CALC;
            case "quiz":
                return IconView.Type.QUIZ;
            case "network":
                return IconView.Type.NETWORK;
            case "spark":
            default:
                return IconView.Type.SPARK;
        }
    }

    private View optionRow(
            IconView.Type type,
            String title,
            String detail,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(12), dp(14));
        row.setBackground(UiKit.ripple(stroked(UiKit.SURFACE, UiKit.LINE, 18f)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(UiKit.rounded(this, UiKit.ACCENT_SOFT, 14f));
        IconView icon = new IconView(this).type(type);
        icon.setColor(UiKit.ACCENT_DEEP);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        textParams.leftMargin = dp(13);
        textParams.rightMargin = dp(8);
        row.addView(text, textParams);

        TextView titleView = UiKit.text(this, title, 14.5f, UiKit.INK, true);
        text.addView(titleView);
        TextView detailView = UiKit.text(this, detail, 11f, UiKit.MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(4);
        text.addView(detailView, detailParams);

        IconView chevron = new IconView(this).type(IconView.Type.CHEVRON_RIGHT);
        chevron.setColor(UiKit.FAINT);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(18)));
        return row;
    }

    private void showDetailSheet(InstalledModule module) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(head);

        TextView icon = UiKit.text(
                this,
                UiKit.initialOf(module.name(), module.id()),
                26f,
                accent,
                true
        );
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(UiKit.rounded(this, UiKit.blend(accent, Color.WHITE, 0.16f), 18f));
        head.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams namesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        namesParams.leftMargin = dp(14);
        head.addView(names, namesParams);

        TextView name = UiKit.text(this, module.name(), 20f, UiKit.INK, true);
        names.addView(name);

        TextView meta = UiKit.text(
                this,
                "v" + module.version() + " · " + module.origin(),
                10.5f,
                UiKit.FAINT,
                false
        );
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(5);
        names.addView(meta, metaParams);

        FrameLayout close = UiKit.iconButton(
                this,
                new IconView(this).type(IconView.Type.CLOSE),
                "关闭",
                view -> sheet.dismiss()
        );
        close.getLayoutParams().width = dp(38);
        close.getLayoutParams().height = dp(38);
        head.addView(close);

        if (module.description() != null && !module.description().isEmpty()) {
            TextView description = UiKit.text(this, module.description(), 12f, UiKit.MUTED, false);
            description.setLineSpacing(dp(2), 1.2f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            descriptionParams.topMargin = dp(16);
            content.addView(description, descriptionParams);
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(UiKit.rounded(this, UiKit.SURFACE_SOFT, 18f));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.topMargin = dp(18);
        content.addView(panel, panelParams);

        CrashGuard.State crash = crashGuard == null
                ? CrashGuard.State.NONE
                : crashGuard.state(module.id());

        addInfoRow(panel, "模块 ID", module.id(), false);
        addInfoRow(panel, "存储", module.capabilities().contains("storage")
                ? "data/" + module.id() + "/app.db"
                : "不使用 SQLite", false);
        addInfoRow(panel, "能力", module.capabilities().isEmpty()
                ? "无"
                : TextUtils.join(" · ", module.capabilities()), false);
        addInfoRow(panel, "已安装版本", TextUtils.join(", ", module.versions()), false);
        addInfoRow(panel, "上一版本",
                module.canRollback() ? module.previousVersion() : "无", false);
        if (crash.failed()) {
            addInfoRow(panel, "启动状态",
                    crash.blocked()
                            ? "安全模式 · 连续 " + crash.count() + " 次失败"
                            : "上次启动失败",
                    false);
        }
        addInfoRow(panel, "占用",
                UiKit.formatBytes(module.codeBytes()) + " 代码 · "
                        + UiKit.formatBytes(module.dataBytes()) + " 数据", true);

        if (crash.failed()) {
            TextView warning = UiKit.text(
                    this,
                    (crash.blocked() ? "已进入安全模式。" : "上次启动失败。")
                            + (crash.reason().isEmpty() ? "" : " " + crash.reason()),
                    10.5f,
                    UiKit.DANGER,
                    false
            );
            warning.setLineSpacing(dp(1), 1.2f);
            warning.setPadding(dp(12), dp(10), dp(12), dp(10));
            warning.setBackground(UiKit.rounded(this, UiKit.DANGER_SOFT, 13f));
            LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            warningParams.topMargin = dp(12);
            content.addView(warning, warningParams);
        }

        TextView open = UiKit.primaryButton(
                this,
                crash.failed() ? "重试启动" : "打开模块"
        );
        open.setOnClickListener(view -> {
            sheet.dismiss();
            launchModule(module);
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        openParams.topMargin = dp(18);
        content.addView(open, openParams);

        if (module.canRollback()) {
            TextView rollback = UiKit.secondaryButton(
                    this,
                    "回滚到 v" + module.previousVersion()
            );
            rollback.setOnClickListener(view -> {
                sheet.dismiss();
                confirmRollback(module);
            });
            LinearLayout.LayoutParams rollbackParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rollbackParams.topMargin = dp(8);
            content.addView(rollback, rollbackParams);
        }

        TextView uninstall = UiKit.textButton(this, "卸载模块", UiKit.DANGER);
        uninstall.setOnClickListener(view -> {
            sheet.dismiss();
            confirmUninstall(module);
        });
        LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        uninstallParams.topMargin = dp(4);
        content.addView(uninstall, uninstallParams);

        sheet.setBody(content);
        sheet.show();
    }

    private void showCrashSheet(InstalledModule module) {
        CrashGuard.State state = crashGuard == null
                ? CrashGuard.State.NONE
                : crashGuard.state(module.id());
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(UiKit.rounded(this, UiKit.DANGER_SOFT, 18f));
        IconView icon = new IconView(this).type(IconView.Type.INFO);
        icon.setColor(UiKit.DANGER);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
        content.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView title = UiKit.text(
                this,
                state.blocked() ? "已进入安全模式" : "上次启动失败",
                20f,
                UiKit.INK,
                true
        );
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(15);
        content.addView(title, titleParams);

        TextView detail = UiKit.text(
                this,
                "连续 2 次启动失败后，Docklet 会停止自动打开这个模块。你可以重试，或清除数据后再试。",
                12f,
                UiKit.MUTED,
                false
        );
        detail.setLineSpacing(dp(2), 1.25f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(7);
        content.addView(detail, detailParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(UiKit.rounded(this, UiKit.SURFACE_SOFT, 18f));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        panelParams.topMargin = dp(16);
        content.addView(panel, panelParams);

        addInfoRow(panel, "模块", module.name() + " · " + module.id(), false);
        addInfoRow(panel, "失败次数", String.valueOf(state.count()), false);
        String when = state.lastFailure() > 0L
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(state.lastFailure()))
                : "未知";
        addInfoRow(panel, "最近失败", when, false);
        addInfoRow(panel, "失败原因", state.reason().isEmpty() ? "未记录" : state.reason(), true);

        TextView retry = UiKit.primaryButton(this, "重试启动");
        retry.setOnClickListener(view -> {
            sheet.dismiss();
            launchModule(module);
        });
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        retryParams.topMargin = dp(18);
        content.addView(retry, retryParams);

        TextView clearData = UiKit.secondaryButton(this, "清除模块数据");
        clearData.setOnClickListener(view -> {
            sheet.dismiss();
            confirmClearData(module);
        });
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        clearParams.topMargin = dp(8);
        content.addView(clearData, clearParams);

        TextView uninstall = UiKit.textButton(this, "卸载模块", UiKit.DANGER);
        uninstall.setOnClickListener(view -> {
            sheet.dismiss();
            confirmUninstall(module);
        });
        LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        uninstallParams.topMargin = dp(2);
        content.addView(uninstall, uninstallParams);

        sheet.setBody(content);
        sheet.show();
    }

    private void confirmClearData(InstalledModule module) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = UiKit.text(
                this,
                "清除 " + module.name() + " 的数据？",
                20f,
                UiKit.INK,
                true
        );
        content.addView(title);

        TextView detail = UiKit.text(
                this,
                "会删除 data/" + module.id() + " 下的数据库和 files/ 文件，模块代码保留。",
                12f,
                UiKit.MUTED,
                false
        );
        detail.setLineSpacing(dp(2), 1.25f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(12);
        content.addView(detail, detailParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);

        TextView cancel = UiKit.secondaryButton(this, "取消");
        cancel.setOnClickListener(view -> sheet.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView confirm = UiKit.text(this, "清除数据", 14f, Color.WHITE, true);
        confirm.setGravity(Gravity.CENTER);
        confirm.setBackground(UiKit.ripple(UiKit.rounded(this, UiKit.DANGER, 16f)));
        confirm.setPadding(dp(16), dp(13), dp(16), dp(13));
        confirm.setClickable(true);
        confirm.setFocusable(true);
        confirm.setOnClickListener(view -> {
            sheet.dismiss();
            performClearData(module);
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        confirmParams.leftMargin = dp(10);
        actions.addView(confirm, confirmParams);

        sheet.setBody(content);
        sheet.show();
    }

    private void performClearData(InstalledModule module) {
        if (moduleAwaitingResult) {
            stopActiveModule(() -> performClearData(module));
            return;
        }
        Dialog progress = showProgress("正在清除数据", "data/" + module.id());
        storage.close(module.id());
        io.execute(() -> {
            try {
                manager.deleteData(module.id());
                if (crashGuard != null) crashGuard.clear(module.id());
                main.post(() -> {
                    dismissQuietly(progress);
                    showHome();
                    showSnack("已清除 " + module.name() + " 的数据");
                });
            } catch (ModuleException error) {
                main.post(() -> {
                    dismissQuietly(progress);
                    showErrorSheet("清除失败", error.getCode(), friendly(error.getCode(), error.getMessage()));
                });
            }
        });
    }

    private void addInfoRow(LinearLayout panel, String label, String value, boolean last) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(14), dp(11));
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView key = UiKit.text(this, label, 11f, UiKit.MUTED, false);
        row.addView(key, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView val = UiKit.text(this, value, 11.5f, UiKit.INK, false);
        val.setGravity(Gravity.END);
        val.setMaxLines(2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(val, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        if (!last) {
            View divider = new View(this);
            divider.setBackgroundColor(0xFFE9E0D3);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            );
            dividerParams.leftMargin = dp(14);
            dividerParams.rightMargin = dp(14);
            panel.addView(divider, dividerParams);
        }
    }

    private void confirmRollback(InstalledModule module) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = UiKit.text(
                this,
                "回滚到 v" + module.previousVersion() + "？",
                20f,
                UiKit.INK,
                true
        );
        content.addView(title);

        TextView detail = UiKit.text(
                this,
                "current 指针会回到上一版。当前版本目录会保留，模块数据不会改动。",
                12f,
                UiKit.MUTED,
                false
        );
        detail.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(8);
        content.addView(detail, detailParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);

        TextView cancel = UiKit.secondaryButton(this, "取消");
        cancel.setOnClickListener(view -> sheet.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        TextView confirm = UiKit.primaryButton(this, "回滚");
        confirm.setOnClickListener(view -> {
            sheet.dismiss();
            performRollback(module);
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        confirmParams.leftMargin = dp(10);
        actions.addView(confirm, confirmParams);

        sheet.setBody(content);
        sheet.show();
    }

    private void confirmUninstall(InstalledModule module) {
        confirmUninstall(module, null);
    }

    private void confirmUninstall(
            InstalledModule module,
            java.util.function.Consumer<List<InstalledModule>> after
    ) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(UiKit.rounded(this, 0xFFF3E1DC, 13f));
        IconView trash = new IconView(this).type(IconView.Type.TRASH);
        trash.setColor(0xFF7A4B43);
        tile.addView(trash, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(
                this, "卸载「" + module.name() + "」？", 17f, UiKit.INK, true
        ));
        TextView sub = UiKit.text(this, "模块会从设备上移除", 12f, UiKit.MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(2);
        titles.addView(sub, subParams);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        head.addView(titles, titlesParams);
        content.addView(head);

        TextView detail = UiKit.text(
                this,
                "模块文件会从 modules/" + module.id() + " 删除，下面可以选择是否一起删除数据。",
                12.5f,
                UiKit.MUTED,
                false
        );
        detail.setLineSpacing(dp(2), 1.2f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(8);
        content.addView(detail, detailParams);

        CheckBox deleteData = new CheckBox(this);
        deleteData.setText("");
        deleteData.setChecked(true);
        deleteData.setButtonTintList(ColorStateList.valueOf(UiKit.ACCENT));
        deleteData.setPadding(0, 0, 0, 0);
        deleteData.setClickable(false);
        deleteData.setFocusable(false);

        LinearLayout dataRow = new LinearLayout(this);
        dataRow.setOrientation(LinearLayout.HORIZONTAL);
        dataRow.setGravity(Gravity.CENTER_VERTICAL);
        dataRow.setClickable(true);
        dataRow.setFocusable(true);
        dataRow.setOnClickListener(view -> {
            deleteData.setChecked(!deleteData.isChecked());
        });

        LinearLayout dataText = new LinearLayout(this);
        dataText.setOrientation(LinearLayout.VERTICAL);
        dataText.addView(UiKit.text(this, "同时删除数据", 13f, UiKit.INK, false));
        TextView path = UiKit.text(
                this,
                "data/" + module.id() + " · " + UiKit.formatBytes(module.dataBytes()),
                10.5f,
                UiKit.FAINT,
                false
        );
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pathParams.topMargin = dp(2);
        dataText.addView(path, pathParams);
        dataRow.addView(dataText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        boxParams.leftMargin = dp(20);
        dataRow.addView(deleteData, boxParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(12);
        content.addView(dataRow, rowParams);

        TextView keepNote = UiKit.text(
                this,
                "取消勾选会保留数据；重新安装同 id 模块时，旧数据仍然可用。",
                10.5f,
                UiKit.FAINT,
                false
        );
        keepNote.setLineSpacing(dp(1), 1.2f);
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        keepParams.topMargin = dp(10);
        content.addView(keepNote, keepParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);

        TextView cancel = UiKit.text(this, "取消", 14f, UiKit.INK, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(UiKit.ripple(UiKit.rounded(this, 0xFFEFE8DB, 14f)));
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setOnClickListener(view -> sheet.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView confirm = UiKit.text(this, "卸载", 14f, UiKit.DANGER, true);
        confirm.setGravity(Gravity.CENTER);
        confirm.setBackground(UiKit.ripple(UiKit.rounded(this, UiKit.DANGER_SOFT, 14f)));
        confirm.setClickable(true);
        confirm.setFocusable(true);
        confirm.setOnClickListener(view -> {
            sheet.dismiss();
            performUninstall(module, deleteData.isChecked(), after);
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        confirmParams.leftMargin = dp(8);
        actions.addView(confirm, confirmParams);

        sheet.setBody(content);
        sheet.show();
    }

    private void addModuleShortcut(InstalledModule module) {
        ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
        if (shortcutManager == null || !shortcutManager.isRequestPinShortcutSupported()) {
            showSnack("当前桌面不支持添加快捷方式");
            return;
        }
        String shortcutId = "module-" + module.id();
        for (ShortcutInfo item : shortcutManager.getPinnedShortcuts()) {
            if (shortcutId.equals(item.getId())) {
                showSnack("桌面已有这个模块");
                return;
            }
        }
        String label = module.name();
        if (label.codePointCount(0, label.length()) > 10) {
            int end = label.offsetByCodePoints(0, 10);
            label = label.substring(0, end);
        }
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_OPEN_MODULE, module.id());
        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, shortcutId)
                .setShortLabel(label)
                .setLongLabel(module.name())
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build();
        boolean requested = shortcutManager.requestPinShortcut(shortcut, null);
        showSnack(requested ? "已请求添加到桌面" : "添加桌面快捷方式失败");
    }

    private void showErrorSheet(String title, String code, String message) {
        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(UiKit.rounded(this, UiKit.DANGER_SOFT, 18f));
        IconView icon = new IconView(this).type(IconView.Type.INFO);
        icon.setColor(UiKit.DANGER);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
        content.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView titleView = UiKit.text(this, title, 20f, UiKit.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(16);
        content.addView(titleView, titleParams);

        if (code != null && !code.isEmpty()) {
            TextView codeView = UiKit.pill(this, code, UiKit.DANGER, UiKit.DANGER_SOFT);
            LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            codeParams.topMargin = dp(10);
            content.addView(codeView, codeParams);
        }

        TextView messageView = UiKit.text(
                this,
                message == null ? "" : message,
                12f,
                UiKit.MUTED,
                false
        );
        messageView.setLineSpacing(dp(2), 1.3f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(10);
        content.addView(messageView, messageParams);

        TextView close = UiKit.primaryButton(this, "知道了");
        close.setOnClickListener(view -> sheet.dismiss());

        sheet.setBody(content);
        sheet.setFooter(close);
        sheet.show();
    }

    /** Installs a catalog entry by downloading its url. */
    private void installDemoApp(
            DemoApp app,
            java.util.function.Consumer<InstalledModule> onSuccess
    ) {
        if (app.url() == null || app.url().isEmpty()) {
            showSnack("目录里没有这个模块的下载地址");
            return;
        }
        installFromUrl(app.url(), app.sha256(), app.name(), onSuccess);
    }

    private void confirmUrlInstall(String url, String sha256) {
        String host;
        try {
            Uri parsed = Uri.parse(url);
            host = parsed.getHost();
        } catch (Exception error) {
            host = null;
        }
        if (host == null || host.isEmpty()) host = "未知来源";

        SheetDialog sheet = new SheetDialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(UiKit.text(this, "确认模块来源", 21f, UiKit.INK, true));

        TextView source = UiKit.text(
                this,
                "来源：" + host,
                12f,
                UiKit.ACCENT_DEEP,
                true
        );
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sourceParams.topMargin = dp(10);
        content.addView(source, sourceParams);

        TextView warning = UiKit.text(
                this,
                "请自行确认 URL 来源可靠。不明来源的 .pkg 可能包含异常模块，导致程序异常、数据风险或安全问题。",
                11f,
                UiKit.DANGER,
                false
        );
        warning.setLineSpacing(dp(1), 1.2f);
        warning.setPadding(dp(12), dp(10), dp(12), dp(10));
        warning.setBackground(UiKit.rounded(this, UiKit.DANGER_SOFT, 13f));
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        warningParams.topMargin = dp(14);
        content.addView(warning, warningParams);

        TextView integrity = UiKit.text(
                this,
                sha256 == null || sha256.isEmpty()
                        ? "未提供 SHA-256，安装前只校验包结构。"
                        : "已提供 SHA-256，下载后会校验完整性。",
                10f,
                UiKit.MUTED,
                false
        );
        LinearLayout.LayoutParams integrityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        integrityParams.topMargin = dp(10);
        content.addView(integrity, integrityParams);

        TextView install = UiKit.primaryButton(this, "继续安装");
        install.setOnClickListener(view -> {
            sheet.dismiss();
            installFromUrl(url, sha256);
        });
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        installParams.topMargin = dp(16);
        content.addView(install, installParams);
        TextView cancel = UiKit.textButton(this, "取消", UiKit.MUTED);
        cancel.setOnClickListener(view -> sheet.dismiss());
        content.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        sheet.setBody(content);
        sheet.show();
    }

    private void installFromUrl(String url, String sha256) {
        installFromUrl(url, sha256, null, null);
    }

    private void installFromUrl(
            String url,
            String sha256,
            String label,
            java.util.function.Consumer<InstalledModule> onSuccess
    ) {
        if (moduleAwaitingResult) {
            stopActiveModule(() -> installFromUrl(url, sha256, label, onSuccess));
            return;
        }
        String host;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception error) {
            host = null;
        }
        String title = label == null || label.isEmpty() ? "正在安装模块" : "安装 " + label;
        InstallDialog dialog = InstallDialog.show(
                this,
                title,
                host == null || host.isEmpty() ? url : host
        );
        io.execute(() -> {
            ModuleException failure = null;
            // 失败自动重试一次：网络抖动很常见，手点重试反而慢。
            for (int attempt = 1; attempt <= 2 && failure == null; attempt += 1) {
                final int round = attempt;
                main.post(() -> dialog.stage(round == 1 ? "获取中…" : "重试获取中…", 0));
                File temp = null;
                try {
                    temp = PackageDownloader.downloadUrl(
                            url,
                            sha256,
                            new File(getCacheDir(), "url-packages"),
                            (received, total) -> {
                                int percent = total > 0
                                        ? (int) Math.min(100, received * 100 / total)
                                        : 0;
                                String text = total > 0
                                        ? (round == 1 ? "获取中… " : "重试获取中… ") + percent + "%"
                                        : (round == 1 ? "获取中… " : "重试获取中… ")
                                                + (received / 1024) + " KB";
                                main.post(() -> dialog.stage(text, percent));
                            }
                    );
                    final File ready = temp;
                    temp = null;
                    main.post(() -> dialog.stage("校验并安装…", 100));
                    InstalledModule module = manager.install(ready);
                    //noinspection ResultOfMethodCallIgnored
                    ready.delete();
                    main.post(() -> {
                        dialog.success("已安装 " + module.name() + " v" + module.version());
                        if (onSuccess != null) {
                            onSuccess.accept(module);
                        } else {
                            showHome();
                            showSnack("已安装 " + module.name() + " v" + module.version());
                        }
                    });
                    return;
                } catch (ModuleException error) {
                    failure = error;
                } catch (Exception error) {
                    failure = new ModuleException(
                            ModuleException.INSTALL_FAILED,
                            safeMessage(error)
                    );
                } finally {
                    if (temp != null) {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                    }
                }
            }
            ModuleException error = failure;
            main.post(() -> {
                if (error == null) return;
                if (ModuleException.VERSION_CONFLICT.equals(error.getCode())) {
                    dialog.failure("已经是最新版本了");
                } else if (ModuleException.VERSION_DOWNGRADE.equals(error.getCode())) {
                    dialog.failure("本地版本比目录里更新，不需要安装");
                } else {
                    dialog.failure("这次没有装成功，请检查网络后重试。"
                            + "如果一直失败，可以稍后再试，或联系 docklet@163.com");
                }
                if (ModuleException.INSTALL_FAILED.equals(error.getCode())
                        || ModuleException.REMOTE_HASH_MISMATCH.equals(error.getCode())) {
                    showSnack(friendly(error.getCode(), error.getMessage()));
                }
            });
        });
    }

    private void installFromUri(Uri uri) {
        installFromUri(uri, null);
    }

    private void installFromUri(Uri uri, String expectedSha256) {
        if (uri == null) return;
        if (moduleAwaitingResult) {
            stopActiveModule(() -> installFromUri(uri, expectedSha256));
            return;
        }
        Dialog progress = showProgress("正在导入模块包", "校验 module.json、入口与版本");
        io.execute(() -> {
            File temp = new File(getCacheDir(), "import-" + System.currentTimeMillis() + ".pkg");
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IOException("无法读取所选文件");
                copyLimited(in, out, ModuleManager.MAX_PACKAGE_BYTES);
                if (expectedSha256 != null && !expectedSha256.isEmpty()) {
                    String actual = sha256(temp);
                    if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
                        throw new ModuleException(
                                ModuleException.REMOTE_HASH_MISMATCH,
                                "sha256 校验失败"
                        );
                    }
                }
                InstalledModule module = manager.install(temp);
                main.post(() -> {
                    dismissQuietly(progress);
                    showHome();
                    showSnack("已安装 " + module.name() + " v" + module.version());
                });
            } catch (ModuleException error) {
                main.post(() -> {
                    dismissQuietly(progress);
                    showErrorSheet("安装失败", error.getCode(), friendly(error.getCode(), error.getMessage()));
                });
            } catch (Exception error) {
                main.post(() -> {
                    dismissQuietly(progress);
                    showErrorSheet("安装失败", "PACKAGE_CORRUPT", safeMessage(error));
                });
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        });
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }

    private void performRollback(InstalledModule module) {
        InstallDialog dialog = InstallDialog.show(
                this,
                "正在回滚",
                "v" + module.version() + " → v" + module.previousVersion()
        );
        io.execute(() -> {
            try {
                main.post(() -> dialog.stage("切换版本…", 40));
                InstalledModule rolled = manager.rollback(module.id());
                main.post(() -> {
                    dialog.stage("切换版本…", 100);
                    dialog.success("已回滚到 v" + rolled.version());
                    if (demoDetailScreen != null) {
                        demoDetailScreen.setState(
                                "已安装",
                                rolled.version(),
                                rolled.previousVersion()
                        );
                    }
                    if (inventory != null) {
                        inventory.refresh(true, modules -> refreshScreens(modules));
                    }
                });
            } catch (ModuleException error) {
                main.post(() -> dialog.failure(friendly(error.getCode(), error.getMessage())));
            }
        });
    }

    private void performUninstall(InstalledModule module, boolean deleteData) {
        performUninstall(module, deleteData, null);
    }

    private void performUninstall(
            InstalledModule module,
            boolean deleteData,
            java.util.function.Consumer<List<InstalledModule>> after
    ) {
        if (moduleAwaitingResult) {
            stopActiveModule(() -> performUninstall(module, deleteData, after));
            return;
        }
        InstallDialog dialog = InstallDialog.show(
                this,
                "卸载 " + module.name(),
                "v" + module.version() + (deleteData ? " · 同时删除数据" : " · 保留数据")
        );
        dialog.stage("正在移除…", 30);
        // The module is leaving: drop its background subscription so the
        // service stops polling an endpoint that no longer has an owner.
        DockletMessageService.unsubscribe(this, module.id());
        storage.close(module.id());
        // WebView storage belongs to the module process. Never touch WebStorage
        // from the shell process: it initializes Chromium in the shell and
        // adds tens of MB of unnecessary PSS.
        io.execute(() -> {
            try {
                manager.uninstall(module.id(), deleteData);
                ModuleCategories.clearModule(this, module.id());
                if (crashGuard != null) crashGuard.clear(module.id());
                List<InstalledModule> remaining = manager.listInstalled();
                main.post(() -> {
                    dialog.stage("正在移除…", 100);
                    dialog.success("已卸载 " + module.name());
                    if (after != null) {
                        after.accept(remaining);
                    } else {
                        refreshAfterUninstall(remaining);
                    }
                });
            } catch (ModuleException error) {
                main.post(() -> {
                    uninstallFromAdd = false;
                    dialog.failure(friendly(error.getCode(), error.getMessage()));
                });
            } catch (Exception error) {
                main.post(() -> {
                    uninstallFromAdd = false;
                    dialog.failure("卸载没有完成，请稍后再试");
                });
            }
        });
    }

    private void refreshAfterUninstall(List<InstalledModule> remaining) {
        boolean fromAdd = uninstallFromAdd;
        uninstallFromAdd = false;
        if (fromAdd) {
            detailReturnToAdd = false;
            if (addScreen != null) {
                addScreen.refreshInstalled(remaining);
                return;
            }
            showAddScreen();
            if (addScreen != null) addScreen.refreshInstalled(remaining);
            return;
        }
        if (home != null) {
            home.refreshModules(remaining);
        } else {
            showHome();
        }
    }

    private void copyLimited(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("安装包超过 50 MB");
            out.write(buffer, 0, read);
        }
    }

    private Dialog showProgress(String title, String subtitle) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(26), dp(26), dp(26), dp(24));
        GradientDrawable bg = UiKit.rounded(this, UiKit.SURFACE, 24f);
        content.setBackground(bg);
        content.setElevation(dp(8));

        ProgressBar progress = new ProgressBar(this);
        progress.getIndeterminateDrawable().setTint(UiKit.ACCENT);
        content.addView(progress, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView titleView = UiKit.text(this, title, 15f, UiKit.INK, true);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(14);
        content.addView(titleView, titleParams);

        TextView subtitleView = UiKit.text(this, subtitle, 11f, UiKit.MUTED, false);
        subtitleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(6);
        content.addView(subtitleView, subtitleParams);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.35f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(dp(292), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private void dismissQuietly(Dialog dialog) {
        if (dialog == null) return;
        try {
            if (dialog.isShowing()) dialog.dismiss();
        } catch (Exception ignored) {
            // Activity may be finishing.
        }
    }

    private void showSnack(String message) {
        if (snack == null) return;
        snack.animate().cancel();
        snack.setText(message);
        snack.setVisibility(View.VISIBLE);
        snack.setAlpha(0f);
        snack.setTranslationY(dp(8));
        snack.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .start();
        if (hideSnack != null) main.removeCallbacks(hideSnack);
        hideSnack = () -> snack.animate()
                .alpha(0f)
                .translationY(dp(8))
                .setDuration(180)
                .withEndAction(() -> snack.setVisibility(View.GONE))
                .start();
        main.postDelayed(hideSnack, 2600);
    }

    private String friendly(String code, String fallback) {
        if (code == null) return fallback;
        switch (code) {
            case ModuleException.PACKAGE_MISSING:
                return "安装包不存在。";
            case ModuleException.PACKAGE_TOO_LARGE:
                return "安装包超过大小限制。";
            case ModuleException.PACKAGE_CORRUPT:
                return "安装包损坏，或不是有效的 .pkg。";
            case ModuleException.UNSAFE_ENTRY:
                return "安装包包含不安全的路径，已拒绝。";
            case ModuleException.MANIFEST_MISSING:
                return "安装包根目录缺少 module.json。";
            case ModuleException.MANIFEST_INVALID:
                return "module.json 不合法：" + fallback;
            case ModuleException.ENTRY_MISSING:
                return "入口文件不存在，或 entry 路径不合法。";
            case ModuleException.UNSUPPORTED_SHELL:
                return "这个模块需要更新的 Docklet。";
            case ModuleException.VERSION_CONFLICT:
                return "当前已经安装了这个版本。";
            case ModuleException.VERSION_DOWNGRADE:
                return "不能降级安装；请用回滚功能切换上一版。";
            case ModuleException.INSTALL_FAILED:
                return "安装没有完成，旧版本仍然可用。";
            case ModuleException.REMOTE_CATALOG_ERROR:
                return "远程目录或下载失败：" + fallback;
            case ModuleException.REMOTE_URL_REJECTED:
                return "远程地址不合法，或 HTTPS 目录被降级到 HTTP。";
            case ModuleException.REMOTE_HASH_MISMATCH:
                return "sha256 校验失败，安装包已丢弃，当前版本未改变。";
            case ModuleException.REMOTE_ID_MISMATCH:
                return "安装包内的 id/版本与 catalog 不一致，已拒绝安装。";
            case "CAPABILITY_DENIED":
                return "模块没有声明该能力。";
            case "PERMISSION_REQUESTED":
                return "请在系统弹窗中允许通知，然后重试。";
            default:
                return fallback == null || fallback.trim().isEmpty() ? code : fallback;
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private GradientDrawable stroked(int color, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void showFatal(Exception error) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(30), dp(30), dp(30), dp(30));
        TextView title = UiKit.text(this, "Docklet 无法启动", 21f, UiKit.INK, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView detail = UiKit.text(
                this,
                safeMessage(error) + "\n\n请清除应用数据后重试；数据目录位于 "
                        + new File(getFilesDir(), "data").getAbsolutePath(),
                12f,
                UiKit.MUTED,
                false
        );
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(10);
        panel.addView(detail, detailParams);
        screenHost.removeAllViews();
        screenHost.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MODULE) {
            lastModuleReturnAt = android.os.SystemClock.uptimeMillis();
        }
        if (requestCode == REQUEST_MODULE) {
            moduleAwaitingResult = false;
            deathRetries = 0;
            main.removeCallbacks(processDeathCheck);
            main.removeCallbacks(stopTimeout);
            Runnable after = afterStop;
            afterStop = null;
            String id = activeModuleId;
            activeModuleId = null;
            if (activeSlot != null) {
                slots.release(activeSlot);
                activeSlot = null;
            }
            setActiveModule(null);
            boolean ready = data != null && data.getBooleanExtra(ModuleActivity.EXTRA_READY, false);
            boolean cancelled = data != null
                    && data.getBooleanExtra(ModuleActivity.EXTRA_CANCELLED, false);
            String reason = data == null
                    ? "模块进程已退出"
                    : data.getStringExtra(ModuleActivity.EXTRA_REASON);
            boolean wasReady = false;
            if (id != null && data == null) {
                String[] readyMarker = ModuleSessionStore.readReady(this);
                wasReady = readyMarker != null && id.equals(readyMarker[0]);
            }
            int failureCount = 0;
            if (id != null && crashGuard != null) {
                if (ready) {
                    crashGuard.onReady(id);
                } else if (cancelled) {
                    crashGuard.cancelLaunch(id);
                } else if (wasReady) {
                    // The module had already reached ready. Process death after
                    // that point is an exit, not a failed launch.
                    crashGuard.onReady(id);
                    ModuleSessionStore.clear(this);
                } else {
                    failureCount = crashGuard.recordFailure(
                            id,
                            reason == null || reason.isEmpty() ? "模块启动失败" : reason
                    );
                }
            }
            if (failureCount >= CrashGuard.BLOCK_THRESHOLD) {
                showSnack("连续启动失败，模块已进入安全模式");
            }
            String switched = data == null
                    ? null
                    : data.getStringExtra(ModuleActivity.EXTRA_NEXT_MODULE);
            String switchedSession = data == null
                    ? null
                    : data.getStringExtra(ModuleActivity.EXTRA_NEXT_SESSION);
            if (switched != null) {
                pendingOpenModuleId = switched;
                pendingSessionId = switchedSession;
            }
            if (after != null) {
                pendingModuleReturn = null;
                showHome();
                main.post(after);
            } else if (pendingOpenModuleId != null) {
                String next = pendingOpenModuleId;
                pendingOpenModuleId = null;
                showHome();
                openModuleById(next);
            } else {
                restoreModuleOrigin();
                if (!isPreloadEnabled()) {
                    // The module Activity process is cached by Android after finish.
                    // Reclaim it once the result has been delivered; otherwise the
                    // WebView PSS stays visible in 运行内存 with no active task.
                    main.removeCallbacks(reclaimIfIdle);
                    main.postDelayed(reclaimIfIdle, 800L);
                }
            }
            return;
        }
        if (requestCode == REQUEST_PICK_PACKAGE) {
            String expectedHash = pendingPackageHash;
            pendingPackageHash = null;
            if (resultCode != RESULT_OK || data == null) return;
            Uri uri = data.getData();
            if (uri == null) return;
            String name = displayName(uri);
            if (name != null && !name.toLowerCase(Locale.ROOT).endsWith(".pkg")) {
                showErrorSheet(
                        "文件格式不对",
                        "PACKAGE_CORRUPT",
                        "请选择扩展名为 .pkg 的 Docklet 模块包。"
                );
                return;
            }
            installFromUri(uri, expectedHash);
        }
    }

    private String displayName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
            // Some providers do not expose a name.
        }
        return null;
    }

    @Override
    public void onRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                showSnack("通知权限已开启，再点一次模拟消息");
                return;
            }
            showSnack("请允许通知，然后再点一次模拟消息");
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
            showSnack(granted ? "通知权限已开启" : "通知权限被拒绝");
            if (home != null) home.refreshActivityPage();
        }
    }

    /** Swipe navigation between the main sections; false falls back to taps only. */
    private static final boolean SWIPE_NAV = true;
    private float swipeStartX;
    private long swipeDownAt;
    private android.view.VelocityTracker swipeVelocity;
    private float swipeStartY;
    private boolean swipeTracking;
    private boolean swipeHandled;
    private long lastModuleReturnAt;
    private boolean swallowNextBack;
    private long lastSwipeAt;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!SWIPE_NAV) return super.dispatchTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                swipeDownAt = android.os.SystemClock.uptimeMillis();
                swipeTracking = true;
                if (swipeVelocity == null) {
                    swipeVelocity = android.view.VelocityTracker.obtain();
                } else {
                    swipeVelocity.clear();
                }
                swipeVelocity.addMovement(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (swipeVelocity != null) swipeVelocity.addMovement(event);
                break;
            case MotionEvent.ACTION_UP:
                if (swipeTracking) {
                    swipeTracking = false;
                    float dx = event.getX() - swipeStartX;
                    float dy = event.getY() - swipeStartY;
                    long held = android.os.SystemClock.uptimeMillis() - swipeDownAt;
                    float screenWidth = getResources().getDisplayMetrics().widthPixels;
                    boolean nearEdge = swipeStartX < dp(20) || swipeStartX > screenWidth - dp(20);

                    float vx = 0f;
                    float vy = 0f;
                    if (swipeVelocity != null) {
                        swipeVelocity.addMovement(event);
                        swipeVelocity.computeCurrentVelocity(1000);
                        vx = swipeVelocity.getXVelocity();
                        vy = swipeVelocity.getYVelocity();
                    }

                    boolean slowDrag = Math.abs(dx) >= dp(56)
                            && Math.abs(dx) > Math.abs(dy) * 2.2f
                            && held >= 60;
                    boolean fling = Math.abs(vx) >= dp(700)
                            && Math.abs(vx) > Math.abs(vy) * 1.8f
                            && Math.abs(dx) >= dp(28);

                    if (!nearEdge
                            && (slowDrag || fling)
                            && android.os.SystemClock.uptimeMillis() - lastSwipeAt > 320) {
                        lastSwipeAt = android.os.SystemClock.uptimeMillis();
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        handleSwipe(dx < 0 ? 1 : -1);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                swipeTracking = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    /** dir 1 = swipe left (next section), -1 = swipe right (previous). */
    private void handleSwipe(int dir) {
        if (home == null) return;
        int tab = home.activeTab();
        if (dir > 0) {
            if (tab == 0) {
                home.slideToTab(1, 1);
            } else if (tab == 1) {
                if (home.hasAppPage()) {
                    home.slideToTab(2, 1);
                } else {
                    showAddScreen();
                }
            } else if (tab == 2) {
                home.slideToTab(3, 1);
            } else {
                home.slideToTab(0, 1);
            }
        } else {
            if (tab == 0) {
                home.slideToTab(3, -1);
            } else if (tab == 1) {
                home.slideToTab(0, -1);
            } else if (tab == 2) {
                home.slideToTab(1, -1);
            } else {
                if (home.hasAppPage()) {
                    home.slideToTab(2, -1);
                } else {
                    showAddScreen();
                }
            }
        }
    }



    private void handleBack() {
        // The gesture that closed a module can land here as a second back:
        // swallow it (once, and only right after the return) so 首页 does not
        // exit the app.
        if (swallowNextBack) {
            swallowNextBack = false;
            if (android.os.SystemClock.uptimeMillis() - lastModuleReturnAt < 1200) return;
        }
        if (android.os.SystemClock.uptimeMillis() - lastModuleReturnAt < 700) {
            return;
        }
        if (addScreen != null && addScreen.handleBack()) return;
        if (overlayHost != null && overlayHost.getVisibility() == View.VISIBLE) {
            if (overlayHost.getChildCount() > 0) {
                View top = overlayHost.getChildAt(overlayHost.getChildCount() - 1);
                if (top instanceof BackStep && ((BackStep) top).onBack()) return;
            }
            // Closing a full-screen layer returns to the page it was opened
            // from; the tab must not be reset.
            closeOverlay();
            return;
        }
        if (home != null && home.activeTab() != 0) {
            home.selectTab(0);
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (home != null) home.refreshActivityPage();
        if (moduleAwaitingResult) {
            // Coming back from a module: the closing gesture can reach us as
            // one more back, which must not leave 首页.
            lastModuleReturnAt = android.os.SystemClock.uptimeMillis();
            swallowNextBack = true;
            main.removeCallbacks(processDeathCheck);
            main.postDelayed(processDeathCheck, 1200L);
        } else {
            maybePreload();
            if (!isPreloadEnabled()) {
                main.removeCallbacks(reclaimIfIdle);
                main.postDelayed(reclaimIfIdle, 1200L);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && preloadWarmed) {
            stopService(new Intent(this, SlotWarmupService.class));
            preloadWarmed = false;
        }
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL && !moduleAwaitingResult) {
            reclaimInactiveSlots();
        }
    }

    private void reclaimInactiveSlots() {
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return;
        try {
            activityManager.killBackgroundProcesses(getPackageName());
        } catch (Exception ignored) {
            // Process reclamation is best effort.
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(processDeathCheck);
        main.removeCallbacks(stopTimeout);
        if (moduleReadyReceiver != null) {
            try {
                unregisterReceiver(moduleReadyReceiver);
            } catch (Exception ignored) {
                // Receiver may already be unregistered during process teardown.
            }
            moduleReadyReceiver = null;
        }
        if (storage != null) storage.closeAll();
        if (io != null) io.shutdownNow();
        super.onDestroy();
    }
}
