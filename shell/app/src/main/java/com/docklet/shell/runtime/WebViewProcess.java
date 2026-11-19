package com.docklet.shell.runtime;

import android.os.Build;
import android.webkit.WebView;

import java.util.HashSet;
import java.util.Set;

/** One WebView data-directory suffix per module process. */
public final class WebViewProcess {

    private static final Set<String> CONFIGURED = new HashSet<>();

    private WebViewProcess() {
    }

    public static synchronized void ensureSuffix(String suffix) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || suffix == null) return;
        if (CONFIGURED.contains(suffix)) return;
        try {
            WebView.setDataDirectorySuffix(suffix);
            CONFIGURED.add(suffix);
        } catch (IllegalStateException ignored) {
            // The process may already have a WebView; do not destabilize it.
        }
    }
}
