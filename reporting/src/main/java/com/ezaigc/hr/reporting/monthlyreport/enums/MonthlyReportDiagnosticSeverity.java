package com.ezaigc.hr.reporting.monthlyreport.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;

public enum MonthlyReportDiagnosticSeverity implements CodeDescEnum {
    ERROR("错误"),
    WARNING("警告"),
    INFO("提示");

    private final String desc;

    MonthlyReportDiagnosticSeverity(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
