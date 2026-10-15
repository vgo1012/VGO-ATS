package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;

/**
 * 审计投影采集通道。
 */
public enum AuditCaptureLane implements CodeDescEnum {

    INCREMENTAL("INCREMENTAL", "增量采集"),
    RECONCILIATION("RECONCILIATION", "循环校对");

    @Getter
    private final String code;
    private final String desc;

    AuditCaptureLane(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
