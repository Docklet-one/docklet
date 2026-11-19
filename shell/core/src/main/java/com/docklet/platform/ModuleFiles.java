package com.docklet.platform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Module-private file storage under {@code data/<id>/files/}.
 *
 * <p>Only relative paths are accepted; canonical path checks prevent escaping
 * the module's own data directory. Text and base64 payloads are supported.</p>
 */
public final class ModuleFiles {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    public static final int MAX_LIST_ENTRIES = 2000;
    private static final int MAX_PATH_LENGTH = 240;

    private final File dataRoot;

    public ModuleFiles(File dataRoot) {
        if (dataRoot == null) throw new IllegalArgumentException("dataRoot 不能为空");
        this.dataRoot = dataRoot;
    }

    public synchronized Map<String, Object> write(
            String moduleId,
            String path,
            String data,
            String encoding
    ) throws ModuleException {
        String clean = normalizeRelative(path, false);
        String normalizedEncoding = normalizeEncoding(encoding);
        byte[] payload;
        try {
            if ("base64".equals(normalizedEncoding)) {
                payload = Base64.getDecoder().decode(data == null ? "" : data);
            } else {
                payload = (data == null ? "" : data).getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException error) {
            throw new ModuleException(ModuleException.FILES_ERROR, "base64 数据不合法");
        }
        if (payload.length > MAX_FILE_BYTES) {
            throw new ModuleException(
                    ModuleException.FILE_TOO_LARGE,
                    "单文件不能超过 5 MB"
            );
        }

        File root = ensureRoot(moduleId);
        File target = resolve(root, clean);
        if (target.isDirectory()) {
            throw new ModuleException(ModuleException.FILE_CONFLICT, "目标是一个目录");
        }
        long existing = target.isFile() ? target.length() : 0L;
        long total = sizeOf(root);
        if (total - existing + payload.length > MAX_TOTAL_BYTES) {
            throw new ModuleException(ModuleException.FILE_TOO_LARGE, "模块文件总大小不能超过 50 MB");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new ModuleException(ModuleException.FILES_ERROR, "无法创建目录");
        }
        File temp = new File(
                parent,
                "." + target.getName() + "." + UUID.randomUUID() + ".tmp"
        );
        try {
            Files.write(temp.toPath(), payload);
            try {
                FileOps.move(temp.toPath(), target.toPath());
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } catch (IOException error) {
            throw new ModuleException(ModuleException.FILES_ERROR, "写入失败: " + error.getMessage(), error);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", clean);
        result.put("bytes", payload.length);
        result.put("encoding", normalizedEncoding);
        return result;
    }

    public synchronized Map<String, Object> read(
            String moduleId,
            String path,
            String encoding
    ) throws ModuleException {
        String clean = normalizeRelative(path, false);
        String normalizedEncoding = normalizeEncoding(encoding);
        File root = ensureRoot(moduleId);
        File target = resolve(root, clean);
        if (!target.isFile()) {
            throw new ModuleException(ModuleException.FILE_NOT_FOUND, "文件不存在: " + clean);
        }
        if (target.length() > MAX_FILE_BYTES) {
            throw new ModuleException(ModuleException.FILE_TOO_LARGE, "单文件不能超过 5 MB");
        }
        try {
            byte[] bytes = Files.readAllBytes(target.toPath());
            String data = "base64".equals(normalizedEncoding)
                    ? Base64.getEncoder().encodeToString(bytes)
                    : new String(bytes, StandardCharsets.UTF_8);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", clean);
            result.put("data", data);
            result.put("encoding", normalizedEncoding);
            result.put("bytes", bytes.length);
            return result;
        } catch (IOException error) {
            throw new ModuleException(ModuleException.FILES_ERROR, "读取失败: " + error.getMessage(), error);
        }
    }

    public synchronized List<Map<String, Object>> list(String moduleId, String path)
            throws ModuleException {
        String clean = normalizeRelative(path, true);
        File root = ensureRoot(moduleId);
        File directory = resolve(root, clean);
        if (!directory.exists()) {
            throw new ModuleException(ModuleException.FILE_NOT_FOUND, "目录不存在: " + clean);
        }
        if (!directory.isDirectory()) {
            throw new ModuleException(ModuleException.FILE_CONFLICT, "路径不是目录: " + clean);
        }
        File[] children = directory.listFiles();
        if (children == null) return Collections.emptyList();
        if (children.length > MAX_LIST_ENTRIES) {
            throw new ModuleException(ModuleException.FILES_ERROR, "目录条目过多");
        }
        List<File> files = new ArrayList<>();
        Collections.addAll(files, children);
        files.sort(Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (File child : files) {
            String childPath = clean.isEmpty() ? child.getName() : clean + "/" + child.getName();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", child.getName());
            item.put("path", childPath);
            item.put("dir", child.isDirectory());
            item.put("size", child.isFile() ? child.length() : 0L);
            item.put("modified", child.lastModified());
            result.add(item);
        }
        return result;
    }

    public synchronized Map<String, Object> delete(
            String moduleId,
            String path,
            boolean recursive
    ) throws ModuleException {
        String clean = normalizeRelative(path, false);
        File root = ensureRoot(moduleId);
        File target = resolve(root, clean);
        if (!target.exists()) {
            throw new ModuleException(ModuleException.FILE_NOT_FOUND, "文件不存在: " + clean);
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null && children.length > 0 && !recursive) {
                throw new ModuleException(ModuleException.FILE_CONFLICT, "目录非空，需要 recursive=true");
            }
            FileOps.deleteRecursively(target);
        } else {
            try {
                Files.deleteIfExists(target.toPath());
            } catch (IOException error) {
                throw new ModuleException(ModuleException.FILES_ERROR, "删除失败: " + error.getMessage(), error);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", clean);
        result.put("deleted", true);
        return result;
    }

    public synchronized boolean exists(String moduleId, String path) throws ModuleException {
        String clean = normalizeRelative(path, true);
        return resolve(ensureRoot(moduleId), clean).exists();
    }

    /** Returns the canonical file for sharing/downloading. */
    public synchronized File fileFor(String moduleId, String path) throws ModuleException {
        String clean = normalizeRelative(path, false);
        return resolve(ensureRoot(moduleId), clean);
    }

    public synchronized long usage(String moduleId) {
        if (!ModuleManifest.isValidId(moduleId)) return 0L;
        return sizeOf(new File(new File(dataRoot, moduleId), "files"));
    }

    private File ensureRoot(String moduleId) throws ModuleException {
        if (!ModuleManifest.isValidId(moduleId)) {
            throw new ModuleException(ModuleException.INVALID_ID, "非法模块 id: " + moduleId);
        }
        File root = new File(new File(dataRoot, moduleId), "files");
        if (!root.exists() && !root.mkdirs()) {
            throw new ModuleException(ModuleException.FILES_ERROR, "无法创建模块文件目录");
        }
        if (!root.isDirectory()) {
            throw new ModuleException(ModuleException.FILES_ERROR, "模块文件路径不是目录");
        }
        return root;
    }

    private File resolve(File root, String clean) throws ModuleException {
        try {
            File canonicalRoot = root.getCanonicalFile();
            File target = clean.isEmpty()
                    ? canonicalRoot
                    : new File(canonicalRoot, clean).getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            if (!target.equals(canonicalRoot)
                    && !target.getPath().startsWith(rootPath + File.separator)) {
                throw new ModuleException(ModuleException.PATH_REJECTED, "路径越界: " + clean);
            }
            return target;
        } catch (IOException error) {
            throw new ModuleException(ModuleException.PATH_REJECTED, "路径不合法: " + clean, error);
        }
    }

    private String normalizeRelative(String path, boolean allowEmpty) throws ModuleException {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty()) {
            if (allowEmpty) return "";
            throw new ModuleException(ModuleException.PATH_REJECTED, "路径不能为空");
        }
        if (value.length() > MAX_PATH_LENGTH) {
            throw new ModuleException(ModuleException.PATH_REJECTED, "路径过长");
        }
        if (value.startsWith("/") || value.startsWith("\\")
                || value.contains("\\") || value.contains("\u0000")) {
            throw new ModuleException(ModuleException.PATH_REJECTED, "只允许包内相对路径");
        }
        if (value.matches("^[A-Za-z]:.*")) {
            throw new ModuleException(ModuleException.PATH_REJECTED, "不允许盘符路径");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new ModuleException(ModuleException.PATH_REJECTED, "路径包含非法路径段");
            }
            for (int i = 0; i < segment.length(); i += 1) {
                if (segment.charAt(i) < 0x20) {
                    throw new ModuleException(ModuleException.PATH_REJECTED, "路径包含控制字符");
                }
            }
        }
        return value;
    }

    private String normalizeEncoding(String encoding) throws ModuleException {
        if (encoding == null || encoding.isEmpty()) return "utf8";
        String value = encoding.toLowerCase(Locale.ROOT);
        if ("utf8".equals(value) || "utf-8".equals(value) || "text".equals(value)) return "utf8";
        if ("base64".equals(value) || "binary".equals(value)) return "base64";
        throw new ModuleException(ModuleException.FILES_ERROR, "不支持的编码: " + encoding);
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile()) {
                    total += child.length();
                } else {
                    total += sizeOf(child);
                }
            }
        }
        return total;
    }
}
