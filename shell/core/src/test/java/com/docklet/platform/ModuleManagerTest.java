package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ModuleManagerTest {

    private File root;
    private File modules;
    private File data;
    private File cache;
    private ModuleManager manager;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("docklet-core-test").toFile();
        modules = new File(root, "modules");
        data = new File(root, "data");
        cache = new File(root, "cache");
        manager = new ModuleManager(modules, data, cache, "1.0.0");
    }

    @After
    public void tearDown() {
        delete(root);
    }

    @Test
    public void installsAndListsModule() throws Exception {
        File pkg = packageWith("demo", "1.0.0", "index.html", "index.html", "<h1>demo</h1>");
        InstalledModule module = manager.install(pkg);

        assertEquals("demo", module.id());
        assertEquals("1.0.0", module.version());
        assertEquals("https://demo.module.local", module.origin());
        assertEquals(1, manager.listInstalled().size());
        assertTrue(new File(modules, "demo/1.0.0/index.html").isFile());
        assertEquals("1.0.0", new String(
                Files.readAllBytes(new File(modules, "demo/current").toPath()),
                StandardCharsets.UTF_8
        ).trim());
    }

    @Test
    public void remoteInstallRejectsCatalogMismatch() throws Exception {
        File pkg = packageWith("demo", "1.0.0", "index.html", "index.html", "v1");

        ModuleException error = assertThrows(ModuleException.class, () ->
                manager.install(pkg, "other", "1.0.0")
        );
        assertEquals(ModuleException.REMOTE_ID_MISMATCH, error.getCode());
        assertTrue(manager.listInstalled().isEmpty());

        ModuleException version = assertThrows(ModuleException.class, () ->
                manager.install(pkg, "demo", "2.0.0")
        );
        assertEquals(ModuleException.REMOTE_ID_MISMATCH, version.getCode());
        assertTrue(manager.listInstalled().isEmpty());
    }

    @Test
    public void updateKeepsOnlyTheCurrentVersion() throws Exception {
        manager.install(packageWith("demo", "1.0.0", "index.html", "index.html", "v1"));
        writeData("demo", "app.db", "keep-me");

        InstalledModule updated = manager.install(
                packageWith("demo", "1.1.0", "index.html", "index.html", "v2")
        );
        assertEquals("1.1.0", updated.version());
        assertEquals(1, updated.versions().size());
        assertEquals(1, manager.installedVersions("demo").size());
        assertEquals("1.1.0", manager.installedVersions("demo").get(0));
        assertEquals("keep-me", readData("demo", "app.db"));
    }

    @Test
    public void rejectsDowngradeAndSameVersionWithoutTouchingCurrent() throws Exception {
        manager.install(packageWith("demo", "2.0.0", "index.html", "index.html", "v2"));

        ModuleException downgrade = assertThrows(ModuleException.class, () ->
                manager.install(packageWith("demo", "1.9.9", "index.html", "index.html", "old"))
        );
        assertEquals(ModuleException.VERSION_DOWNGRADE, downgrade.getCode());
        assertEquals("2.0.0", manager.get("demo").version());

        ModuleException same = assertThrows(ModuleException.class, () ->
                manager.install(packageWith("demo", "2.0.0", "index.html", "index.html", "same"))
        );
        assertEquals(ModuleException.VERSION_CONFLICT, same.getCode());
    }

    @Test
    public void failedUpdateLeavesCurrentVersion() throws Exception {
        manager.install(packageWith("demo", "1.0.0", "index.html", "index.html", "v1"));
        File broken = packageWith("demo", "1.1.0", "index.html", null, null);

        ModuleException error = assertThrows(ModuleException.class, () -> manager.install(broken));
        assertEquals(ModuleException.ENTRY_MISSING, error.getCode());
        assertEquals("1.0.0", manager.get("demo").version());
        assertEquals(1, manager.installedVersions("demo").size());
    }

    @Test
    public void rejectsTooNewMinShell() throws Exception {
        File pkg = makePackage("demo", "1.0.0", "index.html", "index.html", "v1", "9.0.0");
        ModuleException error = assertThrows(ModuleException.class, () -> manager.install(pkg));
        assertEquals(ModuleException.UNSUPPORTED_SHELL, error.getCode());
        assertTrue(manager.listInstalled().isEmpty());
    }

    @Test
    public void rejectsPathTraversal() throws Exception {
        File traversal = new File(root, "traversal.pkg");
        Map<String, String> files = new LinkedHashMap<>();
        files.put(ModuleManifest.FILE_NAME, manifest("demo", "1.0.0", "index.html"));
        files.put("index.html", "ok");
        files.put("../evil.txt", "bad");
        zip(traversal, files);
        assertEquals(
                ModuleException.UNSAFE_ENTRY,
                assertThrows(ModuleException.class, () -> manager.install(traversal)).getCode()
        );
        assertFalse(new File(root, "evil.txt").exists());
        assertTrue(manager.listInstalled().isEmpty());
    }

    @Test
    public void uninstallCanKeepOrRemoveData() throws Exception {
        manager.install(packageWith("demo", "1.0.0", "index.html", "index.html", "v1"));
        writeData("demo", "state.txt", "important");

        manager.uninstall("demo", false);
        assertTrue(new File(modules, "demo").exists() == false);
        assertEquals("important", readData("demo", "state.txt"));
        assertTrue(manager.listInstalled().isEmpty());

        manager.install(packageWith("demo", "1.0.0", "index.html", "index.html", "v1"));
        // 列表不再预计算大小：用 measure() 现算。
        assertTrue(manager.measure("demo")[1] > 0);

        manager.uninstall("demo", true);
        assertFalse(new File(data, "demo").exists());
    }

    @Test
    public void repairsMissingCurrentPointer() throws Exception {
        manager.install(packageWith("demo", "1.0.0", "index.html", "index.html", "v1"));
        manager.install(packageWith("demo", "1.1.0", "index.html", "index.html", "v2"));
        assertTrue(new File(modules, "demo/current").delete());

        ModuleManager recovered = new ModuleManager(modules, data, cache, "1.0.0");
        assertEquals("1.1.0", recovered.get("demo").version());
    }

    @Test
    public void rejectsWrongExtension() throws Exception {
        File src = packageWith("demo", "1.0.0", "index.html", "index.html", "v1");
        File renamed = new File(root, "demo.zip");
        Files.copy(src.toPath(), renamed.toPath());
        assertEquals(
                ModuleException.PACKAGE_CORRUPT,
                assertThrows(ModuleException.class, () -> manager.install(renamed)).getCode()
        );
    }

    @Test
    public void rejectsManifestDataMismatchAndBadId() throws Exception {
        File pkg = new File(root, "bad.pkg");
        Map<String, String> files = new LinkedHashMap<>();
        files.put(ModuleManifest.FILE_NAME, "{\"id\":\"demo\",\"name\":\"Demo\","
                + "\"version\":\"1.0.0\",\"entry\":\"index.html\",\"data\":\"other\"}");
        files.put("index.html", "ok");
        zip(pkg, files);
        assertEquals(
                ModuleException.MANIFEST_INVALID,
                assertThrows(ModuleException.class, () -> manager.install(pkg)).getCode()
        );

        File badId = new File(root, "bad-id.pkg");
        Map<String, String> badIdFiles = new LinkedHashMap<>();
        badIdFiles.put(ModuleManifest.FILE_NAME,
                "{\"id\":\"../demo\",\"name\":\"Demo\",\"version\":\"1.0.0\",\"entry\":\"index.html\"}");
        badIdFiles.put("index.html", "ok");
        zip(badId, badIdFiles);
        assertEquals(
                ModuleException.INVALID_ID,
                assertThrows(ModuleException.class, () -> manager.install(badId)).getCode()
        );
    }

    @Test
    public void requiresModuleJsonAtPackageRoot() throws Exception {
        File pkg = new File(root, "nested.pkg");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("nested/" + ModuleManifest.FILE_NAME, manifest("demo", "1.0.0", "index.html"));
        files.put("nested/index.html", "ok");
        zip(pkg, files);
        assertEquals(
                ModuleException.MANIFEST_MISSING,
                assertThrows(ModuleException.class, () -> manager.install(pkg)).getCode()
        );
    }

    private File packageWith(String id, String version, String entry, String file, String content)
            throws IOException {
        return makePackage(id, version, entry, file, content, "1.0.0");
    }

    private File makePackage(
            String id,
            String version,
            String entry,
            String file,
            String content,
            String minShell
    ) throws IOException {
        File pkg = new File(root, id + "-" + version + ".pkg");
        Map<String, String> files = new LinkedHashMap<>();
        files.put(ModuleManifest.FILE_NAME, manifest(id, version, entry, minShell));
        files.put("icon.png", "fake-png");
        if (file != null) files.put(file, content);
        zip(pkg, files);
        return pkg;
    }

    private String manifest(String id, String version, String entry) {
        return manifest(id, version, entry, "1.0.0");
    }

    private String manifest(String id, String version, String entry, String minShell) {
        return "{"
                + "\"id\":\"" + id + "\","
                + "\"name\":\"Demo\","
                + "\"version\":\"" + version + "\","
                + "\"entry\":\"" + entry + "\","
                + "\"icon\":\"icon.png\","
                + "\"minShell\":\"" + minShell + "\","
                + "\"capabilities\":[\"storage\"],"
                + "\"data\":\"" + id + "\""
                + "}";
    }

    private void zip(File target, Map<String, String> files) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(target))) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                add(out, entry.getKey(), entry.getValue());
            }
        }
    }

    private void add(ZipOutputStream out, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        out.putNextEntry(entry);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private void writeData(String id, String name, String content) throws IOException {
        File dir = new File(data, id);
        assertTrue(dir.exists() || dir.mkdirs());
        Files.write(new File(dir, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String readData(String id, String name) throws IOException {
        return new String(Files.readAllBytes(new File(new File(data, id), name).toPath()), StandardCharsets.UTF_8);
    }

    private void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }
}

