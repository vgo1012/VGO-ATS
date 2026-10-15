package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarRemoteStatus implements CodeDescEnum {
    ACTIVE("远端对象已创建"),
    ACTIVE_WITH_EXCLUDED_USERS("远端对象已创建，部分成员未加入"),
    CANCELLED("远端对象已取消");

    private final String desc;
}
