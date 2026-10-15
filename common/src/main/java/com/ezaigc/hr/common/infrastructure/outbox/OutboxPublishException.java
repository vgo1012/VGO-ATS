package com.ezaigc.hr.common.infrastructure.outbox;

import lombok.Getter;

/** 发布失败分类只包含固定安全诊断，供应商原始消息不能进入工作台。 */
@Getter
public class OutboxPublishException extends RuntimeException {
    private final String code;
    private final String safeDetail;
    private final boolean retryable;

    public OutboxPublishException(String code, String safeDetail, boolean retryable, Throwable cause) {
        super(safeDetail, cause);
        this.code = code;
        this.safeDetail = safeDetail;
        this.retryable = retryable;
    }
}
