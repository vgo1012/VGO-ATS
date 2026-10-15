package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarAttemptStatus implements CodeDescEnum {
    RUNNING("已提交创建意图"),
    SUCCEEDED("成功"),
    FAILED("失败"),
    UNKNOWN("创建结果待核对");

    private final String desc;
}
