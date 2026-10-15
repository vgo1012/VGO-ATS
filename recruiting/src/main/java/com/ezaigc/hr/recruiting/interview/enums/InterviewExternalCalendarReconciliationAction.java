package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarReconciliationAction implements CodeDescEnum {
    ADOPT_EXISTING("核对并采纳已有远端对象"),
    CONFIRM_NOT_CREATED("确认原请求已终止且远端未创建");

    private final String desc;
}
