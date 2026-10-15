package com.ezaigc.hr.common.web.request;

import com.ezaigc.hr.common.web.error.PlatformException;
import org.springframework.http.HttpStatus;

public final class RequiredText {

    private RequiredText() {
    }

    public static String require(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw unprocessable("FIELD_REQUIRED", field + " 不能为空");
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw unprocessable("FIELD_TOO_LONG", field + " 超过最大长度 " + maxLength);
        }
        return result;
    }

    public static String require(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw unprocessable("FIELD_REQUIRED", "必填字段不能为空");
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw unprocessable("FIELD_TOO_LONG", "字段超过最大长度 " + maxLength);
        }
        return result;
    }

    /**
     * 规范化可选文本并限制其长度，避免绕过 HTTP Bean Validation 的内部调用把超长值传给下游。
     */
    public static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw unprocessable("FIELD_TOO_LONG", field + " 超过最大长度 " + maxLength);
        }
        return result;
    }

    /**
     * 校验旧版统一字段契约：空白或超长均返回 {@code FIELD_INVALID}。
     *
     * <p>仅供仍公开该稳定错误码的接口使用；新接口优先使用 {@link #require(String, String, int)}。
     */
    public static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.strip().length() > maxLength) {
            throw unprocessable("FIELD_INVALID", field + " 必须为 1～" + maxLength + " 字");
        }
        return value.strip();
    }

    private static PlatformException unprocessable(String code, String message) {
        return new PlatformException(HttpStatus.UNPROCESSABLE_CONTENT, code, message, false);
    }
}
