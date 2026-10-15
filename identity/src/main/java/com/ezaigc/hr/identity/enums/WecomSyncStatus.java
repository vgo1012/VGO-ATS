package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WecomSyncStatus implements CodeDescEnum {
    QUEUED("等待执行"),
    RUNNING("执行中"),
    SUCCEEDED("已发布"),
    WARNING("已发布且需核实人员"),
    FAILED("失败未发布"),
    INTERRUPTED("执行中断");

    private final String desc;
}
