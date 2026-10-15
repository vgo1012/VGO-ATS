package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarMode implements CodeDescEnum {
    LOCAL_ONLY("仅本地面试"),
    WECOM_SCHEDULE("企业微信日程"),
    WECOM_MEETING("企业微信会议");

    private final String desc;

    public static boolean supports(String value) {
        if (value == null) return false;
        for (InterviewExternalCalendarMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public static boolean localOnly(String value) {
        return LOCAL_ONLY.name().equalsIgnoreCase(value);
    }
}
