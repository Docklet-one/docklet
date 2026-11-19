package com.docklet.platform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists launch/crash state for the shell's safety mode.
 *
 * <p>State is intentionally stored in a small JSON file instead of Android
 * SharedPreferences so the safety rules can be exercised by plain JVM tests.</p>
 */
public final class CrashGuard {

    public static final int BLOCK_THRESHOLD = 2;

    private static final String KEY_LAUNCHING = "launching";
    private static final String KEY_LAUNCH_AT = "launchAt";
    private static final String KEY_MODULES = "modules";
    private static final String KEY_COUNT = "count";
    private static final String KEY_LAST_FAILURE = "lastFailure";
    private static final String KEY_REASON = "reason";

    private final File stateFile;
    private String launching;
    private long launchAt;
    private final Map<String, Entry> modules = new LinkedHashMap<>();

    public CrashGuard(File stateFile) {
        if (stateFile == null) throw new IllegalArgumentException("stateFile 不能为空");
        this.stateFile = stateFile;
        load();
    }

    public synchronized void beginLaunch(String moduleId) {
        if (!ModuleManifest.isValidId(moduleId)) return;
        launching = moduleId;
        launchAt = System.currentTimeMillis();
        save();
    }

    public synchronized void onReady(String moduleId) {
        if (!ModuleManifest.isValidId(moduleId)) return;
        boolean changed = false;
        if (moduleId.equals(launching)) {
            launching = null;
            launchAt = 0L;
            changed = true;
        }
        if (modules.remove(moduleId) != null) changed = true;
        if (changed) save();
    }

    /**
     * Records one failure. The caller is responsible for de-duplicating
     * callbacks from the same runtime instance.
     *
     * @return the new consecutive failure count
     */
    public synchronized int recordFailure(String moduleId, String reason) {
        if (!ModuleManifest.isValidId(moduleId)) return 0;
        Entry entry = modules.get(moduleId);
        if (entry == null) {
            entry = new Entry();
            modules.put(moduleId, entry);
        }
        entry.count += 1;
        entry.lastFailure = System.currentTimeMillis();
        entry.reason = sanitizeReason(reason);
        if (moduleId.equals(launching)) {
            launching = null;
            launchAt = 0L;
        }
        save();
        return entry.count;
    }

    /**
     * Called once at shell startup. A persisted {@code launching} marker means
     * the previous shell process died before the module reached ready.
     *
     * @return the module id that was recovered, or null
     */
    public synchronized String recoverInterruptedLaunch() {
        if (launching == null || launching.isEmpty()) return null;
        String id = launching;
        launching = null;
        launchAt = 0L;
        Entry entry = modules.get(id);
        if (entry == null) {
            entry = new Entry();
            modules.put(id, entry);
        }
        entry.count += 1;
        entry.lastFailure = System.currentTimeMillis();
        entry.reason = "上一个壳进程在模块启动完成前被终止";
        save();
        return id;
    }

    public synchronized State state(String moduleId) {
        Entry entry = modules.get(moduleId);
        if (entry == null) return State.NONE;
        return new State(entry.count, entry.lastFailure, entry.reason);
    }

    public synchronized boolean isBlocked(String moduleId) {
        Entry entry = modules.get(moduleId);
        return entry != null && entry.count >= BLOCK_THRESHOLD;
    }

    public synchronized boolean hasFailure(String moduleId) {
        Entry entry = modules.get(moduleId);
        return entry != null && entry.count > 0;
    }

