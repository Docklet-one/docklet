package com.docklet.shell.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Default-off, best-effort warm-up for the spare :m2 slot. It only prepares
 * the process and WebView data directory; it never creates a second WebView.
 */
public final class SlotWarmupService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        WebViewProcess.ensureSuffix("docklet-m2");
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
