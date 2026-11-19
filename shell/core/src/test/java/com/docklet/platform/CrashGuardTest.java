package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CrashGuardTest {

    private File dir;
    private File stateFile;
    private CrashGuard guard;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("docklet-crash-guard").toFile();
        stateFile = new File(dir, "crash_guard.json");
        guard = new CrashGuard(stateFile);
    }

    @After
    public void tearDown() {
        delete(dir);
    }

    @Test
    public void readyClearsLaunchingAndFailureCount() {
        guard.beginLaunch("demo");
        assertEquals("demo", guard.pendingLaunch());
        guard.recordFailure("demo", "timeout");
        assertEquals(1, guard.state("demo").count());

        guard.beginLaunch("demo");
        guard.onReady("demo");

        assertNull(guard.pendingLaunch());
        assertFalse(guard.state("demo").failed());
        assertFalse(guard.isBlocked("demo"));
    }

    @Test
    public void twoFailuresEnterSafetyModeAndPersist() {
        guard.beginLaunch("demo");
        assertEquals(1, guard.recordFailure("demo", "ready timeout"));
        assertEquals(1, guard.state("demo").count());
        assertFalse(guard.isBlocked("demo"));

        guard.beginLaunch("demo");
        assertEquals(2, guard.recordFailure("demo", "render process gone"));
        assertTrue(guard.isBlocked("demo"));
        assertEquals("render process gone", guard.state("demo").reason());
        assertTrue(guard.state("demo").lastFailure() > 0L);
        assertNull(guard.pendingLaunch());

        CrashGuard reloaded = new CrashGuard(stateFile);
        assertTrue(reloaded.isBlocked("demo"));
        assertEquals(2, reloaded.state("demo").count());
    }

    @Test
    public void interruptedLaunchCountsAsFailureOnNextStart() {
        guard.beginLaunch("notes");
        CrashGuard next = new CrashGuard(stateFile);
        assertEquals("notes", next.recoverInterruptedLaunch());
        assertEquals(1, next.state("notes").count());
        assertTrue(next.state("notes").reason().contains("终止"));
        assertNull(next.pendingLaunch());

        next.beginLaunch("notes");
        next.recordFailure("notes", "again");
        assertTrue(next.isBlocked("notes"));
    }

    @Test
    public void clearRemovesModuleAndPendingLaunch() {
        guard.beginLaunch("focus");
        guard.recordFailure("focus", "timeout");
        guard.beginLaunch("focus");
        guard.clear("focus");

        assertFalse(guard.hasFailure("focus"));
        assertNull(guard.pendingLaunch());

        CrashGuard reloaded = new CrashGuard(stateFile);
        assertFalse(reloaded.hasFailure("focus"));
    }

    @Test
    public void corruptStateFileIsIgnored() throws Exception {
        Files.write(stateFile.toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        CrashGuard recovered = new CrashGuard(stateFile);
        assertFalse(recovered.hasFailure("demo"));
        recovered.beginLaunch("demo");
        assertEquals("demo", recovered.pendingLaunch());
    }

    @Test
    public void statesMapContainsOnlyFailedModules() {
        guard.beginLaunch("demo");
        guard.recordFailure("demo", "x");
        guard.beginLaunch("notes");
        guard.recordFailure("notes", "y");
        guard.recordFailure("notes", "z");
        guard.onReady("demo");

        assertEquals(1, guard.states().size());
        assertTrue(guard.states().containsKey("notes"));
        assertTrue(guard.states().get("notes").blocked());
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
