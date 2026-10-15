package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarOperation implements CodeDescEnum {

    CREATE("创建"),
    UPDATE("更新"),
    CANCEL("取消"),
    NONE("无需远端操作");

    private final String desc;
}
