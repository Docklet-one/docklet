package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SqlGuardTest {

    @Test
    public void allowsNormalDataStatements() throws Exception {
        assertEquals(
                "SELECT * FROM demo_state WHERE key = ?",
                SqlGuard.validate(" SELECT * FROM demo_state WHERE key = ? ; ")
        );
        assertEquals(
                "INSERT OR REPLACE INTO demo_state(key, value) VALUES (?, ?)",
                SqlGuard.validate("INSERT OR REPLACE INTO demo_state(key, value) VALUES (?, ?)")
        );
        assertEquals("PRAGMA user_version = 1", SqlGuard.validate("PRAGMA user_version = 1"));
        assertTrue(SqlGuard.validate("CREATE TABLE IF NOT EXISTS t (id TEXT)").startsWith("CREATE"));
        assertTrue(SqlGuard.validate("WITH x AS (SELECT 1) SELECT * FROM x").startsWith("WITH"));
    }

    @Test
    public void rejectsMultipleStatementsAndComments() {
        assertThrows(ModuleException.class, () -> SqlGuard.validate("SELECT 1; DELETE FROM t"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("SELECT 1 -- comment"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("SELECT /* x */ 1"));
    }

    @Test
    public void rejectsDangerousStatements() {
        assertThrows(ModuleException.class, () -> SqlGuard.validate("ATTACH DATABASE 'x' AS y"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("DETACH DATABASE y"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("VACUUM INTO 'x'"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("PRAGMA writable_schema = ON"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("BEGIN"));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("SELECT load_extension('x')"));
    }

    @Test
    public void rejectsEmptyAndOversizedStatements() {
        assertThrows(ModuleException.class, () -> SqlGuard.validate(""));
        assertThrows(ModuleException.class, () -> SqlGuard.validate("   "));
        StringBuilder huge = new StringBuilder("SELECT ");
        for (int i = 0; i < 11_000; i += 1) huge.append('x');
        assertThrows(ModuleException.class, () -> SqlGuard.validate(huge.toString()));
    }
}
