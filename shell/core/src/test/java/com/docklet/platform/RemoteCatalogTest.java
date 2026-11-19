package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.URL;

public class RemoteCatalogTest {

    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static URL base(String value) throws Exception {
        return new URL(value);
    }

    private static String catalog(String url, String sha) {
        return "{"
                + "\"spec\":1,"
                + "\"modules\":[{"
                + "\"id\":\"notes\","
                + "\"name\":\"清单\","
                + "\"kind\":\"效率\","
                + "\"version\":\"1.0.0\","
                + "\"description\":\"测试\","
                + "\"accent\":\"#5e7c6a\","
                + "\"url\":\"" + url + "\","
                + "\"sha256\":\"" + sha + "\","
                + "\"size\":1234"
                + "}]"
                + "}";
    }

    @Test
    public void resolvesRelativePackageUrl() throws Exception {
        RemoteCatalog catalog = RemoteCatalog.parse(
                catalog("modules/notes-1.0.0.pkg", SHA),
                base("https://apps.example.com/catalog.json")
        );

        assertEquals(1, catalog.modules().size());
        RemoteModule module = catalog.modules().get(0);
        assertEquals("notes", module.id());
        assertEquals("1.0.0", module.version());
        assertEquals("https://apps.example.com/modules/notes-1.0.0.pkg", module.packageUrl());
        assertEquals(SHA, module.sha256());
        assertEquals(1234L, module.size());
    }

    @Test
    public void rejectsMissingOrBadSha() throws Exception {
        ModuleException missing = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(
                        catalog("modules/notes.pkg", ""),
                        base("https://apps.example.com/catalog.json")
                )
        );
        assertEquals(ModuleException.REMOTE_CATALOG_ERROR, missing.getCode());

        ModuleException bad = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(
                        catalog("modules/notes.pkg", "not-a-hash"),
                        base("https://apps.example.com/catalog.json")
                )
        );
        assertEquals(ModuleException.REMOTE_CATALOG_ERROR, bad.getCode());
    }

    @Test
    public void rejectsHttpsToHttpPackageDowngrade() {
        ModuleException error = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(
                        catalog("http://cdn.example.com/notes.pkg", SHA),
                        base("https://apps.example.com/catalog.json")
                )
        );
        assertEquals(ModuleException.REMOTE_URL_REJECTED, error.getCode());
    }

    @Test
    public void rejectsDuplicateIds() throws Exception {
        String item = "{"
                + "\"id\":\"notes\",\"name\":\"清单\",\"version\":\"1.0.0\","
                + "\"url\":\"a.pkg\",\"sha256\":\"" + SHA + "\""
                + "}";
        String json = "{\"spec\":1,\"modules\":[" + item + "," + item + "]}";
        ModuleException error = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(json, base("https://apps.example.com/catalog.json"))
        );
        assertTrue(error.getMessage().contains("重复"));
    }

    @Test
    public void rejectsUnsupportedSpecAndNonPkgUrl() throws Exception {
        ModuleException spec = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(
                        "{\"spec\":2,\"modules\":[]}",
                        base("https://apps.example.com/catalog.json")
                )
        );
        assertEquals(ModuleException.REMOTE_CATALOG_ERROR, spec.getCode());

        ModuleException pkg = assertThrows(ModuleException.class, () ->
                RemoteCatalog.parse(
                        catalog("modules/notes.zip", SHA),
                        base("https://apps.example.com/catalog.json")
                )
        );
        assertEquals(ModuleException.REMOTE_URL_REJECTED, pkg.getCode());
    }
}
