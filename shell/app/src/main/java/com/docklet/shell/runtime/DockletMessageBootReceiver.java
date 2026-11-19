package com.docklet.shell.runtime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores message subscriptions after reboot without starting an empty service. */
public class DockletMessageBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (MessageSubscriptionStore.load(context).isEmpty()) return;
        try {
            context.startForegroundService(
                    new Intent(context, DockletMessageService.class)
            );
        } catch (Exception ignored) {
            // Android or an OEM may block background FGS startup after boot.
        }
    }
}
