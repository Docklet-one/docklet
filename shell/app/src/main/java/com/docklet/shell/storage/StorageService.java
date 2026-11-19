package com.docklet.shell.storage;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.docklet.platform.ModuleException;
import com.docklet.platform.ModuleManifest;
import com.docklet.platform.SqlGuard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-module native SQLite storage.
 *
 * <p>The only database a module can reach is
 * {@code files/data/<id>/app.db}. Schema and migrations belong to the module;
 * this service provides connections, guarded statements and manual backups.</p>
 */
public final class StorageService {

    public static final int MAX_QUERY_ROWS = 500;
    private static final int MAX_BACKUPS = 5;

    private final File dataRoot;
    private final Map<String, SQLiteDatabase> openDatabases = new LinkedHashMap<>();

    public StorageService(File dataRoot) {
        if (dataRoot == null) throw new IllegalArgumentException("dataRoot 不能为空");
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new IllegalStateException("无法创建 data 目录: " + dataRoot);
        }
        this.dataRoot = dataRoot;
    }

    public synchronized StorageInfo open(String moduleId) throws ModuleException {
        SQLiteDatabase database = database(moduleId);
        return new StorageInfo(
                "data/" + moduleId + "/app.db",
                database.getVersion(),
                database.getPath()
        );
    }

    public synchronized QueryResult query(String moduleId, String sql, List<Object> params)
            throws ModuleException {
        String guarded = SqlGuard.validate(sql);
        SQLiteDatabase database = database(moduleId);
        String[] args = queryArgs(params);
        try (Cursor cursor = database.rawQuery(guarded, args)) {
            List<String> columns = new ArrayList<>(Arrays.asList(cursor.getColumnNames()));
            List<Map<String, Object>> rows = new ArrayList<>();
            while (cursor.moveToNext()) {
                if (rows.size() >= MAX_QUERY_ROWS) {
                    throw new ModuleException(
                            ModuleException.STORAGE_ERROR,
                            "查询结果超过 " + MAX_QUERY_ROWS + " 行，请使用 LIMIT"
                    );
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i += 1) {
                    row.put(columns.get(i), readValue(cursor, i));
                }
                rows.add(row);
            }
            return new QueryResult(columns, rows);
        } catch (ModuleException error) {
            throw error;
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.STORAGE_ERROR,
                    "查询失败: " + safeMessage(error),
                    error
            );
        }
    }

    public synchronized ExecResult exec(String moduleId, String sql, List<Object> params)
            throws ModuleException {
        String guarded = SqlGuard.validate(sql);
        SQLiteDatabase database = database(moduleId);
        Object[] args = bindArgs(params);
        try {
            database.execSQL(guarded, args);
            long changes = DatabaseUtils.longForQuery(database, "SELECT changes()", null);
            long lastInsertRowId = DatabaseUtils.longForQuery(
                    database,
                    "SELECT last_insert_rowid()",
                    null
            );
            return new ExecResult(changes, lastInsertRowId);
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.STORAGE_ERROR,
                    "执行失败: " + safeMessage(error),
                    error
            );
        }
    }

    public synchronized TransactionResult transaction(String moduleId, List<Statement> statements)
            throws ModuleException {
        if (statements == null || statements.isEmpty()) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "事务不能为空");
        }
        if (statements.size() > 100) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "单个事务最多 100 条语句");
        }
        SQLiteDatabase database = database(moduleId);
        database.beginTransaction();
        try {
            int applied = 0;
            for (Statement statement : statements) {
                if (statement == null) {
                    throw new ModuleException(ModuleException.STORAGE_ERROR, "事务语句为空");
                }
                database.execSQL(
                        SqlGuard.validate(statement.sql),
                        bindArgs(statement.params)
                );
                applied += 1;
            }
            database.setTransactionSuccessful();
            return new TransactionResult(applied);
        } catch (ModuleException error) {
            throw error;
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.STORAGE_ERROR,
                    "事务失败: " + safeMessage(error),
                    error
            );
        } finally {
            try {
                database.endTransaction();
            } catch (Exception ignored) {
                // Connection may already be invalid.
            }
        }
    }

    public synchronized BackupResult backup(String moduleId) throws ModuleException {
        SQLiteDatabase database = database(moduleId);
        File moduleDir = new File(dataRoot, moduleId);
        File backupDir = new File(moduleDir, "backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "无法创建备份目录");
        }
        try (Cursor cursor = database.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
            cursor.moveToFirst();
        } catch (Exception ignored) {
            // Best effort before copying the main database file.
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        File backup = new File(backupDir, "app-" + stamp + ".db");
        File source = new File(moduleDir, "app.db");
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(backup)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        } catch (IOException error) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "备份失败: " + error.getMessage(), error);
        }
        pruneBackups(backupDir);
        return new BackupResult(backup.getAbsolutePath(), backup.length());
    }

    private void pruneBackups(File backupDir) {
        File[] files = backupDir.listFiles((dir, name) -> name.startsWith("app-") && name.endsWith(".db"));
        if (files == null || files.length <= MAX_BACKUPS) return;
        Arrays.sort(files, (left, right) -> left.getName().compareTo(right.getName()));
        int remove = files.length - MAX_BACKUPS;
        for (int i = 0; i < remove; i += 1) {
            try {
                Files.deleteIfExists(files[i].toPath());
            } catch (IOException ignored) {
                // Retention is best effort; a failed delete never blocks backup.
            }
        }
    }

    public synchronized void close(String moduleId) {
        SQLiteDatabase database = openDatabases.remove(moduleId);
        if (database != null) {
            try {
                database.close();
            } catch (Exception ignored) {
                // Nothing useful to recover here.
            }
        }
    }

    public synchronized void closeAll() {
        for (SQLiteDatabase database : openDatabases.values()) {
            try {
                database.close();
            } catch (Exception ignored) {
                // Continue closing the rest.
            }
        }
        openDatabases.clear();
    }

    public synchronized File databaseFile(String moduleId) throws ModuleException {
        validateId(moduleId);
        File moduleDir = new File(dataRoot, moduleId);
        if (!moduleDir.exists() && !moduleDir.mkdirs()) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "无法创建模块数据目录");
        }
        File filesDir = new File(moduleDir, "files");
        if (!filesDir.exists()) filesDir.mkdirs();
        try {
            File base = moduleDir.getCanonicalFile();
            File db = new File(base, "app.db").getCanonicalFile();
            if (!db.getPath().startsWith(base.getPath() + File.separator)) {
                throw new ModuleException(ModuleException.STORAGE_ERROR, "数据库路径越界");
            }
            return db;
        } catch (IOException error) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "无法解析数据库路径", error);
        }
    }

    public synchronized long dataSize(String moduleId) {
        if (!ModuleManifest.isValidId(moduleId)) return 0L;
        return sizeOf(new File(dataRoot, moduleId));
    }

    private SQLiteDatabase database(String moduleId) throws ModuleException {
        validateId(moduleId);
        SQLiteDatabase existing = openDatabases.get(moduleId);
        if (existing != null && existing.isOpen()) return existing;
        File file = databaseFile(moduleId);
        try {
            SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(file, null);
            try {
                database.enableWriteAheadLogging();
            } catch (Exception ignored) {
                // Some filesystems do not support WAL; the default journal still works.
            }
            openDatabases.put(moduleId, database);
            return database;
        } catch (Exception error) {
            throw new ModuleException(
                    ModuleException.STORAGE_ERROR,
                    "无法打开数据库: " + safeMessage(error),
                    error
            );
        }
    }

    private void validateId(String moduleId) throws ModuleException {
        if (!ModuleManifest.isValidId(moduleId)) {
            throw new ModuleException(ModuleException.INVALID_ID, "非法模块 id: " + moduleId);
        }
    }

    private Object[] bindArgs(List<Object> params) throws ModuleException {
        if (params == null || params.isEmpty()) return new Object[0];
        if (params.size() > 100) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "参数数量超过 100");
        }
        Object[] args = new Object[params.size()];
        for (int i = 0; i < params.size(); i += 1) {
            Object value = params.get(i);
            if (value == null || value instanceof String || value instanceof Number || value instanceof byte[]) {
                args[i] = value;
            } else if (value instanceof Boolean) {
                args[i] = (Boolean) value ? 1 : 0;
            } else {
                throw new ModuleException(
                        ModuleException.STORAGE_ERROR,
                        "不支持的 SQL 参数类型: " + value.getClass().getSimpleName()
                );
            }
        }
        return args;
    }

    /**
     * Android's query API only accepts text bind arguments. Numeric and boolean
     * values are stringified; SQLite's column affinity handles comparisons and
     * inserts. Exec/transaction still use typed Object[] bindings.
     */
    private String[] queryArgs(List<Object> params) throws ModuleException {
        if (params == null || params.isEmpty()) return new String[0];
        if (params.size() > 100) {
            throw new ModuleException(ModuleException.STORAGE_ERROR, "参数数量超过 100");
        }
        String[] args = new String[params.size()];
        for (int i = 0; i < params.size(); i += 1) {
            Object value = params.get(i);
            if (value == null) {
                args[i] = null;
            } else if (value instanceof String) {
                args[i] = (String) value;
            } else if (value instanceof Boolean) {
                args[i] = (Boolean) value ? "1" : "0";
            } else if (value instanceof Number) {
                args[i] = String.valueOf(value);
            } else {
                throw new ModuleException(
                        ModuleException.STORAGE_ERROR,
                        "查询参数只支持字符串、数字、布尔和 null"
                );
            }
        }
        return args;
    }

    private Object readValue(Cursor cursor, int index) {
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_INTEGER:
                return cursor.getLong(index);
            case Cursor.FIELD_TYPE_FLOAT:
                return cursor.getDouble(index);
            case Cursor.FIELD_TYPE_BLOB:
                return android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP);
            case Cursor.FIELD_TYPE_STRING:
            default:
                return cursor.getString(index);
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
        }
        return total;
    }

    public static final class StorageInfo {
        public final String database;
        public final int schemaVersion;
        public final String absolutePath;

        StorageInfo(String database, int schemaVersion, String absolutePath) {
            this.database = database;
            this.schemaVersion = schemaVersion;
            this.absolutePath = absolutePath;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("database", database);
            map.put("schemaVersion", schemaVersion);
            return map;
        }
    }

    public static final class QueryResult {
        public final List<String> columns;
        public final List<Map<String, Object>> rows;

        QueryResult(List<String> columns, List<Map<String, Object>> rows) {
            this.columns = columns;
            this.rows = rows;
        }

        public List<Map<String, Object>> rows() {
            return rows;
        }
    }

    public static final class ExecResult {
        public final long changes;
        public final long lastInsertRowId;

        ExecResult(long changes, long lastInsertRowId) {
            this.changes = changes;
            this.lastInsertRowId = lastInsertRowId;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("changes", changes);
            map.put("lastInsertRowId", lastInsertRowId);
            return map;
        }
    }

    public static final class TransactionResult {
        public final int applied;

        TransactionResult(int applied) {
            this.applied = applied;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("applied", applied);
            return map;
        }
    }

    public static final class BackupResult {
        public final String path;
        public final long bytes;

        BackupResult(String path, long bytes) {
            this.path = path;
            this.bytes = bytes;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", path);
            map.put("bytes", bytes);
            return map;
        }
    }

    public static final class Statement {
        public final String sql;
        public final List<Object> params;

        public Statement(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params == null ? new ArrayList<>() : params;
        }
    }
}
