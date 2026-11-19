package com.docklet.shell;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Invisible notification router. It lets a notification switch away from the
 * currently active ModuleActivity without letting MainActivity's singleTask
 * launch mode tear down the module stack without a save.
 */
public final class ModuleRouterActivity extends Activity {

    public static final String EXTRA_MODULE_ID = "com.docklet.shell.ROUTE_MODULE";
    public static final String EXTRA_SESSION_ID = "com.docklet.shell.ROUTE_SESSION";
    public static final String ACTIVE_PREFS = "docklet_shell";
    public static final String ACTIVE_MODULE = "active_module";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String target = getIntent().getStringExtra(EXTRA_MODULE_ID);
        String session = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (target == null || target.isEmpty()) {
            finish();
            return;
        }
        String active = getSharedPreferences(ACTIVE_PREFS, MODE_PRIVATE)
                .getString(ACTIVE_MODULE, null);
        if (target.equals(active)) {
            if (session != null && !session.isEmpty()) {
                Intent current = new Intent(ModuleActivity.ACTION_SWITCH_TO)
                        .setPackage(getPackageName())
                        .putExtra(ModuleActivity.EXTRA_TARGET_MODULE, target)
                        .putExtra(ModuleActivity.EXTRA_TARGET_SESSION, session);
                sendBroadcast(current);
            }
            finish();
            return;
        }
        if (active != null && !active.isEmpty()) {
            Intent switchIntent = new Intent(ModuleActivity.ACTION_SWITCH_TO)
                    .setPackage(getPackageName())
                    .putExtra(ModuleActivity.EXTRA_TARGET_MODULE, target)
                    .putExtra(ModuleActivity.EXTRA_TARGET_SESSION, session == null ? "" : session);
            sendBroadcast(switchIntent);
            finish();
            return;
        }
        Intent main = new Intent(this, MainActivity.class);
        main.putExtra(MainActivity.EXTRA_OPEN_MODULE, target);
        if (session != null) main.putExtra(MainActivity.EXTRA_OPEN_SESSION, session);
        main.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(main);
        finish();
    }
}
