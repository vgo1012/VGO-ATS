package com.ezaigc.hr.common.web.request;

import static com.ezaigc.hr.common.web.error.PlatformExceptions.badRequest;

import java.util.regex.Pattern;

public final class IdempotencyKey {

    public static final int MAX_LENGTH = 128;

    public static final String FORMAT_REGEXP = "[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}";

    private static final Pattern FORMAT = Pattern.compile(FORMAT_REGEXP);

    private IdempotencyKey() {
    }

    public static String fromHttpHeader(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw badRequest(
                    "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key 必须为 1～128 位 URL 安全字符");
        }
        return value;
    }
}
