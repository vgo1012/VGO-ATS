package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 匹配运行尝试状态。
 */
@Getter
@RequiredArgsConstructor
public enum MatchRunAttemptStatus implements CodeDescEnum {

    RUNNING("执行中"),
    SUCCEEDED("已成功"),
    FAILED("已失败"),
    CANCELLED("已取消");

    private final String desc;

    public static MatchRunAttemptStatus fromCode(String code) {
        return MatchRunAttemptStatus.valueOf(code);
    }
}
