package com.docklet.shell.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;

import java.util.ArrayList;
import java.util.List;

/** PSS breakdown for the shell and its module/WebView processes. */
public final class MemoryUsage {

    public static final class Snapshot {
        public final long shellPssKb;
        public final long modulePssKb;
        public final long webViewPssKb;
        public final long totalPssKb;
        public final long availableBytes;
        public final long totalBytes;
        public final int processCount;

        Snapshot(
                long shellPssKb,
                long modulePssKb,
                long webViewPssKb,
                long totalPssKb,
                long availableBytes,
                long totalBytes,
                int processCount
        ) {
            this.shellPssKb = shellPssKb;
            this.modulePssKb = modulePssKb;
            this.webViewPssKb = webViewPssKb;
            this.totalPssKb = totalPssKb;
            this.availableBytes = availableBytes;
            this.totalBytes = totalBytes;
            this.processCount = processCount;
        }
    }

    private MemoryUsage() {
    }

    public static Snapshot read(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = context.getPackageName();
        long shell = 0L;
        long modules = 0L;
        long webView = 0L;
        int count = 0;

        if (manager != null) {
            try {
                List<ActivityManager.RunningAppProcessInfo> processes =
                        manager.getRunningAppProcesses();
                if (processes != null) {
                    List<Integer> pids = new ArrayList<>();
                    List<String> names = new ArrayList<>();
                    for (ActivityManager.RunningAppProcessInfo process : processes) {
                        if (process.processName == null) continue;
                        if (!process.processName.equals(packageName)
                                && !process.processName.startsWith(packageName + ":")) {
                            continue;
                        }
                        pids.add(process.pid);
                        names.add(process.processName);
                    }
                    if (!pids.isEmpty()) {
                        int[] pidArray = new int[pids.size()];
                        for (int i = 0; i < pids.size(); i += 1) pidArray[i] = pids.get(i);
                        Debug.MemoryInfo[] infos = manager.getProcessMemoryInfo(pidArray);
                        for (int i = 0; i < infos.length && i < names.size(); i += 1) {
                            long pss = Math.max(0, infos[i].getTotalPss());
                            String name = names.get(i);
                            count += 1;
                            if (name.equals(packageName)) {
                                shell += pss;
                            } else if (name.endsWith(":m1") || name.endsWith(":m2")) {
                                modules += pss;
                            } else {
                                webView += pss;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the current process below.
            }
        }

        if (shell <= 0L) {
            try {
                Debug.MemoryInfo info = new Debug.MemoryInfo();
                Debug.getMemoryInfo(info);
                shell = Math.max(0, info.getTotalPss());
                if (count == 0) count = 1;
            } catch (Exception ignored) {
                // No memory information available.
            }
        }

        long available = 0L;
        long total = 0L;
        if (manager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            available = Math.max(0L, memory.availMem);
            total = Math.max(0L, memory.totalMem);
        }
        return new Snapshot(
                shell,
                modules,
                webView,
                shell + modules + webView,
                available,
                total,
                count
        );
    }
}
