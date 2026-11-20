package com.docklet.shell.demo;

import android.content.Context;

import com.docklet.platform.Json;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * shell.json：壳自身的最新版本与下载地址。
 * 和 catalog 一样只缓存、按 6 小时规则刷新，不额外加请求节奏。
 */
public final class ShellUpdate {

    public static final String URL = "https://apps.docklet.cn/shell.json";
    public static final long MAX_AGE_MS = 6L * 60L * 60L * 1000L;

    private static final String CACHE_FILE = "shell.json";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 256 * 1024;

    public static final class Info {
        public final String version;
        public final String url;
        public final String sha256;

        Info(String version, String url, String sha256) {
            this.version = version == null ? "" : version.trim();
            this.url = url == null ? "" : url.trim();
            this.sha256 = sha256 == null ? "" : sha256.trim();
        }
    }

    private ShellUpdate() {
    }

    /** 缓存里的版本信息，没有缓存时返回 null。 */
    public static Info cached(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return parse(out.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception error) {
            return null;
        }
    }

    /** 缓存是否过期（没有缓存也算过期）。 */
    public static boolean cacheStale(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.isFile()) return true;
        return System.currentTimeMillis() - file.lastModified() >= MAX_AGE_MS;
    }

    public static Info parse(String json) {
        try {
            Map<String, Object> root = Json.parseObject(json);
            Map<String, Object> shell = Json.asObject(root == null ? null : root.get("shell"));
            if (shell == null) shell = root;
            if (shell == null) return null;
            String version = Json.string(shell, "version");
            if (version == null || version.isEmpty()) return null;
            return new Info(version, Json.string(shell, "url"), Json.string(shell, "sha256"));
        } catch (Exception error) {
            return null;
        }
    }

    /** 拉取最新信息；失败返回 null。成功时写入缓存。 */
    public static Info refresh(Context context) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cache-Control", "no-store");
            if (connection.getResponseCode() != 200) return null;
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) return null;
                    out.write(buffer, 0, read);
                }
                String json = out.toString(StandardCharsets.UTF_8.name());
                Info info = parse(json);
                if (info == null) return null;
                save(context, json);
                return info;
            }
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void save(Context context, String json) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
