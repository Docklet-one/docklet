package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/** Smoke test against the package produced by the real demo build. */
public class DemoPackageTest {

    @Test
    public void installsRealDemoPackage() throws Exception {
        File pkg = new File("../../dist/demo-1.0.0.pkg");
        Assume.assumeTrue("run :app:demoPackage first", pkg.isFile());

        File root = Files.createTempDirectory("docklet-demo-package").toFile();
        try {
            ModuleManager manager = new ModuleManager(
                    new File(root, "modules"),
                    new File(root, "data"),
                    new File(root, "cache"),
                    "1.0.0"
            );
            InstalledModule module = manager.install(pkg);
            assertEquals("demo", module.id());
            assertEquals("1.0.0", module.version());
            assertEquals("index.html", module.entry());
            assertTrue(module.capabilities().contains("storage"));
            assertNotNull(manager.currentDir("demo"));
            assertTrue(new File(manager.currentDir("demo"), "app.js").isFile());
            assertTrue(new File(manager.currentDir("demo"), "app.css").isFile());
            assertTrue(new File(manager.currentDir("demo"), "module.json").isFile());
        } finally {
            delete(root);
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
