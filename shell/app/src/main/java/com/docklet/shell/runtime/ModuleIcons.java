package com.docklet.shell.runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.docklet.platform.InstalledModule;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 模块自带图标（module.json 的 icon 字段）：读盘 + 内存缓存。
 * 读不到就返回 null，调用方回落到线条图标。
 */
public final class ModuleIcons {

    private static final int GRID_PX = 144;
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private ModuleIcons() {
    }

    private static String key(InstalledModule module) {
        return module.id() + "@" + module.version();
    }

    /** 网格 / 列表用的小图。 */
    public static Bitmap thumbnail(Context context, InstalledModule module) {
        return load(context, module, GRID_PX);
    }

    /** 原尺寸（快捷方式用）。 */
    public static Bitmap full(Context context, InstalledModule module) {
        return load(context, module, 0);
    }

    private static Bitmap load(Context context, InstalledModule module, int targetPx) {
        if (module == null) return null;
        String key = key(module) + "#" + targetPx;
        synchronized (CACHE) {
            Bitmap cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        Bitmap bitmap = decode(module, targetPx);
        if (bitmap != null) {
            synchronized (CACHE) {
                CACHE.put(key, bitmap);
            }
        }
        return bitmap;
    }

    private static Bitmap decode(InstalledModule module, int targetPx) {
        try {
            String icon = module.icon();
            if (icon == null || icon.isEmpty()) return null;
            if (icon.contains("..") || icon.startsWith("/")) return null;
            File file = new File(module.directory(), icon);
            if (!file.isFile()) return null;

            if (targetPx <= 0) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable error) {
            return null;
        }
    }

    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
