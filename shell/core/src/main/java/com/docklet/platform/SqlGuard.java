package com.docklet.platform;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Conservative SQL policy for the module storage bridge.
 *
 * <p>Modules are trusted first-party code, so this is not a security sandbox.
 * It prevents accidental multi-statement execution, cross-database operations
 * and the most dangerous SQLite escape hatches.</p>
 */
public final class SqlGuard {

    private static final int MAX_SQL_LENGTH = 10_000;

    private static final Set<String> ALLOWED_FIRST = new HashSet<>(Arrays.asList(
            "SELECT",
            "WITH",
            "INSERT",
            "UPDATE",
            "DELETE",
            "REPLACE",
            "CREATE",
            "DROP",
            "ALTER",
            "PRAGMA"
    ));

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(ATTACH|DETACH|VACUUM|LOAD_EXTENSION|WRITABLE_SCHEMA|"
                    + "TEMP_STORE_DIRECTORY|DATA_STORE_DIRECTORY|BEGIN|COMMIT|ROLLBACK|SAVEPOINT|RELEASE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private SqlGuard() {
    }

    public static String validate(String sql) throws ModuleException {
        if (sql == null) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 为空");
        }
        String trimmed = sql.trim();
        if (trimmed.isEmpty()) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 为空");
        }
        if (trimmed.length() > MAX_SQL_LENGTH) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 过长");
        }
        if (trimmed.indexOf('\u0000') >= 0) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 包含非法字符");
        }
        if (trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "不允许 SQL 注释");
        }

        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 为空");
        }
        if (trimmed.indexOf(';') >= 0) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "一次只能执行一条 SQL");
        }

        String upper = trimmed.toUpperCase(Locale.ROOT);
        String first = firstKeyword(upper);
        if (first == null || !ALLOWED_FIRST.contains(first)) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "不允许的 SQL 语句: " + first);
        }
        if (FORBIDDEN.matcher(upper).find()) {
            throw new ModuleException(ModuleException.SQL_REJECTED, "SQL 包含被禁止的关键字");
        }
        return trimmed;
    }

    private static String firstKeyword(String upper) {
        int index = 0;
        while (index < upper.length() && Character.isWhitespace(upper.charAt(index))) {
            index += 1;
        }
        int start = index;
        while (index < upper.length()) {
            char c = upper.charAt(index);
            if (!(Character.isLetter(c) || c == '_')) break;
            index += 1;
        }
        return start == index ? null : upper.substring(start, index);
    }
}
