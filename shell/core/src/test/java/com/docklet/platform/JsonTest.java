package com.docklet.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonTest {

    @Test
    public void parsesNestedDocument() throws Exception {
        Map<String, Object> object = Json.parseObject(
                "{\"id\":\"demo\",\"n\":3,\"f\":1.5,\"ok\":true,\"none\":null,"
                        + "\"list\":[\"storage\",\"notify\"],\"escaped\":\"a\\n\\u4e2d\"}"
        );
        assertEquals("demo", object.get("id"));
        assertEquals(3L, object.get("n"));
        assertEquals(1.5d, (Double) object.get("f"), 0.0001);
        assertEquals(Boolean.TRUE, object.get("ok"));
        assertTrue(object.containsKey("none"));
        assertEquals(Arrays.asList("storage", "notify"), object.get("list"));
        assertEquals("a\n中", object.get("escaped"));
    }

    @Test
    public void roundTripsUnicodeAndNestedValues() throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "示例模块");
        value.put("quote", "a\"b\\c\n");
        value.put("rows", Arrays.asList(1L, 2.5, Boolean.FALSE, null));
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("ok", true);
        value.put("child", child);

        String encoded = Json.stringify(value);
        Map<String, Object> parsed = Json.parseObject(encoded);
        assertEquals("示例模块", parsed.get("name"));
        assertEquals("a\"b\\c\n", parsed.get("quote"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) parsed.get("child")).get("ok"));
        List<?> rows = (List<?>) parsed.get("rows");
        assertEquals(4, rows.size());
    }

    @Test
    public void rejectsTrailingGarbage() {
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(Json.JsonException.class, () -> Json.parse(""));
    }

    @Test
    public void helpersDoNotSilentlyCoerceObjects() throws Exception {
        Map<String, Object> json = Json.parseObject("{\"name\":42,\"items\":[1,\"a\"]}");
        assertFalse(json.get("name") instanceof String);
        assertEquals(1, Json.stringList(json, "items").size());
        assertEquals("fallback", Json.string(json, "name", "fallback"));
    }
}
