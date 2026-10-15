package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarStatus implements CodeDescEnum {
    PENDING("待同步"),
    PROCESSING("同步中"),
    SYNCED("已同步"),
    CANCELLED("已取消"),
    FAILED("同步失败"),
    UNKNOWN("创建结果待核对");

    private final String desc;
}
