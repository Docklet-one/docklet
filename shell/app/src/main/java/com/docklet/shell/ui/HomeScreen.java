package com.docklet.shell.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.docklet.platform.CrashGuard;
import com.docklet.platform.InstalledModule;
import com.docklet.shell.BuildConfig;
import com.docklet.shell.runtime.DockletMessageService;
import com.docklet.shell.runtime.FavoriteModules;
import com.docklet.shell.runtime.MessageSubscription;
import com.docklet.shell.runtime.MessageSubscriptionStore;
import com.docklet.shell.runtime.NotificationPrefs;
import com.docklet.shell.runtime.RecentModules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native shell home: modules, activity and settings in one three-tab frame. */
@SuppressLint("ViewConstructor")
public final class HomeScreen extends LinearLayout {

    public interface Listener {
        void onOpenModule(InstalledModule module);

        void onBrowseDemos();

        void onInstallFromFile();

        void onShowModuleDetails(InstalledModule module);

        void onShowInstallMenu();

        void onShowApp();

        void onFavoriteChanged();

        void onRequestNotificationPermission();

        void onStopBackground(InstalledModule module);

        void onShowMemory();

        boolean isPreloadEnabled();

        void onTogglePreload();
    }

    private final Activity activity;
    private final Listener listener;
    private final Map<String, String> glyphs;
    private final Map<String, CrashGuard.State> crashStates;
    private final List<InstalledModule> modules;
    private final FrameLayout contentHost;
    private View appPage;
    private FrameLayout runningHost;
    private List<InstalledModule> runningList = new ArrayList<>();
    private int runningIndex;
    private final LinearLayout tabbar;
    private final List<LinearLayout> tabs = new ArrayList<>();
    private final List<View> tabIcons = new ArrayList<>();
    private final List<TextView> tabLabels = new ArrayList<>();

    private EditText searchInput;
    private LinearLayout searchWrap;
    private LinearLayout listHost;
    private LinearLayout recentHost;
    private LinearLayout favoriteHost;
    private View recentSection;
    private View favoriteSection;
    private TextView countView;
    private View emptyState;
    private String query = "";
    private int activeTab;
    private boolean switchingTab;

