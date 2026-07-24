package com.jgeraldo.mediaprojectionsample;

public final class EnumUtils {
    private EnumUtils() {}

    public static <T> T getSafeEnumValue(T[] values, int ordinal, T fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        if (ordinal < 0 || ordinal >= values.length) {
            return fallback;
        }
        return values[ordinal];
    }
}
