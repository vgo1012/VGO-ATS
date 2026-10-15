package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportLimitsDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** 显式指定本地样例，不将个人信息或源文件写入版本库。 */
@EnabledIfSystemProperty(named = "monthly.report.sampleDirectory", matches = ".+")
class MonthlyReportLocalSampleTest {
    @Test
    void verifiesApprovedSampleAndWritesReviewDocument() throws Exception {
        Path directory = Path.of(System.getProperty("monthly.report.sampleDirectory"));
        Path offer;
        Path demand;
        try (var files = Files.list(directory)) {
            offer = files.filter(p -> p.getFileName().toString().contains("offer明细") && p.toString().endsWith(".xlsx")).findFirst().orElseThrow();
        }
        try (var files = Files.list(directory)) {
            demand = files.filter(p -> p.getFileName().toString().contains("北森招聘需求") && p.toString().endsWith(".xlsx")).findFirst().orElseThrow();
        }
        var snapshot = new MonthlyReportEngine().parseAndCalculate(offer, demand,
                YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), MonthlyReportLimitsDTO.defaults());
        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.offerSummary().totalCount()).isEqualTo(171);
        assertThat(snapshot.offerSummary().totalScore()).isEqualByComparingTo("122.75");
        assertThat(snapshot.offerSummary().statusCounts()).containsEntry("已入职offer", 96)
                .containsEntry("已接受offer", 52).containsEntry("已拒绝offer", 21).containsEntry("已发offer", 2);
        assertThat(snapshot.demandSummary().approvedHeadcount()).isEqualTo(8);
        var wrongMonth = new MonthlyReportEngine().parseAndCalculate(offer, demand,
                YearMonth.of(2026, 9), LocalDate.of(2026, 9, 14), MonthlyReportLimitsDTO.defaults());
        assertThat(wrongMonth.ready()).isFalse();
        assertThat(wrongMonth.offerSummary().totalCount()).isZero();
        assertThat(wrongMonth.diagnostics()).extracting(item -> item.code()).contains("NO_OFFERS_IN_REPORT_MONTH");
        Path output = Path.of("target", "monthly-report-local-sample.docx");
        Files.write(output, new MonthlyReportDocxRenderer().render(snapshot, ""));
    }
}