    public HomeScreen(
            Activity activity,
            List<InstalledModule> modules,
            Map<String, String> glyphs,
            Map<String, CrashGuard.State> crashStates,
            Listener listener
    ) {
        super(activity);
        this.activity = activity;
        this.listener = listener;
        // Copy: callers may hand over unmodifiable maps, and updateData() mutates these.
        this.glyphs = glyphs == null ? new HashMap<>() : new HashMap<>(glyphs);
        this.crashStates = crashStates == null
                ? new HashMap<>()
                : new HashMap<>(crashStates);
        this.modules = modules == null ? new ArrayList<>() : new ArrayList<>(modules);
        setOrientation(VERTICAL);
        setBackgroundColor(UiKit.BG);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        contentHost = new FrameLayout(activity);
        addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        tabbar = buildTabbar();
        addView(tabbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(70)
        ));
        showTab(0);
    }

    private int dp(float value) {
        return UiKit.dp(activity, value);
    }

    private void showTab(int index) {
        if (index == 2 && appPage == null) {
            listener.onShowApp();
            return;
        }
        activeTab = index;
        contentHost.removeAllViews();
        View page;
        if (index == 1) {
            page = buildActivityPage();
        } else if (index == 2) {
            page = appPage;
        } else if (index == 3) {
            page = buildSettingsPage();
        } else {
            page = buildModulesPage();
        }
        if (page.getParent() instanceof ViewGroup) {
            ((ViewGroup) page.getParent()).removeView(page);
        }
        page.setAlpha(1f);
        page.setTranslationX(0f);
        contentHost.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        // Re-attached pages must see the current insets (keyboard included).
        page.requestApplyInsets();
        for (int i = 0; i < tabs.size(); i += 1) {
            boolean selected = i == index;
            tabs.get(i).setBackground(selected
                    ? UiKit.rounded(activity, UiKit.SURFACE_SOFT, 15f)
                    : UiKit.rounded(activity, Color.TRANSPARENT, 15f));
            View tabIcon = tabIcons.get(i);
            if (tabIcon instanceof IconView) {
                ((IconView) tabIcon).setColor(selected ? UiKit.INK : UiKit.FAINT);
            } else {
                tabIcon.setAlpha(selected ? 1f : 0.42f);
            }
            tabLabels.get(i).setTextColor(selected ? UiKit.INK : UiKit.FAINT);
        }
    }

    private LinearLayout buildTabbar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(7), dp(14), dp(8));
        bar.setBackgroundColor(Color.rgb(255, 252, 245));
        bar.setElevation(dp(8));
        addTab(bar, "首页", IconView.Type.GRID, 0);
        addTab(bar, "活动", IconView.Type.ACTIVITY, 1);
        addTab(bar, "模块", IconView.Type.DOWNLOAD, 2);
        addTab(bar, "设置", IconView.Type.SETTINGS, 3);
        return bar;
    }


    /** The dark rounded "D" mark used as the app logo. */
    private View logoMark(int sizeDp) {
        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(UiKit.gradient(activity, new int[]{0xFF3B2C1F, 0xFF241B13}, 6f));
        TextView letter = UiKit.text(activity, "D", 11f, Color.rgb(255, 248, 237), true);
        letter.setGravity(Gravity.CENTER);
        mark.addView(letter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return mark;
    }

    private void addTab(LinearLayout bar, String label, IconView.Type type, int index) {
        LinearLayout tab = new LinearLayout(activity);
        tab.setOrientation(VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setContentDescription(label);
        IconView icon = new IconView(activity).type(type);
        icon.setColor(index == 0 ? UiKit.INK : UiKit.FAINT);
        tab.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView text = UiKit.text(activity, label, 9.5f, index == 0 ? UiKit.INK : UiKit.FAINT, true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dp(4);
        tab.addView(text, textParams);
        tab.setOnClickListener(view -> showTab(index));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
        if (index > 0) params.leftMargin = dp(4);
        bar.addView(tab, params);
        tabs.add(tab);
        tabIcons.add(icon);
        tabLabels.add(text);
    }

    private ScrollView scrollPage(LinearLayout content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        content.setOrientation(VERTICAL);
        content.setPadding(dp(20), 0, dp(20), dp(28));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    /** Thin rule used inside the section divider. */
    private View sectionRule(int leftMargin, int rightMargin) {
        View line = new View(activity);
        line.setBackgroundColor(UiKit.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(1), 1f
        );
        params.leftMargin = leftMargin;
        params.rightMargin = rightMargin;
        line.setLayoutParams(params);
        return line;
    }

    /** Small label used inside the section divider. */
    private TextView sectionWord(String text) {
        TextView word = UiKit.text(activity, text, 10.5f, UiKit.FAINT, false);
        word.setIncludeFontPadding(false);
        return word;
    }

    private View buildModulesPage() {
        LinearLayout content = new LinearLayout(activity);
        ScrollView scroll = scrollPage(content);
        content.addView(buildTopbar("小坞", "DOCKLET·CN", false), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(VERTICAL);
        hero.setPadding(dp(2), dp(10), dp(2), dp(18));
        TextView meta = UiKit.label(activity, "●  已安装 · " + modules.size() + " 个模块", 9f, UiKit.ACCENT_DEEP);
        hero.addView(meta);
        SpannableString heroText = new SpannableString("你的模块，\n都在这里。");
        heroText.setSpan(
                new ForegroundColorSpan(UiKit.ACCENT),
                "你的模块，\n".length(),
                heroText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        TextView title = UiKit.text(activity, heroText, 34f, UiKit.INK, true);
        title.setLineSpacing(dp(2), 1.04f);
        title.setLetterSpacing(-0.045f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(12);
        hero.addView(title, titleParams);
        content.addView(hero);

        LinearLayout recentSectionRow = new LinearLayout(activity);
        recentSectionRow.setOrientation(HORIZONTAL);
        recentSectionRow.setGravity(Gravity.CENTER_VERTICAL);
        recentSectionRow.setPadding(dp(2), dp(0), dp(2), dp(0));
        recentSection = recentSectionRow;
        content.addView(recentSection);

        recentHost = new LinearLayout(activity);
        recentHost.setOrientation(VERTICAL);
        content.addView(recentHost);

        // Divider between 最近 and 收藏, carrying both labels:
        //   —— 最近 △ ▽ 收藏 ——
        LinearLayout favoriteSectionRow = new LinearLayout(activity);
        favoriteSectionRow.setOrientation(HORIZONTAL);
        favoriteSectionRow.setGravity(Gravity.CENTER_VERTICAL);
        favoriteSectionRow.setPadding(dp(2), dp(12), dp(2), dp(22));

        LinearLayout.LayoutParams shortRule = new LinearLayout.LayoutParams(dp(32), dp(1));
        shortRule.rightMargin = dp(8);
        View leftRule = new View(activity);
        leftRule.setBackgroundColor(UiKit.LINE);
        favoriteSectionRow.addView(leftRule, shortRule);
        LinearLayout.LayoutParams recentWord = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        favoriteSectionRow.addView(sectionWord("最近"), recentWord);
        TextView upMark = sectionWord("△");
        TextView downMark = sectionWord("▽");
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        markParams.leftMargin = dp(6);
        favoriteSectionRow.addView(upMark, markParams);
        LinearLayout.LayoutParams markParams2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        markParams2.leftMargin = dp(2);
        favoriteSectionRow.addView(downMark, markParams2);
        LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        wordParams.leftMargin = dp(6);
        favoriteSectionRow.addView(sectionWord("收藏"), wordParams);
        favoriteSectionRow.addView(sectionRule(dp(8), dp(0)));

        favoriteSection = favoriteSectionRow;
        content.addView(favoriteSection);

        favoriteHost = new LinearLayout(activity);
        favoriteHost.setOrientation(VERTICAL);
        content.addView(favoriteHost);

        TextView empty = UiKit.text(
                activity,
                "还没有模块。点底部「模块」添加。",
                11.5f,
                UiKit.MUTED,
                false
        );
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(2), dp(40), dp(2), dp(10));
        emptyState = empty;
        content.addView(emptyState);
        rebuildList();
        return scroll;
    }

    private View buildSpotlight(InstalledModule module, boolean running) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable background = UiKit.gradient(
                activity,
                running
                        ? new int[]{0xFF27423E, 0xFF1D2D2B}
                        : new int[]{0xFF3A3128, 0xFF272018},
                23f
        );
        card.setBackground(background);
        card.setElevation(dp(5));
        card.setClickable(true);
        card.setOnClickListener(view -> listener.onOpenModule(module));

        TextView status = UiKit.label(
                activity,
                running ? "●  后台运行中" : "最近使用",
                8.5f,
                running ? Color.rgb(158, 211, 199) : Color.rgb(216, 195, 168)
        );
        card.addView(status);

        LinearLayout main = new LinearLayout(activity);
        main.setOrientation(HORIZONTAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mainParams.topMargin = dp(10);
        card.addView(main, mainParams);

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(activity, Color.argb(35, 129, 195, 178), 15f));
        IconView icon = new IconView(activity).type(iconFor(module));
        icon.setColor(Color.rgb(184, 224, 213));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        main.addView(iconWrap, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(12);
        main.addView(copy, copyParams);
        TextView name = UiKit.text(activity, module.name(), 18f, Color.WHITE, true);
        copy.addView(name);
        TextView sub = UiKit.text(
                activity,
                running ? "后台消息连接保持中" : "上次打开 · v" + module.version(),
                10.5f,
                running ? Color.rgb(134, 170, 161) : Color.rgb(184, 164, 142),
                false
        );
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(4);
        copy.addView(sub, subParams);

        TextView open = UiKit.text(activity, "打开  ›", 11f, Color.rgb(24, 39, 35), true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(13), dp(10), dp(13), dp(10));
        open.setBackground(UiKit.rounded(activity, Color.rgb(213, 236, 228), 12f));
        open.setClickable(true);
        open.setOnClickListener(view -> listener.onOpenModule(module));
        main.addView(open);
        return card;
    }

    private void rebuildList() {
        if (recentHost == null) return;
        recentHost.removeAllViews();
        List<InstalledModule> recent = recentModules();
        if (recent.isEmpty()) {
            recentSection.setVisibility(View.GONE);
            recentHost.setVisibility(View.GONE);
        } else {
            recentSection.setVisibility(View.VISIBLE);
            recentHost.setVisibility(View.VISIBLE);
            recentHost.addView(buildModuleGrid(recent, false));
        }
        if (favoriteHost == null) return;
        favoriteHost.removeAllViews();
        List<InstalledModule> favorites = favoriteModules();
        favoriteSection.setVisibility(View.VISIBLE);
        favoriteHost.setVisibility(View.VISIBLE);
        if (favorites.isEmpty()) {
            TextView emptyFavorites = UiKit.text(activity, "暂无收藏", 11f, UiKit.FAINT, false);
            emptyFavorites.setGravity(Gravity.CENTER);
            emptyFavorites.setPadding(dp(2), dp(20), dp(2), dp(10));
            favoriteHost.addView(emptyFavorites);
        } else {
            favoriteHost.addView(buildModuleGrid(favorites, true));
        }
        if (emptyState != null) {
            emptyState.setVisibility(recent.isEmpty() && favorites.isEmpty()
                    ? View.VISIBLE
                    : View.GONE);
        }
    }

    private List<InstalledModule> favoriteModules() {
        Map<String, InstalledModule> byId = new HashMap<>();
        for (InstalledModule module : modules) byId.put(module.id(), module);
        List<InstalledModule> favorites = new ArrayList<>();
        for (String id : FavoriteModules.list(activity)) {
            InstalledModule module = byId.get(id);
            if (module != null && !favorites.contains(module)) {
                favorites.add(module);
                if (favorites.size() >= 6) break;
            }
        }
        return favorites;
    }

    private List<InstalledModule> recentModules() {
        Map<String, InstalledModule> byId = new HashMap<>();
        for (InstalledModule module : modules) byId.put(module.id(), module);
        List<InstalledModule> recent = new ArrayList<>();
        for (String id : RecentModules.list(activity)) {
            InstalledModule module = byId.get(id);
            if (module != null && !recent.contains(module)) {
                recent.add(module);
                if (recent.size() >= 6) break;
            }
        }
        return recent;
    }

    private View buildModuleGrid(List<InstalledModule> items, boolean allowFavorite) {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(VERTICAL);
        int columns = 3;
        int tileHeight = 88;
        for (int index = 0; index < items.size(); index += columns) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.TOP);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index > 0) rowParams.topMargin = dp(7);
            grid.addView(row, rowParams);
            for (int column = 0; column < columns; column += 1) {
                int position = index + column;
                if (position < items.size()) {
                    View tile = buildModuleTile(items.get(position), allowFavorite);
                    LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                            0, dp(tileHeight), 1f
                    );
                    if (column > 0) tileParams.leftMargin = dp(7);
                    row.addView(tile, tileParams);
                } else {
                    View spacer = new View(activity);
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                            0, dp(tileHeight), 1f
                    );
                    if (column > 0) spacerParams.leftMargin = dp(7);
                    row.addView(spacer, spacerParams);
                }
            }
        }
        return grid;
    }

    private View buildModuleTile(InstalledModule module, boolean allowFavorite) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(VERTICAL);
        tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(4), dp(4), dp(4), dp(2));
        tile.setBackgroundColor(Color.TRANSPARENT);
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setOnClickListener(view -> listener.onOpenModule(module));
        if (allowFavorite) {
            tile.setOnLongClickListener(view -> {
                boolean added = FavoriteModules.toggle(activity, module.id());
                Toast.makeText(
                        activity,
                        added ? "已收藏 " + module.name() : "已取消收藏 " + module.name(),
                        Toast.LENGTH_SHORT
                ).show();
                listener.onFavoriteChanged();
                return true;
            });
        }

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(
                activity,
                UiKit.blend(accent(module), Color.WHITE, 0.14f),
                13f
        ));
        IconView icon = new IconView(activity).type(iconFor(module));
        icon.setColor(accent(module));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        tile.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = UiKit.text(activity, module.name(), 10.5f, UiKit.INK, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = dp(6);
        tile.addView(name, nameParams);
        return tile;
    }

    private View buildModuleRow(InstalledModule module, boolean last) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(10), dp(8), dp(10));
        row.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 0f));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> listener.onOpenModule(module));
        if (!last) {
            row.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 0f));
        }

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(activity, UiKit.blend(accent(module), Color.WHITE, 0.14f), 13f));
        IconView icon = new IconView(activity).type(iconFor(module));
        icon.setColor(accent(module));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        row.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(11);
        row.addView(copy, copyParams);
        TextView name = UiKit.text(activity, module.name(), 13f, UiKit.INK, true);
        copy.addView(name);
        TextView meta = UiKit.text(
                activity,
                metaFor(module) + "  ·  v" + module.version(),
                8.5f,
                UiKit.FAINT,
                false
        );
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(4);
        copy.addView(meta, metaParams);

        CrashGuard.State crash = crashStates.get(module.id());
        if (MessageSubscriptionStore.has(activity, module.id())) {
            row.addView(pill("后台", UiKit.TEAL, UiKit.TEAL_SOFT));
        } else if (crash != null && crash.blocked()) {
            row.addView(pill("安全模式", UiKit.DANGER, UiKit.DANGER_SOFT));
        } else if (crash != null && crash.failed()) {
            row.addView(pill("上次失败", UiKit.ACCENT_DEEP, UiKit.ACCENT_SOFT));
        }

        FrameLayout more = new FrameLayout(activity);
        more.setClickable(true);
        more.setFocusable(true);
        more.setContentDescription("模块详情");
        IconView chevron = new IconView(activity).type(IconView.Type.CHEVRON_RIGHT);
        chevron.setColor(Color.rgb(185, 173, 156));
        more.addView(chevron, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        more.setOnClickListener(view -> listener.onShowModuleDetails(module));
        row.addView(more, new LinearLayout.LayoutParams(dp(32), dp(40)));

        if (!last) {
            LinearLayout wrap = new LinearLayout(activity);
            wrap.setOrientation(VERTICAL);
            wrap.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            View divider = new View(activity);
            divider.setBackgroundColor(UiKit.LINE);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            );
            dividerParams.leftMargin = dp(64);
            wrap.addView(divider, dividerParams);
            return wrap;
        }
        return row;
    }

    private View buildTopbar(String title, String sub, boolean actions) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(14), 0, dp(8));

        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(UiKit.gradient(activity, new int[]{0xFF3B2C1F, 0xFF241B13}, 12f));
        TextView markText = UiKit.text(activity, "D", 17f, Color.rgb(255, 248, 237), true);
        markText.setGravity(Gravity.CENTER);
        mark.addView(markText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        bar.addView(mark, new LinearLayout.LayoutParams(dp(37), dp(37)));

        LinearLayout brand = new LinearLayout(activity);
        brand.setOrientation(VERTICAL);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        brandParams.leftMargin = dp(10);
        bar.addView(brand, brandParams);
        TextView name = UiKit.text(activity, title.equals("模块") ? "Docklet" : title, 18f, UiKit.INK, true);
        name.setLetterSpacing(-0.03f);
        brand.addView(name);
        TextView subText = UiKit.label(activity, sub, 8f, UiKit.FAINT);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(4);
        brand.addView(subText, subParams);

        return bar;
    }

    private FrameLayout roundButton(IconView.Type type, String desc, boolean dark, View.OnClickListener action) {
        FrameLayout button = new FrameLayout(activity);
        button.setBackground(UiKit.ripple(
                activity,
                dark ? UiKit.INK : UiKit.SURFACE,
                19f
        ));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(desc);
        IconView icon = new IconView(activity).type(type);
        icon.setColor(dark ? Color.WHITE : UiKit.INK);
        button.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        button.setOnClickListener(action);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        return button;
    }

    private View buildActivityPage() {
        LinearLayout content = new LinearLayout(activity);
        ScrollView scroll = scrollPage(content);
        content.addView(buildTopbar("活动", "SYSTEM ACTIVITY", false), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(VERTICAL);
        hero.setPadding(dp(2), dp(6), dp(2), dp(10));
        TextView title = UiKit.text(activity, "最近发生", 33f, UiKit.INK, true);
        title.setLetterSpacing(-0.05f);
        hero.addView(title);
        content.addView(hero);

        List<InstalledModule> running = runningModules();
        if (!running.isEmpty()) {
            runningList = running;
            runningIndex = 0;
            runningHost = new FrameLayout(activity);
            content.addView(runningHost, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            showRunningCard();
        } else {
            content.addView(infoCard("没有后台消息", "模块离开后不会继续接收消息；需要时可在模块内打开通知。"),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
        }

        content.addView(sectionLabel("记录", "TODAY"));
        LinearLayout records = new LinearLayout(activity);
        records.setOrientation(VERTICAL);
        records.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 18f));
        boolean anyGuard = false;
        for (InstalledModule module : modules) {
            CrashGuard.State state = crashStates.get(module.id());
            if (state == null || !state.failed()) continue;
            anyGuard = true;
            records.addView(activityRow(
                    IconView.Type.INFO,
                    "CrashGuard 已拦截启动失败",
                    module.name() + " · " + (state.blocked() ? "安全模式" : "上次失败"),
                    "查看 ›",
                    view -> listener.onShowModuleDetails(module),
                    UiKit.DANGER,
                    UiKit.DANGER_SOFT
            ));
        }
        if (!anyGuard) {
            records.addView(activityRow(
                    IconView.Type.CHECK,
                    "CrashGuard 正常",
                    "最近没有模块启动失败",
                    "",
                    null,
                    UiKit.GREEN,
                    UiKit.GREEN_SOFT
            ));
        }
        content.addView(records);
        content.addView(buildNotificationSection());
        return scroll;
    }

    private View buildNotificationSection() {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(VERTICAL);
        section.addView(sectionLabel("通知", "SYSTEM"));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 18f));
        boolean permission = notificationsEnabled();
        // Nothing to toggle until some installed module declares a notification
        // capability; a fresh shell shows a disabled row instead.
        boolean anyRequester = false;
        for (InstalledModule item : modules) {
            if (item.capabilities().contains("notify")
                    || item.capabilities().contains("background")) {
                anyRequester = true;
                break;
            }
        }
        final List<Switch> toggles = new ArrayList<>();
        final boolean[] updating = {false};
        final TextView[] permissionDetailRef = new TextView[1];

        Switch global = new Switch(activity);
        if (anyRequester) {
            // 未授予系统通知权限时，开关一律显示为关。
            global.setChecked(permission
                    && !notifyingModules().isEmpty()
                    && !"全部关闭".equals(notificationState()));
            global.setEnabled(!notifyingModules().isEmpty());
            global.setThumbTintList(ColorStateList.valueOf(UiKit.ACCENT));
            global.setTrackTintList(switchTrack(0xFFD9A262, 0xFFC9BBA8));
        } else {
            global.setChecked(false);
            global.setEnabled(false);
            global.setThumbTintList(ColorStateList.valueOf(UiKit.FAINT));
            global.setTrackTintList(switchTrack(0xFFDCD3C6, 0xFFE7DFD3));
        }
        global.setContentDescription("全部模块通知");
        global.setSwitchMinWidth(dp(64));
        global.setOnCheckedChangeListener((button, checked) -> {
            if (updating[0]) return;
            if (checked && !permission) {
                // Not granted yet: ask the system, keep the switch off until it is.
                updating[0] = true;
                button.setChecked(false);
                updating[0] = false;
                listener.onRequestNotificationPermission();
                return;
            }
            updating[0] = true;
            setAllNotifications(checked);
            for (Switch toggle : toggles) toggle.setChecked(checked);
            if (permissionDetailRef[0] != null) {
                permissionDetailRef[0].setText(notificationState());
            }
            updating[0] = false;
        });

        LinearLayout permissionRow = new LinearLayout(activity);
        permissionRow.setOrientation(HORIZONTAL);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionRow.setPadding(dp(13), dp(11), dp(13), dp(11));
        if (anyRequester && !permission) {
            permissionRow.setClickable(true);
            permissionRow.setOnClickListener(view -> listener.onRequestNotificationPermission());
        }
        FrameLayout iconWrap = new FrameLayout(activity);
        int iconBg = !anyRequester
                ? UiKit.SURFACE_SOFT
                : (permission ? UiKit.TEAL_SOFT : UiKit.DANGER_SOFT);
        iconWrap.setBackground(UiKit.rounded(activity, iconBg, 9f));
        IconView icon = new IconView(activity).type(IconView.Type.INFO);
        icon.setColor(!anyRequester
                ? UiKit.FAINT
                : (permission ? UiKit.TEAL : UiKit.DANGER));
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        permissionRow.addView(iconWrap, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout permissionCopy = new LinearLayout(activity);
        permissionCopy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams permissionCopyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        permissionCopyParams.leftMargin = dp(10);
        permissionRow.addView(permissionCopy, permissionCopyParams);
        permissionCopy.addView(UiKit.text(activity, "通知权限", 13f, UiKit.INK, true));
        String detailText;
        int detailColor;
        if (!anyRequester) {
            detailText = "未有模块申请";
            detailColor = UiKit.FAINT;
        } else if (permission) {
            detailText = notificationState();
            detailColor = UiKit.FAINT;
        } else {
            detailText = "未允许，点此打开";
            detailColor = UiKit.DANGER;
        }
        TextView permissionDetail = UiKit.text(
                activity,
                detailText,
                9.5f,
                detailColor,
                false
        );
        permissionDetailRef[0] = permissionDetail;
        LinearLayout.LayoutParams permissionDetailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        permissionDetailParams.topMargin = dp(3);
        permissionCopy.addView(permissionDetail, permissionDetailParams);
        permissionRow.addView(global);
        card.addView(permissionRow);

        View divider = new View(activity);
        divider.setBackgroundColor(UiKit.LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        );
        dividerParams.topMargin = dp(2);
        dividerParams.bottomMargin = dp(2);
        card.addView(divider, dividerParams);

        int shown = 0;
        for (InstalledModule module : modules) {
            if (!module.capabilities().contains("notify")
                    && !module.capabilities().contains("background")) {
                continue;
            }
            shown += 1;
            card.addView(notificationModuleRow(
                    module, permission, toggles,
                    () -> {
                        if (updating[0]) return;
                        updating[0] = true;
                        String state = notificationState();
                        global.setChecked(!"全部关闭".equals(state));
                        if ("全部关闭".equals(state)) {
                            clearNotifications();
                        } else {
                            startMessageServiceIfNeeded();
                        }
                        if (permissionDetailRef[0] != null) {
                            permissionDetailRef[0].setText(state);
                        }
                        updating[0] = false;
                    }
            ));
        }
        if (shown == 0) {
            TextView empty = UiKit.text(activity, "没有模块请求通知能力。", 10f, UiKit.FAINT, false);
            empty.setPadding(dp(13), dp(8), dp(13), dp(12));
            card.addView(empty);
        }
        section.addView(card);
        return section;
    }

    private View notificationModuleRow(InstalledModule module, boolean enabled,
                                      List<Switch> toggles, Runnable onChange) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(9), dp(13), dp(9));

        FrameLayout iconWrap = new FrameLayout(activity);
        int accent = accent(module);
        iconWrap.setBackground(UiKit.rounded(activity, UiKit.blend(accent, Color.WHITE, 0.14f), 10f));
        IconView icon = new IconView(activity).type(iconFor(module));
        icon.setColor(accent);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        row.addView(iconWrap, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, module.name(), 11.5f, UiKit.INK, true));
        TextView detail = UiKit.text(
                activity,
                NotificationPrefs.moduleEnabled(activity, module.id()) ? "模块消息" : "已关闭",
                8.5f,
                UiKit.FAINT,
                false
        );
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        copy.addView(detail, detailParams);

        Switch toggle = new Switch(activity);
        toggle.setChecked(enabled && NotificationPrefs.moduleEnabled(activity, module.id()));
        toggle.setEnabled(true);
        toggle.setThumbTintList(ColorStateList.valueOf(UiKit.TEAL));
        toggle.setSwitchMinWidth(dp(62));
        toggle.setTrackTintList(switchTrack(0xFF86A992, 0xFFC6CFC7));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !enabled) {
                // 系统未授权：先申请，开关保持关。
                button.setChecked(false);
                listener.onRequestNotificationPermission();
                return;
            }
            NotificationPrefs.setModuleEnabled(activity, module.id(), checked);
            detail.setText(checked ? "模块消息" : "已关闭");
            onChange.run();
        });
        toggles.add(toggle);
        row.addView(toggle);
        return row;
    }

    /** Macaron palette: pastel cards with dark text on top. */
    private static final int[][] CARD_COLORS = {
            {0xFFF7C6CF, 0xFFF0B4C0},
            {0xFFFAC3B4, 0xFFF3B0A0},
            {0xFFF6EEDF, 0xFFEDE1CC},
            {0xFFC3E4D3, 0xFFB2DAC6},
            {0xFFBFDFE8, 0xFFAED4E0},
            {0xFFD6E4BF, 0xFFC8D8AC},
            {0xFFF8ECC0, 0xFFF2E1A8},
            {0xFFD5CBE8, 0xFFC6BADF},
            {0xFFDEC9DE, 0xFFD0B7D0},
            {0xFFC6DCF2, 0xFFB4CFEC},
    };

    /** Palette order for this process: shuffled once on cold start. */
    private static int[] processColorOrder;

    private static int[] colorOrder() {
        if (processColorOrder == null) {
            int[] order = new int[CARD_COLORS.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Random random = new java.util.Random();
            for (int i = order.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }
            processColorOrder = order;
        }
        return processColorOrder;
    }

    /** Renders the current running card; tapping it moves to the next module. */
    private void showRunningCard() {
        if (runningHost == null || runningList.isEmpty()) return;
        runningHost.removeAllViews();
        int total = runningList.size();
        int index = runningIndex % total;
        // A card always sits underneath, so the flip reveals real content even
        // when only one module is running.
        int nextIndex = (index + 1) % total;
        View behind = buildRunningCard(runningList.get(nextIndex), nextIndex, total);
        behind.setOnClickListener(view -> showRunningCard());
        runningHost.addView(behind, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        View card = buildRunningCard(runningList.get(index), index, total);
        card.setOnClickListener(view -> advanceRunningCard(card));
        runningHost.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void advanceRunningCard(View card) {
        if (runningList.isEmpty()) return;
        card.setEnabled(false);
        card.setCameraDistance(dp(2400));
        card.setPivotX(card.getWidth() / 2f);
        card.setPivotY(card.getHeight() / 2f);
        card.animate()
                .rotationY(90f)
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> {
                    runningIndex = (runningIndex + 1) % runningList.size();
                    showRunningCard();
                })
                .start();
    }

    private View buildRunningCard(InstalledModule module, int colorIndex, int total) {
        int[] order = colorOrder();
        int slot = order[Math.abs(colorIndex) % order.length];
        int[] palette = CARD_COLORS[slot % CARD_COLORS.length];
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(12));
        card.setBackground(UiKit.gradient(activity, palette, 21f));
        TextView head = UiKit.label(activity, "●  正在运行   " + (colorIndex + 1) + "/" + total, 9.5f, UiKit.INK);
        card.addView(head);
        LinearLayout main = new LinearLayout(activity);
        main.setOrientation(HORIZONTAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mainParams.topMargin = dp(13);
        card.addView(main, mainParams);
        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(activity, 0x59FFFFFF, 13f));
        IconView icon = new IconView(activity).type(iconFor(module));
        icon.setColor(UiKit.INK);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        main.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(11);
        main.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, module.name(), 16f, UiKit.INK, true));
        TextView sub = UiKit.text(activity, "后台消息连接保持中", 10f, UiKit.MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(4);
        copy.addView(sub, subParams);
        TextView open = UiKit.text(activity, "打开", 9.5f, UiKit.INK, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(14), dp(8), dp(14), dp(8));
        open.setBackground(UiKit.rounded(activity, 0x66FFFFFF, 9f));
        open.setClickable(true);
        open.setOnClickListener(view -> listener.onOpenModule(module));

        TextView stop = UiKit.text(activity, "停止", 9.5f, UiKit.INK, true);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(dp(14), dp(8), dp(14), dp(8));
        stop.setBackground(UiKit.rounded(activity, 0x66FFFFFF, 9f));
        stop.setClickable(true);
        stop.setOnClickListener(view -> listener.onStopBackground(module));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(VERTICAL);
        actions.setGravity(Gravity.CENTER_HORIZONTAL);
        actions.addView(open);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        stopParams.topMargin = dp(12);
        actions.addView(stop, stopParams);
        main.addView(actions);
        return card;
    }

    public void showSettings() {
        showTab(3);
    }

    /** Switches page with a short horizontal slide (used by swipe navigation). */
    public void slideToTab(int index, int direction) {
        if (switchingTab || index == activeTab) return;
        SheetDialog.dismissOpen();
        switchingTab = true;
        View old = contentHost.getChildCount() > 0 ? contentHost.getChildAt(0) : null;
        if (old == null) {
            selectTab(index);
            switchingTab = false;
            return;
        }
        float offset = dp(26);
        old.animate()
                .translationX(direction > 0 ? -offset : offset)
                .alpha(0f)
                .setDuration(90)
                .withEndAction(() -> {
                    old.setAlpha(1f);
                    old.setTranslationX(0f);
                    selectTab(index);
                    View fresh = contentHost.getChildCount() > 0
                            ? contentHost.getChildAt(0) : null;
                    if (fresh == null) {
                        switchingTab = false;
                        return;
                    }
                    fresh.setTranslationX(direction > 0 ? offset : -offset);
                    fresh.setAlpha(0f);
                    fresh.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(110)
                            .withEndAction(() -> switchingTab = false)
                            .start();
                })
                .start();
    }

    /** Hosts the app page as a normal sibling page. */
    /** The keyboard covers the nav anyway; hiding it avoids a strip above the IME. */
    public void setTabbarVisible(boolean visible) {
        if (tabbar == null) return;
        int target = visible ? View.VISIBLE : View.GONE;
        if (tabbar.getVisibility() != target) tabbar.setVisibility(target);
    }

    public void showAppScreen(View page) {
        appPage = page;
        showTab(2);
    }

    public boolean hasAppPage() {
        return appPage != null;
    }

    public void selectTab(int index) {
        SheetDialog.dismissOpen();
        showTab(index);
    }

    public void refreshModules(List<InstalledModule> next) {
        modules.clear();
        if (next != null) modules.addAll(next);
        showTab(activeTab);
    }

    /** Refreshes data in place so the selected page (and the app page) survives. */
    public void updateData(List<InstalledModule> next, Map<String, String> nextGlyphs,
                           Map<String, CrashGuard.State> nextCrash) {
        modules.clear();
        if (next != null) modules.addAll(next);
        glyphs.clear();
        if (nextGlyphs != null) glyphs.putAll(nextGlyphs);
        crashStates.clear();
        if (nextCrash != null) crashStates.putAll(nextCrash);
        showTab(activeTab);
    }

    public int activeTab() {
        return activeTab;
    }

    private View buildSettingsPage() {
        LinearLayout content = new LinearLayout(activity);
        ScrollView scroll = scrollPage(content);
        content.addView(buildTopbar("设置", "LOCAL FIRST", false), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView intro = UiKit.text(activity, "Docklet、模块和存储。", 11.5f, UiKit.MUTED, false);
        intro.setPadding(dp(2), dp(4), dp(2), dp(4));
        content.addView(intro);

        content.addView(sectionLabel("状态", "STATUS"));
        LinearLayout status = new LinearLayout(activity);
        status.setOrientation(VERTICAL);
        status.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 18f));
        long dataBytes = 0L;
        for (InstalledModule module : modules) dataBytes += module.dataBytes();
        status.addView(settingRow(
                IconView.Type.FILE,
                "模块数据",
                modules.size() + " 个模块",
                UiKit.formatBytes(dataBytes),
                null,
                UiKit.TEAL,
                UiKit.TEAL_SOFT
        ));
        status.addView(settingRow(
                IconView.Type.NETWORK,
                "仓库线路",
                "Cloudflare · 当前线路",
                "当前",
                null,
                UiKit.TEAL,
                UiKit.TEAL_SOFT
        ));
        status.addView(settingRow(
                IconView.Type.SPARK,
                "预载槽位",
                "只预热 :m2 进程，不创建 WebView",
                listener.isPreloadEnabled() ? "已开启" : "已关闭",
                view -> listener.onTogglePreload(),
                UiKit.GOLD,
                UiKit.GOLD_SOFT
        ));
        status.addView(settingRow(
                IconView.Type.ACTIVITY,
                "运行内存",
                "Docklet、模块进程和 WebView",
                "查看",
                view -> listener.onShowMemory(),
                UiKit.GOLD,
                UiKit.GOLD_SOFT
        ));
        content.addView(status);

        content.addView(sectionLabel("关于与声明", "ABOUT"));
        LinearLayout about = new LinearLayout(activity);
        about.setOrientation(VERTICAL);
        about.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 18f));
        long aboutBytes = 0L;
        for (InstalledModule module : modules) aboutBytes += module.dataBytes();
        final long aboutDataBytes = aboutBytes;
        about.addView(settingRow(
                IconView.Type.INFO,
                "软件介绍",
                "平台说明、版本与数据",
                "›",
                view -> showAboutInfo(
                        "软件介绍",
                        "Docklet 是一个本地优先的 HTML 模块宿主。\n\n"
                                + "它不把所有能力堆进一个模块：Docklet 负责模块的安装、运行、隔离、存储和恢复；模块只提供自己的界面与业务，数据留在各自目录中。\n\n"
                                + "在线目录只负责发现和下载；模块安装后在本机运行，Docklet 本身不依赖账号，数据由模块自己管理。\n\n"
                                + "Docklet 坚持单前台 WebView，模块退出后释放页面；模块使用独立 origin 和独立 SQLite，崩溃后可恢复，卸载可清理数据。\n\n"
                                + "已安装：" + modules.size() + " 个模块\n"
                                + "模块数据：" + UiKit.formatBytes(aboutDataBytes) + "\n"
                                + "运行方式：单前台 WebView · 独立 origin\n"
                                + "数据位置：files/data/<id>/app.db\n"
                                + "Shell " + UiKit.SHELL_VERSION
                                + " · BUILD " + BuildConfig.BUILD_STAMP
                ),
                UiKit.ACCENT,
                UiKit.ACCENT_SOFT
        ));
        about.addView(settingRow(
                IconView.Type.MAIL,
                "联系我们",
                "问题反馈与复现信息",
                "›",
                view -> showContactSheet(),
                UiKit.TEAL,
                UiKit.TEAL_SOFT
        ));
        about.addView(settingRow(
                IconView.Type.SHIELD,
                "侵权处理",
                "权属证明与内容处理",
                "›",
                view -> showAboutInfo(
                        "侵权处理",
                        "模块内的资源来自网络收集、AI 生成和网友提供。\n\n"
                                + "如果你认为本应用的图标、文案、素材等侵犯了你的合法权益，请通过「联系我们」告知，并说明涉及的具体内容；我们核实后会尽快处理。"
                ),
                UiKit.TEAL,
                UiKit.TEAL_SOFT
        ));
        about.addView(settingRow(
                IconView.Type.FILE,
                "责任声明",
                "第三方模块使用说明",
                "›",
                view -> showAboutInfo(
                        "责任声明",
                        "代码能开源的尽量开源；一些简单的小模块没必要单独开源。\n\n"
                                + "如果不放心，建议先审查代码，再决定要不要安装。\n\n"
                                + "作者不对使用中出现的软硬件问题、BUG 等一切负面后果负责。"
                ),
                UiKit.MUTED,
                UiKit.SURFACE_SOFT
        ));
        content.addView(about);

        TextView foot = UiKit.label(
                activity,
                "© 2026 应用小坞 Docklet",
                8f,
                UiKit.FAINT
        );
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        footParams.topMargin = dp(20);
        content.addView(foot, footParams);

        // 构建号写在版权下面，方便对照手里的安装包是不是最新构建。
        TextView build = UiKit.label(
                activity,
                "BUILD " + BuildConfig.BUILD_STAMP,
                8f,
                UiKit.FAINT
        );
        build.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buildParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buildParams.topMargin = dp(6);
        buildParams.bottomMargin = dp(6);
        content.addView(build, buildParams);
        return scroll;
    }

    private void showContactSheet() {
        SheetDialog sheet = new SheetDialog(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(VERTICAL);
        content.addView(UiKit.text(activity, "联系我们", 20f, UiKit.INK, true));
        TextView intro = UiKit.text(
                activity,
                "有问题、建议或合作意向，欢迎通过以下方式联系：",
                12f,
                UiKit.MUTED,
                false
        );
        intro.setLineSpacing(dp(2), 1.35f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        introParams.topMargin = dp(12);
        content.addView(intro, introParams);

        content.addView(contactRow("电子邮件", "docklet@163.com", view ->
                openExternal(
                        Uri.parse("mailto:docklet@163.com"),
                        Intent.ACTION_SENDTO,
                        "没有可用的邮件应用"
                )));
        content.addView(contactDivider());
        content.addView(contactRow("QQ 群", "55839173", view -> {
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Docklet QQ 群", "55839173"));
                Toast.makeText(activity, "QQ 群号已复制", Toast.LENGTH_SHORT).show();
            }
        }));
        content.addView(contactDivider());
        content.addView(contactRow("官网", "https://docklet.cn", view ->
                openExternal(
                        Uri.parse("https://docklet.cn"),
                        Intent.ACTION_VIEW,
                        "没有可用的浏览器"
                )));
        content.addView(contactDivider());
        content.addView(contactRow("开源地址", "https://github.com/Docklet-one/docklet", view ->
                openExternal(
                        Uri.parse("https://github.com/Docklet-one/docklet"),
                        Intent.ACTION_VIEW,
                        "没有可用的浏览器"
                )));

        TextView close = UiKit.secondaryButton(activity, "关闭");
        close.setOnClickListener(view -> sheet.dismiss());
        sheet.setBody(content);
        sheet.setFooter(close);
        sheet.show();
    }

    private View contactRow(String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(11), dp(13), dp(11));
        row.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 0f));
        row.setClickable(true);
        row.setOnClickListener(click);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));
        copy.addView(UiKit.label(activity, label, 8.5f, UiKit.FAINT));
        TextView valueView = UiKit.text(activity, value, 13f, UiKit.INK, false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        valueParams.topMargin = dp(4);
        copy.addView(valueView, valueParams);

        TextView arrow = UiKit.text(activity, "↗", 14f, UiKit.MUTED, false);
        arrow.setPadding(dp(8), 0, 0, 0);
        row.addView(arrow);
        return row;
    }

    private View contactDivider() {
        View divider = new View(activity);
        divider.setBackgroundColor(UiKit.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        );
        params.leftMargin = dp(13);
        params.rightMargin = dp(13);
        divider.setLayoutParams(params);
        return divider;
    }

    private void openExternal(Uri uri, String action, String error) {
        try {
            activity.startActivity(new Intent(action, uri));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutInfo(String title, String body) {
        SheetDialog sheet = new SheetDialog(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(VERTICAL);
        content.addView(UiKit.text(activity, title, 20f, UiKit.INK, true));
        TextView text = UiKit.text(activity, body, 12f, UiKit.MUTED, false);
        text.setLineSpacing(dp(2), 1.35f);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.topMargin = dp(14);
        content.addView(text, textParams);
        TextView close = UiKit.secondaryButton(activity, "关闭");
        close.setOnClickListener(view -> sheet.dismiss());
        sheet.setBody(content);
        sheet.setFooter(close);
        sheet.show();
    }

    private View infoCard(String title, String text) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 18f));
        card.addView(UiKit.text(activity, title, 13f, UiKit.INK, true));
        TextView sub = UiKit.text(activity, text, 10.5f, UiKit.MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(5);
        card.addView(sub, subParams);
        return card;
    }

    private View sectionLabel(String title, String meta) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(18), dp(2), dp(8));
        TextView label = UiKit.label(activity, title, 8f, UiKit.MUTED);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(UiKit.label(activity, meta, 8f, UiKit.FAINT));
        return row;
    }

    private View activityRow(
            IconView.Type iconType,
            String title,
            String detail,
            String action,
            View.OnClickListener actionListener,
            int accent,
            int accentSoft
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(11), dp(13), dp(11));
        row.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 0f));
        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, accentSoft, 9f));
        IconView view = new IconView(activity).type(iconType);
        view.setColor(accent);
        icon.addView(view, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, title, 11.5f, UiKit.INK, true));
        TextView detailView = UiKit.text(activity, detail, 9.5f, UiKit.MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(4);
        copy.addView(detailView, detailParams);
        if (action != null && !action.isEmpty()) {
            TextView actionView = UiKit.text(activity, action, 8.5f, accent, true);
            actionView.setPadding(dp(4), dp(8), dp(4), dp(8));
            if (actionListener != null) {
                actionView.setClickable(true);
                actionView.setOnClickListener(actionListener);
            }
            row.addView(actionView);
        }
        return row;
    }

    private View settingRow(
            IconView.Type iconType,
            String title,
            String detail,
            String value,
            View.OnClickListener action,
            int accent,
            int accentSoft
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13), dp(11), dp(13), dp(11));
        row.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 0f));
        if (action != null) {
            row.setClickable(true);
            row.setOnClickListener(action);
        }
        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, accentSoft, 9f));
        IconView view = new IconView(activity).type(iconType);
        view.setColor(accent);
        icon.addView(view, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, title, 11.5f, UiKit.INK, true));
        TextView detailView = UiKit.text(activity, detail, 8.5f, UiKit.FAINT, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        copy.addView(detailView, detailParams);
        if (value != null && !value.isEmpty()) {
            TextView valueView = UiKit.text(activity, value, 8.5f, UiKit.FAINT, false);
            row.addView(valueView);
        }
        return row;
    }

    private TextView pill(String text, int color, int background) {
        TextView view = UiKit.text(activity, text, 8.5f, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(7), dp(4), dp(7), dp(4));
        view.setBackground(UiKit.rounded(activity, background, 99f));
        return view;
    }

    /** Every module that currently holds a message subscription. */
    private List<InstalledModule> runningModules() {
        List<MessageSubscription> subscriptions = MessageSubscriptionStore.load(activity);
        List<InstalledModule> live = new ArrayList<>();
        for (InstalledModule module : modules) {
            for (MessageSubscription item : subscriptions) {
                if (module.id().equals(item.moduleId)) { live.add(module); break; }
            }
        }
        return live;
    }

    private InstalledModule firstModule() {
        return modules.isEmpty() ? null : modules.get(0);
    }

    private int accent(InstalledModule module) {
        return UiKit.parseColor(module.accent(), UiKit.ACCENT);
    }

    private String metaFor(InstalledModule module) {
        if (module.capabilities().contains("background")) return "后台消息";
        if (module.capabilities().contains("storage")) return "SQLite";
        return "无存储";
    }

    private boolean notificationsEnabled() {
        // Android 13+：POST_NOTIFICATIONS 未授权时 areNotificationsEnabled()
        // 在部分机型上仍返回 true，必须显式查权限。
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager == null || manager.areNotificationsEnabled();
    }

    /** 重建活动页，让权限/开关状态立刻反映到界面上。 */
    public void refreshActivityPage() {
        if (activeTab == 1) showTab(1);
    }

    private List<InstalledModule> notifyingModules() {
        List<InstalledModule> result = new ArrayList<>();
        for (InstalledModule module : modules) {
            if (module.capabilities().contains("notify")
                    || module.capabilities().contains("background")) {
                result.add(module);
            }
        }
        return result;
    }

    private ColorStateList switchTrack(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{checked, unchecked}
        );
    }

    private String notificationState() {
        List<InstalledModule> items = notifyingModules();
        if (items.isEmpty()) return "全部关闭";
        int enabled = 0;
        for (InstalledModule module : items) {
            if (NotificationPrefs.moduleEnabled(activity, module.id())) enabled += 1;
        }
        if (enabled == items.size()) return "全部打开";
        if (enabled == 0) return "全部关闭";
        return "部分允许";
    }

    private void setAllNotifications(boolean enabled) {
        for (InstalledModule module : notifyingModules()) {
            NotificationPrefs.setModuleEnabled(activity, module.id(), enabled);
        }
        if (enabled) {
            startMessageServiceIfNeeded();
        } else {
            clearNotifications();
        }
    }

    private void clearNotifications() {
        NotificationManager manager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancelAll();
        activity.stopService(new Intent(activity, DockletMessageService.class));
    }

    private void startMessageServiceIfNeeded() {
        if (MessageSubscriptionStore.load(activity).isEmpty()) return;
        try {
            activity.startService(new Intent(activity, DockletMessageService.class));
        } catch (Exception ignored) {
            // Notification permission can change while the page is open.
        }
    }

    private IconView.Type iconFor(InstalledModule module) {
        String glyph = glyphs.get(module.id());
        if (glyph == null) glyph = "";
        switch (glyph) {
            case "spark":
                return IconView.Type.SPARK;
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
