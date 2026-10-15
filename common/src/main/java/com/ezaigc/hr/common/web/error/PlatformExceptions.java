package com.ezaigc.hr.common.web.error;

import org.springframework.http.HttpStatus;

public final class PlatformExceptions {

    private PlatformExceptions() {
    }

    public static PlatformException badRequest(String code, String detail) {
        return new PlatformException(HttpStatus.BAD_REQUEST, code, detail, true);
    }

    public static PlatformException forbidden(String code, String detail) {
        return new PlatformException(HttpStatus.FORBIDDEN, code, detail, false);
    }

    public static PlatformException notFound(String code, String detail) {
        return new PlatformException(HttpStatus.NOT_FOUND, code, detail, false);
    }

    public static PlatformException conflict(String code, String detail) {
        return new PlatformException(HttpStatus.CONFLICT, code, detail, true);
    }

    /**
     * 创建统一的乐观锁/版本冲突响应。
     */
    public static PlatformException versionConflict(String code) {
        return conflict(code, "数据已被其他操作修改，请刷新后重试");
    }

    /**
     * 创建使用平台默认错误码的乐观锁/版本冲突响应。
     */
    public static PlatformException versionConflict() {
        return versionConflict("VERSION_CONFLICT");
    }

    public static PlatformException unprocessable(String code, String detail) {
        return new PlatformException(HttpStatus.UNPROCESSABLE_CONTENT, code, detail, false);
    }

    public static PlatformException unavailable(String code, String detail) {
        return new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, code, detail, true);
    }

    /** 创建保留底层失败原因的依赖不可用异常，供统一 5xx 日志输出完整异常链。 */
    public static PlatformException unavailable(String code, String detail, Throwable cause) {
        return new PlatformException(HttpStatus.SERVICE_UNAVAILABLE, code, detail, true, cause);
    }
}
