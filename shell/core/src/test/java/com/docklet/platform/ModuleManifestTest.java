package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModuleManifestTest {

    private static final String VALID = "{"
            + "\"id\":\"demo\","
            + "\"name\":\"示例模块\","
            + "\"version\":\"1.2.3\","
            + "\"entry\":\"index.html\","
            + "\"minShell\":\"1.0.0\","
            + "\"capabilities\":[\"storage\",\"notify\",\"storage\"],"
            + "\"data\":\"demo\","
            + "\"description\":\"演示\","
            + "\"accent\":\"#d9792b\""
            + "}";

    @Test
    public void parsesValidManifest() throws Exception {
        ModuleManifest manifest = ModuleManifest.fromJson(VALID);
        assertEquals("demo", manifest.id());
        assertEquals("1.2.3", manifest.version());
        assertEquals("index.html", manifest.entry());
        assertEquals("demo", manifest.data());
        assertEquals("#D9792B", manifest.accent());
        assertEquals(2, manifest.capabilities().size());
        assertTrue(manifest.hasCapability("storage"));
    }

    @Test
    public void dataDefaultsToId() throws Exception {
        ModuleManifest manifest = ModuleManifest.fromJson(
                "{\"id\":\"demo\",\"name\":\"示例\",\"version\":\"1.0.0\",\"entry\":\"index.html\"}"
        );
        assertEquals("demo", manifest.data());
        assertEquals("1.0.0", manifest.minShell());
        assertTrue(manifest.capabilities().isEmpty());
    }

    @Test
    public void displayDefaultsFromChrome() throws Exception {
        ModuleManifest standard = ModuleManifest.fromJson(VALID);
        assertEquals(DisplayConfig.MODE_SAFE, standard.display().mode());
        assertEquals(DisplayConfig.ORIENTATION_AUTO, standard.display().orientation());

        ModuleManifest none = ModuleManifest.fromJson(
                VALID.replace("\"accent\":\"#d9792b\"", "\"accent\":\"#d9792b\",\"chrome\":\"none\"")
        );
        assertEquals(DisplayConfig.MODE_EDGE_TO_EDGE, none.display().mode());
    }

    @Test
    public void parsesExplicitDisplayPolicy() throws Exception {
        ModuleManifest manifest = ModuleManifest.fromJson(
                VALID.replace(
                        "\"accent\":\"#d9792b\"",
                        "\"accent\":\"#d9792b\",\"display\":{"
                                + "\"mode\":\"immersive\","
                                + "\"orientation\":\"portrait\","
                                + "\"statusBarIcons\":\"light\","
                                + "\"safeContent\":true}"
                )
        );
        assertEquals(DisplayConfig.MODE_IMMERSIVE, manifest.display().mode());
        assertEquals(DisplayConfig.ORIENTATION_PORTRAIT, manifest.display().orientation());
        assertEquals(DisplayConfig.ICONS_LIGHT, manifest.display().statusBarIcons());
        assertTrue(manifest.display().safeContent());
    }

    @Test
    public void rejectsInvalidDisplayMode() {
        assertThrows(ModuleException.class, () -> ModuleManifest.fromJson(
                VALID.replace(
                        "\"accent\":\"#d9792b\"",
                        "\"accent\":\"#d9792b\",\"display\":{\"mode\":\"bogus\"}"
                )
        ));
    }

    @Test
    public void rejectsDataDifferentFromId() {
        ModuleException error = assertThrows(ModuleException.class, () ->
                ModuleManifest.fromJson(VALID.replace("\"data\":\"demo\"", "\"data\":\"other\""))
        );
        assertEquals(ModuleException.MANIFEST_INVALID, error.getCode());
    }

    @Test
    public void rejectsUnsafeEntryPaths() {
        for (String entry : new String[]{"../index.html", "/index.html", "a\\b.html", "a//b.html", "a/./b.html"}) {
            ModuleException error = assertThrows(ModuleException.class, () ->
                    ModuleManifest.fromJson(VALID.replace("\"index.html\"", "\"" + entry + "\""))
            );
            assertTrue(error.getCode().equals(ModuleException.ENTRY_MISSING)
                    || error.getCode().equals(ModuleException.MANIFEST_INVALID));
        }
    }

    @Test
    public void rejectsUnknownCapabilityAndBadId() {
        assertEquals(
                ModuleException.MANIFEST_INVALID,
                assertThrows(ModuleException.class, () ->
                        ModuleManifest.fromJson(VALID.replace("\"notify\"", "\"camera\""))
                ).getCode()
        );
        assertEquals(
                ModuleException.INVALID_ID,
                assertThrows(ModuleException.class, () ->
                        ModuleManifest.fromJson(VALID.replace("\"demo\"", "\"Demo\""))
                ).getCode()
        );
    }

    @Test
    public void capabilityAllowListIsStable() {
        assertTrue(ModuleManifest.knownCapabilities().contains("storage"));
        assertFalse(ModuleManifest.knownCapabilities().contains("camera"));
    }
}
