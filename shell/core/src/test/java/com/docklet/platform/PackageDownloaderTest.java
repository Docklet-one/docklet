package com.docklet.platform;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

public class PackageDownloaderTest {

    private static final byte[] PACKAGE = "fake-pkg-content".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String base;
    private File cache;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pkg.pkg", exchange -> {
            exchange.sendResponseHeaders(200, PACKAGE.length);
            exchange.getResponseBody().write(PACKAGE);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/catalog.json", exchange -> {
            String sha = sha256(PACKAGE);
            String json = "{\"spec\":1,\"modules\":[{"
                    + "\"id\":\"notes\",\"name\":\"清单\",\"version\":\"1.0.0\","
                    + "\"url\":\"pkg.pkg\",\"sha256\":\"" + sha + "\",\"size\":" + PACKAGE.length
                    + "}]}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        cache = Files.createTempDirectory("docklet-download-test").toFile();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
        delete(cache);
    }

    @Test
    public void downloadsAndVerifiesPackage() throws Exception {
        RemoteModule module = module("/pkg.pkg", sha256(PACKAGE));
        File file = PackageDownloader.download(module, cache);

        assertTrue(file.isFile());
        assertArrayEquals(PACKAGE, Files.readAllBytes(file.toPath()));
        assertEquals("notes-1.0.0.pkg", file.getName());
    }

    @Test
    public void rejectsBadHashAndDoesNotKeepTarget() {
        RemoteModule module = module("/pkg.pkg", "0".repeat(64));
        ModuleException error = assertThrows(ModuleException.class, () ->
                PackageDownloader.download(module, cache)
        );

        assertEquals(ModuleException.REMOTE_HASH_MISMATCH, error.getCode());
        assertFalse(new File(cache, "notes-1.0.0.pkg").exists());
    }

    @Test
    public void rejectsHttpError() {
        RemoteModule module = module("/missing", sha256(PACKAGE));
        ModuleException error = assertThrows(ModuleException.class, () ->
                PackageDownloader.download(module, cache)
        );
        assertEquals(ModuleException.REMOTE_CATALOG_ERROR, error.getCode());
        assertFalse(new File(cache, "notes-1.0.0.pkg").exists());
    }

    @Test
    public void loadsCatalogOverHttp() throws Exception {
        RemoteCatalog catalog = RemoteCatalog.load(new URL(base + "/catalog.json"));
        assertEquals(1, catalog.modules().size());
        assertEquals("notes", catalog.modules().get(0).id());
    }

    private RemoteModule module(String path, String sha) {
        return new RemoteModule(
                "notes",
                "清单",
                "效率",
                "1.0.0",
                "测试",
                "#5E7C6A",
                sha,
                PACKAGE.length,
                base + path
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte value : digest) {
                out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
