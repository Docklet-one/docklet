# Keep bridge methods reachable when minification is enabled later.
-keepclassmembers class com.docklet.shell.runtime.DockletBridge {
    @android.webkit.JavascriptInterface <methods>;
}
