package com.docklet.platform;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable view of one installed module. */
public final class InstalledModule {

    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String tagline;
    private final String author;
    private final String authorContact;
    private final String glyph;
    private final String icon;
    private final List<ModuleManifest.Permission> permissions;
    private final String kind;
    private final String accent;
    private final String chrome;
    private final DisplayConfig display;
    private final String entry;
    private final String data;
    private final String origin;
    private final List<String> capabilities;
    private final List<String> versions;
    private final long codeBytes;
    private final long dataBytes;
    private final File directory;

    public InstalledModule(
            String id,
            String name,
            String version,
            String description,
            String tagline,
            String author,
            String authorContact,
            String glyph,
            String icon,
            List<ModuleManifest.Permission> permissions,
            String kind,
            String accent,
            String chrome,
            DisplayConfig display,
            String entry,
            String data,
            String origin,
            List<String> capabilities,
            List<String> versions,
            long codeBytes,
            long dataBytes,
            File directory
    ) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.tagline = tagline == null ? "" : tagline;
        this.author = author == null ? "" : author;
        this.authorContact = authorContact == null ? "" : authorContact;
        this.glyph = glyph == null || glyph.isEmpty() ? "spark" : glyph;
        this.icon = icon == null ? "" : icon;
        this.permissions = Collections.unmodifiableList(
                permissions == null ? new ArrayList<>() : new ArrayList<>(permissions)
        );
        this.kind = kind;
        this.accent = accent;
        this.chrome = chrome;
        this.display = display == null ? DisplayConfig.defaults(chrome) : display;
        this.entry = entry;
        this.data = data;
        this.origin = origin;
        this.capabilities = Collections.unmodifiableList(new ArrayList<>(capabilities));
        this.versions = Collections.unmodifiableList(new ArrayList<>(versions));
        this.codeBytes = codeBytes;
        this.dataBytes = dataBytes;
        this.directory = directory;
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

    public String icon() {
        return icon;
    }

    public String glyph() {
        return glyph;
    }

    public List<ModuleManifest.Permission> permissions() {
        return permissions;
    }

    public String kind() {
        return kind == null || kind.isEmpty() ? "其他" : kind;
    }

    public String accent() {
        return accent;
    }

    public String chrome() {
        return chrome == null || chrome.isEmpty() ? "standard" : chrome;
    }

    public DisplayConfig display() {
        return display;
    }

    public String entry() {
        return entry;
    }

    public String data() {
        return data;
    }

    public String origin() {
        return origin;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public List<String> versions() {
        return versions;
    }

    public long codeBytes() {
        return codeBytes;
    }

    public long dataBytes() {
        return dataBytes;
    }

    public File directory() {
        return directory;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("version", version);
        if (description != null) map.put("description", description);
        if (!tagline.isEmpty()) map.put("tagline", tagline);
        if (!author.isEmpty()) map.put("author", author);
        if (!authorContact.isEmpty()) map.put("authorContact", authorContact);
        map.put("glyph", glyph);
        if (!permissions.isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (ModuleManifest.Permission permission : permissions) list.add(permission.toMap());
            map.put("permissions", list);
        }
        map.put("accent", accent);
        map.put("chrome", chrome());
        map.put("display", display.toMap());
        map.put("entry", entry);
        map.put("data", data);
        map.put("origin", origin);
        map.put("capabilities", capabilities);
        map.put("versions", versions);
        map.put("codeBytes", codeBytes);
        map.put("dataBytes", dataBytes);
        return map;
    }
}
