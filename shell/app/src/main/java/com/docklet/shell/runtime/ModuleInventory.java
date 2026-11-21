package com.docklet.shell.runtime;

import android.os.Handler;

import com.docklet.platform.InstalledModule;
import com.docklet.platform.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Background cache for installed-module metadata. Filesystem scanning and
 * size calculation must never run on the UI thread during normal navigation.
 */
public final class ModuleInventory {

    private final ModuleManager manager;
    private final ExecutorService io;
    private final Handler main;
    private final Object lock = new Object();
    private final List<Consumer<List<InstalledModule>>> pending = new ArrayList<>();

    private volatile List<InstalledModule> snapshot;
    private boolean loading;

    public ModuleInventory(ModuleManager manager, ExecutorService io, Handler main) {
        this.manager = manager;
        this.io = io;
        this.main = main;
    }

    public List<InstalledModule> snapshot() {
        return snapshot;
    }

    /** 先用索引里的结果占位，首帧不等扫描。 */
    public void prime(List<InstalledModule> modules) {
        if (modules != null) snapshot = modules;
    }

    /**
     * Refreshes in the background. A cached snapshot is returned immediately
     * through the callback when force is false.
     */
    public void refresh(boolean force, Consumer<List<InstalledModule>> callback) {
        List<InstalledModule> cached = snapshot;
        if (!force && cached != null) {
            main.post(() -> callback.accept(cached));
            return;
        }
        synchronized (lock) {
            pending.add(callback);
            if (loading) return;
            loading = true;
        }
        io.execute(() -> {
            List<InstalledModule> loaded;
            try {
                loaded = manager.listInstalled();
            } catch (Exception error) {
                loaded = new ArrayList<>();
            }
            snapshot = loaded;
            List<Consumer<List<InstalledModule>>> callbacks;
            synchronized (lock) {
                loading = false;
                callbacks = new ArrayList<>(pending);
                pending.clear();
            }
            List<InstalledModule> result = loaded;
            main.post(() -> {
                for (Consumer<List<InstalledModule>> pendingCallback : callbacks) {
                    pendingCallback.accept(result);
                }
            });
        });
    }

    public void invalidate() {
        snapshot = null;
    }
}
