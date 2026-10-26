package com.project.back_end.util;

import java.util.Locale;

/** Small helpers for cleaning user input. */
public final class NameUtils {

    private NameUtils() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
