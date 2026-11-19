package com.docklet.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict {@code x.y.z} version used by module packages. */
public final class SemVer implements Comparable<SemVer> {

    private static final Pattern PATTERN = Pattern.compile("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String text;

    private SemVer(int major, int minor, int patch, String text) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.text = text;
    }

    public static SemVer parse(String value) {
        if (value == null) throw new IllegalArgumentException("版本号为空");
        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) throw new IllegalArgumentException("版本号必须为 x.y.z: " + value);
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = Integer.parseInt(matcher.group(3));
            return new SemVer(major, minor, patch, value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("版本号数值过大: " + value);
        }
    }

    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    public static int compare(String left, String right) {
        return parse(left).compareTo(parse(right));
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    @Override
    public int compareTo(SemVer other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SemVer)) return false;
        return text.equals(((SemVer) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
