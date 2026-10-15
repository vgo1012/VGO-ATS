package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportCalculator;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportCalculator.DemandRow;
import com.ezaigc.hr.reporting.monthlyreport.application.support.MonthlyReportCalculator.OfferRow;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportDiagnosticDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportLimitsDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSourceDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.OfferSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportDiagnosticSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Collectors;
import javax.xml.parsers.SAXParserFactory;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import lombok.Value;
import lombok.experimental.Accessors;

public final class MonthlyReportEngine {

    public static final String PARSER_VERSION = "monthly-report-public-xlsx-v3";
    public static final String TEMPLATE_VERSION = "monthly-report-public-docx-v3";
    private static final String OFFER = "offer";
    private static final String DEMAND = "demand";
    private static final Set<String> OFFER_REQUIRED = Set.of(
            "一级部门", "雇佣关系", "职级", "offer状态", "审批状态", "Offer发送人", "Offer小组", "Offer发送日期", "人员来源");
    private static final Set<String> DEMAND_REQUIRED = Set.of("申请部门", "申请日期", "职位名称", "招聘人数", "到岗时间", "审批状态");
    private static final Set<String> OFFER_STATUSES = Set.of("已拒绝offer", "已发offer", "已接受offer", "已入职offer");
    private static final Set<String> KNOWN_EXCLUDED_APPROVALS = Set.of("审批中", "审批拒绝", "已拒绝", "已撤回", "撤回", "驳回", "未通过");
    private static final Pattern CELL_COLUMN = Pattern.compile("([A-Z]+)");

    public MonthlyReportSnapshotDTO parseAndCalculate(
            Path offerFile,
            Path demandFile,
            YearMonth reportMonth,
            LocalDate dataAsOf,
            MonthlyReportLimitsDTO limits) {
        return parseAndCalculate(offerFile, demandFile, reportMonth, dataAsOf, limits, () -> false);
    }

