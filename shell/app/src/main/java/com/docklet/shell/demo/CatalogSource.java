package com.docklet.shell.demo;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Downloads the online catalog (catalog.json) and keeps a cached copy. */
public final class CatalogSource {

    public static final String CATALOG_URL = "https://apps.docklet.cn/catalog.json";

    private static final String CACHE_FILE = "catalog.json";
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private CatalogSource() {
    }

    /** Reads the last downloaded catalog, or null when nothing was cached yet. */
    public static String cachedJson(Context context) {
        File file = new File(context.getFilesDir(), CACHE_FILE);
        if (!file.isFile()) return null;
        try {
            byte[] data = new byte[(int) file.length()];
            try (InputStream in = new java.io.FileInputStream(file)) {
                int read = 0;
                while (read < data.length) {
                    int count = in.read(data, read, data.length - read);
                    if (count < 0) break;
                    read += count;
                }
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return null;
        }
    }

    /** Fetches the catalog; returns the body or null on any failure. */
    public static String fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CATALOG_URL).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cache-Control", "no-store");
            int code = connection.getResponseCode();
            if (code != 200) return null;
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
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Stores a fetched catalog so the next launch is instant and works offline. */
    public static void save(Context context, String json) {
        if (json == null || json.isEmpty()) return;
        File file = new File(context.getFilesDir(), CACHE_FILE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
