package com.docklet.platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Remote catalog contract:
 *
 * <pre>
 * {
 *   "spec": 1,
 *   "modules": [{
 *     "id": "notes", "name": "清单", "kind": "效率",
 *     "version": "1.0.0", "description": "...", "accent": "#5E7C6A",
 *     "url": "modules/notes-1.0.0.pkg",
 *     "sha256": "64 hex chars", "size": 6442
 *   }]
 * }
 * </pre>
 */
public final class RemoteCatalog {

    public static final int SPEC = 1;
    public static final int MAX_CATALOG_BYTES = 512 * 1024;
    private static final int MAX_MODULES = 100;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern ACCENT = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final URL source;
    private final List<RemoteModule> modules;

    private RemoteCatalog(URL source, List<RemoteModule> modules) {
        this.source = source;
        this.modules = Collections.unmodifiableList(modules);
    }

    public URL source() {
        return source;
    }

    public List<RemoteModule> modules() {
        return modules;
    }

    public static RemoteCatalog load(URL url) throws ModuleException {
        validateCatalogUrl(url);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if ("https".equalsIgnoreCase(url.getProtocol())
                    && !"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new ModuleException(
                        ModuleException.REMOTE_URL_REJECTED,
                        "HTTPS catalog 被重定向到 HTTP，已拒绝"
                );
            }
            if (status < 200 || status >= 300) {
                throw new ModuleException(
                        ModuleException.REMOTE_CATALOG_ERROR,
                        "catalog 请求失败: HTTP " + status
                );
            }
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = readLimited(in, MAX_CATALOG_BYTES);
            }
            return parse(body, url);
        } catch (ModuleException error) {
            throw error;
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.REMOTE_CATALOG_ERROR,
                    "无法加载 catalog: " + error.getMessage(),
                    error
            );
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static RemoteCatalog parse(String json, URL source) throws ModuleException {
        validateCatalogUrl(source);
        Map<String, Object> root;
        try {
            root = Json.parseObject(json);
        } catch (Json.JsonException error) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 不是合法 JSON");
        }
        int spec = Json.intValue(root, "spec", 1);
        if (spec != SPEC) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "不支持的 catalog spec: " + spec);
        }
        List<Object> rawModules = Json.asArray(root.get("modules"));
        if (rawModules == null) rawModules = Json.asArray(root.get("apps"));
        if (rawModules == null) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 缺少 modules 数组");
        }
        if (rawModules.size() > MAX_MODULES) {
            throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 模块数量过多");
        }

        URI base;
        try {
            base = source.toURI();
        } catch (Exception error) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "catalog URL 不合法");
        }
        Set<String> ids = new HashSet<>();
        List<RemoteModule> modules = new ArrayList<>();
        for (Object raw : rawModules) {
            Map<String, Object> item = Json.asObject(raw);
            if (item == null) continue;
            String id = Json.string(item, "id");
            if (!ModuleManifest.isValidId(id)) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 含非法模块 id");
            }
            if (!ids.add(id)) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 含重复模块 id: " + id);
            }
            String name = Json.string(item, "name");
            if (name == null || name.trim().isEmpty()) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, id + " 缺少 name");
            }
            String version = Json.string(item, "version");
            if (!SemVer.isValid(version)) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, id + " 版本号不合法");
            }
            String sha = Json.string(item, "sha256");
            if (sha == null || !SHA256.matcher(sha).matches()) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, id + " 缺少合法 sha256");
            }
            String relative = Json.string(item, "url");
            if (relative == null || relative.trim().isEmpty()) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, id + " 缺少下载 url");
            }
            URI resolved;
            try {
                resolved = base.resolve(relative);
            } catch (Exception error) {
                throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, id + " 下载 URL 不合法");
            }
            validatePackageUrl(resolved, source);
            String path = resolved.getPath();
            if (path == null || !path.toLowerCase(Locale.ROOT).endsWith(".pkg")) {
                throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, id + " 下载 URL 必须以 .pkg 结尾");
            }
            long size = Json.longValue(item, "size", 0L);
            if (size < 0L || size > ModuleManager.MAX_PACKAGE_BYTES) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, id + " size 超出范围");
            }
            String description = Json.string(item, "description", "");
            String kind = Json.string(item, "kind", "模块");
            String accent = Json.string(item, "accent", "#D9792B");
            if (!ACCENT.matcher(accent).matches()) {
                accent = "#D9792B";
            }
            modules.add(new RemoteModule(
                    id,
                    name.trim(),
                    kind,
                    version,
                    description,
                    accent.toUpperCase(Locale.ROOT),
                    sha.toLowerCase(Locale.ROOT),
                    size,
                    resolved.toString()
            ));
        }
        return new RemoteCatalog(source, modules);
    }

    private static void validateCatalogUrl(URL url) throws ModuleException {
        if (url == null) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "catalog URL 为空");
        }
        String scheme = url.getProtocol();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "catalog 只支持 http/https");
        }
    }

    private static void validatePackageUrl(URI uri, URL catalog) throws ModuleException {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new ModuleException(ModuleException.REMOTE_URL_REJECTED, "模块包只支持 http/https");
        }
        if ("https".equalsIgnoreCase(catalog.getProtocol()) && "http".equalsIgnoreCase(scheme)) {
            throw new ModuleException(
                    ModuleException.REMOTE_URL_REJECTED,
                    "HTTPS catalog 不允许降级到 HTTP 模块包"
            );
        }
    }

    private static String readLimited(InputStream in, int max) throws IOException, ModuleException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > max) {
                throw new ModuleException(ModuleException.REMOTE_CATALOG_ERROR, "catalog 超过大小限制");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
