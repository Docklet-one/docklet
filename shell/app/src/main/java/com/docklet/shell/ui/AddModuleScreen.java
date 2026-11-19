package com.docklet.shell.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleException;
import com.docklet.platform.SemVer;
import com.docklet.shell.demo.DemoApp;
import com.docklet.shell.runtime.ModuleCategories;
import com.docklet.shell.runtime.FavoriteModules;
import com.docklet.shell.runtime.ModuleUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full-screen add module page: examples, remote catalog and local file. */
@SuppressLint("ViewConstructor")
public final class AddModuleScreen extends FrameLayout implements BackStep {

    public interface Listener {
        void onClose();

        void onDemo(DemoApp app, String state);

        void onDemoDetails(DemoApp app, String state);

        void onRemote();

        void onPickFile(String sha256);

        void onUrl(String url, String sha256);

        void onOpenInstalled(InstalledModule module);

        void onInstalledDetails(InstalledModule module);

        void onNavigate(int tab);

        void onAddToDesktop(InstalledModule module);

        void onUninstall(InstalledModule module);

        void onOpenOnline();

        void onOpenLocalInstall();

        void onRefreshCatalog();

        void onShowFullscreenPage(View page);

        void onHideFullscreenPage();
    }

    private final Activity activity;
    private List<DemoApp> demoApps;
    private final Map<String, InstalledModule> installed = new HashMap<>();
    private final Listener listener;
    private final FrameLayout contentHost;
    private FrameLayout pageHost;
    private List<ModuleCategories.Category> pageDraft;
    private boolean pageOrderChanged;
    private LinearLayout categoryRows;
    private LinearLayout categoryAddHost;
    private boolean categoryAtLimit;
    private ScrollView categoryScroll;
    private View toolRow;
    private View onlinePill;
    private View chromeRow;
    private boolean onlineOnly;
    private LinearLayout innerColumn;
    private LinearLayout bodyRoot;
    private View editingRow;
    private final Map<String, FrameLayout> categoryIconButtons = new HashMap<>();
    private final Map<String, IconView> categoryIconViews = new HashMap<>();
    private TextView activeNotice;
    private View searchWrap;
    private EditText searchInput;
    private String query = "";
    private String category = ModuleCategories.DEFAULT_ID;
    private int activeTab;
    private LinearLayout railHost;
    private ScrollView railScroll;
    private LinearLayout paneHost;
    private final Map<String, View> railButtons = new HashMap<>();
    private final Map<String, Integer> railColors = new HashMap<>();
    private final List<String> railOrder = new ArrayList<>();
    private boolean categorySwipeConsumed;

    private static final int SIGNAL_RED = 0xFFD94841;
    private static final int DEFAULT_SOFT = 0xFFFFE8E5;
    private static final int UNCLASSIFIED_SOFT = 0xFFE5EAF2;

    public AddModuleScreen(
            Activity activity,
            List<DemoApp> demoApps,
            List<InstalledModule> modules,
            Listener listener
    ) {
        super(activity);
        this.activity = activity;
        this.demoApps = demoApps;
        this.listener = listener;
        if (modules != null) {
            for (InstalledModule module : modules) installed.put(module.id(), module);
        }
        setBackgroundColor(UiKit.BG);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        KeyboardColumn column = new KeyboardColumn(activity);
        LinearLayout inner = column.content();
        innerColumn = inner;
        inner.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        searchWrap = buildSearchBar();
        contentHost = new FrameLayout(activity);
        inner.addView(contentHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageHost = new FrameLayout(activity);
        pageHost.setVisibility(View.GONE);
        addView(pageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        showTab(0);
    }

    @Override
    public boolean onBack() {
        if (onlineOnly) {
            listener.onClose();
            return true;
        }
        if (pageHost != null && pageHost.getVisibility() == View.VISIBLE) {
            finishCategoryPage();
            return true;
        }
        return false;
    }

    /** Header for the standalone 在线应用 page: back arrow + title. */
    /** Replaces the online catalog (after a refresh) and repaints the page. */
    public void setCatalog(java.util.List<DemoApp> apps) {
        this.demoApps = apps;
        if (activeTab == 1 && railHost != null) {
            rebuildBody();
        }
    }

    private View buildOnlineHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), 0);

        LinearLayout hrow = new LinearLayout(activity);
        hrow.setOrientation(LinearLayout.HORIZONTAL);
        hrow.setGravity(Gravity.CENTER_VERTICAL);

