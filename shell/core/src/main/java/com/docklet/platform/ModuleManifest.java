package com.docklet.platform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parsed and validated {@code module.json}. */
public final class ModuleManifest {

    public static final String FILE_NAME = "module.json";

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern ACCENT_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]+$");
    private static final Set<String> CAPABILITIES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "storage",
                    "files",
                    "download",
                    "clipboard",
                    "openExternal",
                    "notify",
                    "background",
                    "network"
            ))
    );

    private final String id;
    private final String name;
    private final String version;
    private final String entry;
    private final String minShell;
    private final String data;
    private final String description;
    private final String tagline;
    private final String author;
    private final String authorContact;
    private final String glyph;
    private final List<Permission> permissions;
    private final String kind;
    private final String accent;
    private final String icon;
    private final String chrome;
    private final DisplayConfig display;
    private final List<String> capabilities;

    /** Declared system permission from the manifest. */
    public static final class Permission {
        private final String name;
        private final String reason;

        Permission(String name, String reason) {
            this.name = name == null ? "" : name;
            this.reason = reason == null ? "" : reason;
        }

        public String name() {
            return name;
        }

        public String reason() {
            return reason;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("reason", reason);
            return map;
        }
    }

    private ModuleManifest(
            String id,
            String name,
            String version,
            String entry,
            String minShell,
            String data,
            String description,
            String tagline,
            String author,
            String authorContact,
            String glyph,
            List<Permission> permissions,
            String kind,
            String accent,
            String icon,
            String chrome,
            DisplayConfig display,
            List<String> capabilities
    ) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.entry = entry;
        this.minShell = minShell;
        this.data = data;
        this.description = description;
        this.tagline = tagline == null ? "" : tagline;
        this.author = author == null ? "" : author;
        this.authorContact = authorContact == null ? "" : authorContact;
        this.glyph = glyph == null || glyph.isEmpty() ? "spark" : glyph;
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
        this.kind = kind;
        this.accent = accent;
        this.icon = icon;
        this.chrome = chrome;
        this.display = display == null ? DisplayConfig.defaults(chrome) : display;
        this.capabilities = Collections.unmodifiableList(new ArrayList<>(capabilities));
    }

    public static ModuleManifest read(File file) throws ModuleException {
        if (file == null || !file.isFile()) {
            throw new ModuleException(ModuleException.MANIFEST_MISSING, "包内缺少 module.json");
        }
        try {
            return fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "读取 module.json 失败", error);
        }
    }

    public static ModuleManifest fromJson(String text) throws ModuleException {
        Map<String, Object> json;
        try {
            json = Json.parseObject(text);
        } catch (Json.JsonException error) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "module.json 不是合法 JSON: " + error.getMessage());
        }

        String id = requireString(json, "id");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new ModuleException(
                    ModuleException.INVALID_ID,
                    "模块 id 必须为小写字母开头的 2–32 位 [a-z0-9-]: " + id
            );
        }

        String name = requireString(json, "name").trim();
        if (name.isEmpty() || name.length() > 64) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "name 必须为 1–64 字符");
        }

        String version = requireString(json, "version");
        try {
            SemVer.parse(version);
        } catch (IllegalArgumentException error) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, error.getMessage());
        }

        String entry = requireString(json, "entry").trim();
        validateRelativePath(entry, "entry", ModuleException.ENTRY_MISSING);
        if (entry.endsWith("/")) {
            throw new ModuleException(ModuleException.ENTRY_MISSING, "entry 必须指向文件，而不是目录");
        }
        if (entry.length() > 200) {
            throw new ModuleException(ModuleException.ENTRY_MISSING, "entry 路径过长");
        }

        String minShell = Json.string(json, "minShell", "1.0.0").trim();
        try {
            SemVer.parse(minShell);
        } catch (IllegalArgumentException error) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "minShell 不是合法版本号");
        }

        String data = Json.string(json, "data", id).trim();
        if (!data.equals(id)) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID,
                    "data 必须等于 id（v1 强制按模块 id 隔离数据）"
            );
        }

        String description = Json.string(json, "description", "");
        if (description.length() > 200) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "description 不能超过 200 字符");
        }
        String tagline = Json.string(json, "tagline", "").trim();
        if (tagline.length() > 60) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "tagline 不能超过 60 字符");
        }
        String author = Json.string(json, "author", "").trim();
        if (author.length() > 40) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "author 不能超过 40 字符");
        }
        String authorContact = Json.string(json, "authorContact", "").trim();
        if (authorContact.length() > 120) {
            throw new ModuleException(
                    ModuleException.MANIFEST_INVALID, "authorContact 不能超过 120 字符"
            );
        }
        String glyph = Json.string(json, "glyph", "spark").trim();
        if (glyph.length() > 24) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "glyph 不能超过 24 字符");
        }
        List<Permission> permissions = new ArrayList<>();
        Object rawPermissions = json.get("permissions");
        if (rawPermissions != null) {
            List<Object> array = Json.asArray(rawPermissions);
            if (array == null) {
                throw new ModuleException(ModuleException.MANIFEST_INVALID, "permissions 必须是数组");
            }
            for (Object item : array) {
                Map<String, Object> permissionJson = Json.asObject(item);
                if (permissionJson == null) {
                    throw new ModuleException(
                            ModuleException.MANIFEST_INVALID, "permissions 每项必须是对象"
                    );
                }
                String permName = Json.string(permissionJson, "name", "").trim();
                if (permName.isEmpty()) {
                    throw new ModuleException(
                            ModuleException.MANIFEST_INVALID, "permissions 每项必须有 name"
                    );
                }
                permissions.add(new Permission(
                        permName, Json.string(permissionJson, "reason", "").trim()
                ));
            }
        }
        String kind = Json.string(json, "kind", "").trim();
        if (kind.length() > 24) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "kind 不能超过 24 字符");
        }

        String accent = Json.string(json, "accent", "#D9792B");
        if (!ACCENT_PATTERN.matcher(accent).matches()) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "accent 必须是 #RRGGBB");
        }

        String icon = Json.string(json, "icon", "");
        if (icon != null && !icon.isEmpty()) {
            validateRelativePath(icon, "icon", ModuleException.MANIFEST_INVALID);
        }

        String chrome = Json.string(json, "chrome", "standard").trim();
        if (!"standard".equals(chrome) && !"none".equals(chrome)) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "chrome 只支持 standard 或 none");
        }
        DisplayConfig display = DisplayConfig.fromJson(json.get("display"), chrome);
        List<String> capabilities = new ArrayList<>();
        Object rawCapabilities = json.get("capabilities");
        if (rawCapabilities != null) {
            List<Object> array = Json.asArray(rawCapabilities);
            if (array == null) {
                throw new ModuleException(ModuleException.MANIFEST_INVALID, "capabilities 必须是数组");
            }
            for (Object item : array) {
                if (!(item instanceof String)) {
                    throw new ModuleException(ModuleException.MANIFEST_INVALID, "capabilities 只能包含字符串");
                }
                String capability = (String) item;
                if (!CAPABILITIES.contains(capability)) {
                    throw new ModuleException(ModuleException.MANIFEST_INVALID, "未知能力: " + capability);
                }
                if (!capabilities.contains(capability)) capabilities.add(capability);
            }
        }

        return new ModuleManifest(
                id,
                name,
                version,
                entry,
                minShell,
                data,
                description,
                tagline,
                author,
                authorContact,
                glyph,
                permissions,
                kind,
                accent.toUpperCase(Locale.ROOT),
                icon,
                chrome,
                display,
                capabilities
        );
    }

    private static String requireString(Map<String, Object> json, String key) throws ModuleException {
        Object value = json.get(key);
        if (!(value instanceof String)) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "缺少字符串字段 " + key);
        }
        String text = (String) value;
        if (text.trim().isEmpty()) {
            throw new ModuleException(ModuleException.MANIFEST_INVALID, "字段 " + key + " 不能为空");
        }
        return text;
    }

    private static void validateRelativePath(String path, String field, String code) throws ModuleException {
        if (path.isEmpty() || path.startsWith("/") || path.startsWith("\\")) {
            throw new ModuleException(code, field + " 必须是包内相对路径");
        }
        if (path.contains("\\") || path.contains("\u0000") || path.contains("//")) {
            throw new ModuleException(code, field + " 包含非法路径字符");
        }
        if (!ENTRY_PATTERN.matcher(path).matches()) {
            throw new ModuleException(code, field + " 只能使用字母、数字、点、下划线和短横线");
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new ModuleException(code, field + " 包含非法路径段");
            }
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String entry() {
        return entry;
    }

    public String minShell() {
        return minShell;
    }

    public String data() {
        return data;
    }

    public String description() {
        return description;
    }

    public String tagline() {
        return tagline;
    }

    public String author() {
        return author;
    }

    public String authorContact() {
        return authorContact;
    }

    public String glyph() {
        return glyph;
    }

    public List<Permission> permissions() {
        return permissions;
    }

    public String kind() {
        return kind;
    }

    public String accent() {
        return accent;
    }

    public String icon() {
        return icon;
    }

    public String chrome() {
        return chrome;
    }

    public DisplayConfig display() {
        return display;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("version", version);
        map.put("entry", entry);
        map.put("minShell", minShell);
        map.put("data", data);
        map.put("capabilities", capabilities);
        if (!description.isEmpty()) map.put("description", description);
        if (!tagline.isEmpty()) map.put("tagline", tagline);
        if (!author.isEmpty()) map.put("author", author);
        if (!authorContact.isEmpty()) map.put("authorContact", authorContact);
        map.put("glyph", glyph);
        if (!permissions.isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (Permission permission : permissions) list.add(permission.toMap());
            map.put("permissions", list);
        }
        if (!kind.isEmpty()) map.put("kind", kind);
        map.put("accent", accent);
        if (icon != null && !icon.isEmpty()) map.put("icon", icon);
        if (!"standard".equals(chrome)) map.put("chrome", chrome);
        map.put("display", display.toMap());
        return map;
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public static Set<String> knownCapabilities() {
        return CAPABILITIES;
    }
}
