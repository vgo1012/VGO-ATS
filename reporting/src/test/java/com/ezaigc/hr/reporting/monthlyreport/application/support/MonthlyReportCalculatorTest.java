package com.ezaigc.hr.reporting.monthlyreport.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportCalculator.DemandRow;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportCalculator.OfferRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonthlyReportCalculatorTest {

    @Test
    void demandTotalsDoNotOverflowIntegerAndEmptyOfferRatesStayUndefined() {
        var date = LocalDate.of(2026, 8, 1);
        var demands = List.of(new DemandRow("部门甲", date, "岗位甲", Integer.MAX_VALUE, date),
                new DemandRow("部门乙", date, "岗位乙", 1, date));
        var result = new MonthlyReportCalculator().calculate(List.of(), demands, YearMonth.of(2026, 8));
        assertThat(result.demandSummary().approvedHeadcount()).isEqualTo(2147483648L);
        assertThat(result.demandSummary().monthExpectedHeadcount()).isEqualTo(2147483648L);
        assertThat(result.offerSummary().acceptedOrOnboardedRate()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"G1,1", "G2,1", "G3,1"})
    void mapsEverySupportedGrade(String grade, String expected) {
        assertThat(MonthlyReportCalculator.gradeScore(grade)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void publicExampleScoresEveryNonRejectedOfferEquallyAcrossGradesAndChannels() {
        var date = LocalDate.of(2026, 8, 1);
        var offers = List.of(
                new OfferRow("部门甲", "内部员工", "G1", "已发offer", "发送人甲", "小组甲", date, "内部推荐"),
                new OfferRow("部门甲", "内部员工", "G2", "已接受offer", "发送人乙", "小组乙", date, "示例渠道"),
                new OfferRow("部门甲", "内部员工", "G3", "已入职offer", "发送人丙", "小组丙", date, "招聘网站"),
                new OfferRow("部门甲", "内部员工", "G1", "已拒绝offer", "发送人丁", "小组丁", date, "招聘网站"));
        var result = new MonthlyReportCalculator().calculate(offers, List.of(), YearMonth.of(2026, 8));
        assertThat(result.offerSummary().totalCount()).isEqualTo(4);
        assertThat(result.offerSummary().scoredCount()).isEqualTo(3);
        assertThat(result.offerSummary().totalScore()).isEqualByComparingTo("3.00");
        assertThat(result.dimensions()).filteredOn(metric -> metric.dimension().equals("sender"))
                .extracting(metric -> metric.group()).containsExactlyInAnyOrder("小组甲", "小组乙", "小组丙", "小组丁");
        assertThat(result.analysis()).noneMatch(item -> item.ruleId().equals("DISCOUNT_CHANNEL"));
    }
}
