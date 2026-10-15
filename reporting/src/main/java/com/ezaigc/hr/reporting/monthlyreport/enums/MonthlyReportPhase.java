package com.ezaigc.hr.reporting.monthlyreport.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MonthlyReportPhase implements CodeDescEnum {
    UPLOAD("上传"),
    VALIDATE("校验"),
    GENERATE("生成");

    private final String desc;
}
