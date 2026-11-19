package com.docklet.platform;

import java.util.LinkedHashMap;
import java.util.Map;

/** One downloadable module entry from a remote catalog. */
public final class RemoteModule {

    private final String id;
    private final String name;
    private final String kind;
    private final String version;
    private final String description;
    private final String accent;
    private final String sha256;
    private final long size;
    private final String packageUrl;

    public RemoteModule(
            String id,
            String name,
            String kind,
            String version,
            String description,
            String accent,
            String sha256,
            long size,
            String packageUrl
    ) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.version = version;
        this.description = description;
        this.accent = accent;
        this.sha256 = sha256;
        this.size = size;
        this.packageUrl = packageUrl;
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

    public String version() {
        return version;
    }

    public String description() {
        return description;
    }

    public String accent() {
        return accent;
    }

    public String sha256() {
        return sha256;
    }

    public long size() {
        return size;
    }

    public String packageUrl() {
        return packageUrl;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("kind", kind);
        map.put("version", version);
        map.put("description", description);
        map.put("accent", accent);
        map.put("sha256", sha256);
        map.put("size", size);
        map.put("url", packageUrl);
        return map;
    }
}
