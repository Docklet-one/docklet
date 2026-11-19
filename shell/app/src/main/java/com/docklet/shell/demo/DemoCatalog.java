package com.docklet.shell.demo;

import android.content.Context;

import com.docklet.platform.Json;
import com.docklet.platform.ModuleManifest;
import com.docklet.platform.SemVer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the build-generated demo catalog from APK assets. */
public final class DemoCatalog {

    private DemoCatalog() {
    }

    /** Bundled catalog shipped inside the APK. */
    public static List<DemoApp> loadBundled(Context context) {
        String json;
        try (InputStream in = context.getAssets().open("demos.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            json = out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return Collections.emptyList();
        }
        return parse(json);
    }

    /** Online catalog only: the last downloaded copy, empty when never fetched. */
    public static List<DemoApp> load(Context context) {
        String cached = CatalogSource.cachedJson(context);
        if (cached == null) return Collections.emptyList();
        return parse(cached);
    }

    public static List<DemoApp> parse(String json) {
        List<DemoApp> apps = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        try {
            Map<String, Object> root = Json.parseObject(json);
            List<Object> rawApps = Json.asArray(root.get("apps"));
            if (rawApps == null) return apps;
            for (Object raw : rawApps) {
                Map<String, Object> item = Json.asObject(raw);
                if (item == null) continue;
                String id = Json.string(item, "id");
                String name = Json.string(item, "name");
                String version = Json.string(item, "version");
                String asset = Json.string(item, "asset");
                if (!ModuleManifest.isValidId(id) || name == null || name.trim().isEmpty()) continue;
                if (!SemVer.isValid(version)) continue;
                if (asset == null || !asset.matches("^[a-z0-9.-]+\\.pkg$")) continue;
                if (!ids.add(id)) continue;
                String kind = Json.string(item, "kind", "示例");
                String glyph = Json.string(item, "glyph", "spark");
                String description = Json.string(item, "description", "");
                String accent = Json.string(item, "accent", "#D9792B");
                String tagline = Json.string(item, "tagline", "");
                String author = Json.string(item, "author", "Docklet");
                String authorContact = Json.string(item, "authorContact", "");
                String minShell = Json.string(item, "minShell", "1.0.0");
                String entry = Json.string(item, "entry", "");
                List<String> capabilities = new ArrayList<>();
                List<Object> rawCapabilities = Json.asArray(item.get("capabilities"));
                if (rawCapabilities != null) {
                    for (Object rawCapability : rawCapabilities) {
                        if (rawCapability instanceof String) {
                            capabilities.add((String) rawCapability);
                        }
                    }
                }
                long size = Json.longValue(item, "size", 0L);
                String updatedAt = Json.string(item, "updatedAt", "");
                String url = Json.string(item, "url", "");
                String sha256 = Json.string(item, "sha256", "");
                List<DemoApp.Permission> permissions = new ArrayList<>();
                List<Object> rawPermissions = Json.asArray(item.get("permissions"));
                if (rawPermissions != null) {
                    for (Object rawPermission : rawPermissions) {
                        Map<String, Object> permission = Json.asObject(rawPermission);
                        if (permission != null) {
                            permissions.add(new DemoApp.Permission(
                                    Json.string(permission, "name", ""),
                                    Json.string(permission, "reason", "")
                            ));
                        }
                    }
                }
                apps.add(new DemoApp(
                        id,
                        name,
                        kind,
                        glyph,
                        version,
                        description,
                        accent,
                        asset,
                        url,
                        sha256,
                        tagline,
                        author,
                        authorContact,
                        minShell,
                        entry,
                        capabilities,
                        size,
                        updatedAt,
                        permissions
                ));
            }
        } catch (Json.JsonException error) {
            return Collections.emptyList();
        }
        return apps;
    }
}
