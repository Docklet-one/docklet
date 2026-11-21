package com.docklet.shell.runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.docklet.shell.demo.DemoApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 在线目录里“未安装模块”的图标：按 URL 缓存，缺了才下。
 * 文件名 <id>-<version>.png；catalog 里的地址带 ?v=version，版本一变就是新地址。
 */
public final class RemoteIcons {

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 256 * 1024;
    private static final int DECODE_PX = 144;

    private static final Map<String, Bitmap> MEMORY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    private RemoteIcons() {
    }

    private static String key(String id, String version) {
        return id + "-" + (version == null ? "" : version);
    }

    private static File iconsDir(Context context) {
        File dir = new File(context.getFilesDir(), "icons");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** 内存 → 磁盘，同步返回；都没有就是 null。 */
    public static Bitmap cached(Context context, String id, String version) {
        String key = key(id, version);
        synchronized (MEMORY) {
            Bitmap hit = MEMORY.get(key);
            if (hit != null) return hit;
        }
        File file = new File(iconsDir(context), key + ".png");
        if (!file.isFile()) return null;
        Bitmap bitmap = decode(file);
        if (bitmap != null) {
            synchronized (MEMORY) {
                MEMORY.put(key, bitmap);
            }
        }
        return bitmap;
    }

    /**
     * 补齐一批图标：本地有的跳过，没有的下载。调用方负责放到 IO 线程。
     * 失败的不再重试（本次会话），下次刷新/进页面会再试一次。
     */
    public static void prefetch(Context context, List<DemoApp> apps) {
        if (apps == null || apps.isEmpty()) return;
        final Context app = context.getApplicationContext();
        List<String[]> queue = new ArrayList<>();
        for (DemoApp app2 : apps) {
            String url = app2.iconUrl();
            if (url == null || url.isEmpty()) continue;
            String key = key(app2.id(), app2.version());
            if (new File(iconsDir(app), key + ".png").isFile()) continue;
            synchronized (LOADING) {
                if (LOADING.contains(key) || FAILED.contains(key)) continue;
            }
            queue.add(new String[]{app2.id(), app2.version(), url});
        }
        for (String[] task : queue) {
            POOL.execute(() -> downloadOne(app, task[0], task[1], task[2]));
        }
    }

    private static void downloadOne(Context context, String id, String version, String url) {
        String key = key(id, version);
        synchronized (LOADING) {
            if (LOADING.contains(key)) return;
            LOADING.add(key);
        }
        boolean ok = false;
        try {
            File target = new File(iconsDir(context), key + ".png");
            ok = download(url, target);
            if (ok) {
                Bitmap bitmap = decode(target);
                if (bitmap != null) {
                    synchronized (MEMORY) {
                        MEMORY.put(key, bitmap);
                    }
                }
            }
        } catch (Exception ignored) {
            ok = false;
        } finally {
            synchronized (LOADING) {
                LOADING.remove(key);
            }
            if (!ok) {
                synchronized (FAILED) {
                    FAILED.add(key);
                }
            }
        }
    }

    /** 删掉当前 catalog 已不再引用的图标文件。 */
    public static void cleanup(Context context, List<DemoApp> apps) {
        Set<String> keep = new HashSet<>();
        if (apps != null) {
            for (DemoApp app : apps) {
                keep.add(key(app.id(), app.version()) + ".png");
            }
        }
        File[] files = iconsDir(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (keep.contains(name)) continue;
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            String key = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
            synchronized (MEMORY) {
                MEMORY.remove(key);
            }
        }
    }

    private static boolean download(String url, File target) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
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
