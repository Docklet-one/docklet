package com.docklet.platform;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional display/window policy declared by a module. */
public final class DisplayConfig {

    public static final String MODE_SAFE = "safe";
    public static final String MODE_EDGE_TO_EDGE = "edge-to-edge";
    public static final String MODE_IMMERSIVE = "immersive";

    public static final String ORIENTATION_AUTO = "auto";
    public static final String ORIENTATION_PORTRAIT = "portrait";
    public static final String ORIENTATION_LANDSCAPE = "landscape";

    public static final String ICONS_AUTO = "auto";
    public static final String ICONS_LIGHT = "light";
    public static final String ICONS_DARK = "dark";

    private final String mode;
    private final String orientation;
    private final String statusBarIcons;
    private final boolean safeContent;

    public DisplayConfig(
            String mode,
            String orientation,
            String statusBarIcons,
            boolean safeContent
    ) {
        this.mode = mode;
        this.orientation = orientation;
        this.statusBarIcons = statusBarIcons;
        this.safeContent = safeContent;
    }

    public static DisplayConfig defaults(String chrome) {
        String mode = "none".equals(chrome) ? MODE_EDGE_TO_EDGE : MODE_SAFE;
        return new DisplayConfig(mode, ORIENTATION_AUTO, ICONS_AUTO, false);
    }

    public static DisplayConfig fromJson(Object raw, String chrome) throws ModuleException {
        DisplayConfig fallback = defaults(chrome);
        if (raw == null) return fallback;
        Map<String, Object> json = Json.asObject(raw);
        if (json == null) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID,
                    "display 必须是对象"
            );
        }
        String mode = Json.string(json, "mode", fallback.mode).trim();
        if (!MODE_SAFE.equals(mode)
                && !MODE_EDGE_TO_EDGE.equals(mode)
                && !MODE_IMMERSIVE.equals(mode)) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID,
                    "display.mode 只支持 safe / edge-to-edge / immersive"
            );
        }
        String orientation = Json.string(
                json, "orientation", ORIENTATION_AUTO
        ).trim();
        if (!ORIENTATION_AUTO.equals(orientation)
                && !ORIENTATION_PORTRAIT.equals(orientation)
                && !ORIENTATION_LANDSCAPE.equals(orientation)) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID,
                    "display.orientation 只支持 auto / portrait / landscape"
            );
        }
        String icons = Json.string(json, "statusBarIcons", ICONS_AUTO).trim();
        if (!ICONS_AUTO.equals(icons)
                && !ICONS_LIGHT.equals(icons)
                && !ICONS_DARK.equals(icons)) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID,
                    "display.statusBarIcons 只支持 auto / light / dark"
            );
        }
        boolean safeContent = Json.bool(json, "safeContent", false);
        return new DisplayConfig(mode, orientation, icons, safeContent);
    }

    public String mode() {
        return mode;
    }

    public String orientation() {
        return orientation;
    }

    public String statusBarIcons() {
        return statusBarIcons;
    }

    public boolean safeContent() {
        return safeContent;
    }

    public boolean isSafe() {
        return MODE_SAFE.equals(mode);
    }

    public boolean isImmersive() {
        return MODE_IMMERSIVE.equals(mode);
    }

    public boolean isEdgeToEdge() {
        return MODE_EDGE_TO_EDGE.equals(mode);
    }

    public boolean useDarkStatusIcons() {
        if (ICONS_DARK.equals(statusBarIcons)) return true;
        if (ICONS_LIGHT.equals(statusBarIcons)) return false;
        return isSafe();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", mode);
        map.put("orientation", orientation);
        map.put("statusBarIcons", statusBarIcons);
        map.put("safeContent", safeContent);
        return map;
    }
}
