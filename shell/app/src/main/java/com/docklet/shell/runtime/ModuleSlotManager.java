package com.docklet.shell.runtime;

import android.os.Build;

/** One-foreground-slot coordinator for the V3 :m1 / :m2 processes. */
public final class ModuleSlotManager {

    public enum Slot {
        M1,
        M2
    }

    private Slot active;
    private Slot next = Slot.M1;

    public synchronized Slot acquire() {
        if (active != null) return null;
        // WebView.setDataDirectorySuffix() is available from API 28. Below
        // that, two WebView processes sharing a data directory crash.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            active = Slot.M1;
            return active;
        }
        active = next;
        next = next == Slot.M1 ? Slot.M2 : Slot.M1;
        return active;
    }

    public synchronized void release(Slot slot) {
        if (active == slot) active = null;
    }

    public synchronized Slot active() {
        return active;
    }
}
