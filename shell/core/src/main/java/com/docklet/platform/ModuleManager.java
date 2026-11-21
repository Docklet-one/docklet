package com.docklet.platform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs, validates, lists, rolls back and uninstalls modules.
 *
 * <p>This class is intentionally Android-free so the package pipeline can be
 * covered by plain JVM tests. The Android layer only supplies directory paths.</p>
 */
public final class ModuleManager {

    public static final long MAX_PACKAGE_BYTES = 50L * 1024 * 1024;
    public static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    public static final long MAX_SINGLE_FILE_BYTES = 25L * 1024 * 1024;
    public static final int MAX_ZIP_ENTRIES = 2000;

    private static final String CURRENT_FILE = "current";

    private final File modulesRoot;
    private final File dataRoot;
    private final File cacheRoot;
    private final String shellVersion;

    public ModuleManager(File modulesRoot, File dataRoot, File cacheRoot, String shellVersion) {
        if (modulesRoot == null || dataRoot == null || cacheRoot == null) {
            throw new IllegalArgumentException("目录不能为空");
        }
        if (!SemVer.isValid(shellVersion)) {
            throw new IllegalArgumentException("壳版本必须是 x.y.z");
        }
        this.modulesRoot = modulesRoot;
        this.dataRoot = dataRoot;
        this.cacheRoot = cacheRoot;
        this.shellVersion = shellVersion;
        ensureDirectory(modulesRoot, "modules");
        ensureDirectory(dataRoot, "data");
        ensureDirectory(cacheRoot, "cache");
        recover();
    }

