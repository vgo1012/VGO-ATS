package com.ezaigc.hr.reporting.monthlyreport.constant;

/** 月报独立资源和任务标识，不复用已退役的候选人导出。 */
public final class MonthlyReportConstants {
    public static final String PERMISSION = "reporting.monthly-report.manage";
    public static final String RESOURCE = "MONTHLY_REPORT";
    public static final String VALIDATE_TASK = "MONTHLY_REPORT_VALIDATE";
    public static final String GENERATE_TASK = "MONTHLY_RECRUITMENT_REPORT";
    private MonthlyReportConstants() {}
    public static boolean isMonthlyTask(String type) {
        return VALIDATE_TASK.equals(type) || GENERATE_TASK.equals(type);
    }
}
