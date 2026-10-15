package com.ezaigc.hr.common.web.version;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.badRequest;

/**
 * HTTP 资源乐观锁版本的字符串边界转换。
 */
public final class ResourceVersion {

    /** 非负 long 版本号在线路上的最大十进制长度。 */
    public static final int MAXIMUM_INPUT_LENGTH = 19;

    /** 版本号仅接受 ASCII 十进制数字，避免符号、空白和其他数字字符混入。 */
    public static final String INPUT_PATTERN = "[0-9]{1,19}";

    private ResourceVersion() {
    }

    /**
     * 将线路上的十进制字符串解析为内部版本号。
     */
    public static long parse(String value) {
        if (!isUnsignedDecimal(value)) {
            throw invalid();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    /**
     * 将内部版本号格式化为线路字符串。
     */
    public static String format(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("resource version must not be negative");
        }
        return Long.toString(value);
    }

    /**
     * 判断线路版本值是否可以无损转换为非负 long。
     *
     * <p>供 Bean Validation 与显式解析共享完全一致的格式、长度和溢出规则。
     */
    public static boolean isValid(String value) {
        if (!isUnsignedDecimal(value)) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isUnsignedDecimal(String value) {
        if (value == null || value.isEmpty() || value.length() > MAXIMUM_INPUT_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static RuntimeException invalid() {
        return badRequest("VERSION_INVALID", "version 必须是非负十进制整数");
    }
}