    public synchronized Map<String, State> states() {
        Map<String, State> result = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> entry : modules.entrySet()) {
            result.put(entry.getKey(), new State(
                    entry.getValue().count,
                    entry.getValue().lastFailure,
                    entry.getValue().reason
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    public synchronized void clear(String moduleId) {
        boolean changed = modules.remove(moduleId) != null;
        if (moduleId != null && moduleId.equals(launching)) {
            launching = null;
            launchAt = 0L;
            changed = true;
        }
        if (changed) save();
    }

    public synchronized String pendingLaunch() {
        return launching;
    }

    /** User left/closed the module before ready; this is not a crash. */
    public synchronized void cancelLaunch(String moduleId) {
        if (moduleId != null && moduleId.equals(launching)) {
            launching = null;
            launchAt = 0L;
            save();
        }
    }

    private void load() {
        if (!stateFile.isFile()) return;
        try {
            String text = FileOps.readUtf8(stateFile);
            Map<String, Object> root = Json.parseObject(text);
            String pending = Json.string(root, KEY_LAUNCHING);
            if (ModuleManifest.isValidId(pending)) {
                launching = pending;
                launchAt = Json.longValue(root, KEY_LAUNCH_AT, 0L);
            }
            Map<String, Object> rawModules = Json.asObject(root.get(KEY_MODULES));
            if (rawModules == null) return;
            for (Map.Entry<String, Object> item : rawModules.entrySet()) {
                if (!ModuleManifest.isValidId(item.getKey())) continue;
                Map<String, Object> raw = Json.asObject(item.getValue());
                if (raw == null) continue;
                Entry entry = new Entry();
                entry.count = Math.max(0, Json.intValue(raw, KEY_COUNT, 0));
                entry.lastFailure = Json.longValue(raw, KEY_LAST_FAILURE, 0L);
                entry.reason = sanitizeReason(Json.string(raw, KEY_REASON, ""));
                if (entry.count > 0) modules.put(item.getKey(), entry);
            }
        } catch (Exception ignored) {
            // A corrupt guard file must never prevent the shell from starting.
            launching = null;
            launchAt = 0L;
            modules.clear();
        }
    }

    private void save() {
        try {
            File parent = stateFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Object> root = new LinkedHashMap<>();
            if (launching != null) {
                root.put(KEY_LAUNCHING, launching);
                root.put(KEY_LAUNCH_AT, launchAt);
            }
            Map<String, Object> rawModules = new LinkedHashMap<>();
            for (Map.Entry<String, Entry> item : modules.entrySet()) {
                Map<String, Object> raw = new LinkedHashMap<>();
                raw.put(KEY_COUNT, item.getValue().count);
                raw.put(KEY_LAST_FAILURE, item.getValue().lastFailure);
                raw.put(KEY_REASON, item.getValue().reason);
                rawModules.put(item.getKey(), raw);
            }
            root.put(KEY_MODULES, rawModules);

            File temp = new File(
                    stateFile.getParentFile(),
                    stateFile.getName() + "." + UUID.randomUUID() + ".tmp"
            );
            Files.write(temp.toPath(), Json.stringify(root).getBytes(StandardCharsets.UTF_8));
            try {
                FileOps.move(temp.toPath(), stateFile.toPath());
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } catch (Exception ignored) {
            // Safety state is best effort; never break module launch because
            // the guard file could not be persisted.
        }
    }

    private String sanitizeReason(String reason) {
        if (reason == null) return "";
        String clean = reason.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private static final class Entry {
        int count;
        long lastFailure;
        String reason = "";
    }

    public static final class State {
        public static final State NONE = new State(0, 0L, "");

        private final int count;
        private final long lastFailure;
        private final String reason;

        public State(int count, long lastFailure, String reason) {
            this.count = count;
            this.lastFailure = lastFailure;
            this.reason = reason == null ? "" : reason;
        }

        public int count() {
            return count;
        }

        public long lastFailure() {
            return lastFailure;
        }

        public String reason() {
            return reason;
        }

        public boolean failed() {
            return count > 0;
        }

        public boolean blocked() {
            return count >= BLOCK_THRESHOLD;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("count", count);
            map.put("lastFailure", lastFailure);
            map.put("reason", reason);
            map.put("blocked", blocked());
            return map;
        }
    }
}
