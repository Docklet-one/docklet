package com.docklet.shell.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.docklet.shell.demo.DemoApp;

/** Full-screen repository detail page shown before installing a demo. */
@SuppressLint("ViewConstructor")
public final class DemoDetailScreen extends LinearLayout implements BackStep {

    public interface Listener {
        void onClose();

        void onInstall();

        void onOpen();

        void onUninstall();

        /** 回滚到上一版，仅在 previousVersion 存在时触发。 */
        void onRollback();
    }

    private final Activity activity;
    private final DemoApp app;
    private String state;
    private String installedVersion;
    private String previousVersion;
    private final Listener listener;
    private LinearLayout contentHost;
    private View actionBar;

    public DemoDetailScreen(
            Activity activity,
            DemoApp app,
            String state,
            String installedVersion,
            String previousVersion,
            Listener listener
    ) {
        super(activity);
        this.activity = activity;
        this.app = app;
        this.state = state == null ? "" : state;
        this.installedVersion = installedVersion;
        this.previousVersion = previousVersion;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(UiKit.BG);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        contentHost = new LinearLayout(activity);
        contentHost.setOrientation(VERTICAL);
        contentHost.setPadding(dp(20), 0, dp(20), dp(30));
        scroll.addView(contentHost, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        buildContent(contentHost);
        addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        actionBar = buildActionBar();
        addView(actionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    public void setState(String state, String installedVersion) {
        setState(state, installedVersion, previousVersion);
    }

    /** 安装/更新/回滚之后同步版本信息，包括“上一版”这一行。 */
    public void setState(String state, String installedVersion, String previousVersion) {
        this.state = state == null ? "" : state;
        this.installedVersion = installedVersion;
        this.previousVersion = previousVersion;
        if (contentHost != null) {
            contentHost.removeAllViews();
            buildContent(contentHost);
        }
        if (actionBar != null) {
            removeView(actionBar);
            actionBar = buildActionBar();
            addView(actionBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    @Override
    public boolean onBack() {
        listener.onClose();
        return true;
    }

    private int dp(float value) {
        return UiKit.dp(activity, value);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(8));

        FrameLayout back = new FrameLayout(activity);
        back.setBackground(UiKit.ripple(activity, UiKit.SURFACE, 19f));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        IconView icon = new IconView(activity).type(IconView.Type.BACK);
        icon.setColor(UiKit.INK);
        back.addView(icon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        back.setOnClickListener(view -> listener.onClose());
        header.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = UiKit.text(activity, "模块详情", 17f, UiKit.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titleParams.leftMargin = dp(12);
        header.addView(title, titleParams);

        TextView close = UiKit.text(activity, "关闭", 11f, UiKit.MUTED, true);
        close.setPadding(dp(8), dp(8), dp(2), dp(8));
        close.setClickable(true);
        close.setOnClickListener(view -> listener.onClose());
        header.addView(close);
        return header;
    }

    private void buildContent(LinearLayout content) {
        int accent = UiKit.parseColor(app.accent(), UiKit.ACCENT);

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(0, dp(18), 0, dp(14));

        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(UiKit.rounded(
                activity, UiKit.blend(accent, Color.WHITE, 0.14f), 18f
        ));
        IconView icon = new IconView(activity).type(iconFor(app.glyph()));
        icon.setColor(accent);
        mark.addView(icon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        hero.addView(mark, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout titleColumn = new LinearLayout(activity);
        titleColumn.setOrientation(VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titleParams.leftMargin = dp(14);
        hero.addView(titleColumn, titleParams);

        TextView name = UiKit.text(activity, app.name(), 22f, UiKit.INK, true);
        name.setLetterSpacing(-0.03f);
        name.setMaxLines(2);
        titleColumn.addView(name);
        content.addView(hero);

        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(HORIZONTAL);
        stats.setPadding(0, dp(2), 0, dp(20));
        content.addView(stats);
        addStat(stats, "当前版本", "v" + app.version());
        addStat(stats, "文件大小", app.size() > 0 ? UiKit.formatBytes(app.size()) : "—");
        addStat(stats, "更新时间", app.updatedAt().isEmpty() ? "—" : app.updatedAt());

        if (installedVersion != null && !installedVersion.isEmpty()) {
            content.addView(section("版本", "VERSION"));
            content.addView(detailLine("当前", "v" + installedVersion));
            if (previousVersion != null && !previousVersion.isEmpty()) {
                content.addView(versionActionRow(
                        "上一版",
                        "v" + previousVersion,
                        "回滚",
                        () -> listener.onRollback()
                ));
            }
            if (com.docklet.platform.SemVer.compare(installedVersion, app.version()) < 0) {
                content.addView(versionActionRow(
                        "发现更新",
                        "v" + app.version(),
                        "更新",
                        () -> listener.onInstall()
                ));
            }
        }

        String intro = app.description().isEmpty() ? app.tagline() : app.description();
        if (!intro.isEmpty()) {
            content.addView(section("简介", "ABOUT"));
            TextView description = UiKit.text(activity, intro, 12.5f, UiKit.MUTED, false);
            description.setLineSpacing(dp(2), 1.35f);
            content.addView(description);
        }

        content.addView(section("作者", "AUTHOR"));
        if (app.author().isEmpty()) {
            content.addView(detailMuted("未提供作者信息"));
        } else {
            content.addView(authorRow(
                    app.author(),
                    app.authorContact().isEmpty() ? "—" : app.authorContact()
            ));
        }

        content.addView(section("权限", "PERMISSIONS"));
        if (app.permissions().isEmpty()) {
            content.addView(detailMuted("该模块不请求系统权限"));
        } else {
            for (DemoApp.Permission permission : app.permissions()) {
                content.addView(detailLine(
                        permissionLabel(permission.name()),
                        permission.reason().isEmpty() ? "—" : permission.reason()
                ));
            }
        }

        content.addView(section("能力", "CAPABILITIES"));
        if (app.capabilities().isEmpty()) {
            content.addView(detailMuted("无额外能力"));
        } else {
            content.addView(capabilityGrid());
            content.addView(detailMuted(isInstalled()
                    ? "已按安装清单校验生效"
                    : "作者声明，安装后校验"));
        }
    }

    private void addStat(LinearLayout parent, String label, String value) {
        LinearLayout stat = new LinearLayout(activity);
        stat.setOrientation(VERTICAL);
        stat.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView caption = UiKit.text(activity, label, 10.5f, UiKit.FAINT, false);
        caption.setGravity(Gravity.CENTER);
        stat.addView(caption);
        TextView content = UiKit.text(activity, value, 12.5f, UiKit.INK, false);
        content.setGravity(Gravity.CENTER);
        content.setMaxLines(1);
        content.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.topMargin = dp(4);
        stat.addView(content, valueParams);
        parent.addView(stat, params);
    }

    private View detailLine(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView name = UiKit.text(activity, label, 12.5f, UiKit.MUTED, false);
        row.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView content = UiKit.text(activity, value, 12.5f, UiKit.INK, false);
        content.setGravity(Gravity.END);
        row.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        return row;
    }

    private TextView detailMuted(String text) {
        TextView view = UiKit.text(activity, text, 12f, UiKit.FAINT, false);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private View authorRow(String author, String contact) {
        LinearLayout wrap = new LinearLayout(activity);
        wrap.setOrientation(VERTICAL);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(11));
        TextView name = UiKit.text(activity, author, 15f, UiKit.INK, true);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        TextView contactView = UiKit.text(activity, contact, 12.5f, UiKit.MUTED, false);
        contactView.setGravity(Gravity.END);
        contactView.setMaxLines(1);
        contactView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(contactView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        wrap.addView(row);

        View divider = new View(activity);
        divider.setBackgroundColor(UiKit.LINE);
        wrap.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ));
        return wrap;
    }

    private View capabilityGrid() {
        FlowLayout grid = new FlowLayout(activity);
        grid.setGap(dp(5), dp(5));
        for (String capability : app.capabilities()) {
            grid.addView(capabilityChip(capabilityLabel(capability)));
        }
        return grid;
    }

    private TextView capabilityChip(String label) {
        TextView chip = UiKit.text(activity, "✓ " + label, 9f, UiKit.GREEN, false);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        chip.setBackground(UiKit.rounded(activity, UiKit.GREEN_SOFT, 7f));
        chip.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return chip;
    }

    private View buildActionBar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(HORIZONTAL);
        bar.setPadding(dp(20), dp(10), dp(20), dp(16));
        bar.setBackgroundColor(UiKit.BG);

        // 主按钮只表达“能不能进去”：装了就是打开，没装就是安装。
        // 更新与回滚放在版本区块里，不挤占主操作。
        boolean installed = isInstalled() || isUpdate();
        boolean hasInstalled = installed;
        TextView action = darkButton(installed ? "打开" : "安装");
        action.setOnClickListener(view -> {
            if (installed) {
                listener.onOpen();
            } else {
                listener.onInstall();
            }
        });
        bar.addView(action, hasInstalled
                ? new LinearLayout.LayoutParams(0, dp(50), 1f)
                : new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
                ));
        if (hasInstalled) {
            FrameLayout uninstall = new FrameLayout(activity);
            uninstall.setBackground(UiKit.ripple(activity, UiKit.DANGER, 12f));
            uninstall.setClickable(true);
            uninstall.setFocusable(true);
            uninstall.setContentDescription("卸载");
            IconView trash = new IconView(activity).type(IconView.Type.TRASH);
            trash.setColor(Color.WHITE);
            uninstall.addView(trash, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            uninstall.setOnClickListener(view -> listener.onUninstall());
            LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(dp(52), dp(50));
            uninstallParams.leftMargin = dp(8);
            bar.addView(uninstall, uninstallParams);
        }
        return bar;
    }

    private TextView darkButton(String label) {
        TextView button = UiKit.text(activity, label, 14f, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(UiKit.ripple(activity, UiKit.INK, 15f));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private boolean isInstalled() {
        return state.startsWith("已安装");
    }

    private boolean isUpdate() {
        return state.startsWith("可更新");
    }

    private String permissionLabel(String permission) {
        if (permission == null) return "未知权限";
        switch (permission) {
            case "notifications":
                return "通知";
            case "camera":
                return "相机";
            case "microphone":
                return "麦克风";
            case "location":
                return "位置";
            case "storage":
                return "存储";
            case "network":
                return "网络";
            default:
                return permission;
        }
    }

    private String capabilityLabel(String capability) {
        if (capability == null) return "未知能力";
        switch (capability) {
            case "storage":
                return "本地存储";
            case "files":
                return "文件读写";
            case "download":
                return "下载文件";
            case "clipboard":
                return "剪贴板";
            case "openExternal":
                return "打开外部链接";
            case "notify":
                return "通知";
            case "background":
                return "后台运行";
            case "network":
                return "网络访问";
            default:
                return capability;
        }
    }

    private View versionActionRow(
            String label,
            String value,
            String action,
            Runnable onClick
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView name = UiKit.text(activity, label, 12.5f, UiKit.MUTED, false);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        TextView version = UiKit.text(activity, value, 12.5f, UiKit.MUTED, false);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        versionParams.rightMargin = dp(12);
        row.addView(version, versionParams);
        TextView button = UiKit.text(activity, action, 12.5f, UiKit.ACCENT_DEEP, true);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setBackground(UiKit.ripple(activity, UiKit.ACCENT_SOFT, 9f));
        button.setOnClickListener(view -> onClick.run());
        row.addView(button);
        return row;
    }

    private View section(String label, String tag) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(24);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        TextView title = UiKit.text(activity, label, 10.5f, UiKit.MUTED, true);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        TextView tagView = UiKit.text(activity, tag, 8f, UiKit.FAINT, true);
        tagView.setLetterSpacing(0.1f);
        row.addView(tagView);
        return row;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(VERTICAL);
        panel.setBackground(UiKit.stroked(activity, UiKit.SURFACE, UiKit.LINE, 18f));
        panel.setPadding(dp(14), dp(2), dp(14), dp(2));
        return panel;
    }

    private View infoRow(String label, String value, int color) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        TextView name = UiKit.text(activity, label, 10.5f, UiKit.MUTED, false);
        row.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        TextView content = UiKit.text(activity, value, 10.5f, color, true);
        content.setGravity(Gravity.END);
        row.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private IconView.Type iconFor(String glyph) {
        if (glyph == null) return IconView.Type.SPARK;
        switch (glyph) {
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
            default:
                return IconView.Type.SPARK;
        }
    }
}
