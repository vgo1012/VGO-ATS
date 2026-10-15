package com.ezaigc.hr.common.infrastructure.task.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;

public enum ScheduledScanState implements CodeDescEnum {
    QUEUED("已入队"), RUNNING("扫描运行中"), COMPLETED("扫描完成"), FAILED("扫描失败"), REJECTED("提交被拒绝");

    private final String desc;

    ScheduledScanState(String desc) { this.desc = desc; }

    public String getDesc() { return desc; }
}
