package com.docklet.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small dependency-free JSON reader/writer.
 *
 * <p>The shell needs JSON on Android and in plain JVM unit tests. Android's
 * {@code org.json} is not available on the JVM, and pulling a JSON library into
 * a platform core is unnecessary for the narrow contract here.</p>
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) throws JsonException {
        if (text == null) throw new JsonException("JSON 内容为空");
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("JSON 末尾存在多余内容，位置 " + parser.position());
        }
        return value;
    }

    public static Map<String, Object> parseObject(String text) throws JsonException {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new JsonException("JSON 顶层必须是对象");
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) value;
        return object;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    public static String string(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        return value instanceof String ? (String) value : null;
    }

    public static String string(Map<String, Object> object, String key, String fallback) {
        String value = string(object, key);
        return value == null ? fallback : value;
    }

    public static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object == null ? null : object.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static long longValue(Map<String, Object> object, String key, long fallback) {
        Object value = object == null ? null : object.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        return fallback;
    }

    public static int intValue(Map<String, Object> object, String key, int fallback) {
        Object value = object == null ? null : object.get(key);
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            if (number > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            if (number < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            return (int) number;
        }
        return fallback;
    }

    public static List<String> stringList(Map<String, Object> object, String key) {
        Object value = object == null ? null : object.get(key);
        List<?> array = asArray(value);
        if (array == null) return new ArrayList<>();
        List<String> strings = new ArrayList<>(array.size());
        for (Object item : array) {
            if (item instanceof String) strings.add((String) item);
        }
        return strings;
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number) {
            if (value instanceof Double || value instanceof Float) {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException("JSON 不支持非有限数字");
                }
                if (number == Math.rint(number) && Math.abs(number) < 9.0e15) {
                    out.append((long) number);
                } else {
                    out.append(number);
                }
            } else {
                out.append(value);
            }
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new IllegalArgumentException("JSON 对象的 key 必须是字符串");
                }
                if (!first) out.append(',');
                first = false;
                writeString(out, (String) entry.getKey());
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i += 1) {
                if (i > 0) out.append(',');
                writeValue(out, java.lang.reflect.Array.get(value, i));
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("不支持的 JSON 类型: " + value.getClass());
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i += 1) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    public static final class JsonException extends Exception {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        int position() {
            return index;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    index += 1;
                } else {
                    break;
                }
            }
        }

        Object readValue() throws JsonException {
            skipWhitespace();
            if (atEnd()) throw error("JSON 意外结束");
            char c = text.charAt(index);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                    throw error("无法识别的 JSON 字符 '" + c + "'");
            }
        }

        private Map<String, Object> readObject() throws JsonException {
            Map<String, Object> object = new LinkedHashMap<>();
            index += 1; // {
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index += 1;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') throw error("对象 key 必须是字符串");
                String key = readString();
                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') throw error("对象 key 后缺少 ':'");
                index += 1;
                Object value = readValue();
                object.put(key, value);
                skipWhitespace();
                if (atEnd()) throw error("对象未闭合");
                char c = text.charAt(index);
                if (c == ',') {
                    index += 1;
                } else if (c == '}') {
                    index += 1;
                    return object;
                } else {
                    throw error("对象成员之间缺少 ',' 或 '}'");
                }
            }
        }

        private List<Object> readArray() throws JsonException {
            List<Object> array = new ArrayList<>();
            index += 1; // [
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index += 1;
                return array;
            }
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (atEnd()) throw error("数组未闭合");
                char c = text.charAt(index);
                if (c == ',') {
                    index += 1;
                } else if (c == ']') {
                    index += 1;
                    return array;
                } else {
                    throw error("数组元素之间缺少 ',' 或 ']'");
                }
            }
        }

        private String readString() throws JsonException {
            index += 1; // opening quote
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index);
                index += 1;
                if (c == '"') return out.toString();
                if (c == '\\') {
                    if (atEnd()) throw error("字符串转义不完整");
                    char esc = text.charAt(index);
                    index += 1;
                    switch (esc) {
                        case '"':
                            out.append('"');
                            break;
                        case '\\':
                            out.append('\\');
                            break;
                        case '/':
                            out.append('/');
                            break;
                        case 'b':
                            out.append('\b');
                            break;
                        case 'f':
                            out.append('\f');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > text.length()) throw error("Unicode 转义不完整");
                            String hex = text.substring(index, index + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException error) {
                                throw error("非法 Unicode 转义 \\u" + hex);
                            }
                            index += 4;
                            break;
                        default:
                            throw error("非法转义 \\" + esc);
                    }
                } else if (c < 0x20) {
                    throw error("字符串中包含控制字符");
                } else {
                    out.append(c);
                }
            }
            throw error("字符串未闭合");
        }

        private Object readNumber() throws JsonException {
            int start = index;
            if (text.charAt(index) == '-') index += 1;
            while (!atEnd() && Character.isDigit(text.charAt(index))) index += 1;
            boolean floating = false;
            if (!atEnd() && text.charAt(index) == '.') {
                floating = true;
                index += 1;
                while (!atEnd() && Character.isDigit(text.charAt(index))) index += 1;
            }
            if (!atEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                floating = true;
                index += 1;
                if (!atEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index += 1;
                }
                while (!atEnd() && Character.isDigit(text.charAt(index))) index += 1;
            }
            String number = text.substring(start, index);
            try {
                if (floating) return Double.parseDouble(number);
                return Long.parseLong(number);
            } catch (NumberFormatException error) {
                throw error("非法数字 " + number);
            }
        }

        private void expect(String literal) throws JsonException {
            if (!text.startsWith(literal, index)) throw error("期望 " + literal);
            index += literal.length();
        }

        private JsonException error(String message) {
            return new JsonException(message + "（位置 " + index + "）");
        }
    }
}
