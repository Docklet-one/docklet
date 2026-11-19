package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SemVerTest {

    @Test
    public void ordersNumerically() {
        assertTrue(SemVer.compare("1.0.10", "1.0.9") > 0);
        assertTrue(SemVer.compare("2.0.0", "1.99.99") > 0);
        assertEquals(0, SemVer.compare("1.2.3", "1.2.3"));
        assertTrue(SemVer.compare("1.2.3", "1.3.0") < 0);
    }

    @Test
    public void rejectsNonStrictVersions() {
        assertFalse(SemVer.isValid("v1.0.0"));
        assertFalse(SemVer.isValid("1.0"));
        assertFalse(SemVer.isValid("01.0.0"));
        assertFalse(SemVer.isValid("1.0.0-beta"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.0"));
    }
}
