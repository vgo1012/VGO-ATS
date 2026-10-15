package com.ezaigc.hr.common.infrastructure.task.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;

public enum ScheduledScanError implements CodeDescEnum {
    DISPATCH_REJECTED("执行队列拒绝提交"), DISPATCH_FAILED("提交失败"),
    SCAN_FAILED("扫描异常"), SCAN_ABORTED("扫描异常中断");

    private final String desc;

    ScheduledScanError(String desc) { this.desc = desc; }

    public String getDesc() { return desc; }
}
