package com.docklet.shell.runtime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.docklet.platform.ModuleException;
import com.docklet.platform.ModuleFiles;
import com.docklet.platform.ModuleManifest;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

/**
 * Read-only content provider for module-private files. The shell only grants
 * URIs through share intents; other apps cannot browse the provider.
 */
public final class ModuleFileProvider extends ContentProvider {

    public static final String AUTHORITY_SUFFIX = ".modulefiles";

    private ModuleFiles files;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        files = new ModuleFiles(new File(context.getFilesDir(), "data"));
        return true;
    }

    public static Uri uriFor(Context context, String moduleId, String relativePath) {
        Uri.Builder builder = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + AUTHORITY_SUFFIX)
                .appendPath(moduleId);
        for (String segment : relativePath.split("/")) {
            if (!segment.isEmpty()) builder.appendPath(segment);
        }
        return builder.build();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !mode.equals("r")) {
            throw new FileNotFoundException("只允许读取模块文件");
        }
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        try {
            String name = resolve(uri).getName();
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length() - 1) return "application/octet-stream";
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            return mime == null ? "application/octet-stream" : mime;
        } catch (FileNotFoundException error) {
            return "application/octet-stream";
        }
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException error) {
            throw new IllegalArgumentException("模块文件不存在", error);
        }
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i += 1) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = file.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (files == null) throw new IllegalStateException("provider 未初始化");
        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2) throw new FileNotFoundException("路径为空");
        String moduleId = segments.get(0);
        if (!ModuleManifest.isValidId(moduleId)) {
            throw new FileNotFoundException("非法模块 id");
        }
        StringBuilder relative = new StringBuilder();
        for (int i = 1; i < segments.size(); i += 1) {
            if (relative.length() > 0) relative.append('/');
            relative.append(segments.get(i));
        }
        try {
            File file = files.fileFor(moduleId, relative.toString());
            if (!file.isFile()) throw new FileNotFoundException("文件不存在");
            return file;
        } catch (ModuleException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
