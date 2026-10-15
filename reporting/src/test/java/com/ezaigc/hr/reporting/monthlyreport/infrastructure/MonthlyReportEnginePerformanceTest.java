package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportLimitsDTO;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfSystemProperty(named = "monthly.report.performance", matches = "true")
class MonthlyReportEnginePerformanceTest {

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "stream parse {0} offer rows")
    @MethodSource("rowCounts")
    void parsesBoundedSyntheticWorkbook(int rowCount) throws IOException {
        Path offer = offerWorkbook(rowCount);
        Path demand = demandWorkbook();
        MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
        MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(
                defaults.maxFileBytes(), defaults.maxUncompressedBytes(), rowCount, defaults.maxColumns(),
                defaults.maxCellChars(), defaults.maxGroups(), defaults.maxZipEntries(), defaults.maxCompressionRatio(),
                defaults.maxManagementRemarksChars());

        long parseStartedAt = System.nanoTime();
        var snapshot = new MonthlyReportEngine().parseAndCalculate(
                offer, demand, YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), limits);
        long parseMillis = (System.nanoTime() - parseStartedAt) / 1_000_000;
        long renderStartedAt = System.nanoTime();
        byte[] document = new MonthlyReportDocxRenderer().render(snapshot, "");
        long renderMillis = (System.nanoTime() - renderStartedAt) / 1_000_000;

        System.out.printf("monthly-report-benchmark rows=%d xlsxBytes=%d parseMs=%d docxBytes=%d renderMs=%d%n",
                rowCount, Files.size(offer), parseMillis, document.length, renderMillis);

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.offerSummary().totalCount()).isEqualTo(rowCount);
        assertThat(document).hasSizeGreaterThan(10_000);
    }

    private static Stream<Arguments> rowCounts() {
        return Stream.of(Arguments.of(10_000), Arguments.of(100_000));
    }

    private Path offerWorkbook(int rowCount) throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "offer-performance-", ".xlsx");
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), "一级部门", "雇佣关系", "职级", "offer状态", "审批状态", "Offer发送人", "Offer小组", "Offer发送日期", "人员来源", "唯一标识");
            for (int index = 0; index < rowCount; index++) {
                writeRow(sheet.createRow(index + 1), "部门" + index % 20, index % 4 == 0 ? "实习生" : "内部员工",
                        index % 4 == 0 ? "G3" : "G1", index % 11 == 0 ? "已拒绝offer" : "已入职offer", "审批通过",
                        "发送人" + index % 30, "小组" + index % 10, "2026-08-15", index % 7 == 0 ? "内部推荐" : "招聘网站", index);
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
            workbook.dispose();
        }
        return path;
    }

    private Path demandWorkbook() throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "demand-performance-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("需求");
            writeRow(sheet.createRow(0), "department", "application", "position", "headcount", "expected", "approval");
            writeRow(sheet.createRow(1), "申请部门", "申请日期", "职位名称", "招聘人数", "到岗时间", "审批状态");
            writeRow(sheet.createRow(2), "部门1", "2026-08-01", "工程师", 1, "2026-08-20", "已通过");
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private static void writeRow(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] instanceof Number number) row.createCell(index).setCellValue(number.doubleValue());
            else row.createCell(index).setCellValue(values[index].toString());
        }
    }
}