    public MonthlyReportSnapshotDTO parseAndCalculate(
            Path offerFile,
            Path demandFile,
            YearMonth reportMonth,
            LocalDate dataAsOf,
            MonthlyReportLimitsDTO limits,
            BooleanSupplier cancelled) {
        if (offerFile == null || demandFile == null || reportMonth == null || dataAsOf == null || limits == null || cancelled == null) {
            throw new IllegalArgumentException("月报输入参数不能为空");
        }
        List<MonthlyReportDiagnosticDTO> diagnostics = new BoundedDiagnostics();
        ParsedOffers offers = parseOffers(offerFile, reportMonth, limits, cancelled, diagnostics);
        checkCancelled(cancelled);
        ParsedDemands demands = parseDemands(demandFile, reportMonth, limits, cancelled, diagnostics);
        checkCancelled(cancelled);

        MonthlyReportCalculator.Result result = new MonthlyReportCalculator().calculate(offers.rows(), demands.rows(), reportMonth);
        result.dimensions().stream().collect(Collectors.groupingBy(metric -> metric.dimension(), Collectors.counting()))
                .forEach((dimension, count) -> {
                    if (count > limits.maxGroups()) {
                        diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "TOO_MANY_GROUPS", OFFER, offers.sheet(), null, dimension,
                                "维度分组数超过上限" + limits.maxGroups(), Math.toIntExact(count)));
                    }
                });
        OfferSummaryDTO calculated = result.offerSummary();
        OfferSummaryDTO summary = new OfferSummaryDTO(
                calculated.totalCount(), calculated.scoredCount(), calculated.totalScore(), calculated.statusCounts(),
                calculated.rejectedRate(), calculated.nonRejectedRate(), calculated.acceptedOrOnboardedRate(), calculated.onboardedRate(),
                calculated.dateFrom(), calculated.dateTo(), Map.copyOf(offers.excludedCounts()));
        boolean ready = diagnostics.stream().noneMatch(item -> item.severity() == MonthlyReportDiagnosticSeverity.ERROR)
                && offers.sheet() != null && demands.sheet() != null;
        List<MonthlyReportSourceDTO> sources = List.of(
                new MonthlyReportSourceDTO(OFFER, offers.sha256(), offers.sheet(), offers.inputRows(), offers.rows().size(), offers.excluded()),
                new MonthlyReportSourceDTO(DEMAND, demands.sha256(), demands.sheet(), demands.inputRows(), demands.rows().size(), demands.excluded()));
        return new MonthlyReportSnapshotDTO(
                MonthlyReportCalculator.RULE_VERSION, PARSER_VERSION, TEMPLATE_VERSION,
                reportMonth.toString(), dataAsOf.toString(), "国内招聘", ready, summary, result.demandSummary(),
                result.dimensions(), result.analysis(), List.copyOf(diagnostics), sources);
    }

    private ParsedOffers parseOffers(Path file, YearMonth month, MonthlyReportLimitsDTO limits,
                                      BooleanSupplier cancelled, List<MonthlyReportDiagnosticDTO> diagnostics) {
        WorkbookData workbook = readWorkbook(file, OFFER, limits, cancelled, diagnostics);
        List<SheetData> candidates = workbook.sheets().stream().filter(sheet -> sheet.headerRow() == 0 && sheet.headers().keySet().containsAll(OFFER_REQUIRED)).toList();
        if (candidates.size() != 1) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, candidates.isEmpty() ? "OFFER_SHEET_NOT_FOUND" : "MULTIPLE_OFFER_SHEETS",
                    OFFER, null, null, null, candidates.isEmpty() ? "未找到包含全部必需列的Offer工作表" : "存在多个符合条件的Offer工作表，不能自动合并", candidates.size()));
            return new ParsedOffers(workbook.sha256(), null, 0, 0, List.of(), Map.of());
        }
        SheetData sheet = candidates.getFirst();
        reportDuplicateHeaders(sheet, OFFER_REQUIRED, OFFER, diagnostics);
        List<OfferRow> rows = new ArrayList<>();
        GroupLimiter groupLimiter = new GroupLimiter(limits.maxGroups());
        Map<String, Integer> excluded = new LinkedHashMap<>();
        Set<String> fingerprints = new HashSet<>();
        int inputRows = 0;
        LocalDate firstSentDate = null;
        LocalDate lastSentDate = null;
        for (int index = 0; index < sheet.rows().size(); index++) {
            if ((index & 1023) == 0) checkCancelled(cancelled);
            RowData row = sheet.rows().get(index);
            if (row.rowNumber() <= sheet.headerRow()) continue;
            if (row.blank()) continue;
            inputRows++;
            int excelRow = row.rowNumber() + 1;
            if (inputRows > limits.maxRows()) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "ROW_LIMIT_EXCEEDED", OFFER, sheet.name(), excelRow,
                        null, "数据行数超过上限" + limits.maxRows(), inputRows));
                break;
            }
            Map<String, String> values = values(sheet, row);
            if (hasFormulaInRequired(sheet, row, OFFER_REQUIRED)) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "FORMULA_NOT_ALLOWED", OFFER, sheet.name(), excelRow, null, "核心字段不接受公式单元格", 1));
                continue;
            }
            if (!requiredValues(values, OFFER_REQUIRED, OFFER, sheet.name(), excelRow, diagnostics)) continue;
            String approval = normalize(values.get("审批状态"));
            if (!approval.equals("审批通过")) {
                if (!KNOWN_EXCLUDED_APPROVALS.contains(approval)) {
                    diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "UNKNOWN_APPROVAL", OFFER, sheet.name(), excelRow, "审批状态", "未知审批状态", 1));
                } else {
                    excluded.merge("APPROVAL_NOT_INCLUDED", 1, Integer::sum);
                }
                continue;
            }
            LocalDate sentDate = parseDate(values.get("Offer发送日期"));
            if (sentDate == null) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "INVALID_DATE", OFFER, sheet.name(), excelRow, "Offer发送日期", "日期格式无效", 1));
                continue;
            }
            if (firstSentDate == null || sentDate.isBefore(firstSentDate)) firstSentDate = sentDate;
            if (lastSentDate == null || sentDate.isAfter(lastSentDate)) lastSentDate = sentDate;
            if (!YearMonth.from(sentDate).equals(month)) {
                excluded.merge("OUTSIDE_REPORT_MONTH", 1, Integer::sum);
                continue;
            }
            String status = normalizeOfferStatus(values.get("offer状态"));
            if (!OFFER_STATUSES.contains(status)) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "UNKNOWN_OFFER_STATUS", OFFER, sheet.name(), excelRow, "offer状态", "未知Offer状态", 1));
                continue;
            }
            String onboardingDate = normalize(values.get("入职日期"));
            if ((status.equals("已入职offer") && onboardingDate.isEmpty())
                    || (!status.equals("已入职offer") && !onboardingDate.isEmpty())) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.WARNING, "ONBOARDING_DATE_INCONSISTENT", OFFER,
                        sheet.name(), excelRow, "入职日期", "Offer状态与入职日期不一致，统计仍以Offer状态为准", 1));
            }
            String grade = normalize(values.get("职级")).toUpperCase(Locale.ROOT);
            try {
                MonthlyReportCalculator.gradeScore(grade);
            } catch (IllegalArgumentException exception) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "UNKNOWN_GRADE", OFFER, sheet.name(), excelRow, "职级", "未知职级", 1));
                continue;
            }
            String fingerprint = row.fingerprint();
            if (!fingerprints.add(fingerprint)) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "DUPLICATE_OFFER_ROW", OFFER, sheet.name(), excelRow, null, "发现完全重复的Offer记录", 1));
                continue;
            }
            OfferRow offer = new OfferRow(normalize(values.get("一级部门")), normalize(values.get("雇佣关系")), grade, status,
                    normalize(values.get("Offer发送人")), normalize(values.get("Offer小组")), sentDate, normalize(values.get("人员来源")));
            String exceededDimension = groupLimiter.exceededDimension(offer);
            if (exceededDimension != null) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "TOO_MANY_GROUPS", OFFER, sheet.name(), excelRow,
                        exceededDimension, "维度分组数超过上限" + limits.maxGroups(), limits.maxGroups() + 1));
                break;
            }
            rows.add(offer);
        }
        if (inputRows == 0) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "EMPTY_OFFER_SHEET", OFFER, sheet.name(), null, null, "Offer工作表没有数据记录，请上传包含Offer明细的文件", 0));
        } else if (rows.isEmpty() && excluded.getOrDefault("OUTSIDE_REPORT_MONTH", 0) == 0) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "NO_VALID_OFFER_ROWS", OFFER, sheet.name(), null, null, "没有可用于报告月份的有效Offer记录", 0));
        } else if (rows.isEmpty()) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "NO_OFFERS_IN_REPORT_MONTH", OFFER, sheet.name(), null, "Offer发送日期",
                    "所选报告月份" + month + "没有有效Offer记录；文件中审批通过记录的发送日期范围为"
                            + firstSentDate + "至" + lastSentDate + "，请重新选择报告月份或上传对应月份的文件", 0));
        }
        return new ParsedOffers(workbook.sha256(), sheet.name(), inputRows, excluded.values().stream().mapToInt(Integer::intValue).sum(), List.copyOf(rows), Map.copyOf(excluded));
    }

    private ParsedDemands parseDemands(Path file, YearMonth month, MonthlyReportLimitsDTO limits,
                                        BooleanSupplier cancelled, List<MonthlyReportDiagnosticDTO> diagnostics) {
        WorkbookData workbook = readWorkbook(file, DEMAND, limits, cancelled, diagnostics);
        List<SheetData> candidates = workbook.sheets().stream().filter(sheet -> sheet.headerRow() == 1 && sheet.headers().keySet().containsAll(DEMAND_REQUIRED)).toList();
        if (candidates.size() != 1) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, candidates.isEmpty() ? "DEMAND_SHEET_NOT_FOUND" : "MULTIPLE_DEMAND_SHEETS",
                    DEMAND, null, null, null, candidates.isEmpty() ? "未找到双表头招聘需求工作表" : "存在多个符合条件的招聘需求工作表，不能自动合并", candidates.size()));
            return new ParsedDemands(workbook.sha256(), null, 0, 0, List.of());
        }
        SheetData sheet = candidates.getFirst();
        reportDuplicateHeaders(sheet, DEMAND_REQUIRED, DEMAND, diagnostics);
        List<DemandRow> rows = new ArrayList<>();
        int inputRows = 0;
        int excluded = 0;
        Set<String> fingerprints = new HashSet<>();
        for (int index = 0; index < sheet.rows().size(); index++) {
            if ((index & 1023) == 0) checkCancelled(cancelled);
            RowData row = sheet.rows().get(index);
            if (row.rowNumber() <= sheet.headerRow()) continue;
            if (row.blank()) continue;
            inputRows++;
            int excelRow = row.rowNumber() + 1;
            if (inputRows > limits.maxRows()) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "ROW_LIMIT_EXCEEDED", DEMAND, sheet.name(), excelRow,
                        null, "数据行数超过上限" + limits.maxRows(), inputRows));
                break;
            }
            Map<String, String> values = values(sheet, row);
            if (hasFormulaInRequired(sheet, row, DEMAND_REQUIRED)) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "FORMULA_NOT_ALLOWED", DEMAND, sheet.name(), excelRow, null, "核心字段不接受公式单元格", 1));
                continue;
            }
            if (!requiredValues(values, DEMAND_REQUIRED, DEMAND, sheet.name(), excelRow, diagnostics)) continue;
            String approval = normalize(values.get("审批状态"));
            if (!Set.of("已通过", "审批通过").contains(approval)) {
                if (!KNOWN_EXCLUDED_APPROVALS.contains(approval)) {
                    diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "UNKNOWN_APPROVAL", DEMAND, sheet.name(), excelRow, "审批状态", "未知审批状态", 1));
                } else {
                    excluded++;
                }
                continue;
            }
            LocalDate applied = parseDate(values.get("申请日期"));
            LocalDate expected = parseDate(values.get("到岗时间"));
            Integer headcount = parsePositiveInteger(values.get("招聘人数"));
            if (applied == null || expected == null || headcount == null) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "INVALID_DEMAND_VALUE", DEMAND, sheet.name(), excelRow, null, "申请日期、到岗时间或招聘人数格式无效", 1));
                continue;
            }
            String fingerprint = row.fingerprint();
            if (!fingerprints.add(fingerprint)) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "DUPLICATE_DEMAND_ROW", DEMAND, sheet.name(), excelRow, null, "发现完全重复的招聘需求记录", 1));
                continue;
            }
            rows.add(new DemandRow(normalize(values.get("申请部门")), applied, normalize(values.get("职位名称")), headcount, expected));
        }
        if (inputRows == 0) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.WARNING, "EMPTY_DEMAND_SHEET", DEMAND, sheet.name(), null, null,
                    "招聘需求工作表没有数据记录", 0));
        }
        return new ParsedDemands(workbook.sha256(), sheet.name(), inputRows, excluded, List.copyOf(rows));
    }

    private WorkbookData readWorkbook(Path file, String source, MonthlyReportLimitsDTO limits,
                                      BooleanSupplier cancelled, List<MonthlyReportDiagnosticDTO> diagnostics) {
        checkCancelled(cancelled);
        String hash = null;
        try {
            if (!Files.isRegularFile(file)) throw new IOException("文件不存在");
            if (Files.size(file) > limits.maxFileBytes()) throw new LimitException("FILE_TOO_LARGE", "文件大小超过限制");
            validateZip(file, limits);
            hash = sha256(file, cancelled);
            List<SheetData> sheets = new ArrayList<>();
            WorkbookBudget budget = new WorkbookBudget(limits);
            try (OPCPackage pack = OPCPackage.open(file.toFile())) {
                XSSFReader reader = new XSSFReader(pack);
                StylesTable styles = reader.getStylesTable();
                ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pack);
                XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
                while (iterator.hasNext()) {
                    checkCancelled(cancelled);
                    try (InputStream stream = iterator.next()) {
                        SheetData sheet = parseSheet(iterator.getSheetName(), stream, styles, strings, limits, cancelled, budget);
                        // 不保留无关工作表；全部表的解析工作仍计入同一预算。
                        if (sheet.headerRow() == (OFFER.equals(source) ? 0 : 1)
                                && sheet.headers().keySet().containsAll(OFFER.equals(source) ? OFFER_REQUIRED : DEMAND_REQUIRED)) {
                            sheets.add(sheet);
                        }
                    }
                }
            }
            return new WorkbookData(hash, List.copyOf(sheets));
        } catch (LimitException exception) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, exception.code, source, null, null, null, exception.getMessage(), 1));
        } catch (RowLimitRuntimeException exception) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "ROW_LIMIT_EXCEEDED", source,
                    null, null, null, "工作簿全部工作表的实际行数超过限制", 1));
        } catch (TextLimitRuntimeException exception) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "WORKBOOK_TEXT_LIMIT_EXCEEDED", source,
                    null, null, null, "工作簿展开后的单元格文本总量超过限制", 1));
        } catch (Exception exception) {
            diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "INVALID_XLSX", source, null, null, null, "文件不是可安全读取的OOXML工作簿", 1));
        }
        return new WorkbookData(hash, List.of());
    }

    private static SheetData parseSheet(String name, InputStream stream, StylesTable styles, ReadOnlySharedStringsTable strings,
                                        MonthlyReportLimitsDTO limits, BooleanSupplier cancelled, WorkbookBudget budget) throws Exception {
        SheetCollector collector = new SheetCollector(name, limits, cancelled, budget);
        XMLReader parser = secureXmlReader();
        XSSFSheetXMLHandler poiHandler = new XSSFSheetXMLHandler(styles, strings, collector, new DataFormatter(Locale.CHINA), false);
        FormulaAwareFilter filter = new FormulaAwareFilter(collector);
        filter.setContentHandler(poiHandler);
        parser.setContentHandler(filter);
        parser.parse(new InputSource(stream));
        return collector.toSheetData();
    }

    private static XMLReader secureXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory.newSAXParser().getXMLReader();
    }

    private static void validateZip(Path file, MonthlyReportLimitsDTO limits) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            long expanded = 0;
            int entries = 0;
            boolean contentTypes = false;
            for (ZipEntry entry : Collections.list(zip.entries())) {
                entries++;
                if (entries > limits.maxZipEntries()) throw new LimitException("TOO_MANY_ZIP_ENTRIES", "压缩包条目数超过限制");
                if (entry.getName().equals("[Content_Types].xml")) contentTypes = true;
                long size = Math.max(entry.getSize(), 0);
                long compressed = Math.max(entry.getCompressedSize(), 1);
                expanded += size;
                if (expanded > limits.maxUncompressedBytes()) throw new LimitException("ZIP_EXPANDED_TOO_LARGE", "压缩展开大小超过限制");
                if (size > 0 && (double) size / compressed > limits.maxCompressionRatio()) {
                    throw new LimitException("SUSPICIOUS_COMPRESSION_RATIO", "压缩率超过安全限制");
                }
                if (entry.getName().contains("..") || entry.getName().startsWith("/") || entry.getName().startsWith("\\")) {
                    throw new LimitException("INVALID_ZIP_ENTRY", "压缩包包含非法路径");
                }
                if (entry.getName().endsWith(".rels")) {
                    try (InputStream input = zip.getInputStream(entry)) {
                        String relationships = new String(input.readNBytes(Math.min(limits.maxCellChars() * 4, 1_000_000)), StandardCharsets.UTF_8);
                        if (relationships.matches("(?s).*TargetMode\\s*=\\s*[\"']External[\"'].*")) {
                            throw new LimitException("EXTERNAL_LINK_NOT_ALLOWED", "工作簿包含外部链接");
                        }
                    }
                }
            }
            if (!contentTypes) throw new LimitException("INVALID_OOXML", "缺少OOXML内容类型声明");
        }
    }

    private static String sha256(Path file, BooleanSupplier cancelled) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream source = Files.newInputStream(file); DigestInputStream input = new DigestInputStream(source, digest)) {
            int read;
            int blocks = 0;
            while ((read = input.read(buffer)) >= 0) {
                if ((++blocks & 127) == 0) checkCancelled(cancelled);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean requiredValues(Map<String, String> values, Set<String> required, String source,
                                          String sheet, int row, List<MonthlyReportDiagnosticDTO> diagnostics) {
        boolean valid = true;
        for (String column : required) {
            if (normalize(values.get(column)).isEmpty()) {
                diagnostics.add(diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "REQUIRED_VALUE_MISSING", source, sheet, row, column, "必需值为空", 1));
                valid = false;
            }
        }
        return valid;
    }

    private static void reportDuplicateHeaders(SheetData sheet, Set<String> required, String source,
                                               List<MonthlyReportDiagnosticDTO> diagnostics) {
        sheet.duplicateHeaders().stream().filter(required::contains).sorted().forEach(header -> diagnostics.add(
                diagnostic(MonthlyReportDiagnosticSeverity.ERROR, "DUPLICATE_REQUIRED_HEADER", source, sheet.name(),
                        sheet.headerRow() + 1, header, "必需列重复出现", 2)));
    }

    private static boolean hasFormulaInRequired(SheetData sheet, RowData row, Set<String> required) {
        return required.stream().map(sheet.headers()::get).anyMatch(index -> index != null && row.formulaColumns().contains(index));
    }

    private static Map<String, String> values(SheetData sheet, RowData row) {
        Map<String, String> values = new HashMap<>();
        sheet.headers().forEach((header, index) -> values.put(header, index < row.values().length ? row.values()[index] : ""));
        return values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String normalizeOfferStatus(String value) {
        return normalize(value).replace("Offer", "offer").replace("OFFER", "offer");
    }

    private static LocalDate parseDate(String text) {
        String value = normalize(text);
        if (value.isEmpty()) return null;
        try {
            BigDecimal serial = new BigDecimal(value);
            if (serial.compareTo(BigDecimal.valueOf(1)) >= 0 && serial.compareTo(BigDecimal.valueOf(2_958_465)) <= 0) {
                return DateUtil.getLocalDateTime(serial.doubleValue()).toLocalDate();
            }
        } catch (NumberFormatException ignored) {
            // Continue with supported literal date formats.
        }
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT),
                DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA),
                DateTimeFormatter.ofPattern("M/d/yy", Locale.ROOT),
                DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ROOT))) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next explicitly supported format.
            }
        }
        return null;
    }

    private static Integer parsePositiveInteger(String text) {
        try {
            BigDecimal number = new BigDecimal(normalize(text));
            return number.scale() <= 0 || number.stripTrailingZeros().scale() <= 0
                    ? number.intValueExact() > 0 ? number.intValueExact() : null
                    : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private static MonthlyReportDiagnosticDTO diagnostic(MonthlyReportDiagnosticSeverity severity, String code, String source, String sheet,
                                                          Integer row, String column, String message, int count) {
        return new MonthlyReportDiagnosticDTO(severity, code, source, sheet, row, column, message, count);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new MonthlyReportInterruptedException();
        }
    }

    @Value
    @Accessors(fluent = true)
    private static class WorkbookData {
        String sha256;
        List<SheetData> sheets;
    }

    @Value
    @Accessors(fluent = true)
    private static class SheetData {
        String name;
        int headerRow;
        Map<String, Integer> headers;
        Set<String> duplicateHeaders;
        List<RowData> rows;
    }

    @Value
    @Accessors(fluent = true)
    private static class RowData {
        int rowNumber;
        String[] values;
        Set<Integer> formulaColumns;
        String fingerprint;

        boolean blank() {
            return Arrays.stream(values).allMatch(value -> normalize(value).isEmpty());
        }
    }

    @Value
    @Accessors(fluent = true)
    private static class ParsedOffers {
        String sha256;
        String sheet;
        int inputRows;
        int excluded;
        List<OfferRow> rows;
        Map<String, Integer> excludedCounts;
    }

    @Value
    @Accessors(fluent = true)
    private static class ParsedDemands {
        String sha256;
        String sheet;
        int inputRows;
        int excluded;
        List<DemandRow> rows;
    }

    private static final class LimitException extends IOException {
        private final String code;

        private LimitException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final class GroupLimiter {
        private final int maximum;
        private final Map<String, Set<String>> keys = new LinkedHashMap<>();

        private GroupLimiter(int maximum) {
            this.maximum = maximum;
        }

        private String exceededDimension(OfferRow offer) {
            Map<String, String> candidate = new LinkedHashMap<>();
            candidate.put("employment", offer.employment());
            candidate.put("group", offer.group());
            candidate.put("department", offer.department());
            candidate.put("channel", offer.channel());
            candidate.put("gradeBand", MonthlyReportCalculator.gradeScore(offer.grade()).stripTrailingZeros().toPlainString());
            candidate.put("sender", offer.group() + "\u001f" + offer.sender());
            for (Map.Entry<String, String> entry : candidate.entrySet()) {
                Set<String> dimensionKeys = keys.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>());
                if (!dimensionKeys.contains(entry.getValue()) && dimensionKeys.size() >= maximum) return entry.getKey();
            }
            candidate.forEach((dimension, key) -> keys.get(dimension).add(key));
            return null;
        }
    }

    private static final class BoundedDiagnostics extends ArrayList<MonthlyReportDiagnosticDTO> {
        private static final int CONCRETE_LIMIT = 150;
        private static final int TOTAL_LIMIT = 200;

        @Override
        public boolean add(MonthlyReportDiagnosticDTO item) {
            if (size() < CONCRETE_LIMIT) return super.add(item);
            for (int index = CONCRETE_LIMIT; index < size(); index++) {
                MonthlyReportDiagnosticDTO existing = get(index);
                if (existing.code().equals(item.code()) && Objects.equals(existing.source(), item.source())) {
                    MonthlyReportDiagnosticSeverity severity = existing.severity() == MonthlyReportDiagnosticSeverity.ERROR
                            || item.severity() == MonthlyReportDiagnosticSeverity.ERROR
                            ? MonthlyReportDiagnosticSeverity.ERROR : existing.severity();
                    set(index, diagnostic(severity, existing.code(), existing.source(), null, null, null,
                            "另有同类诊断已按代码汇总", existing.count() + item.count()));
                    return true;
                }
            }
            if (size() < TOTAL_LIMIT) {
                return super.add(diagnostic(item.severity(), item.code(), item.source(), null, null, null,
                        "另有同类诊断已按代码汇总", item.count()));
            }
            int last = TOTAL_LIMIT - 1;
            MonthlyReportDiagnosticDTO existing = get(last);
            MonthlyReportDiagnosticSeverity severity = existing.severity() == MonthlyReportDiagnosticSeverity.ERROR
                    || item.severity() == MonthlyReportDiagnosticSeverity.ERROR
                    ? MonthlyReportDiagnosticSeverity.ERROR : existing.severity();
            set(last, diagnostic(severity, "DIAGNOSTICS_OMITTED", null, null, null, null,
                    "其余诊断因响应上限省略", existing.count() + item.count()));
            return true;
        }
    }

    /** 稀疏坐标不分配空行；所有工作表及共享字符串引用共用实际解析预算。 */
    private static final class WorkbookBudget {
        private final MonthlyReportLimitsDTO limits;
        private long rows;
        private long textBytes;

        private WorkbookBudget(MonthlyReportLimitsDTO limits) { this.limits = limits; }

        private void row() {
            if (++rows > (long) limits.maxRows() + 2) throw new RowLimitRuntimeException();
        }

        private void text(String value) {
            // Java 字符存储最多两字节；重复引用共享字符串也必须重新计入总量。
            long bytes = value == null ? 0 : 2L * value.length();
            if (bytes > limits.maxUncompressedBytes() - textBytes) throw new TextLimitRuntimeException();
            textBytes += bytes;
        }
    }

    private static final class SheetCollector implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final String name;
        private final MonthlyReportLimitsDTO limits;
        private final BooleanSupplier cancelled;
        private final WorkbookBudget budget;
        private final List<RowData> rows = new ArrayList<>();
        private Map<Integer, String> current;
        private Set<Integer> currentFormulaColumns;
        private Set<Integer> retainedColumns;
        private MessageDigest currentDigest;
        private int currentMaximumColumn;
        private int previousRow = -1;

        private SheetCollector(String name, MonthlyReportLimitsDTO limits, BooleanSupplier cancelled, WorkbookBudget budget) {
            this.name = name;
            this.limits = limits;
            this.cancelled = cancelled;
            this.budget = budget;
        }

        @Override
        public void startRow(int rowNum) {
            if (rowNum < 0 || rowNum > 1_048_575 || rowNum <= previousRow) {
                throw new IllegalArgumentException("工作表行号无效或顺序错误");
            }
            previousRow = rowNum;
            budget.row();
            checkCancelled(cancelled);
            current = new HashMap<>();
            currentFormulaColumns = new HashSet<>();
            currentMaximumColumn = -1;
            try {
                currentDigest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum > 1 && retainedColumns == null) return;
            String[] values = new String[currentMaximumColumn + 1];
            Arrays.fill(values, "");
            current.forEach((column, value) -> values[column] = value);
            rows.add(new RowData(rowNum, values, Set.copyOf(currentFormulaColumns), HexFormat.of().formatHex(currentDigest.digest())));
            if (rowNum <= 1 && retainedColumns == null) {
                Map<String, Integer> candidate = new HashMap<>();
                current.forEach((column, value) -> candidate.putIfAbsent(normalize(value), column));
                Set<String> required = candidate.keySet().containsAll(OFFER_REQUIRED) ? OFFER_REQUIRED
                        : candidate.keySet().containsAll(DEMAND_REQUIRED) ? DEMAND_REQUIRED : Set.of();
                if (!required.isEmpty()) {
                    retainedColumns = new HashSet<>();
                    required.forEach(header -> retainedColumns.add(candidate.get(header)));
                    candidate.forEach((header, column) -> {
                        if (Set.of("入职日期", "备注").contains(header)) retainedColumns.add(column);
                    });
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = columnIndex(cellReference);
            if (column >= limits.maxColumns()) throw new ColumnLimitRuntimeException();
            if (formattedValue != null && formattedValue.length() > limits.maxCellChars()) throw new CellLimitRuntimeException();
            budget.text(formattedValue);
            if (previousRow > 1 && retainedColumns == null) return;
            currentDigest.update(ByteBuffer.allocate(Integer.BYTES).putInt(column).array());
            currentDigest.update((formattedValue == null ? "" : formattedValue).getBytes(StandardCharsets.UTF_8));
            if (retainedColumns != null && !retainedColumns.contains(column)) return;
            current.put(column, formattedValue == null ? "" : formattedValue);
            currentMaximumColumn = Math.max(currentMaximumColumn, column);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
        }

        private void formula(int column) {
            if (column >= 0 && column < limits.maxColumns()) currentFormulaColumns.add(column);
        }

        private SheetData toSheetData() {
            int headerRow = -1;
            Map<String, Integer> headers = Map.of();
            Set<String> duplicateHeaders = Set.of();
            for (int row = 0; row < Math.min(2, rows.size()); row++) {
                if (rows.get(row).rowNumber() > 1) break;
                Map<String, Integer> candidate = new LinkedHashMap<>();
                String[] values = rows.get(row).values();
                for (int column = 0; column < values.length; column++) {
                    String value = normalize(values[column]);
                    if (!value.isEmpty()) candidate.putIfAbsent(value, column);
                }
                if (candidate.keySet().containsAll(OFFER_REQUIRED) || candidate.keySet().containsAll(DEMAND_REQUIRED)) {
                    headerRow = rows.get(row).rowNumber();
                    headers = Map.copyOf(candidate);
                    Set<String> seen = new HashSet<>();
                    Set<String> duplicates = new HashSet<>();
                    for (String value : values) {
                        String normalized = normalize(value);
                        if (!normalized.isEmpty() && !seen.add(normalized)) duplicates.add(normalized);
                    }
                    duplicateHeaders = Set.copyOf(duplicates);
                    break;
                }
            }
            return new SheetData(name, headerRow, headers, duplicateHeaders, List.copyOf(rows));
        }

        private static int columnIndex(String reference) {
            Matcher matcher = CELL_COLUMN.matcher(reference == null ? "A" : reference);
            if (!matcher.find()) return 0;
            int result = 0;
            for (char character : matcher.group(1).toCharArray()) result = result * 26 + character - 'A' + 1;
            return result - 1;
        }
    }

    private static final class FormulaAwareFilter extends XMLFilterImpl {
        private final SheetCollector collector;
        private int currentColumn = -1;

        private FormulaAwareFilter(SheetCollector collector) {
            this.collector = collector;
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes)
                throws SAXException {
            if ("c".equals(localName)) currentColumn = SheetCollector.columnIndex(attributes.getValue("r"));
            if ("f".equals(localName)) collector.formula(currentColumn);
            super.startElement(uri, localName, qualifiedName, attributes);
        }
    }

    private static final class RowLimitRuntimeException extends RuntimeException {
    }

    private static final class TextLimitRuntimeException extends RuntimeException {
    }

    private static final class ColumnLimitRuntimeException extends RuntimeException {
    }

    private static final class CellLimitRuntimeException extends RuntimeException {
    }
}