        // The back button is the dialog-header tile: 42dp rounded block with the
        // arrow inside it.
        FrameLayout back = new FrameLayout(activity);
        back.setBackground(UiKit.ripple(activity, UiKit.ACCENT_SOFT, 13f));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        IconView arrow = new IconView(activity).type(IconView.Type.BACK);
        arrow.setColor(UiKit.ACCENT_DEEP);
        back.addView(arrow, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setOnClickListener(view -> listener.onClose());
        hrow.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "在线模块", 17f, UiKit.INK, true));
        titles.addView(UiKit.text(
                activity, "发现新模块",
                12f, UiKit.MUTED, false
        ));
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        hrow.addView(titles, titlesParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        View refresh = headerChip(IconView.Type.REFRESH, "刷新");
        refresh.setOnClickListener(view -> listener.onRefreshCatalog());
        actions.addView(refresh, actionChipSize(0));
        actions.addView(headerChip(IconView.Type.NETWORK, "切换仓库"), actionChipSize(12));
        View localInstall = headerChip(IconView.Type.DOWNLOAD, "本地安装");
        localInstall.setOnClickListener(view -> listener.onOpenLocalInstall());
        actions.addView(localInstall, actionChipSize(12));
        hrow.addView(actions);

        header.addView(hrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ));
        return header;
    }

    /** Turns this screen into the standalone 在线应用 page (own back button). */
    public void setOnlineOnly(boolean online) {
        onlineOnly = online;
        if (online) {
            showTab(1);
            // The standalone page has its own header; drop the whole row so it
            // does not leave an empty strip above the search field.
            if (chromeRow != null) {
                chromeRow.setVisibility(View.GONE);
            } else if (onlinePill != null) {
                onlinePill.setVisibility(View.GONE);
            }
            if (innerColumn != null && innerColumn.getChildCount() > 0) {
                View brand = innerColumn.getChildAt(0);
                innerColumn.removeView(brand);
                innerColumn.addView(buildOnlineHeader(), 0);
            }
        }
    }

    public void refreshInstalled(List<InstalledModule> modules) {
        installed.clear();
        if (modules != null) {
            for (InstalledModule module : modules) installed.put(module.id(), module);
        }
        rebuildBody();
    }

    private int dp(float value) {
        return UiKit.dp(activity, value);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), 0);

        LinearLayout brand = new LinearLayout(activity);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(activity);
        mark.setBackground(UiKit.gradient(activity, new int[]{0xFF3B2C1F, 0xFF241B13}, 12f));
        TextView markText = UiKit.text(activity, "D", 17f, 0xFFFFF8ED, true);
        markText.setGravity(Gravity.CENTER);
        mark.addView(markText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(37), dp(37)));

        LinearLayout brandText = new LinearLayout(activity);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        brandTextParams.leftMargin = dp(10);
        brand.addView(brandText, brandTextParams);
        TextView brandName = UiKit.text(activity, "模块", 18f, UiKit.INK, true);
        brandName.setLetterSpacing(-0.03f);
        brandText.addView(brandName);
        TextView brandSub = UiKit.label(activity, "MODULES · CATALOG", 8f, UiKit.FAINT);
        LinearLayout.LayoutParams brandSubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        brandSubParams.topMargin = dp(4);
        brandText.addView(brandSub, brandSubParams);

        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        brandParams.topMargin = dp(4);
        brandParams.bottomMargin = dp(2);
        header.addView(brand, brandParams);

        return header;
    }

    /** Top-right actions on the installed page: 本地导入 + 在线应用, icon only. */
    private View buildTopActions() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(topIconButton(IconView.Type.DOWNLOAD, "本地导入", view -> listener.onOpenLocalInstall()));
        row.addView(
                topIconButton(IconView.Type.CLOUD_DOWN, "在线模块", view -> listener.onOpenOnline()),
                arrowGap()
        );
        return row;
    }

    private View topIconButton(IconView.Type type, String description,
                               View.OnClickListener click) {
        FrameLayout button = new FrameLayout(activity);
        button.setBackground(UiKit.ripple(activity, UiKit.SURFACE_SOFT, 14f));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(description);
        button.setOnClickListener(click);
        IconView icon = new IconView(activity).type(type);
        icon.setColor(UiKit.MUTED);
        button.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return button;
    }

    private LinearLayout.LayoutParams arrowGap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
        params.leftMargin = dp(10);
        return params;
    }

    /** Square icon button used by the toolbar above the search field. */
    private View headerChip(IconView.Type type, String description) {
        FrameLayout chip = new FrameLayout(activity);
        chip.setBackground(UiKit.ripple(activity, UiKit.SURFACE_SOFT, 10f));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(description);
        IconView icon = new IconView(activity).type(type);
        icon.setColor(UiKit.MUTED);
        chip.addView(icon, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        return chip;
    }

    private LinearLayout.LayoutParams actionChipSize(int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.leftMargin = dp(leftMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams chipSize(int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
        params.leftMargin = dp(leftMarginDp);
        return params;
    }





    private View buildSearchBar() {
        FrameLayout box = new FrameLayout(activity);
        box.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 14f));

        IconView icon = new IconView(activity).type(IconView.Type.SEARCH);
        icon.setColor(UiKit.FAINT);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(17), dp(17), Gravity.START | Gravity.CENTER_VERTICAL
        );
        iconParams.leftMargin = dp(13);
        box.addView(icon, iconParams);

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(12f);
        input.setTextColor(UiKit.INK);
        input.setHintTextColor(UiKit.FAINT);
        input.setHint("搜索模块、功能…");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setPadding(dp(40), 0, dp(13), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim();
                rebuildBody();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        box.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        searchInput = input;
        return box;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // Section swipes belong to the host; this page must not steal them.
        return super.onInterceptTouchEvent(event);
    }



    private View findViewAt(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i -= 1) {
            View child = parent.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            float childX = x - child.getLeft() - child.getTranslationX();
            float childY = y - child.getTop() - child.getTranslationY();
            if (childX >= 0 && childY >= 0
                    && childX < child.getWidth() && childY < child.getHeight()) {
                return findViewAt((ViewGroup) child, childX, childY);
            }
        }
        return parent;
    }


    /** Tab index inside the app page: 0 installed, 1 online. */
    public int currentTabIndex() {
        return activeTab;
    }

    private void showTab(int index) {
        if (index < 0) index = 0;
        if (index > 1) index = 1;
        // The installed page never renders the online mode itself: that is a
        // separate page now, so route the request there instead of mixing both.
        if (index == 1 && !onlineOnly) {
            listener.onOpenOnline();
            return;
        }
        activeTab = index;
        if (toolRow != null) {
            toolRow.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        }
        category = index == 0 ? ModuleCategories.DEFAULT_ID : RECOMMENDED;
        rebuildBody();
    }

    private void rebuildBody() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        if (activeTab != 0 && activeTab != 1) activeTab = 0;
        {
            buildTwoPane();
        }
    }


    private void buildTwoPane() {
        boolean repository = activeTab == 1;
        List<ModuleCategories.Category> categories = ModuleCategories.customList(activity);

        // The page chrome (pill + search) is built once and never re-attached:
        // rebuilding it on every category change made it flash while scrolling.
        if (paneHost == null) {
            LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.HORIZONTAL);
            body.setPadding(dp(20), dp(10), dp(20), 0);

            ScrollView sideScroll = new ScrollView(activity);
            sideScroll.setVerticalScrollBarEnabled(false);
            LinearLayout side = new LinearLayout(activity);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setPadding(0, dp(6), dp(8), dp(20));
            railHost = side;
            sideScroll.addView(side, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            railScroll = sideScroll;
            body.addView(sideScroll, new LinearLayout.LayoutParams(
                    dp(64), ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout right = new LinearLayout(activity);
            right.setOrientation(LinearLayout.VERTICAL);

            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            chromeRow = topRow;
            onlinePill = buildTopActions();
            topRow.addView(onlinePill, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
            ));
            LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            topParams.bottomMargin = dp(6);
            right.addView(topRow, topParams);

            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
            );
            searchParams.bottomMargin = dp(10);
            right.addView(searchWrap, searchParams);

            paneHost = new LinearLayout(activity);
            paneHost.setOrientation(LinearLayout.VERTICAL);
            right.addView(paneHost, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ));

            body.addView(right, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
            ));
            bodyRoot = body;
            contentHost.addView(bodyRoot, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
        } else if (bodyRoot != null && bodyRoot.getParent() != contentHost) {
            // rebuildBody() cleared contentHost: put the cached body back.
            contentHost.addView(bodyRoot, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }

        // The body is built once (in installed mode), so the rail width has to
        // follow the mode on every rebuild.
        if (railScroll != null) {
            LinearLayout.LayoutParams railParams =
                    (LinearLayout.LayoutParams) railScroll.getLayoutParams();
            int want = dp(activeTab == 1 ? 88 : 64);
            if (railParams.width != want) {
                railParams.width = want;
                railScroll.setLayoutParams(railParams);
            }
        }

        railButtons.clear();
        railColors.clear();
        railOrder.clear();
        railHost.removeAllViews();
        if (repository) {
            addRepositoryCategories(railHost);
        } else {
            addInstalledCategories(railHost, categories);
        }
        // The rail is the only category order. Never re-derive it from
        // customList() after this point.
        if (!railOrder.contains(category)) {
            category = railOrder.isEmpty() ? "" : railOrder.get(0);
        }
        buildRightPane();
    }

    private void buildRightPane() {
        if (paneHost == null) return;
        boolean repository = activeTab == 1;
        paneHost.removeAllViews();
        paneHost.setPadding(0, 0, 0, 0);
        if (!repository && ModuleCategories.DEFAULT_ID.equals(category)) {
            paneHost.addView(new View(activity), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(12)
            ));
        }

        CategoryScrollView listScroll = new CategoryScrollView(activity);
        listScroll.setFillViewport(true);
        listScroll.setScrollbarFadingEnabled(false);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(2), repository ? dp(4) : 0, 0, dp(28));
        if (repository && !onlineOnly && RECOMMENDED.equals(category)) {
            LinearLayout tools = new LinearLayout(activity);
            tools.setOrientation(LinearLayout.HORIZONTAL);
            tools.setGravity(Gravity.END);
            tools.addView(headerChip(IconView.Type.REFRESH, "刷新"), chipSize(0));
            tools.addView(headerChip(IconView.Type.NETWORK, "切换仓库"), chipSize(14));
            View localInstall = headerChip(IconView.Type.DOWNLOAD, "本地安装");
            localInstall.setOnClickListener(view -> listener.onOpenLocalInstall());
            tools.addView(localInstall, chipSize(14));
            LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            toolsParams.bottomMargin = dp(12);
            list.addView(tools, toolsParams);
        }
        listScroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        FrameLayout listWrap = new FrameLayout(activity);
        listWrap.addView(listScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        paneHost.addView(listWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        if (!repository && !ModuleCategories.DEFAULT_ID.equals(category)) {
            boolean unclassified = ModuleCategories.UNCLASSIFIED_ID.equals(category);
            String titleText = unclassified
                    ? ModuleCategories.UNCLASSIFIED_NAME
                    : ModuleCategories.nameOf(activity, category);
            TextView listTitle = UiKit.text(
                    activity,
                    titleText,
                    11.5f,
                    unclassified ? SIGNAL_RED : UiKit.INK,
                    true
            );
            listTitle.setPadding(dp(2), dp(16), dp(2), unclassified ? dp(4) : dp(7));
            list.addView(listTitle);
            if (unclassified) {
                TextView hint = UiKit.text(
                        activity,
                        "新安装和未分类的模块都会显示在这里\n模块全部移走后该分类将隐藏",
                        9.5f,
                        UiKit.MUTED,
                        false
                );
                hint.setLineSpacing(dp(1.5f), 1.2f);
                hint.setPadding(dp(2), 0, dp(2), dp(9));
                list.addView(hint);
            }
        }
        if (repository) {
            buildRepositoryList(list);
        } else {
            buildInstalledList(list);
        }
    }

    private void selectCategory(String id) {
        if (switchingCategory || !railOrder.contains(id)) return;
        category = id;
        refreshRailSelection();
        buildRightPane();
    }

    private void refreshRailSelection() {
        for (Map.Entry<String, View> entry : railButtons.entrySet()) {
            View button = entry.getValue();
            boolean selected = entry.getKey().equals(category);
            if (button instanceof TextView) {
                TextView text = (TextView) button;
                text.setTextColor(selected ? UiKit.INK : UiKit.MUTED);
                text.setTypeface(null, selected
                        ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
                text.setBackground(selected
                        ? UiKit.rounded(activity, UiKit.SURFACE, 11f)
                        : UiKit.rounded(activity, Color.TRANSPARENT, 11f));
                continue;
            }
            if (!(button instanceof FrameLayout)) continue;
            FrameLayout frame = (FrameLayout) button;
            if (frame.getChildCount() == 0) continue;
            View tile = frame.getChildAt(0);
            Integer color = railColors.get(entry.getKey());
            tile.setBackground(UiKit.rounded(
                    activity,
                    selected ? UiKit.SURFACE : (color == null ? UiKit.SURFACE_SOFT : color),
                    13f
            ));
            if (tile instanceof FrameLayout && ((FrameLayout) tile).getChildCount() > 0) {
                View icon = ((FrameLayout) tile).getChildAt(0);
                if (icon instanceof IconView) {
                    boolean signpost = ModuleCategories.DEFAULT_ID.equals(entry.getKey());
                    ((IconView) icon).setColor(signpost
                            ? SIGNAL_RED
                            : selected ? UiKit.INK : UiKit.MUTED);
                }
            }
        }
    }

    private void setRailIconColor(View button, int color) {
        if (!(button instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) button;
        if (frame.getChildCount() == 0) return;
        View tile = frame.getChildAt(0);
        if (!(tile instanceof FrameLayout)) return;
        FrameLayout tileFrame = (FrameLayout) tile;
        if (tileFrame.getChildCount() == 0) return;
        View icon = tileFrame.getChildAt(0);
        if (icon instanceof IconView) ((IconView) icon).setColor(color);
    }

    private long lastCategorySwipe;
    private boolean switchingCategory;

    /**
     * The category gesture must see touches that start on a clickable module
     * row too. A ScrollView OnTouchListener does not: the row becomes the
     * touch target for ACTION_DOWN. dispatchTouchEvent runs before the child
     * gets the event.
     */
    private final class CategoryScrollView extends ScrollView {
        private float startX;
        private float startY;
        private boolean startedAtTop;
        private boolean startedAtBottom;
        private android.view.VelocityTracker tracker;

        CategoryScrollView(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getX();
                startY = event.getY();
                categorySwipeConsumed = false;
                // Ownership is fixed at DOWN: a gesture that starts inside the
                // list belongs to the list, even if it reaches an edge.
                startedAtTop = !canScrollVertically(-1);
                startedAtBottom = !canScrollVertically(1);
                if (tracker == null) tracker = android.view.VelocityTracker.obtain();
                else tracker.clear();
                tracker.addMovement(event);
                return super.dispatchTouchEvent(event);
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (tracker != null) tracker.addMovement(event);
                return super.dispatchTouchEvent(event);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dy = event.getY() - startY;
                float dyVelocity = 0f;
                float dxVelocity = 0f;
                if (tracker != null) {
                    tracker.addMovement(event);
                    tracker.computeCurrentVelocity(1000);
                    dyVelocity = tracker.getYVelocity();
                    dxVelocity = tracker.getXVelocity();
                }
                boolean atEdge = (dy < 0 && startedAtBottom) || (dy > 0 && startedAtTop);
                // Same rule as the section swipe: a slow drag needs more
                // distance, a quick flick only needs a short one.
                boolean slowDrag = Math.abs(dy) >= dp(52) && atEdge;
                boolean fling = Math.abs(dyVelocity) >= dp(700)
                        && Math.abs(dyVelocity) > Math.abs(dxVelocity) * 1.8f
                        && Math.abs(dy) >= dp(24)
                        && atEdge;
                if (action == MotionEvent.ACTION_UP
                        && (slowDrag || fling)
                        && SystemClock.uptimeMillis() - lastCategorySwipe > 320
                        && !switchingCategory) {
                    lastCategorySwipe = SystemClock.uptimeMillis();
                    switchCategoryBySwipe(dy < 0 ? 1 : -1);
                }
                categorySwipeConsumed = false;
                return super.dispatchTouchEvent(event);
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private void switchCategoryBySwipe(int delta) {
        if (railOrder.isEmpty() || switchingCategory) return;
        int index = railOrder.indexOf(category);
        if (index < 0) return;
        int next = (index + delta + railOrder.size()) % railOrder.size();
        if (next == index) return;
        category = railOrder.get(next);
        refreshRailSelection();
        animateCategorySwitch(delta);
    }

    private void animateCategorySwitch(int direction) {
        View old = paneHost;
        if (old == null) {
            buildRightPane();
            return;
        }
        float offset = dp(22);
        float oldY = direction > 0 ? -offset : offset;
        switchingCategory = true;
        old.animate()
                .translationY(oldY)
                .alpha(0f)
                .setDuration(70)
                .withEndAction(() -> {
                    buildRightPane();
                    View next = paneHost;
                    if (next == null) {
                        switchingCategory = false;
                        return;
                    }
                    next.setTranslationY(-oldY);
                    next.setAlpha(0f);
                    next.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(110)
                            .withEndAction(() -> switchingCategory = false)
                            .start();
                })
                .start();
    }

    private List<InstalledModule> unclassifiedModules() {
        List<InstalledModule> result = new ArrayList<>();
        for (InstalledModule module : installed.values()) {
            if (ModuleCategories.UNCLASSIFIED_ID.equals(
                    ModuleCategories.categoryOf(activity, module.id()))) {
                result.add(module);
            }
        }
        return result;
    }

    private List<String> repositoryKinds() {
        List<String> kinds = new ArrayList<>();
        if (demoApps != null) {
            for (DemoApp app : demoApps) {
                String kind = app.kind();
                if (kind != null && !kind.isEmpty() && !kinds.contains(kind)) kinds.add(kind);
            }
        }
        return kinds;
    }

    private void addRepositoryCategories(LinearLayout side) {
        int total = demoApps == null ? 0 : demoApps.size();
        View recommended = categoryButton(
                RECOMMENDED + "  " + total,
                RECOMMENDED.equals(category),
                view -> selectCategory(RECOMMENDED),
                null
        );
        side.addView(recommended);
        railButtons.put(RECOMMENDED, recommended);
        railOrder.add(RECOMMENDED);
        for (String item : repositoryKinds()) {
            int count = repositoryCount(item);
            View button = categoryButton(item + "  " + count, item.equals(category), view -> {
                selectCategory(item);
            }, null);
            side.addView(button);
            railButtons.put(item, button);
            railOrder.add(item);
        }
    }

    private int repositoryCount(String kind) {
        int count = 0;
        if (demoApps != null) {
            for (DemoApp app : demoApps) {
                String value = app.kind();
                if (kind.equals(value == null || value.isEmpty() ? "其他" : value)) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private void addInstalledCategories(LinearLayout side, List<ModuleCategories.Category> categories) {
        View home = categoryIconButton(
                IconView.Type.SIGNPOST,
                "首页",
                ModuleCategories.DEFAULT_ID.equals(category),
                0,
                DEFAULT_SOFT,
                view -> selectCategory(ModuleCategories.DEFAULT_ID),
                view -> {
                    openCategoryPage();
                    return true;
                }
        );
        side.addView(home);
        railButtons.put(ModuleCategories.DEFAULT_ID, home);
        railColors.put(ModuleCategories.DEFAULT_ID, DEFAULT_SOFT);
        railOrder.add(ModuleCategories.DEFAULT_ID);
        setRailIconColor(home, SIGNAL_RED);

        int unclassifiedCount = unclassifiedModules().size();
        if (unclassifiedCount > 0) {
            View inbox = categoryIconButton(
                    IconView.Type.FOLDER_QUERY,
                    ModuleCategories.UNCLASSIFIED_NAME,
                    ModuleCategories.UNCLASSIFIED_ID.equals(category),
                    0,
                    UNCLASSIFIED_SOFT,
                    view -> selectCategory(ModuleCategories.UNCLASSIFIED_ID),
                    view -> {
                        openCategoryPage();
                        return true;
                    }
            );
            side.addView(inbox);
            railButtons.put(ModuleCategories.UNCLASSIFIED_ID, inbox);
            railColors.put(ModuleCategories.UNCLASSIFIED_ID, UNCLASSIFIED_SOFT);
            railOrder.add(ModuleCategories.UNCLASSIFIED_ID);
        }
        if (!categories.isEmpty()) {
            side.addView(categoryDivider());
        }
        for (ModuleCategories.Category item : categories) {
            View button = categoryIconButton(
                    CategoryIcons.typeOf(item.icon),
                    item.name,
                    item.id.equals(category),
                    0,
                    CategoryIcons.backgroundOf(item.icon),
                    view -> selectCategory(item.id),
                    view -> {
                        openCategoryPage();
                        return true;
                    }
            );
            side.addView(button);
            railButtons.put(item.id, button);
            railColors.put(item.id, CategoryIcons.backgroundOf(item.icon));
            railOrder.add(item.id);
        }
    }

    private View categoryIconButton(IconView.Type type, String contentDescription,
                                    boolean selected, int badge, int background,
                                    View.OnClickListener click,
                                    View.OnLongClickListener hold) {
        FrameLayout button = new FrameLayout(activity);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(contentDescription);

        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(
                activity, selected ? UiKit.SURFACE : background, 13f
        ));
        IconView icon = new IconView(activity).type(type);
        icon.setColor(selected ? UiKit.INK : UiKit.MUTED);
        tile.addView(icon, new FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER));
        if (badge > 0) {
            TextView count = UiKit.label(
                    activity,
                    String.valueOf(badge),
                    8f,
                    selected ? UiKit.ACCENT_DEEP : UiKit.MUTED
            );
            count.setIncludeFontPadding(false);
            count.setPadding(dp(4), dp(1), dp(4), dp(2));
            count.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 99f));
            FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END
            );
            countParams.topMargin = dp(1);
            countParams.rightMargin = dp(1);
            tile.addView(count, countParams);
        }
        button.addView(tile, new FrameLayout.LayoutParams(
                dp(40), dp(40), Gravity.START | Gravity.CENTER_VERTICAL
        ));
        button.setOnClickListener(click);
        if (hold != null) button.setOnLongClickListener(hold);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        );
        params.topMargin = dp(4);
        button.setLayoutParams(params);
        return button;
    }

    /** Divider between the recent grid and the favourites grid. */
    private View sectionDivider() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(22));

        LinearLayout.LayoutParams shortRule = new LinearLayout.LayoutParams(dp(32), dp(1));
        shortRule.rightMargin = dp(8);
        View leftRule = new View(activity);
        leftRule.setBackgroundColor(UiKit.LINE);
        row.addView(leftRule, shortRule);

        row.addView(sectionWord("最近"));

        LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mark.leftMargin = dp(6);
        row.addView(sectionWord("△"), mark);

        LinearLayout.LayoutParams mark2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        mark2.leftMargin = dp(2);
        row.addView(sectionWord("▽"), mark2);

        LinearLayout.LayoutParams word = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        word.leftMargin = dp(6);
        row.addView(sectionWord("收藏"), word);

        LinearLayout.LayoutParams tailRule = new LinearLayout.LayoutParams(0, dp(1), 1f);
        tailRule.leftMargin = dp(8);
        View rightRule = new View(activity);
        rightRule.setBackgroundColor(UiKit.LINE);
        row.addView(rightRule, tailRule);
        return row;
    }

    private TextView sectionWord(String text) {
        TextView word = UiKit.text(activity, text, 10.5f, UiKit.FAINT, false);
        word.setIncludeFontPadding(false);
        return word;
    }

    private View categoryDivider() {
        FrameLayout wrap = new FrameLayout(activity);
        View line = new View(activity);
        line.setBackgroundColor(UiKit.LINE);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                dp(22), dp(1), Gravity.START | Gravity.CENTER_VERTICAL
        );
        lineParams.leftMargin = dp(9);
        lineParams.topMargin = dp(6);
        lineParams.bottomMargin = dp(6);
        wrap.addView(line, lineParams);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(13)
        ));
        return wrap;
    }

    private View categoryButton(String label, boolean selected, View.OnClickListener click,
                                View.OnLongClickListener hold) {
        TextView button = UiKit.text(activity, label, 10.5f,
                selected ? UiKit.INK : UiKit.MUTED, selected);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(selected
                ? UiKit.rounded(activity, UiKit.SURFACE, 11f)
                : UiKit.rounded(activity, Color.TRANSPARENT, 11f));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(click);
        if (hold != null) button.setOnLongClickListener(hold);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)
        );
        params.topMargin = dp(3);
        button.setLayoutParams(params);
        return button;
    }

    private void buildRepositoryList(LinearLayout list) {
        if (demoApps == null || demoApps.isEmpty()) {
            // Nothing preset in the app: the list only exists after a successful
            // catalog fetch.
            list.addView(emptyResult("还没有在线目录，点右上角刷新"));
            return;
        }
        int count = 0;
        for (DemoApp app : demoApps == null ? new ArrayList<DemoApp>() : demoApps) {
            if (!matches(app.kind(), app.name(), app.id(), app.description())) continue;
            addSpacedRow(list, buildDemoRow(app));
            count += 1;
        }
        if (count == 0) list.addView(emptyResult("没有匹配的模块。"));
    }

    private void buildInstalledList(LinearLayout list) {
        boolean dashboard = ModuleCategories.DEFAULT_ID.equals(category);
        if (dashboard && query.isEmpty()) {
            List<InstalledModule> recent = recentModules();
            List<InstalledModule> favorites = favoriteModules();
            ModuleCategories.sort(activity, favorites, FAVORITES_SCOPE);
            if (!recent.isEmpty()) {
                list.addView(buildRecentGrid(recent, null, "最近"));
            }
            if (!favorites.isEmpty()) {
                if (!recent.isEmpty()) {
                    list.addView(sectionDivider());
                }
                list.addView(buildRecentGrid(favorites, FAVORITES_SCOPE, "收藏"));
            }
            if (recent.isEmpty() && favorites.isEmpty()) {
                list.addView(emptyResult("还没有模块。点底部「模块」添加。"));
            }
            return;
        }

        List<InstalledModule> modules = new ArrayList<>(installed.values());
        modules.sort(Comparator.comparing(InstalledModule::name));
        List<InstalledModule> filtered = new ArrayList<>();
        for (InstalledModule module : modules) {
            String assigned = ModuleCategories.categoryOf(activity, module.id());
            if (!query.isEmpty()) {
                if (matchesName(module.name(), module.id(), module.description())) {
                    filtered.add(module);
                }
            } else if (category.equals(assigned)) {
                filtered.add(module);
            }
        }
        if (query.isEmpty() && !filtered.isEmpty()
                && ModuleCategories.isCustom(activity, category)) {
            ModuleCategories.sort(activity, filtered, category);
        }
        if (filtered.isEmpty()) {
            if (query.isEmpty() && ModuleCategories.isCustom(activity, category)) {
                LinearLayout empty = new LinearLayout(activity);
                empty.setOrientation(LinearLayout.VERTICAL);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(2), dp(34), dp(2), dp(20));
                TextView copy = UiKit.text(activity, "这个分类还没有模块", 12f, UiKit.MUTED, false);
                copy.setGravity(Gravity.CENTER);
                empty.addView(copy);
                TextView add = UiKit.secondaryButton(activity, "从在线模块添加");
                add.setOnClickListener(view -> listener.onOpenOnline());
                LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
                );
                addParams.topMargin = dp(14);
                empty.addView(add, addParams);
                list.addView(empty);
            } else {
                list.addView(emptyResult("没有匹配的模块。"));
            }
            return;
        }
        list.addView(buildRecentGrid(filtered, currentSortScope(), currentSortLabel()));
    }

    private boolean matches(String kind, String name, String id, String description) {
        if (query.isEmpty() && !RECOMMENDED.equals(category)
                && (category == null || category.isEmpty()
                || !category.equals(kind == null ? "其他" : kind))) {
            return false;
        }
        return matchesName(name, id, description);
    }

    private boolean matchesName(String name, String id, String description) {
        if (query.isEmpty()) return true;
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        return (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle))
                || (id != null && id.toLowerCase(java.util.Locale.ROOT).contains(needle))
                || (description != null && description.toLowerCase(java.util.Locale.ROOT).contains(needle));
    }

    private void showModuleMenu(InstalledModule module, String sortScope, String sortLabel) {
        SheetDialog sheet = new SheetDialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        sheet.setBody(root);
        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> sheet.dismiss());
        sheet.setFooter(cancel);
        buildModuleMenuBody(sheet, root, module, sortScope, sortLabel);
        sheet.show();
    }

    /** Rebuilds the rows in place so toggling state never re-opens the sheet. */
    private void buildModuleMenuBody(
            SheetDialog sheet,
            LinearLayout root,
            InstalledModule module,
            String sortScope,
            String sortLabel
    ) {
        root.removeAllViews();

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);
        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(
                activity, UiKit.blend(accent, Color.WHITE, 0.14f), 13f
        ));
        IconView icon = new IconView(activity).type(iconFor(glyphFor(module.id())));
        icon.setColor(accent);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        header.addView(iconWrap, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(12);
        header.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, module.name(), 17f, UiKit.INK, true));
        TextView desc = UiKit.text(activity, module.description(), 12f, UiKit.MUTED, false);
        desc.setMaxLines(1);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(2);
        copy.addView(desc, descParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.bottomMargin = dp(14);
        root.addView(header, headerParams);

        String currentCategory = ModuleCategories.nameOf(
                activity, ModuleCategories.categoryOf(activity, module.id())
        );
        if (currentCategory == null || currentCategory.isEmpty()) {
            currentCategory = ModuleCategories.DEFAULT_NAME;
        }
        root.addView(menuRow("详情", "查看模块详细信息", false, true, () -> {
            sheet.dismiss();
            DemoApp app = demoAppById(module.id());
            if (app != null) {
                listener.onDemoDetails(app, stateOf(app));
            } else {
                listener.onInstalledDetails(module);
            }
        }));
        root.addView(menuRow("分类", "当前分类 · " + currentCategory, false, true, () -> {
            sheet.dismiss();
            showCategoryPicker(module);
        }));
        boolean favorited = FavoriteModules.contains(activity, module.id());
        boolean sortable = sortScope != null;
        root.addView(menuRow(
                "排序",
                sortable ? "调整「" + sortLabel + "」里的顺序" : sortLabel + "栏目下不能排序",
                false,
                sortable,
                () -> {
                    sheet.dismiss();
                    showSortSheet(sortScope, sortLabel, module);
                }
        ));
        if (favorited) {
            root.addView(menuRow(
                    "已收藏", "已同步到首页和收藏", false, true,
                    () -> {
                        sheet.dismiss();
                        showUnfavoriteSheet(module, () -> rebuildBody());
                    }
            ));
        } else {
            root.addView(menuRow("收藏", "添加到首页和收藏", false, true, () -> {
                FavoriteModules.toggle(activity, module.id());
                rebuildBody();
                buildModuleMenuBody(sheet, root, module, sortScope, sortLabel);
            }));
        }
        if (shortcutSupported()) {
            boolean pinned = shortcutPinned(module.id());
            root.addView(menuRow(
                    "添加到桌面",
                    pinned ? "桌面已有这个模块" : "添加手机桌面快捷入口",
                    false,
                    !pinned,
                    () -> listener.onAddToDesktop(module)
            ));
        }
        root.addView(menuRow("卸载", null, true, true, () -> {
            sheet.dismiss();
            listener.onUninstall(module);
        }));
    }

    private View menuRow(
            String label,
            String hint,
            boolean danger,
            boolean enabled,
            Runnable action
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setBackground(UiKit.ripple(activity, 0xFFFFFDF9, 12f));
        row.setClickable(enabled);
        row.setFocusable(enabled);

        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        int labelColor = danger ? UiKit.DANGER : (enabled ? UiKit.INK : UiKit.FAINT);
        TextView name = UiKit.text(activity, label, 14f, labelColor, true);
        line.addView(name);
        if (hint != null && !hint.isEmpty()) {
            TextView hintView = UiKit.text(
                    activity, hint, 11f, enabled ? UiKit.MUTED : UiKit.FAINT, false
            );
            hintView.setSingleLine(true);
            hintView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            );
            hintParams.leftMargin = dp(8);
            line.addView(hintView, hintParams);
        }
        row.addView(line, new LinearLayout.LayoutParams(
                0, dp(48), 1f
        ));

        IconView chevron = new IconView(activity).type(IconView.Type.CHEVRON_RIGHT);
        chevron.setColor(danger ? UiKit.DANGER : (enabled ? UiKit.FAINT : 0xFFE2D8C8));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(14), dp(14)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        );
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);
        if (enabled) {
            row.setOnClickListener(view -> action.run());
        } else {
            row.setAlpha(0.55f);
        }
        return row;
    }

    private android.content.pm.ShortcutManager shortcutManager() {
        return (android.content.pm.ShortcutManager)
                activity.getSystemService(Context.SHORTCUT_SERVICE);
    }

    private boolean shortcutSupported() {
        android.content.pm.ShortcutManager manager = shortcutManager();
        return manager != null && manager.isRequestPinShortcutSupported();
    }

    private boolean shortcutPinned(String moduleId) {
        android.content.pm.ShortcutManager manager = shortcutManager();
        if (manager == null) return false;
        String shortcutId = "module-" + moduleId;
        for (android.content.pm.ShortcutInfo item : manager.getPinnedShortcuts()) {
            if (shortcutId.equals(item.getId())) return true;
        }
        return false;
    }

    private DemoApp demoAppById(String id) {
        if (demoApps == null) return null;
        for (DemoApp app : demoApps) {
            if (app.id().equals(id)) return app;
        }
        return null;
    }

    /** Bottom sheet that offers removing the app from home and favourites. */
    private void showUnfavoriteSheet(InstalledModule module, Runnable onChanged) {
        SheetDialog sheet = new SheetDialog(activity);
        int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(
                activity, UiKit.blend(accent, Color.WHITE, 0.14f), 13f
        ));
        IconView glyph = new IconView(activity).type(iconFor(glyphFor(module.id())));
        glyph.setColor(accent);
        tile.addView(glyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, module.name(), 17f, UiKit.INK, true));
        TextView sub = UiKit.text(activity, "已同步到首页和收藏", 12f, UiKit.MUTED, false);
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
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headParams.bottomMargin = dp(14);
        root.addView(head, headParams);

        TextView body = UiKit.text(
                activity,
                "取消收藏后，这个模块会从首页和收藏里移除。",
                12.5f,
                UiKit.MUTED,
                false
        );
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.bottomMargin = dp(18);
        root.addView(body, bodyParams);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> sheet.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView confirm = dialogButton("确认", DIALOG_BUTTON_DANGER);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        confirmParams.leftMargin = dp(8);
        confirm.setOnClickListener(view -> {
            sheet.dismiss();
            FavoriteModules.toggle(activity, module.id());
            rebuildBody();
            onChanged.run();
        });
        footer.addView(confirm, confirmParams);

        sheet.setBody(root);
        sheet.setFooter(footer);
        sheet.show();
    }

    private void showCategoryPicker(InstalledModule module) {
        List<ModuleCategories.Category> categories = ModuleCategories.customList(activity);
        if (categories.isEmpty()) {
            Toast.makeText(activity, "先长按默认图标新建分类", Toast.LENGTH_SHORT).show();
            return;
        }
        String current = ModuleCategories.categoryOf(activity, module.id());
        final String[] draft = {current};
        SheetDialog sheet = new SheetDialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(
                activity, UiKit.blend(accent, Color.WHITE, 0.14f), 13f
        ));
        IconView icon = new IconView(activity).type(iconFor(glyphFor(module.id())));
        icon.setColor(accent);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        header.addView(iconWrap, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(12);
        header.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, "移动到分类", 17f, UiKit.INK, true));
        String currentName = ModuleCategories.nameOf(activity, current);
        TextView desc = UiKit.text(
                activity,
                "当前分类 · " + (currentName == null || currentName.isEmpty()
                        ? ModuleCategories.UNCLASSIFIED_NAME : currentName),
                12f,
                UiKit.MUTED,
                false
        );
        desc.setMaxLines(1);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(2);
        copy.addView(desc, descParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headerParams.bottomMargin = dp(14);
        root.addView(header, headerParams);

        List<TextView> chips = new ArrayList<>();
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int start = 0; start < categories.size(); start += 3) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int offset = 0; offset < 3; offset += 1) {
                int index = start + offset;
                if (index < categories.size()) {
                    ModuleCategories.Category item = categories.get(index);
                    TextView chip = categoryChip(item.name);
                    chips.add(chip);
                    chip.setTag(item.id);
                    chip.setOnClickListener(view -> {
                        draft[0] = item.id;
                        refreshCategoryChips(chips, draft[0]);
                    });
                    LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                            0, dp(44), 1f
                    );
                    if (offset > 0) chipParams.leftMargin = dp(8);
                    row.addView(chip, chipParams);
                } else {
                    LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                            0, dp(44), 1f
                    );
                    if (offset > 0) spacerParams.leftMargin = dp(8);
                    row.addView(new View(activity), spacerParams);
                }
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (start > 0) rowParams.topMargin = dp(8);
            grid.addView(row, rowParams);
        }
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshCategoryChips(chips, draft[0]);

        root.addView(new View(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18)
        ));

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> sheet.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView save = dialogButton("保存", DIALOG_BUTTON_SOLID);
        save.setOnClickListener(view -> {
            if (!draft[0].equals(current)) {
                ModuleCategories.setCategory(activity, module.id(), draft[0]);
                ModuleCategories.moveToFront(activity, module.id(), draft[0]);
                rebuildBody();
            }
            sheet.dismiss();
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        saveParams.leftMargin = dp(8);
        footer.addView(save, saveParams);

        sheet.setBody(root);
        sheet.setFooter(footer);
        sheet.show();
    }

    private void showSortSheet(String scope, String label, InstalledModule focus) {
        if (scope == null) return;
        List<InstalledModule> order = modulesInScope(scope);
        if (order.isEmpty()) {
            Toast.makeText(activity, "没有可排序的模块", Toast.LENGTH_SHORT).show();
            return;
        }
        ModuleCategories.sort(activity, order, scope);
        final String[] selected = {focus != null && installed.containsKey(focus.id())
                ? focus.id()
                : order.get(0).id()};
        SheetDialog sheet = new SheetDialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(activity, UiKit.ACCENT_SOFT, 13f));
        IconView headGlyph = new IconView(activity).type(IconView.Type.LIST);
        headGlyph.setColor(DIALOG_ICON_WARM);
        tile.addView(headGlyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "模块排序", 17f, UiKit.INK, true));
        TextView scopeView = UiKit.text(
                activity, "在「" + label + "」里排序", 12f, UiKit.MUTED, false
        );
        LinearLayout.LayoutParams scopeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scopeParams.topMargin = dp(2);
        titles.addView(scopeView, scopeParams);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        head.addView(titles, titlesParams);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headParams.bottomMargin = dp(14);
        root.addView(head, headParams);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        renderSortRows(scope, rows, order, selected);

        root.addView(new View(activity), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)
        ));

        TextView close = dialogButton("完成", DIALOG_BUTTON_SOLID);
        close.setOnClickListener(view -> {
            sheet.dismiss();
            rebuildBody();
        });
        sheet.setBody(root);
        sheet.setFooter(close);
        sheet.show();
    }

    /** Sort scope of the pane currently shown: only custom categories can be sorted. */
    private String currentSortScope() {
        return ModuleCategories.isCustom(activity, category) ? category : null;
    }

    private String currentSortLabel() {
        if (ModuleCategories.isCustom(activity, category)) {
            String name = ModuleCategories.nameOf(activity, category);
            return name == null || name.isEmpty() ? "分类" : name;
        }
        if (ModuleCategories.UNCLASSIFIED_ID.equals(category)) {
            return ModuleCategories.UNCLASSIFIED_NAME;
        }
        return "最近";
    }

    /** Modules inside a sort scope: a custom category id, or the favourites list. */
    private List<InstalledModule> modulesInScope(String scope) {
        List<InstalledModule> result = new ArrayList<>();
        for (InstalledModule module : installed.values()) {
            boolean inside = FAVORITES_SCOPE.equals(scope)
                    ? FavoriteModules.contains(activity, module.id())
                    : scope.equals(ModuleCategories.categoryOf(activity, module.id()));
            if (inside) result.add(module);
        }
        result.sort(Comparator.comparing(InstalledModule::name));
        return result;
    }

    private void renderSortRows(
            String scope, LinearLayout host, List<InstalledModule> order, String[] selected
    ) {
        host.removeAllViews();
        for (int index = 0; index < order.size(); index += 1) {
            InstalledModule module = order.get(index);
            int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);
            boolean isSelected = module.id().equals(selected[0]);

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(7), dp(8), dp(7));
            row.setBackground(isSelected
                    ? UiKit.stroked(activity, UiKit.ACCENT_SOFT, 0xFFC97B33, 14f)
                    : UiKit.rounded(activity, 0xFFF7F2E8, 14f));
            row.setOnClickListener(view -> {
                selected[0] = module.id();
                renderSortRows(scope, host, order, selected);
            });

            FrameLayout iconWrap = new FrameLayout(activity);
            iconWrap.setBackground(UiKit.rounded(
                    activity, UiKit.blend(accent, Color.WHITE, 0.14f), 12f
            ));
            IconView icon = new IconView(activity).type(iconFor(glyphFor(module.id())));
            icon.setColor(accent);
            iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            row.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

            TextView name = UiKit.text(activity, module.name(), 13f, UiKit.INK, true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, dp(40), 1f
            );
            nameParams.leftMargin = dp(10);
            row.addView(name, nameParams);

            final int position = index;
            row.addView(sortTopControl(position > 0, () -> {
                if (position <= 0) return;
                selected[0] = module.id();
                order.remove(position);
                order.add(0, module);
                saveSortOrder(scope, order);
                renderSortRows(scope, host, order, selected);
            }), new LinearLayout.LayoutParams(dp(36), dp(34)));
            LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(36), dp(34));
            upParams.leftMargin = dp(12);
            row.addView(sortControl(false, order.size() > 1, () -> {
                if (order.size() < 2) return;
                selected[0] = module.id();
                int target = (position - 1 + order.size()) % order.size();
                order.remove(position);
                order.add(target, module);
                saveSortOrder(scope, order);
                renderSortRows(scope, host, order, selected);
            }), upParams);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index > 0) rowParams.topMargin = dp(6);
            host.addView(row, rowParams);
        }
    }

    private static final class SortTopIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SortTopIcon(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.scale(scale, scale);
            paint.setStrokeWidth(2.2f);
            canvas.drawLine(12f, 6f, 12f, 19.5f, paint);
            canvas.drawLine(12f, 6f, 6f, 12f, paint);
            canvas.drawLine(12f, 6f, 18f, 12f, paint);
            paint.setStrokeWidth(2f);
            canvas.drawLine(4.5f, 5f, 19.5f, 5f, paint);
            canvas.restore();
        }
    }

    private View sortTopControl(boolean enabled, Runnable action) {
        int color = enabled ? 0xFF3A6470 : 0xFFD2C8BA;
        LinearLayout control = new LinearLayout(activity);
        control.setOrientation(LinearLayout.VERTICAL);
        control.setGravity(Gravity.CENTER);
        control.setBackground(UiKit.rounded(
                activity, enabled ? 0xFFE3EFF7 : 0xFFF1ECE4, 10f
        ));
        control.setClickable(enabled);
        control.setEnabled(enabled);

        SortTopIcon icon = new SortTopIcon(activity, color);
        control.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        if (enabled) control.setOnClickListener(view -> action.run());
        return control;
    }

    private View sortControl(boolean down, boolean enabled, Runnable action) {
        FrameLayout control = new FrameLayout(activity);
        control.setClickable(enabled);
        control.setEnabled(enabled);
        control.setBackground(UiKit.rounded(
                activity, enabled ? 0xFFE5F0E8 : 0xFFF1ECE4, 10f
        ));
        SortArrowIcon icon = new SortArrowIcon(
                activity, enabled ? 0xFF52665A : 0xFFD2C8BA, down
        );
        control.addView(icon, new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.CENTER
        ));
        if (enabled) control.setOnClickListener(view -> action.run());
        return control;
    }

    private static final class SortArrowIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean down;

        SortArrowIcon(Context context, int color, boolean down) {
            super(context);
            this.down = down;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(2.2f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.scale(scale, scale);
            if (down) {
                canvas.drawLine(12f, 18.5f, 12f, 5f, paint);
                canvas.drawLine(12f, 18.5f, 6f, 12.5f, paint);
                canvas.drawLine(12f, 18.5f, 18f, 12.5f, paint);
            } else {
                canvas.drawLine(12f, 5.5f, 12f, 19f, paint);
                canvas.drawLine(12f, 5.5f, 6f, 11.5f, paint);
                canvas.drawLine(12f, 5.5f, 18f, 11.5f, paint);
            }
            canvas.restore();
        }
    }

    private void saveSortOrder(String scope, List<InstalledModule> order) {
        List<String> ids = new ArrayList<>();
        for (InstalledModule module : order) ids.add(module.id());
        ModuleCategories.saveOrder(activity, scope, ids);
    }

    private TextView categoryChip(String label) {
        TextView chip = UiKit.text(activity, label, 13f, UiKit.INK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setPadding(dp(6), 0, dp(6), 0);
        return chip;
    }

    private void refreshCategoryChips(List<TextView> chips, String selectedId) {
        for (TextView chip : chips) {
            boolean selected = selectedId.equals(chip.getTag());
            // Same language as the icon grid: soft tint plus an accent ring,
            // instead of a solid dark fill.
            chip.setTextColor(selected ? 0xFF8A5A2B : UiKit.INK);
            chip.setBackground(selected
                    ? UiKit.stroked(activity, UiKit.ACCENT_SOFT, 0xFFC97B33, 14f)
                    : UiKit.rounded(activity, UiKit.SURFACE_SOFT, 14f));
        }
    }

    private void showAddToCategoryDialog() {
        List<InstalledModule> candidates = new ArrayList<>();
        for (InstalledModule module : installed.values()) {
            if (!category.equals(ModuleCategories.categoryOf(activity, module.id()))) {
                candidates.add(module);
            }
        }
        if (candidates.isEmpty()) {
            Toast.makeText(activity, "没有可归类模块", Toast.LENGTH_SHORT).show();
            return;
        }
        candidates.sort(Comparator.comparing(InstalledModule::name));
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i += 1) labels[i] = candidates.get(i).name();
        new AlertDialog.Builder(activity)
                .setTitle("添加模块到分类")
                .setItems(labels, (dialog, which) -> {
                    InstalledModule module = candidates.get(which);
                    ModuleCategories.setCategory(activity, module.id(), category);
                    ModuleCategories.moveToFront(activity, module.id(), category);
                    rebuildBody();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** Centered alert: icon header, body, two equal buttons. */
    private void showAlertDialog(String title, String sub, String message, String confirmLabel,
                                 IconView.Type icon, boolean danger, Runnable action) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(20));
        root.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 20f));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(
                activity, danger ? DIALOG_TILE_DANGER : UiKit.ACCENT_SOFT, 13f
        ));
        IconView glyph = new IconView(activity).type(icon);
        glyph.setColor(danger ? DIALOG_ICON_DANGER : DIALOG_ICON_WARM);
        tile.addView(glyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, title, 17f, UiKit.INK, true));
        if (sub != null && !sub.isEmpty()) {
            TextView subView = UiKit.text(activity, sub, 12f, UiKit.MUTED, false);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subParams.topMargin = dp(2);
            titles.addView(subView, subParams);
        }
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        head.addView(titles, titlesParams);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView body = UiKit.text(activity, "", 12.5f, UiKit.MUTED, false);
        body.setText(emphasized(message));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(12);
        bodyParams.bottomMargin = dp(18);
        root.addView(body, bodyParams);

        final Dialog[] holder = new Dialog[1];
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> holder[0].dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView confirm = dialogButton(
                confirmLabel, danger ? DIALOG_BUTTON_DANGER : DIALOG_BUTTON_SOLID
        );
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        confirmParams.leftMargin = dp(8);
        confirm.setOnClickListener(view -> {
            holder[0].dismiss();
            action.run();
        });
        buttons.addView(confirm, confirmParams);
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ));

        holder[0] = centerDialog(root, false);
    }

    /** Pseudo category id used as the sort scope of the favourites grid. */
    private static final String FAVORITES_SCOPE = "favorites";
    private static final String RECOMMENDED = "推荐";

    private static final int DIALOG_TILE_DANGER = 0xFFF3E1DC;
    private static final int DIALOG_ICON_WARM = 0xFF8A5A2B;
    private static final int DIALOG_ICON_DANGER = 0xFF7A4B43;
    private static final int DIALOG_BUTTON_GHOST = 0;
    private static final int DIALOG_BUTTON_SOLID = 1;
    private static final int DIALOG_BUTTON_DANGER = 2;

    private TextView dialogButton(String label, int kind) {
        int color;
        Drawable background;
        if (kind == DIALOG_BUTTON_SOLID) {
            color = Color.WHITE;
            background = UiKit.ripple(UiKit.rounded(activity, UiKit.ACCENT, 14f));
        } else if (kind == DIALOG_BUTTON_DANGER) {
            color = UiKit.DANGER;
            background = UiKit.ripple(UiKit.rounded(activity, UiKit.DANGER_SOFT, 14f));
        } else {
            color = UiKit.INK;
            background = UiKit.ripple(UiKit.rounded(activity, 0xFFEFE8DB, 14f));
        }
        TextView button = UiKit.text(activity, label, 14f, color, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        // Keep the height on the button itself: parents that only set width
        // (like a sheet footer) would otherwise collapse it onto the text.
        button.setMinHeight(dp(44));
        button.setPadding(dp(18), 0, dp(18), 0);
        return button;
    }

    /** Turns **bold** markers into real bold spans for dialog copy. */
    private CharSequence emphasized(String raw) {
        if (raw == null || !raw.contains("**")) return raw;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] parts = raw.split("\\*\\*");
        for (int i = 0; i < parts.length; i += 1) {
            int start = builder.length();
            builder.append(parts[i]);
            if (i % 2 == 1 && builder.length() > start) {
                builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private Dialog centerDialog(LinearLayout content, boolean aboveKeyboard) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(aboveKeyboard ? Gravity.BOTTOM : Gravity.CENTER);
            window.setDimAmount(0.38f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        dialog.show();
        if (window != null) {
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.86f);
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = aboveKeyboard ? Gravity.BOTTOM : Gravity.CENTER;
            params.y = aboveKeyboard ? dp(12) : 0;
            params.width = width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
            window.setAttributes(params);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (aboveKeyboard) {
                View decor = window.getDecorView();
                decor.setOnApplyWindowInsetsListener((view, insets) -> {
                    int ime;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                    } else {
                        ime = insets.getSystemWindowInsetBottom();
                    }
                    WindowManager.LayoutParams attrs = window.getAttributes();
                    attrs.y = ime + dp(12);
                    window.setAttributes(attrs);
                    return insets;
                });
                decor.requestApplyInsets();
            }
        }
        return dialog;
    }

    /** Full-screen local install page (URL download or a local .pkg). */
    /** Built by the host: the local install page is a full-screen layer. */
    public View buildLocalInstallPage(Runnable onBack) {

        KeyboardColumn page = new KeyboardColumn(activity);
        page.setBackgroundColor(UiKit.BG);
        page.setClickable(true);
        LinearLayout content = page.content();

        // Header follows the shared dialog header: back arrow + 42dp icon tile
        // + 17sp title + 12sp subtitle.
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(6));

        FrameLayout back = new FrameLayout(activity);
        back.setBackground(UiKit.ripple(activity, UiKit.ACCENT_SOFT, 13f));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        IconView backIcon = new IconView(activity).type(IconView.Type.BACK);
        backIcon.setColor(UiKit.ACCENT_DEEP);
        back.addView(backIcon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setOnClickListener(view -> onBack.run());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "本地安装", 17f, UiKit.INK, true));
        titles.addView(UiKit.text(
                activity, "从链接或本地文件安装模块",
                12f, UiKit.MUTED, false
        ));
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        header.addView(titles, titlesParams);

        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(8), dp(20), dp(28));
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        body.addView(UiKit.text(activity, "从链接安装", 15f, UiKit.INK, true));
        TextView sub = UiKit.text(
                activity, "粘贴 .pkg 下载地址，下载完成后自动安装", 11.5f, UiKit.MUTED, false
        );
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(4);
        subParams.bottomMargin = dp(12);
        body.addView(sub, subParams);

        EditText url = new EditText(activity);
        url.setSingleLine(false);
        url.setMinLines(2);
        url.setMaxLines(3);
        url.setGravity(Gravity.TOP | Gravity.START);
        url.setTextSize(13f);
        url.setTextColor(UiKit.INK);
        url.setHint("http://192.168.1.10:8791/app.pkg");
        url.setHintTextColor(UiKit.FAINT);
        url.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        url.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 14f));
        url.setPadding(dp(14), dp(12), dp(14), dp(12));
        body.addView(url, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)
        ));

        TextView error = UiKit.text(activity, "", 11.5f, UiKit.DANGER, false);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errorParams.topMargin = dp(8);
        error.setVisibility(View.GONE);
        body.addView(error, errorParams);

        TextView install = dialogButton("下载并安装", DIALOG_BUTTON_SOLID);
        install.setOnClickListener(view -> {
            String link = url.getText().toString().trim();
            if (!link.startsWith("https://") && !link.startsWith("http://")) {
                error.setText("请输入 http:// 或 https:// 开头的地址");
                error.setVisibility(View.VISIBLE);
                return;
            }
            hideKeyboard(url);
            onBack.run();
            listener.onUrl(link, "");
        });
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        );
        installParams.topMargin = dp(14);
        body.addView(install, installParams);

        TextView or = UiKit.text(activity, "或", 11f, UiKit.FAINT, false);
        or.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams orParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        orParams.topMargin = dp(20);
        orParams.bottomMargin = dp(14);
        body.addView(or, orParams);

        body.addView(menuRow("选择本地 .pkg", "从文件管理器选择安装包", false, true, () -> {
            hideKeyboard(url);
            onBack.run();
            listener.onPickFile("");
        }));

        TextView note = UiKit.text(
                activity,
                "请自行确认安装包来源可靠。不明来源的模块包可能导致程序异常、"
                        + "数据风险或安全问题。",
                10.5f,
                UiKit.DANGER,
                false
        );
        note.setLineSpacing(dp(1), 1.25f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        noteParams.topMargin = dp(16);
        body.addView(note, noteParams);

        return page;
    }

    private void openCategoryPage() {
        if (pageHost == null) return;
        boolean reopening = pageHost.getVisibility() == View.VISIBLE;
        List<ModuleCategories.Category> categories = ModuleCategories.customList(activity);
        final List<ModuleCategories.Category> draft = new ArrayList<>(categories);
        pageDraft = draft;
        pageOrderChanged = false;
        categoryIconButtons.clear();
        categoryIconViews.clear();

        KeyboardColumn page = new KeyboardColumn(activity);
        page.setBackgroundColor(UiKit.BG);
        page.setClickable(true);
        LinearLayout pageContent = page.content();

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), 0);
        FrameLayout headerRow = new FrameLayout(activity);
        FrameLayout back = new FrameLayout(activity);
        back.setBackground(UiKit.ripple(activity, UiKit.ACCENT_SOFT, 13f));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("返回");
        IconView backIcon = new IconView(activity).type(IconView.Type.BACK);
        backIcon.setColor(UiKit.ACCENT_DEEP);
        back.addView(backIcon, new FrameLayout.LayoutParams(
                dp(20), dp(20), Gravity.CENTER
        ));
        back.setOnClickListener(view -> finishCategoryPage());
        headerRow.addView(back, new FrameLayout.LayoutParams(
                dp(38), dp(38), Gravity.START | Gravity.CENTER_VERTICAL
        ));
        TextView pageTitle = UiKit.text(activity, "分类管理", 17f, UiKit.INK, true);
        pageTitle.setGravity(Gravity.CENTER);
        headerRow.addView(pageTitle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        header.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ));
        pageContent.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), 0);
        pageContent.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        categoryScroll = scroll;
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        categoryRows = rows;
        rows.setPadding(0, dp(8), 0, dp(4));
        scroll.addView(rows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        body.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        LinearLayout.LayoutParams builtinParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        builtinParams.bottomMargin = dp(10);
        rows.addView(builtinCategoryRow(), builtinParams);
        if (categories.isEmpty()) {
            TextView empty = UiKit.text(activity, "还没有分类。点下方新建分类。", 11f, UiKit.FAINT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(4), dp(12), dp(4), dp(12));
            rows.addView(empty);
        }
        for (ModuleCategories.Category item : categories) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(7), dp(10), dp(7));
            row.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 16f));
            row.setTag(item.id);

            TextView notice = buildNotice();
            LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            noticeParams.bottomMargin = dp(6);
            row.addView(notice, noticeParams);

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout iconButton = new FrameLayout(activity);
            iconButton.setBackground(UiKit.rounded(
                    activity, CategoryIcons.backgroundOf(item.icon), 10f
            ));
            iconButton.setClickable(true);
            iconButton.setFocusable(true);
            iconButton.setContentDescription("选择图标");
            IconView categoryIcon = new IconView(activity)
                    .type(CategoryIcons.typeOf(item.icon));
            categoryIcon.setColor(UiKit.INK);
            iconButton.addView(categoryIcon, new FrameLayout.LayoutParams(
                    dp(17), dp(17), Gravity.CENTER
            ));
            iconButton.setOnClickListener(view -> showCategoryIconPicker(item));
            categoryIconButtons.put(item.id, iconButton);
            categoryIconViews.put(item.id, categoryIcon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            iconParams.rightMargin = dp(8);
            content.addView(iconButton, iconParams);

            TextView name = UiKit.text(activity, item.name, 12.5f, UiKit.INK, true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(dp(2), 0, 0, 0);
            content.addView(name, new LinearLayout.LayoutParams(
                    0, dp(30), 1f
            ));

            EditText renameInput = inlineInput("最多 4 个汉字");
            renameInput.setVisibility(View.GONE);
            content.addView(renameInput, new LinearLayout.LayoutParams(
                    0, dp(30), 1f
            ));

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout editActions = new LinearLayout(activity);
            editActions.setOrientation(LinearLayout.HORIZONTAL);
            editActions.setGravity(Gravity.CENTER_VERTICAL);
            editActions.setVisibility(View.GONE);
            TextView saveRename = managerAction("保存", view -> {
                finishRowRename(name, renameInput, actions, editActions, item);
            });
            saveRename.setTextColor(UiKit.ACCENT_DEEP);
            editActions.addView(saveRename, new LinearLayout.LayoutParams(dp(44), dp(30)));
            TextView cancelRename = managerAction("取消", view -> {
                endRowEdit(name, renameInput, actions, editActions);
            });
            LinearLayout.LayoutParams cancelRenameParams =
                    new LinearLayout.LayoutParams(dp(44), dp(30));
            cancelRenameParams.leftMargin = dp(6);
            editActions.addView(cancelRename, cancelRenameParams);

            actions.addView(iconAction(IconView.Type.EDIT, "改名", view -> {
                highlightCategoryRow(rows, item.id);
                showRenameDialog(item);
            }), new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            deleteParams.leftMargin = dp(6);
            actions.addView(iconAction(
                    IconView.Type.TRASH, "删除", view -> deleteCategory(item)
            ), deleteParams);
            TextView up = UiKit.text(activity, "↑", 17f, UiKit.MUTED, true);
            up.setGravity(Gravity.CENTER);
            up.setContentDescription("上移分类");
            up.setPadding(dp(6), 0, dp(2), 0);
            up.setOnClickListener(view -> {
                highlightCategoryRow(rows, item.id);
                int index = -1;
                for (int i = 0; i < draft.size(); i += 1) {
                    if (draft.get(i).id.equals(item.id)) index = i;
                }
                if (index < 0 || draft.size() < 2) return;
                // Wraps: the first item moves to the end.
                int target = (index - 1 + draft.size()) % draft.size();
                ModuleCategories.Category moved = draft.remove(index);
                draft.add(target, moved);
                rows.removeView(row);
                // Child 0 is the built-in row, custom rows start at 1.
                rows.addView(row, target + 1);
                highlightCategoryRow(rows, item.id);
                pageOrderChanged = true;
            });
            LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(36), dp(30));
            upParams.leftMargin = dp(8);
            actions.addView(up, upParams);
            content.addView(actions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)
            ));
            content.addView(editActions, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)
            ));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = dp(10);
            rows.addView(row, rowParams);
        }

        LinearLayout addHost = new LinearLayout(activity);
        addHost.setOrientation(LinearLayout.VERTICAL);
        addHost.setPadding(dp(12), dp(7), dp(10), dp(7));
        addHost.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 16f));
        categoryAddHost = addHost;

        LinearLayout addLine = new LinearLayout(activity);
        addLine.setOrientation(LinearLayout.HORIZONTAL);
        addLine.setGravity(Gravity.CENTER_VERTICAL);
        addLine.setPadding(0, dp(5), 0, dp(5));
        TextView add = UiKit.text(activity, "＋ 新建分类", 11f, UiKit.ACCENT_DEEP, true);
        addLine.addView(add);
        TextView addCount = UiKit.text(
                activity,
                "当前 " + categories.size() + " 个 / 最多 " + ModuleCategories.MAX_CATEGORIES + " 个",
                9f,
                UiKit.FAINT,
                false
        );
        LinearLayout.LayoutParams addCountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addCountParams.leftMargin = dp(8);
        addLine.addView(addCount, addCountParams);
        addHost.addView(addLine);
        categoryAtLimit = categories.size() >= ModuleCategories.MAX_CATEGORIES;
        addLine.setOnClickListener(view -> showNewCategoryDialog());

        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addParams.bottomMargin = dp(10);
        addParams.topMargin = dp(14);
        rows.addView(addHost, addParams);
        if (categoryAtLimit) {
            addHost.setAlpha(0.4f);
            setEnabledDeep(addHost, false);
        }

        pageHost.removeAllViews();
        pageHost.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageHost.setVisibility(View.VISIBLE);
        pageHost.setAlpha(1f);
        page.requestApplyInsets();
        listener.onShowFullscreenPage(pageHost);
    }

    private void finishCategoryPage() {
        if (pageHost == null) return;
        if (pageHost.getVisibility() != View.VISIBLE) return;
        if (pageOrderChanged && pageDraft != null) {
            List<String> ids = new ArrayList<>();
            for (ModuleCategories.Category item : pageDraft) {
                if (!ModuleCategories.DEFAULT_ID.equals(item.id)) ids.add(item.id);
            }
            ModuleCategories.reorder(activity, ids);
            rebuildBody();
        }
        closeCategoryPage();
    }

    private void closeCategoryPage() {
        if (pageHost == null) return;
        hideKeyboard(pageHost);
        pageHost.removeAllViews();
        pageHost.setVisibility(View.GONE);
        listener.onHideFullscreenPage();
    }

    /** True when a full-screen page of this screen consumed the back press. */
    public boolean handleBack() {
        if (pageHost != null && pageHost.getVisibility() == View.VISIBLE) {
            finishCategoryPage();
            return true;
        }
        return false;
    }

    /** While a row is being edited everything else is dimmed and inert. */
    private void beginListEdit(View active) {
        editingRow = active;
        if (categoryRows == null) return;
        for (int i = 0; i < categoryRows.getChildCount(); i += 1) {
            View child = categoryRows.getChildAt(i);
            if (child == active) continue;
            child.setAlpha(0.4f);
            setEnabledDeep(child, false);
        }
    }

    private void endListEdit() {
        editingRow = null;
        if (categoryRows == null) return;
        for (int i = 0; i < categoryRows.getChildCount(); i += 1) {
            View child = categoryRows.getChildAt(i);
            child.setAlpha(1f);
            setEnabledDeep(child, true);
        }
        if (categoryAtLimit && categoryAddHost != null) {
            categoryAddHost.setAlpha(0.4f);
            setEnabledDeep(categoryAddHost, false);
        }
    }

    private void setEnabledDeep(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i += 1) {
            setEnabledDeep(group.getChildAt(i), enabled);
        }
    }

    private void highlightCategoryRow(LinearLayout rows, String categoryId) {
        for (int i = 0; i < rows.getChildCount(); i += 1) {
            View child = rows.getChildAt(i);
            boolean selected = categoryId != null && categoryId.equals(child.getTag());
            child.setSelected(selected);
            child.setBackground(UiKit.rounded(
                    activity,
                    selected ? UiKit.ACCENT_SOFT : UiKit.SURFACE,
                    16f
            ));
        }
    }

    private static final android.view.ActionMode.Callback NO_ACTION_MODE =
            new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    return false;
                }

                @Override
                public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                }
            };

    private EditText inlineInput(String hint) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setTextSize(12f);
        input.setTextColor(UiKit.INK);
        input.setHint(hint);
        input.setHintTextColor(UiKit.FAINT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setBackground(UiKit.rounded(activity, UiKit.SURFACE_SOFT, 11f));
        input.setPadding(dp(10), 0, dp(10), 0);
        // No system copy/paste toolbar for a 1-4 character name field.
        input.setLongClickable(false);
        input.setTextIsSelectable(false);
        input.setCustomSelectionActionModeCallback(NO_ACTION_MODE);
        input.setCustomInsertionActionModeCallback(NO_ACTION_MODE);
        return input;
    }

    private void startRowEdit(TextView name, EditText input, LinearLayout actions,
                              LinearLayout editActions, ModuleCategories.Category item) {
        input.setText(item.name);
        name.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        input.setVisibility(View.VISIBLE);
        editActions.setVisibility(View.VISIBLE);
        input.requestFocus();
        input.setSelection(input.getText().length());
        input.postDelayed(() -> showKeyboard(input), 80);
    }

    private void endRowEdit(TextView name, EditText input, LinearLayout actions,
                            LinearLayout editActions) {
        name.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
        input.setVisibility(View.GONE);
        editActions.setVisibility(View.GONE);
        hideKeyboard(input);
        if (activeNotice != null) activeNotice.setVisibility(View.GONE);
        endListEdit();
    }

    private void finishRowRename(TextView name, EditText input, LinearLayout actions,
                                 LinearLayout editActions, ModuleCategories.Category item) {
        String value = input.getText().toString().trim();
        if (!ModuleCategories.rename(activity, item.id, value)) {
            showNotice("名称需 1-4 字且不能重名");
            input.requestFocus();
            return;
        }
        endRowEdit(name, input, actions, editActions);
        name.setText(value);
        rebuildBody();
    }

    /** Error strip that sits above the input inside the editing card. */
    private TextView buildNotice() {
        TextView notice = UiKit.text(activity, "", 10.5f, UiKit.DANGER, true);
        notice.setPadding(dp(10), dp(6), dp(10), dp(6));
        notice.setBackground(UiKit.rounded(activity, UiKit.DANGER_SOFT, 8f));
        notice.setVisibility(View.GONE);
        return notice;
    }

    private void showNotice(String text) {
        TextView notice = activeNotice;
        if (notice == null) return;
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
        notice.setAlpha(0f);
        notice.animate().alpha(1f).setDuration(150).start();
        notice.postDelayed(() -> notice.setVisibility(View.GONE), 3500);
        // The strip grows the card, so make sure the whole card stays visible.
        if (notice.getParent() instanceof View) {
            View card = (View) notice.getParent();
            card.post(() -> scrollIntoView(card));
        }
    }

    private void showKeyboard(EditText input) {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        // The row may still be laying out when the editor becomes visible.
        input.postDelayed(() -> {
            if (!input.hasFocus()) input.requestFocus();
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            scrollIntoView(input);
        }, 260);
        // Again after the window has resized for the keyboard.
        input.postDelayed(() -> scrollIntoView(input), 560);
    }

    /** Asks the surrounding ScrollView to bring the editor fully into view. */
    private void scrollIntoView(View target) {
        if (target.getParent() == null || target.getWidth() == 0) return;
        android.graphics.Rect rect = new android.graphics.Rect(
                0, 0, target.getWidth(), target.getHeight() + dp(28)
        );
        target.requestRectangleOnScreen(rect, false);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /** Built-in categories share one read-only row; they cannot be changed. */
    private View builtinCategoryRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(10), dp(7));
        row.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 16f));

        row.addView(builtinCategoryItem(
                IconView.Type.SIGNPOST, DEFAULT_SOFT, ModuleCategories.DEFAULT_NAME
        ));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(8), dp(30));
        row.addView(new View(activity), gap);
        row.addView(builtinCategoryItem(
                IconView.Type.FOLDER_QUERY, UNCLASSIFIED_SOFT, ModuleCategories.UNCLASSIFIED_NAME
        ));
        row.addView(new View(activity), new LinearLayout.LayoutParams(0, dp(30), 1f));

        TextView note = UiKit.text(activity, "系统内置 不可更改", 9.5f, UiKit.FAINT, false);
        note.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)
        ));
        return row;
    }

    private View builtinCategoryItem(IconView.Type type, int background, String name) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setAlpha(0.4f);

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setBackground(UiKit.rounded(activity, background, 9f));
        IconView icon = new IconView(activity).type(type);
        icon.setColor(UiKit.MUTED);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(
                dp(17), dp(17), Gravity.CENTER
        ));
        item.addView(iconWrap, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView label = UiKit.text(activity, name, 12f, UiKit.MUTED, true);
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)
        );
        labelParams.leftMargin = dp(8);
        item.addView(label, labelParams);
        return item;
    }

    private FrameLayout iconAction(IconView.Type type, String description,
                                   View.OnClickListener click) {
        FrameLayout button = new FrameLayout(activity);
        button.setBackground(UiKit.ripple(activity, Color.TRANSPARENT, 10f));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(description);
        IconView icon = new IconView(activity).type(type);
        icon.setColor(UiKit.MUTED);
        button.addView(icon, new FrameLayout.LayoutParams(
                dp(17), dp(17), Gravity.CENTER
        ));
        button.setOnClickListener(click);
        return button;
    }

    private TextView managerAction(String label, View.OnClickListener click) {
        TextView action = UiKit.text(activity, label, 10f, UiKit.MUTED, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(4), dp(6), dp(4), dp(6));
        action.setOnClickListener(click);
        return action;
    }

    /** Updates one row's icon in place, so picking an icon never rebuilds the page. */
    private void applyCategoryIcon(String categoryId, String key) {
        FrameLayout button = categoryIconButtons.get(categoryId);
        IconView icon = categoryIconViews.get(categoryId);
        if (button == null || icon == null) return;
        button.setBackground(UiKit.rounded(
                activity, CategoryIcons.backgroundOf(key), 9f
        ));
        icon.type(CategoryIcons.typeOf(key));
    }

    private interface IconChoice {
        void onChoose(String key);
    }

    private void showCategoryIconPicker(ModuleCategories.Category item) {
        showIconPicker(item.name, item.icon, key -> {
            ModuleCategories.setIcon(activity, item.id, key);
            applyCategoryIcon(item.id, key);
        });
    }

    /** Centered dialog for renaming a category, same layout as the create one. */
    private void showRenameDialog(ModuleCategories.Category item) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(20));
        root.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 20f));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(
                activity, CategoryIcons.backgroundOf(item.icon), 13f
        ));
        IconView glyph = new IconView(activity).type(CategoryIcons.typeOf(item.icon));
        glyph.setColor(UiKit.INK);
        tile.addView(glyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "改名", 17f, UiKit.INK, true));
        TextView sub = UiKit.text(activity, "1–4 个字，不能重名", 12f, UiKit.MUTED, false);
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
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText input = inlineInput("分类名");
        input.setBackground(UiKit.rounded(activity, UiKit.SURFACE_SOFT, 12f));
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setText(item.name);
        input.setSelection(input.getText().length());
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        );
        inputParams.topMargin = dp(14);
        root.addView(input, inputParams);

        TextView error = UiKit.text(activity, "", 11.5f, UiKit.DANGER, false);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errorParams.topMargin = dp(8);
        error.setVisibility(View.GONE);
        root.addView(error, errorParams);

        final Dialog[] holder = new Dialog[1];
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        );
        buttonsParams.topMargin = dp(16);
        root.addView(buttons, buttonsParams);

        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> holder[0].dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView save = dialogButton("保存", DIALOG_BUTTON_SOLID);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        saveParams.leftMargin = dp(8);
        save.setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            if (!ModuleCategories.rename(activity, item.id, value)) {
                error.setText("名称需 1-4 字且不能重名");
                error.setVisibility(View.VISIBLE);
                return;
            }
            hideKeyboard(input);
            holder[0].dismiss();
            rebuildBody();
            openCategoryPage();
        });
        buttons.addView(save, saveParams);

        holder[0] = centerDialog(root, false);
        Window window = holder[0].getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.postDelayed(() -> {
            input.requestFocus();
            showKeyboard(input);
        }, 80);
    }

    /** Centered dialog for naming a new category. */
    private void showNewCategoryDialog() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(20));
        root.setBackground(UiKit.rounded(activity, UiKit.SURFACE, 20f));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(activity, UiKit.ACCENT_SOFT, 13f));
        IconView glyph = new IconView(activity).type(CategoryIcons.typeOf(CategoryIcons.DEFAULT));
        glyph.setColor(DIALOG_ICON_WARM);
        tile.addView(glyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "新建分类", 17f, UiKit.INK, true));
        TextView sub = UiKit.text(activity, "1–4 个字，不能重名", 12f, UiKit.MUTED, false);
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
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText input = inlineInput("分类名");
        input.setBackground(UiKit.rounded(activity, UiKit.SURFACE_SOFT, 12f));
        input.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        );
        inputParams.topMargin = dp(14);
        root.addView(input, inputParams);

        TextView error = UiKit.text(activity, "", 11.5f, UiKit.DANGER, false);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        errorParams.topMargin = dp(8);
        error.setVisibility(View.GONE);
        root.addView(error, errorParams);

        final Dialog[] holder = new Dialog[1];
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        );
        buttonsParams.topMargin = dp(16);
        root.addView(buttons, buttonsParams);

        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> holder[0].dismiss());
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView create = dialogButton("创建", DIALOG_BUTTON_SOLID);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        createParams.leftMargin = dp(8);
        create.setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            String createdId = ModuleCategories.add(activity, value);
            if (createdId == null) {
                error.setText("名称需 1-4 字且不能重名，最多 "
                        + ModuleCategories.MAX_CATEGORIES + " 个");
                error.setVisibility(View.VISIBLE);
                scrollIntoView(input);
                return;
            }
            hideKeyboard(input);
            holder[0].dismiss();
            category = ModuleCategories.DEFAULT_ID;
            rebuildBody();
            openCategoryPage();
        });
        buttons.addView(create, createParams);

        holder[0] = centerDialog(root, false);
        Window window = holder[0].getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        input.postDelayed(() -> {
            input.requestFocus();
            showKeyboard(input);
        }, 80);
    }

    private void showIconPicker(String subtitle, String currentKey, IconChoice choice) {
        SheetDialog sheet = new SheetDialog(activity);
        final String[] picked = {currentKey};
        final Map<String, FrameLayout> cells = new HashMap<>();
        final Map<String, IconView> glyphs = new HashMap<>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(activity);
        tile.setBackground(UiKit.rounded(activity, CategoryIcons.backgroundOf(currentKey), 13f));
        IconView headGlyph = new IconView(activity).type(CategoryIcons.typeOf(currentKey));
        headGlyph.setColor(UiKit.INK);
        tile.addView(headGlyph, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        head.addView(tile, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.text(activity, "选择图标", 17f, UiKit.INK, true));
        TextView subView = UiKit.text(activity, subtitle, 12f, UiKit.MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(2);
        titles.addView(subView, subParams);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        titlesParams.leftMargin = dp(12);
        head.addView(titles, titlesParams);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headParams.bottomMargin = dp(16);
        root.addView(head, headParams);

        int columns = 5;
        for (int start = 0; start < CategoryIcons.KEYS.length; start += columns) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int offset = 0; offset < columns; offset += 1) {
                int index = start + offset;
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                        0, dp(52), 1f
                );
                if (offset > 0) cellParams.leftMargin = dp(6);
                if (index >= CategoryIcons.KEYS.length) {
                    row.addView(new View(activity), cellParams);
                    continue;
                }
                String key = CategoryIcons.KEYS[index];
                FrameLayout cell = new FrameLayout(activity);
                cell.setClickable(true);
                cell.setFocusable(true);
                cell.setContentDescription(key);
                IconView icon = new IconView(activity).type(CategoryIcons.typeOf(key));
                cell.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
                cells.put(key, cell);
                glyphs.put(key, icon);
                paintIconCell(cell, icon, key.equals(picked[0]));
                cell.setOnClickListener(view -> {
                    picked[0] = key;
                    tile.setBackground(UiKit.rounded(
                            activity, CategoryIcons.backgroundOf(key), 13f
                    ));
                    headGlyph.type(CategoryIcons.typeOf(key));
                    for (Map.Entry<String, FrameLayout> entry : cells.entrySet()) {
                        IconView glyph = glyphs.get(entry.getKey());
                        if (glyph == null) continue;
                        paintIconCell(entry.getValue(), glyph, entry.getKey().equals(key));
                    }
                });
                row.addView(cell, cellParams);
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (start > 0) rowParams.topMargin = dp(6);
            root.addView(row, rowParams);
        }

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = dialogButton("取消", DIALOG_BUTTON_GHOST);
        cancel.setOnClickListener(view -> sheet.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView done = dialogButton("完成", DIALOG_BUTTON_SOLID);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        doneParams.leftMargin = dp(8);
        done.setOnClickListener(view -> {
            choice.onChoose(picked[0]);
            sheet.dismiss();
        });
        footer.addView(done, doneParams);

        sheet.setBody(root);
        sheet.setFooter(footer);
        sheet.show();
    }

    private void paintIconCell(FrameLayout cell, IconView icon, boolean selected) {
        cell.setBackground(selected
                ? UiKit.stroked(activity, UiKit.ACCENT_SOFT, 0xFFC97B33, 14f)
                : UiKit.rounded(activity, 0xFFF7F2E8, 14f));
        icon.setColor(selected ? DIALOG_ICON_WARM : UiKit.MUTED);
    }

    private void deleteCategory(ModuleCategories.Category item) {
        int count = 0;
        for (InstalledModule module : installed.values()) {
            if (item.id.equals(ModuleCategories.categoryOf(activity, module.id()))) count += 1;
        }
        showAlertDialog(
                "删除「" + item.name + "」？",
                count == 0 ? "该分类下还没有模块" : count + " 个模块会被移出",
                count == 0
                        ? "删除后无法恢复，之后可以再新建同名分类。"
                        : "这些模块会回到「" + ModuleCategories.UNCLASSIFIED_NAME
                                + "」，**模块本身不会被卸载**，之后还能重新归类。",
                "删除分类",
                IconView.Type.TRASH,
                true,
                () -> {
                    ModuleCategories.delete(activity, item.id);
                    category = ModuleCategories.DEFAULT_ID;
                    rebuildBody();
                    openCategoryPage();
                }
        );
    }

    private View emptyResult(String text) {
        TextView empty = UiKit.text(activity, text, 12f, UiKit.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(2), dp(28), dp(2), dp(20));
        return empty;
    }

    private void buildInstalled(LinearLayout content) {
        TextView head = UiKit.label(activity, "已安装 · " + installed.size(), 9f, UiKit.MUTED);
        head.setPadding(dp(2), dp(2), dp(2), dp(8));
        content.addView(head);
        if (installed.isEmpty()) {
            TextView empty = UiKit.text(activity, "还没有安装模块。", 12f, UiKit.MUTED, false);
            empty.setPadding(dp(2), dp(18), dp(2), dp(18));
            content.addView(empty);
            return;
        }
        List<InstalledModule> modules = new ArrayList<>(installed.values());
        modules.sort(Comparator.comparing(InstalledModule::name));
        for (InstalledModule module : modules) {
            addSpacedRow(content, buildInstalledRow(module));
        }
    }

    private void addSpacedRow(LinearLayout content, View row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        content.addView(row, params);
    }

    private List<InstalledModule> recentModules() {
        List<InstalledModule> modules = new ArrayList<>();
        for (InstalledModule module : installed.values()) {
            if (ModuleUsage.lastOpened(activity, module.id()) > 0) modules.add(module);
        }
        modules.sort((left, right) -> Long.compare(
                ModuleUsage.lastOpened(activity, right.id()),
                ModuleUsage.lastOpened(activity, left.id())
        ));
        if (modules.size() > 6) return new ArrayList<>(modules.subList(0, 6));
        return modules;
    }

    private List<InstalledModule> favoriteModules() {
        Map<String, InstalledModule> byId = new HashMap<>();
        for (InstalledModule module : installed.values()) byId.put(module.id(), module);
        List<InstalledModule> result = new ArrayList<>();
        for (String id : FavoriteModules.list(activity)) {
            InstalledModule module = byId.get(id);
            if (module != null) result.add(module);
            if (result.size() >= 6) break;
        }
        return result;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installTapGuard(View view, Runnable action) {
        final float[] down = new float[2];
        final boolean[] moved = new boolean[]{false};
        final float slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
        view.setOnTouchListener((target, event) -> {
            int touchAction = event.getActionMasked();
            if (touchAction == MotionEvent.ACTION_DOWN) {
                down[0] = event.getX();
                down[1] = event.getY();
                moved[0] = false;
            } else if (touchAction == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - down[0];
                float dy = event.getY() - down[1];
                if (Math.hypot(dx, dy) > slop) moved[0] = true;
            }
            return false;
        });
        view.setOnClickListener(target -> {
            if (moved[0]) {
                moved[0] = false;
                return;
            }
            action.run();
        });
    }

    private View buildRecentGrid(
            List<InstalledModule> modules, String sortScope, String sortLabel
    ) {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < modules.size(); index += 3) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 3; column += 1) {
                int position = index + column;
                if (position < modules.size()) {
                    InstalledModule module = modules.get(position);
                    LinearLayout tile = new LinearLayout(activity);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                    tile.setPadding(dp(3), dp(3), dp(3), dp(4));
                    tile.setClickable(true);
                    tile.setFocusable(true);
                    installTapGuard(tile, () -> listener.onOpenInstalled(module));
                    tile.setOnLongClickListener(view -> {
                        showModuleMenu(module, sortScope, sortLabel);
                        return true;
                    });

                    int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);
                    FrameLayout icon = new FrameLayout(activity);
                    icon.setBackground(UiKit.rounded(
                            activity, UiKit.blend(accent, Color.WHITE, 0.14f), 12f
                    ));
                    IconView glyph = new IconView(activity).type(iconFor(glyphFor(module.id())));
                    glyph.setColor(accent);
                    icon.addView(glyph, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
                    tile.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

                    TextView name = UiKit.text(activity, module.name(), 9.5f, UiKit.INK, true);
                    name.setMaxLines(2);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    name.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    nameParams.topMargin = dp(5);
                    tile.addView(name, nameParams);
                    row.addView(tile, new LinearLayout.LayoutParams(
                            0, dp(84), 1f
                    ));
                } else {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(
                            0, dp(84), 1f
                    ));
                }
            }
            grid.addView(row);
        }
        return grid;
    }

    private View buildInstalledRow(InstalledModule module) {
        int accent = UiKit.parseColor(module.accent(), UiKit.ACCENT);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(8), dp(11));
        row.setBackground(UiKit.ripple(activity, UiKit.SURFACE, 18f));
        row.setClickable(true);
        row.setFocusable(true);
        installTapGuard(row, () -> listener.onOpenInstalled(module));
        row.setOnLongClickListener(view -> {
            showModuleMenu(module, null, "最近");
            return true;
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(8);
        row.setLayoutParams(rowParams);

        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, UiKit.blend(accent, Color.WHITE, 0.14f), 12f));
        IconView glyph = new IconView(activity).type(iconFor(glyphFor(module.id())));
        glyph.setColor(accent);
        icon.addView(glyph, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, module.name(), 12.5f, UiKit.INK, true));
        String meta = "v" + module.version() + " · " + (module.capabilities().contains("storage")
                ? "SQLite"
                : "无存储");
        TextView sub = UiKit.label(activity, meta, 9f, UiKit.FAINT);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = dp(3);
        copy.addView(sub, subParams);

        FrameLayout details = new FrameLayout(activity);
        details.setClickable(true);
        details.setFocusable(true);
        details.setContentDescription("模块详情");
        IconView chevron = new IconView(activity).type(IconView.Type.CHEVRON_RIGHT);
        chevron.setColor(UiKit.FAINT);
        details.addView(chevron, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        details.setOnClickListener(view -> listener.onInstalledDetails(module));
        row.addView(details, new LinearLayout.LayoutParams(dp(32), dp(38)));
        return row;
    }

    private String glyphFor(String id) {
        if (demoApps != null) {
            for (DemoApp app : demoApps) {
                if (app.id().equals(id)) return app.glyph();
            }
        }
        return "";
    }

    private View buildDemoRow(DemoApp app) {
        String state = stateOf(app);
        int accent = UiKit.parseColor(app.accent(), UiKit.ACCENT);
        int iconBg = UiKit.blend(accent, Color.WHITE, 0.14f);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(UiKit.ripple(activity, UiKit.SURFACE, 18f));
        row.setClickable(true);
        row.setFocusable(true);
        installTapGuard(row, () -> listener.onDemoDetails(app, state));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(7);
        row.setLayoutParams(rowParams);

        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, iconBg, 12f));
        IconView glyph = new IconView(activity).type(iconFor(app.glyph()));
        glyph.setColor(accent);
        icon.addView(glyph, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        );
        copyParams.leftMargin = dp(10);
        row.addView(copy, copyParams);
        copy.addView(UiKit.text(activity, app.name(), 12.5f, UiKit.INK, true));
        String oneLine = app.tagline() == null || app.tagline().isEmpty()
                ? app.description()
                : app.tagline();
        TextView desc = UiKit.text(
                activity,
                oneLine,
                9f,
                UiKit.FAINT,
                false
        );
        desc.setMaxLines(1);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(3);
        copy.addView(desc, descParams);

        TextView status = UiKit.text(
                activity,
                state.isEmpty() ? "查看" : state,
                8.5f,
                state.startsWith("已安装") ? UiKit.FAINT : UiKit.ACCENT_DEEP,
                true
        );
        status.setPadding(dp(7), dp(4), dp(7), dp(4));
        status.setBackground(UiKit.rounded(
                activity,
                state.startsWith("已安装") ? UiKit.SURFACE_SOFT : UiKit.ACCENT_SOFT,
                99f
        ));
        row.addView(status);
        return row;
    }

    private void buildRemote(LinearLayout content) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(UiKit.stroked(activity, UiKit.SURFACE, UiKit.LINE, 20f));
        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, UiKit.TEAL_SOFT, 14f));
        IconView cloud = new IconView(activity).type(IconView.Type.DOWNLOAD);
        cloud.setColor(UiKit.TEAL);
        icon.addView(cloud, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = UiKit.text(activity, "远程 catalog", 17f, UiKit.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(13);
        card.addView(title, titleParams);
        TextView desc = UiKit.text(
                activity,
                "从 URL 加载模块列表，校验 sha256 后原子安装；失败不会替换当前版本。",
                10.5f,
                UiKit.MUTED,
                false
        );
        desc.setLineSpacing(dp(1), 1.2f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(6);
        card.addView(desc, descParams);

        TextView open = UiKit.primaryButton(activity, "打开远程目录");
        open.setOnClickListener(view -> listener.onRemote());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        openParams.topMargin = dp(16);
        content.addView(open, openParams);

        TextView note = UiKit.text(
                activity,
                "远程目录仍使用独立面板，后续可并入此页。",
                9f,
                UiKit.FAINT,
                false
        );
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        noteParams.topMargin = dp(12);
        content.addView(note, noteParams);
    }




    private void buildFile(LinearLayout content) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(28), dp(20), dp(24));
        card.setBackground(UiKit.stroked(activity, UiKit.SURFACE, UiKit.LINE, 22f));
        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout icon = new FrameLayout(activity);
        icon.setBackground(UiKit.rounded(activity, UiKit.ACCENT_SOFT, 17f));
        IconView file = new IconView(activity).type(IconView.Type.FILE);
        file.setColor(UiKit.ACCENT_DEEP);
        icon.addView(file, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView title = UiKit.text(activity, "选择一个 .pkg", 14f, UiKit.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(15);
        card.addView(title, titleParams);
        TextView desc = UiKit.text(
                activity,
                "系统选择器会读取 module.json、入口文件和包结构，失败时旧版本保留。",
                10.5f,
                UiKit.MUTED,
                false
        );
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descParams.topMargin = dp(6);
        card.addView(desc, descParams);
        TextView pick = UiKit.primaryButton(activity, "打开文件选择器");
        pick.setOnClickListener(view -> listener.onPickFile(""));
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(180), dp(48));
        pickParams.topMargin = dp(17);
        card.addView(pick, pickParams);
    }

    private String stateOf(DemoApp app) {
        InstalledModule module = installed.get(app.id());
        if (module == null) return "";
        int compare = SemVer.compare(module.version(), app.version());
        if (compare == 0) return "已安装";
        if (compare < 0) return "可更新";
        return "已安装 " + module.version();
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
