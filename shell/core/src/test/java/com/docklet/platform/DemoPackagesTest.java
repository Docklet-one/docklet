package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Smoke tests for every bundled demo package.
 *
 * <p>Expectations come from the generated catalog instead of a hardcoded list,
 * so adding, renaming or bumping a demo never leaves this test behind.</p>
 */
public class DemoPackagesTest {

    private static File catalogFile() {
        return new File("../../dist/demos.json");
    }

    private static List<Map<String, Object>> catalogApps() throws Exception {
        File file = catalogFile();
        Assume.assumeTrue("run :app:demoCatalog first", file.isFile());
        Map<String, Object> root = Json.parseObject(Files.readString(file.toPath()));
        List<Object> apps = Json.asArray(root.get("apps"));
        assertNotNull(apps);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : apps) {
            Map<String, Object> app = Json.asObject(raw);
            assertNotNull(app);
            result.add(app);
        }
        return result;
    }

    @Test
    public void installsAllBundledDemoPackages() throws Exception {
        List<Map<String, Object>> apps = catalogApps();
        assertTrue(!apps.isEmpty());
        for (Map<String, Object> app : apps) {
            String id = Json.string(app, "id");
            String version = Json.string(app, "version");
            String asset = Json.string(app, "asset");
            assertTrue(ModuleManifest.isValidId(id));
            assertNotNull(version);
            assertNotNull(asset);

            File pkg = new File("../../dist/" + asset);
            Assume.assumeTrue("missing package: " + pkg.getName(), pkg.isFile());

            File root = Files.createTempDirectory("docklet-" + id).toFile();
            try {
                ModuleManager manager = new ModuleManager(
                        new File(root, "modules"),
                        new File(root, "data"),
                        new File(root, "cache"),
                        "1.1.0"
                );
                InstalledModule module = manager.install(pkg);
                assertEquals(id, module.id());
                assertEquals(version, module.version());
                assertEquals("index.html", module.entry());
                assertNotNull(manager.currentDir(module.id()));
                File packageRoot = manager.currentDir(module.id());
                assertTrue(new File(packageRoot, module.entry()).isFile());
                assertEquals(1, manager.listInstalled().size());
            } finally {
                delete(root);
            }
        }
    }

    @Test
    public void catalogMatchesPackages() throws Exception {
        List<Map<String, Object>> apps = catalogApps();
        assertTrue(!apps.isEmpty());
        for (Map<String, Object> app : apps) {
            String id = Json.string(app, "id");
            String asset = Json.string(app, "asset");
            assertTrue(ModuleManifest.isValidId(id));
            assertNotNull(asset);
            assertTrue("missing asset for " + id + ": " + asset,
                    new File("../../dist/" + asset).isFile());
            assertNotNull(Json.string(app, "glyph"));
        }
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
