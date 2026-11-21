package com.docklet.shell.runtime;

import android.content.Context;

import com.docklet.platform.DisplayConfig;
import com.docklet.platform.InstalledModule;
import com.docklet.platform.Json;
import com.docklet.platform.ModuleManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 首页用的模块索引：一个几 KB 的 JSON 文件。
 * 只是缓存——文件系统才是真相；启动后后台校验并重写。
 */
public final class ModuleIndex {

    private static final String FILE_NAME = "modules.index.json";
    private static final int SCHEMA = 1;

    private ModuleIndex() {
    }

    /** 读索引；没有或损坏时返回 null（调用方回落到扫盘）。 */
    public static List<InstalledModule> read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int count = in.read(data, read, data.length - read);
                if (count < 0) break;
                read += count;
            }
            return parse(context, new String(data, 0, read, StandardCharsets.UTF_8));
        } catch (Exception error) {
            return null;
        }
    }

    private static List<InstalledModule> parse(Context context, String json) {
        try {
            Map<String, Object> root = Json.parseObject(json);
            if (Json.intValue(root, "schema", 0) != SCHEMA) return null;
            List<Object> raw = Json.asArray(root.get("modules"));
            if (raw == null) return null;
            File files = context.getFilesDir();
            File modulesRoot = new File(files, "modules");
            List<InstalledModule> result = new ArrayList<>();
            for (Object item : raw) {
                Map<String, Object> node = Json.asObject(item);
                if (node == null) continue;
                String id = Json.string(node, "id");
                String version = Json.string(node, "version");
                if (id == null || version == null) continue;
                File dir = new File(new File(modulesRoot, id), version);
                if (!dir.isDirectory()) continue;
                result.add(new InstalledModule(
                        id,
                        Json.string(node, "name", id),
                        version,
                        Json.string(node, "description", ""),
                        Json.string(node, "tagline", ""),
                        Json.string(node, "author", ""),
                        Json.string(node, "authorContact", ""),
                        Json.string(node, "glyph", "spark"),
                        Json.string(node, "icon", ""),
                        Collections.<ModuleManifest.Permission>emptyList(),
                        Json.string(node, "kind", ""),
                        Json.string(node, "accent", ""),
                        Json.string(node, "chrome", ""),
                        DisplayConfig.defaults(Json.string(node, "chrome", "")),
                        Json.string(node, "entry", "index.html"),
                        Json.string(node, "data", ""),
                        "",
                        Json.stringList(node, "capabilities"),
                        Json.stringList(node, "versions"),
                        0L,
                        0L,
                        dir
                ));
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    /** 写索引（调用方负责在 IO 线程调用）。 */
    public static void write(Context context, List<InstalledModule> modules) {
        if (modules == null) return;
        try {
            List<Object> items = new ArrayList<>();
            for (InstalledModule module : modules) {
                Map<String, Object> node = new java.util.LinkedHashMap<>();
                node.put("id", module.id());
                node.put("name", module.name());
                node.put("version", module.version());
                node.put("description", module.description());
                node.put("tagline", module.tagline());
                node.put("author", module.author());
                node.put("authorContact", module.authorContact());
                node.put("glyph", module.glyph());
                node.put("icon", module.icon());
                node.put("kind", module.kind());
                node.put("accent", module.accent());
                node.put("chrome", module.chrome());
                node.put("entry", module.entry());
                node.put("data", module.data());
                node.put("capabilities", new ArrayList<Object>(module.capabilities()));
                node.put("versions", new ArrayList<Object>(module.versions()));
                items.add(node);
            }
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            root.put("schema", SCHEMA);
            root.put("updatedAt", System.currentTimeMillis());
            root.put("modules", items);

            File target = new File(context.getFilesDir(), FILE_NAME);
            File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(Json.stringify(root).getBytes(StandardCharsets.UTF_8));
            }
            if (!temp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                //noinspection ResultOfMethodCallIgnored
                temp.renameTo(target);
            }
        } catch (Exception ignored) {
            // 索引写失败不影响使用，下次扫盘还会重建。
        }
    }
}
