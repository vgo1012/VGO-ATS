package com.ezaigc.hr.reporting.monthlyreport.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MonthlyReportStatus implements CodeDescEnum {
    UPLOADING("上传中"),
    VALIDATING("校验中"),
    INVALID("校验未通过"),
    READY("可生成"),
    QUEUED("排队中"),
    RUNNING("生成中"),
    CANCELLING("取消中"),
    SUCCEEDED("已完成"),
    FAILED("已失败"),
    CANCELLED("已取消"),
    EXPIRED("已过期"),
    DELETED("已删除");

    private final String desc;

    public static boolean isActive(String code) {
        return UPLOADING.matches(code) || VALIDATING.matches(code) || QUEUED.matches(code);
    }

    public static final class Codes {
        public static final String UPLOADING = "UPLOADING";
        public static final String VALIDATING = "VALIDATING";
        public static final String QUEUED = "QUEUED";

        private Codes() {
        }
    }
}
