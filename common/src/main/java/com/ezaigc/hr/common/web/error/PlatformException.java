package com.ezaigc.hr.common.web.error;

import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatusCode;

/**
 * 平台业务异常类型。
 */
@Getter
@Accessors(fluent = true)
public class PlatformException extends RuntimeException {

    /**
     * 当前状态。
     */
    private final HttpStatusCode status;
    /**
     * 业务编码。
     */
    private final String code;
    /**
     * 异常是否可以恢复。
     */
    private final boolean recoverable;
    private final List<FieldViolation> fieldErrors;

    public PlatformException(HttpStatusCode status, String code, String detail, boolean recoverable) {
        this(status, code, detail, recoverable, List.of(), null);
    }

    /**
     * 初始化保留底层失败原因的平台异常。
     */
    public PlatformException(
            HttpStatusCode status,
            String code,
            String detail,
            boolean recoverable,
            Throwable cause) {
        this(status, code, detail, recoverable, List.of(), cause);
    }

    public PlatformException(
            HttpStatusCode status,
            String code,
            String detail,
            boolean recoverable,
            List<FieldViolation> fieldErrors) {
        this(status, code, detail, recoverable, fieldErrors, null);
    }

    /**
     * 初始化同时包含字段错误和底层失败原因的平台异常。
     */
    public PlatformException(
            HttpStatusCode status,
            String code,
            String detail,
            boolean recoverable,
            List<FieldViolation> fieldErrors,
            Throwable cause) {
        super(detail, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.code = requireText(code, "code");
        this.recoverable = recoverable;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
