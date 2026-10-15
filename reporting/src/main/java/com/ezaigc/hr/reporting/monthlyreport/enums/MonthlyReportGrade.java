package com.ezaigc.hr.reporting.monthlyreport.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.math.BigDecimal;

/** 公开示例职级采用相同基础分，变更时须同时提升计分规则版本。 */
public enum MonthlyReportGrade implements CodeDescEnum {
    G1("1"), G2("1"), G3("1");

    private final BigDecimal score;
    MonthlyReportGrade(String score) { this.score = new BigDecimal(score); }
    public BigDecimal getScore() { return score; }
    @Override public String getDesc() { return name() + "职级基础分" + score.toPlainString(); }
}
