package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportLimitsDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportDiagnosticSeverity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class MonthlyReportEngineTest {

    private static final List<String> OFFER_HEADERS = List.of(
            "一级部门", "雇佣关系", "职级", "offer状态", "审批状态", "Offer发送人", "Offer小组", "Offer发送日期", "人员来源", "备注");
    private static final List<String> DEMAND_HEADERS = List.of("申请部门", "申请日期", "职位名称", "招聘人数", "到岗时间", "审批状态");

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesStrictHeadersFiltersMonthAndProducesDeterministicSnapshot() throws IOException {
        Path offer = offerWorkbook(false, false);
        Path demand = demandWorkbook();

        MonthlyReportSnapshotDTO snapshot = new MonthlyReportEngine().parseAndCalculate(
                offer, demand, YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), MonthlyReportLimitsDTO.defaults());

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.offerSummary().totalCount()).isEqualTo(4);
        assertThat(snapshot.offerSummary().totalScore()).isEqualByComparingTo("3.00");
        assertThat(snapshot.offerSummary().statusCounts()).containsEntry("已发offer", 1)
                .containsEntry("已接受offer", 1).containsEntry("已拒绝offer", 1).containsEntry("已入职offer", 1);
        assertThat(snapshot.offerSummary().excludedCounts()).containsEntry("OUTSIDE_REPORT_MONTH", 1);
        assertThat(snapshot.demandSummary().approvedDemandCount()).isEqualTo(2);
        assertThat(snapshot.demandSummary().approvedHeadcount()).isEqualTo(5);
        assertThat(snapshot.demandSummary().monthAppliedDemandCount()).isEqualTo(1);
        assertThat(snapshot.demandSummary().monthExpectedHeadcount()).isEqualTo(5);
        assertThat(snapshot.dimensions()).allSatisfy(metric -> assertThat(metric.count()).isPositive());
        byte[] document = new MonthlyReportDocxRenderer().render(snapshot, "仅为管理者补充的事实背景");
        assertThat(document).hasSizeGreaterThan(10_000);
        assertThat(document[0]).isEqualTo((byte) 0x50);
        assertThat(document[1]).isEqualTo((byte) 0x4b);
        try (var word = new XWPFDocument(new ByteArrayInputStream(document))) {
            assertThat(word.getAllPictures()).hasSize(1);
            assertThat(word.getDocument().getBody().getSectPr().getPgMar().getLeft().toString()).isEqualTo("1417");
            assertThat(word.getDocument().getBody().getSectPr().getPgMar().getTop().toString()).isEqualTo("1361");
            assertThat(word.getParagraphs().getFirst().getText()).isEqualTo("8月offer数据分析-国内全部");
            assertThat(word.getParagraphs().getFirst().getStyle()).isEqualTo("rdbvau");
            assertThat(word.getParagraphs()).extracting(p -> p.getText()).containsSubsequence(
                    "二、部门维度与发送人维度分析", "（一）按一级部门分析",
                    "（二）按Offer发送人维度分析", "三、数据分析总结及后续优化方向",
                    "管理者补充说明", "仅为管理者补充的事实背景", "附录：需求概览及完整发送人数据");
            assertThat(word.getTables()).hasSize(5);
            assertThat(word.getTables().get(1).getRow(0).getTableCells()).extracting(c -> c.getText())
                    .containsExactly("一级部门", "Offer数量", "Offer分值", "已入职", "已接受", "已拒绝", "平均分值");
            assertThat(word.getTables().get(4).getRows()).extracting(r -> r.getCell(0).getText())
                    .contains("发送人甲", "发送人乙", "发送人丙", "发送人丁");
            word.getTables().forEach(table -> {
                assertThat(table.getRow(0).isRepeatHeader()).isTrue();
                assertThat(table.getRows()).allSatisfy(row -> assertThat(row.isCantSplitRow()).isTrue());
            });
            long contentWidth = 9071L * 635;
            word.getParagraphs().stream().flatMap(p -> p.getRuns().stream())
                    .flatMap(r -> r.getCTR().getDrawingList().stream())
                    .flatMap(d -> d.getInlineList().stream())
                    .forEach(inline -> assertThat(inline.getExtent().getCx()).isLessThanOrEqualTo(contentWidth));
        }
    }

    @Test
    void rejectsMonthWithoutOffersAndExplainsSourceMonth() throws IOException {
        MonthlyReportSnapshotDTO snapshot = new MonthlyReportEngine().parseAndCalculate(
                offerWorkbook(OFFER_HEADERS, List.<Object[]>of(offerRow("部门甲", "G1", "已发offer", "发送人甲"))),
                demandWorkbook(), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 14), MonthlyReportLimitsDTO.defaults());

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).filteredOn(item -> item.code().equals("NO_OFFERS_IN_REPORT_MONTH"))
                .singleElement().satisfies(item -> {
                    assertThat(item.severity()).isEqualTo(MonthlyReportDiagnosticSeverity.ERROR);
                    assertThat(item.message()).contains("2026-09", "2026-08", "重新选择");
                });
        assertThatThrownBy(() -> new MonthlyReportDocxRenderer().render(snapshot, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsHeaderOnlyOfferFile() throws IOException {
        MonthlyReportSnapshotDTO snapshot = parse(offerWorkbook(OFFER_HEADERS, List.of()),
                MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).filteredOn(item -> item.code().equals("EMPTY_OFFER_SHEET"))
                .singleElement().satisfies(item -> assertThat(item.severity()).isEqualTo(MonthlyReportDiagnosticSeverity.ERROR));
        MonthlyReportSnapshotDTO legacyReadySnapshot = new MonthlyReportSnapshotDTO(
                snapshot.ruleVersion(), snapshot.parserVersion(), snapshot.templateVersion(), snapshot.reportMonth(),
                snapshot.dataAsOf(), snapshot.scope(), true, snapshot.offerSummary(), snapshot.demandSummary(),
                snapshot.dimensions(), snapshot.analysis(), List.of(), snapshot.sources());
        assertThatThrownBy(() -> new MonthlyReportDocxRenderer().render(legacyReadySnapshot, ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("没有有效Offer记录");
    }

    @Test
    void rejectsFormulaInCoreFieldButAllowsFormulaOutsideMapping() throws IOException {
        MonthlyReportEngine engine = new MonthlyReportEngine();
        MonthlyReportSnapshotDTO coreFormula = engine.parseAndCalculate(
                offerWorkbook(true, false), demandWorkbook(), YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), MonthlyReportLimitsDTO.defaults());
        MonthlyReportSnapshotDTO nonCoreFormula = engine.parseAndCalculate(
                offerWorkbook(false, true), demandWorkbook(), YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), MonthlyReportLimitsDTO.defaults());

        assertThat(coreFormula.ready()).isFalse();
        assertThat(coreFormula.diagnostics()).extracting(item -> item.code()).contains("FORMULA_NOT_ALLOWED");
        assertThat(nonCoreFormula.ready()).isTrue();
    }

    @Test
    void snapshotSurvivesJsonRoundTrip() throws IOException {
        MonthlyReportSnapshotDTO snapshot = new MonthlyReportEngine().parseAndCalculate(
                offerWorkbook(false, false), demandWorkbook(), YearMonth.of(2026, 8), LocalDate.of(2026, 9, 1), MonthlyReportLimitsDTO.defaults());
        ObjectMapper json = new ObjectMapper();

        MonthlyReportSnapshotDTO restored = json.readValue(json.writeValueAsString(snapshot), MonthlyReportSnapshotDTO.class);

        assertThat(restored.ready()).isTrue();
        assertThat(restored.offerSummary().totalCount()).isEqualTo(4);
        assertThat(restored.dimensions()).hasSameSizeAs(snapshot.dimensions());
    }

    @Test
    void rejectsDuplicateRowsAndUnknownClosedVocabularyValues() throws IOException {
        Object[] duplicate = offerRow("部门甲", "G1", "已发offer", "发送人甲");
        Path offer = offerWorkbook(OFFER_HEADERS, List.<Object[]>of(
                duplicate, duplicate,
                offerRow("部门乙", "X9", "已发offer", "发送人乙"),
                offerRow("部门丙", "G1", "待沟通", "发送人丙")));

        MonthlyReportSnapshotDTO snapshot = parse(offer, MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).extracting(item -> item.code())
                .contains("DUPLICATE_OFFER_ROW", "UNKNOWN_GRADE", "UNKNOWN_OFFER_STATUS");
    }

    @Test
    void rejectsMissingRequiredHeader() throws IOException {
        List<String> headers = OFFER_HEADERS.stream().filter(header -> !header.equals("职级")).toList();
        Path offer = offerWorkbook(headers, List.of());

        MonthlyReportSnapshotDTO snapshot = parse(offer, MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).extracting(item -> item.code()).contains("OFFER_SHEET_NOT_FOUND");
    }

    @Test
    void rejectsDimensionCardinalityWithoutReturningUnboundedGroups() throws IOException {
        Path offer = offerWorkbook(OFFER_HEADERS, List.<Object[]>of(
                offerRow("部门甲", "G1", "已发offer", "发送人甲"),
                offerRow("部门甲", "G1", "已发offer", "发送人乙"),
                offerRow("部门甲", "G1", "已发offer", "发送人丙")));
        MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
        MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(defaults.maxFileBytes(), defaults.maxUncompressedBytes(),
                defaults.maxRows(), defaults.maxColumns(), defaults.maxCellChars(), 2, defaults.maxZipEntries(),
                defaults.maxCompressionRatio(), defaults.maxManagementRemarksChars());

        MonthlyReportSnapshotDTO snapshot = parse(offer, limits, () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).extracting(item -> item.code()).contains("TOO_MANY_GROUPS");
        assertThat(snapshot.dimensions()).filteredOn(item -> item.dimension().equals("sender")).hasSize(2)
                .extracting(item -> item.key()).doesNotContain("发送人丙");
    }

    @Test
    void boundsDiagnosticsWhilePreservingErrorSeverityAndOmittedCounts() throws IOException {
        List<Object[]> invalidRows = new ArrayList<>();
        for (int index = 0; index < 250; index++) {
            invalidRows.add(new Object[] {"部门" + index, "", "", "", "", "", "", "", "", ""});
        }

        MonthlyReportSnapshotDTO snapshot = parse(offerWorkbook(OFFER_HEADERS, invalidRows), MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).hasSizeLessThanOrEqualTo(200)
                .allSatisfy(item -> assertThat(item.severity()).isEqualTo(MonthlyReportDiagnosticSeverity.ERROR));
        assertThat(snapshot.diagnostics().stream().mapToInt(item -> item.count()).sum()).isGreaterThan(200);
    }

    @Test
    void cooperativeCancellationDoesNotSetThreadInterruptedOrProduceDocument() throws IOException {
        Thread.interrupted();
        Path offer = offerWorkbook(false, false);
        MonthlyReportSnapshotDTO valid = parse(offer, MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThatThrownBy(() -> parse(offer, MonthlyReportLimitsDTO.defaults(), () -> true))
                .isInstanceOf(MonthlyReportInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThatThrownBy(() -> new MonthlyReportDocxRenderer().render(valid, "", () -> true))
                .isInstanceOf(MonthlyReportInterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    private MonthlyReportSnapshotDTO parse(Path offer, MonthlyReportLimitsDTO limits,
                                           BooleanSupplier cancelled) throws IOException {
        return new MonthlyReportEngine().parseAndCalculate(offer, demandWorkbook(), YearMonth.of(2026, 8),
                LocalDate.of(2026, 9, 1), limits, cancelled);
    }

    @Test
    void sparseExcelCoordinatesDoNotConsumeTheDataRowBudget() throws IOException {
        Path offer = Files.createTempFile(temporaryDirectory, "offer-sparse-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), OFFER_HEADERS.toArray());
            writeRow(sheet.createRow(1_048_575), offerRow("部门甲", "G1", "已发offer", "发送人甲"));
            try (OutputStream output = Files.newOutputStream(offer)) { workbook.write(output); }
        }
        MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
        MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(defaults.maxFileBytes(), defaults.maxUncompressedBytes(),
                2, defaults.maxColumns(), defaults.maxCellChars(), defaults.maxGroups(), defaults.maxZipEntries(),
                defaults.maxCompressionRatio(), defaults.maxManagementRemarksChars());

        MonthlyReportSnapshotDTO snapshot = parse(offer, limits, () -> false);

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.offerSummary().totalCount()).isEqualTo(1);
        assertThat(snapshot.sources().getFirst().inputRows()).isEqualTo(1);
    }

    @Test
    void workbookRowBudgetCoversAllSheetsIncludingUnrelatedSheets() throws IOException {
        Path offer = Files.createTempFile(temporaryDirectory, "offer-many-sheets-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), OFFER_HEADERS.toArray());
            writeRow(sheet.createRow(1), offerRow("部门甲", "G1", "已发offer", "发送人甲"));
            for (int index = 0; index < 3; index++) {
                Sheet other = workbook.createSheet("无关工作表" + index);
                writeRow(other.createRow(0), "其他数据");
                writeRow(other.createRow(1), "第一行");
            }
            try (OutputStream output = Files.newOutputStream(offer)) { workbook.write(output); }
        }
        MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
        MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(defaults.maxFileBytes(), defaults.maxUncompressedBytes(),
                3, defaults.maxColumns(), defaults.maxCellChars(), defaults.maxGroups(), defaults.maxZipEntries(),
                defaults.maxCompressionRatio(), defaults.maxManagementRemarksChars());

        MonthlyReportSnapshotDTO snapshot = parse(offer, limits, () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).extracting(item -> item.code()).contains("ROW_LIMIT_EXCEEDED");
    }

    @Test
    void sparseDataDiagnosticsKeepTheOriginalExcelRowNumber() throws IOException {
        Path offer = Files.createTempFile(temporaryDirectory, "offer-sparse-diagnostic-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), OFFER_HEADERS.toArray());
            writeRow(sheet.createRow(5_000), offerRow("部门甲", "X9", "已发offer", "发送人甲"));
            try (OutputStream output = Files.newOutputStream(offer)) { workbook.write(output); }
        }

        MonthlyReportSnapshotDTO snapshot = parse(offer, MonthlyReportLimitsDTO.defaults(), () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).filteredOn(item -> item.code().equals("UNKNOWN_GRADE"))
                .singleElement().satisfies(item -> assertThat(item.row()).isEqualTo(5_001));
    }

    @Test
    void repeatedSharedStringsConsumeTheExpandedTextBudget() throws IOException {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            rows.add(offerRow("部".repeat(1_500), "G1", "已发offer", "发送人" + index));
        }
        MonthlyReportLimitsDTO defaults = MonthlyReportLimitsDTO.defaults();
        MonthlyReportLimitsDTO limits = new MonthlyReportLimitsDTO(defaults.maxFileBytes(), 64_000,
                100, defaults.maxColumns(), defaults.maxCellChars(), defaults.maxGroups(), defaults.maxZipEntries(),
                defaults.maxCompressionRatio(), defaults.maxManagementRemarksChars());

        MonthlyReportSnapshotDTO snapshot = parse(offerWorkbook(OFFER_HEADERS, rows), limits, () -> false);

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.diagnostics()).extracting(item -> item.code()).contains("WORKBOOK_TEXT_LIMIT_EXCEEDED");
    }

    private static Object[] offerRow(String department, String grade, String status, String sender) {
        return new Object[] {department, "内部员工", grade, status, "审批通过", sender, "示例小组甲",
                "2026-08-01", "内部推荐", ""};
    }

    private Path offerWorkbook(boolean formulaInCore, boolean formulaOutsideCore) throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "offer-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), OFFER_HEADERS.toArray());
            writeRow(sheet.createRow(1), "部门甲", "内部员工", "G1", "已发offer", "审批通过", "发送人甲", "示例小组甲", "2026-08-01", "内部推荐", "");
            writeRow(sheet.createRow(2), "部门甲", "内部员工", "G2", "已接受offer", "审批通过", "发送人乙", "示例小组甲", "2026-08-02", "BOSS直聘", "");
            writeRow(sheet.createRow(3), "部门乙", "实习生", "G3", "已拒绝offer", "审批通过", "发送人丙", "校招", "2026-08-03", "校园招聘", "");
            writeRow(sheet.createRow(4), "部门乙", "实习生", "G1", "已入职offer", "审批通过", "发送人丁", "示例小组乙", "2026-08-04", "示例渠道", "");
            writeRow(sheet.createRow(5), "部门乙", "实习生", "G1", "已入职offer", "审批通过", "发送人丁", "示例小组乙", "2026-09-01", "校园招聘", "");
            if (formulaInCore) sheet.getRow(1).getCell(2).setCellFormula("\"G1\"");
            if (formulaOutsideCore) sheet.getRow(1).getCell(9).setCellFormula("1+1");
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private Path offerWorkbook(List<String> headers, List<Object[]> rows) throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "offer-boundary-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Offer");
            writeRow(sheet.createRow(0), headers.toArray());
            for (int index = 0; index < rows.size(); index++) writeRow(sheet.createRow(index + 1), rows.get(index));
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private Path demandWorkbook() throws IOException {
        Path path = Files.createTempFile(temporaryDirectory, "demand-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("招聘需求");
            writeRow(sheet.createRow(0), "department_code", "applied_at", "position", "headcount", "expected_at", "approval");
            writeRow(sheet.createRow(1), DEMAND_HEADERS.toArray());
            writeRow(sheet.createRow(2), "部门甲", "2026-08-01", "开发工程师", 2, "2026-08-20", "已通过");
            writeRow(sheet.createRow(3), "部门乙", "2026-07-20", "测试工程师", 3, "2026-08-25", "已通过");
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
