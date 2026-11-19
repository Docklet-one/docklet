package com.docklet.platform;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class ModuleFilesTest {

    private File root;
    private ModuleFiles files;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("docklet-files").toFile();
        files = new ModuleFiles(root);
    }

    @After
    public void tearDown() {
        delete(root);
    }

    @Test
    public void writesReadsListsAndDeletesText() throws Exception {
        Map<String, Object> written = files.write("notes", "exports/today.txt", "你好\nDocklet", "utf8");
        assertEquals("utf8", written.get("encoding"));
        assertEquals(
                (long) "你好\nDocklet".getBytes(StandardCharsets.UTF_8).length,
                ((Number) written.get("bytes")).longValue()
        );

        Map<String, Object> read = files.read("notes", "exports/today.txt", "utf8");
        assertEquals("你好\nDocklet", read.get("data"));

        List<Map<String, Object>> listed = files.list("notes", "exports");
        assertEquals(1, listed.size());
        assertEquals("today.txt", listed.get(0).get("name"));
        assertEquals(false, listed.get(0).get("dir"));
        assertTrue((Long) listed.get(0).get("size") > 0L);

        Map<String, Object> deleted = files.delete("notes", "exports/today.txt", false);
        assertEquals(true, deleted.get("deleted"));
        assertFalse(files.exists("notes", "exports/today.txt"));
    }

    @Test
    public void supportsBase64Binary() throws Exception {
        byte[] payload = {0, 1, 2, 3, (byte) 0xff};
        files.write("notes", "blob.bin", Base64.getEncoder().encodeToString(payload), "base64");
        Map<String, Object> read = files.read("notes", "blob.bin", "base64");
        assertArrayEquals(payload, Base64.getDecoder().decode((String) read.get("data")));
    }

    @Test
    public void rejectsTraversalAndAbsolutePaths() {
        for (String path : new String[]{
                "../other.txt", "/etc/passwd", "a/../../b.txt", "a//b.txt", "a\\b.txt", "C:/x.txt"
        }) {
            ModuleException error = assertThrows(ModuleException.class, () ->
                    files.write("notes", path, "x", "utf8")
            );
            assertEquals(ModuleException.PATH_REJECTED, error.getCode());
        }
    }

    @Test
    public void isolatesModules() throws Exception {
        files.write("notes", "private.txt", "notes-only", "utf8");
        ModuleException error = assertThrows(ModuleException.class, () ->
                files.read("other", "private.txt", "utf8")
        );
        assertEquals(ModuleException.FILE_NOT_FOUND, error.getCode());
        assertEquals("notes-only", files.read("notes", "private.txt", "utf8").get("data"));
    }

    @Test
    public void refusesNonEmptyDirectoryDeleteWithoutRecursive() throws Exception {
        files.write("notes", "dir/a.txt", "a", "utf8");
        assertEquals(
                ModuleException.FILE_CONFLICT,
                assertThrows(ModuleException.class, () ->
                        files.delete("notes", "dir", false)
                ).getCode()
        );
        files.delete("notes", "dir", true);
        assertFalse(files.exists("notes", "dir"));
    }

    @Test
    public void enforcesPerFileLimit() {
        char[] buffer = new char[(int) ModuleFiles.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(buffer, 'x');
        ModuleException error = assertThrows(ModuleException.class, () ->
                files.write("notes", "too-big.txt", new String(buffer), "utf8")
        );
        assertEquals(ModuleException.FILE_TOO_LARGE, error.getCode());
    }

    @Test
    public void rejectsUnknownEncoding() {
        assertEquals(
                ModuleException.FILES_ERROR,
                assertThrows(ModuleException.class, () ->
                        files.write("notes", "a.txt", "x", "utf-16")
                ).getCode()
        );
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
