package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewExternalCalendarLegacyCreateError implements CodeDescEnum {
    WECOM_503("旧版网络结果未确认"),
    WECOM_CALL_EXCEPTION("旧版调用异常未确认"),
    WECOM_INVALID_RESPONSE("旧版创建 ID 缺失"),
    INVALID_ADAPTER_RESULT("旧版适配器无有效结果");

    private final String desc;

    public static boolean ambiguous(String code) {
        for (InterviewExternalCalendarLegacyCreateError value : values()) {
            if (value.matches(code)) return true;
        }
        return false;
    }
}
