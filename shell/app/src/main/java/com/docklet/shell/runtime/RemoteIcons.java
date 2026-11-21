package com.docklet.shell.runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 在线目录里未安装模块的图标：先读本地缓存，没有就下载。
 * 文件名是 <id>-<version>.png，URL 在 catalog 里带 ?v=版本，天然缓存失效。
 */
public final class RemoteIcons {

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 256 * 1024;
    private static final int DECODE_PX = 144;

    private static final Map<String, Bitmap> MEMORY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    private RemoteIcons() {
    }

    private static String key(String id, String version) {
        return id + "-" + (version == null ? "" : version);
    }

    private static File cacheFile(Context context, String id, String version) {
        File dir = new File(context.getFilesDir(), "icons");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, key(id, version) + ".png");
    }

    /** 内存 → 磁盘，都没有时返回 null。 */
    public static Bitmap cached(Context context, String id, String version) {
        String key = key(id, version);
        synchronized (MEMORY) {
            Bitmap hit = MEMORY.get(key);
            if (hit != null) return hit;
        }
        File file = cacheFile(context, id, version);
        if (!file.isFile()) return null;
        Bitmap bitmap = decode(file);
        if (bitmap != null) {
            synchronized (MEMORY) {
                MEMORY.put(key, bitmap);
            }
        }
        return bitmap;
    }

    /** 没有缓存时下载，成功回调一次（在主线程外）。 */
    public static void load(
            Context context,
            String id,
            String version,
            String url,
            Consumer<Bitmap> callback
    ) {
        if (url == null || url.isEmpty()) {
            if (callback != null) callback.accept(null);
            return;
        }
        Bitmap hit = cached(context, id, version);
        if (hit != null) {
            if (callback != null) callback.accept(hit);
            return;
        }
        String key = key(id, version);
        synchronized (LOADING) {
            if (LOADING.contains(key)) return;
            LOADING.add(key);
        }
        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap result = null;
            try {
                File file = cacheFile(app, id, version);
                if (download(url, file)) {
                    result = decode(file);
                }
            } catch (Exception ignored) {
                result = null;
            } finally {
                synchronized (LOADING) {
                    LOADING.remove(key);
                }
            }
            if (result != null) {
                synchronized (MEMORY) {
                    MEMORY.put(key, result);
                }
            }
            if (callback != null) callback.accept(result);
        });
    }

    private static boolean download(String url, File target) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Cache-Control", "no-cache");
            if (connection.getResponseCode() != 200) return false;
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_BYTES) {
                        //noinspection ResultOfMethodCallIgnored
                        target.delete();
                        return false;
                    }
                    out.write(buffer, 0, read);
                }
            }
            return true;
        } catch (Exception error) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= DECODE_PX) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable error) {
            return null;
        }
    }
}
