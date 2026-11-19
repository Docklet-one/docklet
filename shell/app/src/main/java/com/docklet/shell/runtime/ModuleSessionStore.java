package com.docklet.shell.runtime;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Small cross-process marker written by a ready slot. It lets a restarted
 * shell distinguish "slot is still alive" from "slot died during launch"
 * before CrashGuard counts an interrupted launch.
 */
public final class ModuleSessionStore {

    private static final String FILE_NAME = "slot_session";

    private ModuleSessionStore() {
    }

    public static void writeReady(Context context, String moduleId, String slot) {
        if (context == null || moduleId == null) return;
        File file = new File(context.getFilesDir(), FILE_NAME);
        String value = moduleId + "\n" + (slot == null ? "" : slot) + "\n" + System.currentTimeMillis();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Readiness is best effort; Activity result remains authoritative.
        }
    }

    public static String[] readReady(Context context) {
        if (context == null) return null;
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return null;
        try {
            String value = new String(
                    java.nio.file.Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8
            );
            String[] lines = value.split("\n", 3);
            if (lines.length < 2 || lines[0].isEmpty()) return null;
            return new String[]{lines[0], lines[1]};
        } catch (Exception error) {
            return null;
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        try {
            java.nio.file.Files.deleteIfExists(
                    new File(context.getFilesDir(), FILE_NAME).toPath()
            );
        } catch (Exception ignored) {
            // Best effort.
        }
    }
}
