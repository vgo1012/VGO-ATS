package com.ezaigc.hr.reporting.monthlyreport.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MonthlyReportFileKind implements CodeDescEnum {
    OFFER("Offer明细"),
    DEMAND("招聘需求"),
    REPORT_TEMP("临时报告"),
    REPORT("正式报告");

    private final String desc;
}
