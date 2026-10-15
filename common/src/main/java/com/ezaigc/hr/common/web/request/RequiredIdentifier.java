package com.ezaigc.hr.common.web.request;

import com.ezaigc.hr.common.web.error.PlatformExceptions;

/**
 * 请求中的平台 26 位标识校验。
 */
public final class RequiredIdentifier {

    public static final int IDENTIFIER_LENGTH = 26;

    private RequiredIdentifier() {
    }

    /**
     * 校验并返回去除首尾空白后的平台标识。
     */
    public static String require(String value, String field) {
        String clean = RequiredText.required(value, field, IDENTIFIER_LENGTH);
        if (clean.length() != IDENTIFIER_LENGTH) {
            throw PlatformExceptions.unprocessable(
                    "IDENTIFIER_INVALID", field + " 必须是 26 位标识");
        }
        return clean;
    }
}
