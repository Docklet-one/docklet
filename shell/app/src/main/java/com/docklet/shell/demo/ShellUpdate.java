package com.docklet.shell.demo;

import com.docklet.platform.Json;

import java.util.Map;

/**
 * 壳自身的版本信息，直接取自 catalog.json 里的 "shell" 字段。
 * 不额外发请求：目录什么时候刷新，它就什么时候更新。
 */
public final class ShellUpdate {

    public static final class Info {
        public final String version;
        public final String url;
        public final String sha256;

        Info(String version, String url, String sha256) {
            this.version = version == null ? "" : version.trim();
            this.url = url == null ? "" : url.trim();
            this.sha256 = sha256 == null ? "" : sha256.trim();
        }

        public boolean hasUrl() {
            return !url.isEmpty();
        }
    }

    private ShellUpdate() {
    }

    /** 从目录 JSON 里读 "shell" 段；没有或格式不对时返回 null。 */
    public static Info parse(String catalogJson) {
        if (catalogJson == null || catalogJson.isEmpty()) return null;
        try {
            Map<String, Object> root = Json.parseObject(catalogJson);
            Map<String, Object> shell = Json.asObject(root == null ? null : root.get("shell"));
            if (shell == null) return null;
            String version = Json.string(shell, "version");
            if (version == null || version.isEmpty()) return null;
            return new Info(version, Json.string(shell, "url"), Json.string(shell, "sha256"));
        } catch (Exception error) {
            return null;
        }
    }
}
