package com.jgeraldo.mediaprojectionsample;

/**
 * أداة آمنة لقراءة Enum من SharedPreferences
 * تمنع الانهيار إذا كانت القيمة المخزنة غير صالحة
 */
public class EnumUtils {
    public static <T extends Enum<T>> T getSafeEnumValue(T[] values, int ordinal, T defaultValue) {
        if (values == null) return defaultValue;
        if (ordinal < 0 || ordinal >= values.length) return defaultValue;
        return values[ordinal];
    }
}
