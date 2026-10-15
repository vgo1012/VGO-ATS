package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

public final class MonthlyReportInterruptedException extends RuntimeException {

    public MonthlyReportInterruptedException() {
        super("月报处理已取消");
    }
}