    private void ensureDirectory(File directory, String label) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建 " + label + " 目录: " + directory);
        }
        if (!directory.isDirectory()) {
            throw new IllegalStateException(label + " 路径不是目录: " + directory);
        }
    }

    /**
     * Cleans interrupted staging/trash directories and repairs a missing/current
     * pointer. Safe to call repeatedly.
     */
    public synchronized void recover() {
        cleanupChildren(modulesRoot, ".staging-");
        cleanupChildren(modulesRoot, ".trash-");
        cleanupChildren(dataRoot, ".trash-");
        cleanupChildren(dataRoot, ".staging-");

        File[] modules = modulesRoot.listFiles();
        if (modules == null) return;
        for (File moduleDir : modules) {
            if (!moduleDir.isDirectory() || !ModuleManifest.isValidId(moduleDir.getName())) continue;
            cleanupChildren(moduleDir, ".new-");
            cleanupChildren(moduleDir, ".tmp-");
            try {
                currentVersion(moduleDir.getName());
            } catch (ModuleException ignored) {
                // A broken module remains visible to diagnostics; never delete data here.
            }
        }
    }

    private void cleanupChildren(File root, String prefix) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.getName().startsWith(prefix)) FileOps.deleteRecursively(child);
        }
    }

    /**
     * Validates and installs a {@code .pkg}. The current version is not touched
     * unless extraction, manifest validation and the atomic version rename all
     * succeed.
     */
    public synchronized InstalledModule install(File packageFile) throws ModuleException {
        return install(packageFile, null, null);
    }

    /**
     * Installs a package and checks that its manifest matches the selected
     * remote catalog entry. Used for remote downloads so a swapped package
     * cannot install under a different id/version.
     */
    public synchronized InstalledModule install(
            File packageFile,
            String expectedId,
            String expectedVersion
    ) throws ModuleException {
        return install(packageFile, expectedId, expectedVersion, false);
    }

    /**
     * @param allowDowngrade 用户明确确认“装旧版本”时传 true；默认拒绝降级。
     */
    public synchronized InstalledModule install(
            File packageFile,
            String expectedId,
            String expectedVersion,
            boolean allowDowngrade
    ) throws ModuleException {
        if (packageFile == null || !packageFile.isFile()) {
            throw new ModuleException(ModuleException.PACKAGE_MISSING, "安装包不存在");
        }
        if (!packageFile.getName().toLowerCase(Locale.ROOT).endsWith(".pkg")) {
            throw new ModuleException(ModuleException.PACKAGE_CORRUPT, "安装包扩展名必须是 .pkg");
        }
        if (packageFile.length() <= 0) {
            throw new ModuleException(ModuleException.PACKAGE_CORRUPT, "安装包为空");
        }
        if (packageFile.length() > MAX_PACKAGE_BYTES) {
            throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "安装包超过 50 MB");
        }

        File staging = new File(modulesRoot, ".staging-" + UUID.randomUUID());
        try {
            if (!staging.mkdirs()) {
                throw new ModuleException(ModuleException.INSTALL_FAILED, "无法创建解包临时目录");
            }
            extractPackage(packageFile, staging);

            File manifestFile = new File(staging, ModuleManifest.FILE_NAME);
            ModuleManifest manifest = ModuleManifest.read(manifestFile);

            if (expectedId != null && !expectedId.equals(manifest.id())) {
                throw new ModuleException(
                        ModuleException.REMOTE_ID_MISMATCH,
                        "catalog 模块 id 与包内不一致"
                );
            }
            if (expectedVersion != null && !expectedVersion.equals(manifest.version())) {
                throw new ModuleException(
                        ModuleException.REMOTE_ID_MISMATCH,
                        "catalog 版本与包内不一致"
                );
            }

            if (SemVer.compare(manifest.minShell(), shellVersion) > 0) {
                throw new ModuleException(
                        ModuleException.UNSUPPORTED_SHELL,
                        "模块要求壳版本 ≥ " + manifest.minShell() + "，当前为 " + shellVersion
                );
            }

            File entry = FileOps.child(staging, manifest.entry());
            if (!entry.isFile()) {
                throw new ModuleException(
                        ModuleException.ENTRY_MISSING,
                        "入口文件不存在: " + manifest.entry()
                );
            }
            // 图标由模块自己提供：必须有，png/webp，≤50KB。
            String iconPath = manifest.icon();
            if (iconPath == null || iconPath.trim().isEmpty()) {
                throw new ModuleException(
                        ModuleException.MANIFEST_INVALID,
                        "缺少图标：module.json 需要 icon 字段（png/webp，≤50KB）"
                );
            }
            String iconLower = iconPath.toLowerCase(Locale.ROOT);
            if (!iconLower.endsWith(".png") && !iconLower.endsWith(".webp")) {
                throw new ModuleException(
                        ModuleException.MANIFEST_INVALID,
                        "图标只支持 png/webp: " + iconPath
                );
            }
            File icon = FileOps.child(staging, iconPath);
            if (!icon.isFile()) {
                throw new ModuleException(
                        ModuleException.MANIFEST_INVALID,
                        "图标文件不存在: " + iconPath
                );
            }
            if (icon.length() > 50L * 1024L) {
                throw new ModuleException(
                        ModuleException.MANIFEST_INVALID,
                        "图标超过 50KB: " + iconPath
                );
            }

            File moduleDir = new File(modulesRoot, manifest.id());
            List<String> installed = installedVersions(manifest.id());
            String current = currentVersion(manifest.id());
            String baseline = current;
            if (baseline == null && !installed.isEmpty()) {
                baseline = installed.get(installed.size() - 1);
            }
            if (baseline != null) {
                int comparison = SemVer.compare(manifest.version(), baseline);
                if (comparison < 0 && !allowDowngrade) {
                    throw new ModuleException(
                            ModuleException.VERSION_DOWNGRADE,
                            "不允许降级：当前 " + baseline + "，包内 " + manifest.version()
                    );
                }
                if (comparison == 0) {
                    throw new ModuleException(
                            ModuleException.VERSION_CONFLICT,
                            "当前版本已是 " + manifest.version()
                    );
                }
            }
            if (installed.contains(manifest.version())) {
                throw new ModuleException(
                        ModuleException.VERSION_CONFLICT,
                        "版本目录已存在: " + manifest.version()
                );
            }

            File target = new File(moduleDir, manifest.version());
            if (target.exists()) {
                throw new ModuleException(
                        ModuleException.VERSION_CONFLICT,
                        "版本目录已存在: " + target.getName()
                );
            }
            ensureDirectory(moduleDir, "module");
            FileOps.move(staging.toPath(), target.toPath());

            try {
                writeCurrent(manifest.id(), manifest.version());
            } catch (IOException error) {
                throw new ModuleException(
                        ModuleException.INSTALL_FAILED,
                        "写入 current 失败，旧版本仍可用",
                        error
                );
            }

            InstalledModule result = findInstalled(manifest.id());
            if (result == null) {
                throw new ModuleException(ModuleException.INSTALL_FAILED, "安装后无法读取模块信息");
            }
            // 安装已经生效：只保留当前版本，其它版本目录全部清掉。
            pruneVersions(manifest.id());
            InstalledModule pruned = findInstalled(manifest.id());
            return pruned == null ? result : pruned;
        } catch (ModuleException error) {
            throw error;
        } catch (IOException error) {
            throw new ModuleException(ModuleException.INSTALL_FAILED, "安装失败: " + error.getMessage(), error);
        } finally {
            FileOps.deleteRecursively(staging);
        }
    }

    private void extractPackage(File packageFile, File staging) throws ModuleException {
        Set<String> seen = new HashSet<>();
        long total = 0L;
        int entries = 0;

        try (ZipFile zip = new ZipFile(packageFile)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries += 1;
                if (entries > MAX_ZIP_ENTRIES) {
                    throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "包内文件条目超过 2000");
                }

                String rawName = entry.getName();
                if (rawName == null || rawName.isEmpty()) {
                    throw new ModuleException(ModuleException.UNSAFE_ENTRY, "包内存在空路径条目");
                }
                if (rawName.indexOf('\u0000') >= 0) {
                    throw new ModuleException(ModuleException.UNSAFE_ENTRY, "包内路径包含 NUL");
                }
                if (rawName.startsWith("/") || rawName.startsWith("\\")
                        || rawName.contains("\\") || rawName.matches("^[A-Za-z]:.*")) {
                    throw new ModuleException(ModuleException.UNSAFE_ENTRY, "包内存在绝对路径: " + rawName);
                }

                String normalized = entry.isDirectory() && rawName.endsWith("/")
                        ? rawName.substring(0, rawName.length() - 1)
                        : rawName;
                if (normalized.isEmpty()) continue;
                if (normalized.contains("//")) {
                    throw new ModuleException(ModuleException.UNSAFE_ENTRY, "包内存在空路径段: " + rawName);
                }
                String[] segments = normalized.split("/");
                for (String segment : segments) {
                    if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                        throw new ModuleException(ModuleException.UNSAFE_ENTRY, "包内路径越界: " + rawName);
                    }
                }
                if (!seen.add(normalized)) {
                    throw new ModuleException(ModuleException.PACKAGE_CORRUPT, "包内存在重复条目: " + rawName);
                }

                File target = FileOps.child(staging, normalized);
                if (entry.isDirectory()) {
                    if (!target.exists() && !target.mkdirs()) {
                        throw new ModuleException(ModuleException.INSTALL_FAILED, "无法创建目录: " + normalized);
                    }
                    continue;
                }

                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new ModuleException(ModuleException.INSTALL_FAILED, "无法创建父目录: " + normalized);
                }

                if (entry.getSize() > MAX_SINGLE_FILE_BYTES) {
                    throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "单文件超过 25 MB: " + normalized);
                }

                long written = 0L;
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        written += read;
                        total += read;
                        if (written > MAX_SINGLE_FILE_BYTES) {
                            throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "单文件超过 25 MB: " + normalized);
                        }
                        if (total > MAX_UNCOMPRESSED_BYTES) {
                            throw new ModuleException(ModuleException.PACKAGE_TOO_LARGE, "解压后超过 100 MB");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException error) {
            throw new ModuleException(ModuleException.PACKAGE_CORRUPT, "无法读取 zip 安装包: " + error.getMessage(), error);
        }

        if (entries == 0) {
            throw new ModuleException(ModuleException.PACKAGE_CORRUPT, "安装包为空");
        }
    }

    public synchronized List<InstalledModule> listInstalled() {
        List<InstalledModule> result = new ArrayList<>();
        File[] children = modulesRoot.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (!child.isDirectory() || !ModuleManifest.isValidId(child.getName())) continue;
            try {
                InstalledModule module = findInstalled(child.getName());
                if (module != null) result.add(module);
            } catch (ModuleException ignored) {
                // Skip modules whose current manifest is broken; they can be reinstalled.
            }
        }
        result.sort(Comparator.comparing(InstalledModule::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized InstalledModule get(String id) throws ModuleException {
        validateId(id);
        InstalledModule module = findInstalled(id);
        if (module == null) {
            throw new ModuleException(ModuleException.NOT_INSTALLED, "模块未安装: " + id);
        }
        return module;
    }

    private InstalledModule findInstalled(String id) throws ModuleException {
        String current = currentVersion(id);
        if (current == null) return null;
        File versionDir = new File(new File(modulesRoot, id), current);
        if (!versionDir.isDirectory()) return null;
        File manifestFile = new File(versionDir, ModuleManifest.FILE_NAME);
        ModuleManifest manifest = ModuleManifest.read(manifestFile);
        List<String> versions = installedVersions(id);
        File data = new File(dataRoot, id);
        return new InstalledModule(
                manifest.id(),
                manifest.name(),
                current,
                manifest.description(),
                manifest.tagline(),
                manifest.author(),
                manifest.authorContact(),
                manifest.glyph(),
                manifest.icon(),
                manifest.permissions(),
                manifest.kind(),
                manifest.accent(),
                manifest.chrome(),
                manifest.display(),
                manifest.entry(),
                manifest.data(),
                origin(id),
                manifest.capabilities(),
                versions,
                0L,
                0L,
                versionDir
        );
    }

    /**
     * 轻量校验：索引里的 id/version 与磁盘一致时返回 true。
     * 只读每个模块的 current 文件，不解析清单，用于冷启动跳过整轮扫描。
     */
    public boolean isConsistent(List<InstalledModule> indexed) {
        if (indexed == null) return false;
        File[] children = modulesRoot.listFiles();
        if (children == null) return indexed.isEmpty();
        java.util.Set<String> diskIds = new java.util.HashSet<>();
        for (File child : children) {
            if (child.isDirectory() && ModuleManifest.isValidId(child.getName())) {
                diskIds.add(child.getName());
            }
        }
        if (diskIds.size() != indexed.size()) return false;
        for (InstalledModule module : indexed) {
            if (!diskIds.contains(module.id())) return false;
            try {
                String current = currentVersion(module.id());
                if (current == null || !current.equals(module.version())) return false;
            } catch (Exception error) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按需计算占用：{代码字节, 数据字节}。
     * 列表接口不算大小（冷启动不该遍历所有目录），只有详情/设置需要时才调。
     */
    public long[] measure(String id) throws ModuleException {
        validateId(id);
        long code = 0L;
        String current = currentVersion(id);
        if (current != null) {
            code = FileOps.sizeOf(new File(new File(modulesRoot, id), current));
        }
        long data = FileOps.sizeOf(new File(dataRoot, id));
        return new long[]{code, data};
    }

    public synchronized String currentVersion(String id) throws ModuleException {
        validateId(id);
        File moduleDir = new File(modulesRoot, id);
        if (!moduleDir.isDirectory()) return null;

        File currentFile = new File(moduleDir, CURRENT_FILE);
        if (currentFile.isFile()) {
            try {
                String value = FileOps.readUtf8(currentFile).trim();
                if (SemVer.isValid(value) && new File(moduleDir, value).isDirectory()) {
                    return value;
                }
            } catch (IOException ignored) {
                // Fall through to repair.
            }
        }

        List<String> versions = installedVersions(id);
        if (versions.isEmpty()) {
            return null;
        }
        String repaired = versions.get(versions.size() - 1);
        try {
            writeCurrent(id, repaired);
        } catch (IOException ignored) {
            // Still return the best installed version for this session.
        }
        return repaired;
    }

    public synchronized boolean uninstall(String id, boolean deleteData) throws ModuleException {
        validateId(id);
        File moduleDir = new File(modulesRoot, id);
        File data = new File(dataRoot, id);
        boolean hasModule = moduleDir.isDirectory();
        boolean hasData = data.isDirectory();
        if (!hasModule && !(deleteData && hasData)) {
            throw new ModuleException(ModuleException.NOT_INSTALLED, "模块未安装: " + id);
        }

        String stamp = Long.toString(System.currentTimeMillis());
        File moduleTrash = null;
        File dataTrash = null;
        try {
            if (hasModule) {
                moduleTrash = new File(modulesRoot, ".trash-" + id + "-" + stamp);
                FileOps.move(moduleDir.toPath(), moduleTrash.toPath());
            }
            if (deleteData && hasData) {
                dataTrash = new File(dataRoot, ".trash-" + id + "-" + stamp);
                FileOps.move(data.toPath(), dataTrash.toPath());
            }
        } catch (IOException error) {
            throw new ModuleException(ModuleException.UNINSTALL_FAILED, "卸载失败: " + error.getMessage(), error);
        } finally {
            if (moduleTrash != null) FileOps.deleteRecursively(moduleTrash);
            if (dataTrash != null) FileOps.deleteRecursively(dataTrash);
        }
        return true;
    }

    public synchronized void deleteData(String id) throws ModuleException {
        validateId(id);
        File data = new File(dataRoot, id);
        if (!data.isDirectory()) return;
        File trash = new File(dataRoot, ".trash-" + id + "-" + System.currentTimeMillis());
        try {
            FileOps.move(data.toPath(), trash.toPath());
        } catch (IOException error) {
            throw new ModuleException(ModuleException.UNINSTALL_FAILED, "删除数据失败", error);
        } finally {
            FileOps.deleteRecursively(trash);
        }
    }

    private void writeCurrent(String id, String version) throws IOException {
        File moduleDir = new File(modulesRoot, id);
        if (!moduleDir.isDirectory() && !moduleDir.mkdirs()) {
            throw new IOException("无法创建模块目录");
        }
        File temp = new File(moduleDir, "." + CURRENT_FILE + "-" + UUID.randomUUID() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write((version + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        try {
            FileOps.move(temp.toPath(), new File(moduleDir, CURRENT_FILE).toPath());
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    /** 只保留当前版本：安装成功后其它版本目录全部删掉。 */
    private void pruneVersions(String id) {
        try {
            String current = currentVersion(id);
            if (current == null) return;
            for (String version : installedVersions(id)) {
                if (version.equals(current)) continue;
                FileOps.deleteRecursively(new File(moduleDir(id), version));
            }
        } catch (Exception ignored) {
            // 清理失败不影响模块本身可用。
        }
    }

    public synchronized List<String> installedVersions(String id) throws ModuleException {
        validateId(id);
        List<String> versions = new ArrayList<>();
        File moduleDir = new File(modulesRoot, id);
        File[] children = moduleDir.listFiles();
        if (children == null) return versions;
        for (File child : children) {
            if (!child.isDirectory() || !SemVer.isValid(child.getName())) continue;
            versions.add(child.getName());
        }
        versions.sort((left, right) -> SemVer.compare(left, right));
        return versions;
    }

    private void validateId(String id) throws ModuleException {
        if (!ModuleManifest.isValidId(id)) {
            throw new ModuleException(ModuleException.INVALID_ID, "非法模块 id: " + id);
        }
    }

    public File modulesRoot() {
        return modulesRoot;
    }

    public File dataRoot() {
        return dataRoot;
    }

    public File cacheRoot() {
        return cacheRoot;
    }

    public String shellVersion() {
        return shellVersion;
    }

    public File moduleDir(String id) {
        return new File(modulesRoot, id);
    }

    public File currentDir(String id) throws ModuleException {
        String current = currentVersion(id);
        return current == null ? null : new File(new File(modulesRoot, id), current);
    }

    public File dataDir(String id) {
        return new File(dataRoot, id);
    }

    public static String origin(String id) {
        return "https://" + id + ".module.local";
    }

}
