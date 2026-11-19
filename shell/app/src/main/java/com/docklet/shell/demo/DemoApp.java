package com.docklet.shell.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One bundled demo application described by the generated demos.json. */
public final class DemoApp {

    private final String id;
    private final String name;
    private final String kind;
    private final String glyph;
    private final String version;
    private final String description;
    private final String accent;
    private final String asset;
    private final String url;
    private final String sha256;
    private final String tagline;
    private final String author;
    private final String authorContact;
    private final String minShell;
    private final String entry;
    private final List<String> capabilities;
    private final long size;
    private final String updatedAt;
    private final List<Permission> permissions;

    public static final class Permission {
        private final String name;
        private final String reason;

        public Permission(String name, String reason) {
            this.name = name == null ? "" : name;
            this.reason = reason == null ? "" : reason;
        }

        public String name() {
            return name;
        }

        public String reason() {
            return reason;
        }
    }

    public DemoApp(
            String id,
            String name,
            String kind,
            String glyph,
            String version,
            String description,
            String accent,
            String asset,
            String url,
            String sha256,
            String tagline,
            String author,
            String authorContact,
            String minShell,
            String entry,
            List<String> capabilities,
            long size,
            String updatedAt,
            List<Permission> permissions
    ) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.glyph = glyph;
        this.version = version;
        this.description = description;
        this.accent = accent;
        this.asset = asset;
        this.url = url == null ? "" : url;
        this.sha256 = sha256 == null ? "" : sha256;
        this.tagline = tagline == null ? "" : tagline;
        this.author = author == null ? "" : author;
        this.authorContact = authorContact == null ? "" : authorContact;
        this.minShell = minShell == null ? "" : minShell;
        this.entry = entry == null ? "" : entry;
        this.capabilities = capabilities == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(capabilities));
        this.size = size;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
        this.permissions = permissions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String kind() {
        return kind;
    }

    public String glyph() {
        return glyph;
    }

    public String version() {
        return version;
    }

    public String description() {
        return description;
    }

    public String accent() {
        return accent;
    }

    /** Remote package address from the online catalog, empty for bundled demos. */
    public String url() {
        return url;
    }

    /** Expected SHA-256 of the remote package, empty when the catalog omits it. */
    public String sha256() {
        return sha256;
    }

    public String asset() {
        return asset;
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

    public String minShell() {
        return minShell;
    }

    public String entry() {
        return entry;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public long size() {
        return size;
    }

    public String updatedAt() {
        return updatedAt;
    }

    public List<Permission> permissions() {
        return permissions;
    }
}
