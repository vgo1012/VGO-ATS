package com.ezaigc.hr.common.core.util;

import java.util.Locale;

public final class CommonUtils {

    private CommonUtils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String stripToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    public static String upperOrEmpty(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * 去除首尾空白；空值或空白值使用调用方给定的默认值。
     */
    public static String stripOrElse(String value, String fallback) {
        String stripped = stripToNull(value);
        return stripped == null ? fallback : stripped;
    }

    /**
     * 转为大写并去除首尾空白；空值或空白值使用调用方给定的默认值。
     */
    public static String upperOrElse(String value, String fallback) {
        String stripped = stripToNull(value);
        return stripped == null ? fallback : stripped.toUpperCase(Locale.ROOT);
    }

    public static String upperToNull(String value) {
        String stripped = stripToNull(value);
        return stripped == null ? null : stripped.toUpperCase(Locale.ROOT);
    }
}
