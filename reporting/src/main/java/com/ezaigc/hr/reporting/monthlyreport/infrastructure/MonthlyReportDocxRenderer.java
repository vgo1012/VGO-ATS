package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DimensionMetricDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

/** 参考稿固定版式；业务数字只取冻结快照，不把样例月份、人员或结论写入模板。 */
public final class MonthlyReportDocxRenderer {
    private static final int CONTENT_WIDTH = 9071;
    private static final String TEMPLATE = "/monthly-report/reference-template.docx";
    private static final Comparator<DimensionMetricDTO> RANKING = Comparator.comparingInt(DimensionMetricDTO::count)
            .reversed().thenComparing(DimensionMetricDTO::score, Comparator.reverseOrder())
            .thenComparing(metric -> metric.group() == null ? "" : metric.group()).thenComparing(DimensionMetricDTO::key);

    public byte[] render(MonthlyReportSnapshotDTO snapshot, String managementRemarks) {
        return render(snapshot, managementRemarks, () -> false);
    }

    public byte[] render(MonthlyReportSnapshotDTO snapshot, String managementRemarks, BooleanSupplier cancelled) {
        if (snapshot == null || cancelled == null) throw new IllegalArgumentException("月报快照和取消回调不能为空");
        if (!snapshot.ready()) throw new IllegalStateException("存在校验错误的快照不能生成正式报告");
        if (snapshot.offerSummary().totalCount() == 0) throw new IllegalStateException("没有有效Offer记录的快照不能生成正式报告");
        String remarks = managementRemarks == null ? "" : managementRemarks.strip();
        if (remarks.length() > 2000) throw new IllegalArgumentException("管理备注超过2000字符");
        checkCancelled(cancelled);
        List<byte[]> charts = new MonthlyReportDashboardRenderer().render(snapshot, cancelled);
        try (InputStream template = MonthlyReportDocxRenderer.class.getResourceAsStream(TEMPLATE)) {
            if (template == null) throw new IllegalStateException("月报参考模板资源缺失");
            try (XWPFDocument doc = new XWPFDocument(template); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                int month = YearMonth.parse(snapshot.reportMonth()).getMonthValue();
                text(doc, month + "月offer数据分析-国内全部", "rdbvau", 24, true);
                text(doc, "一、" + month + "月整体概览：", "ablt93", 11, true);
                bullet(doc, "整体offer：共计" + snapshot.offerSummary().totalCount() + "个offer，其中"
                        + metrics(snapshot, "employment").stream().map(m -> m.key() + m.count() + "人（占比"
                        + percentage(m.count(), snapshot.offerSummary().totalCount()) + "）").collect(Collectors.joining("，")) + "。");
                bullet(doc, "小组offer：" + metrics(snapshot, "group").stream().limit(3)
                        .map(m -> m.key() + m.count() + "人").collect(Collectors.joining("，")) + "。");
                bullet(doc, "状态截至" + snapshot.dataAsOf() + "：已入职" + status(snapshot.offerSummary().statusCounts(), "已入职")
                        + "人，已接受" + status(snapshot.offerSummary().statusCounts(), "已接受") + "人，已拒绝"
                        + status(snapshot.offerSummary().statusCounts(), "已拒绝") + "人，已发未确认"
                        + status(snapshot.offerSummary().statusCounts(), "已发") + "人。");
                addChart(doc, charts.getFirst(), "整体十图看板");
                rules(doc, snapshot, month);
                heading(doc, "二、部门维度与发送人维度分析", 1).setPageBreak(true);
                heading(doc, "（一）按一级部门分析", 2);
                departmentAnalysis(doc, snapshot);
                departmentTable(doc, snapshot, cancelled);
                heading(doc, "（二）按Offer发送人维度分析", 2);
                focusSenders(doc, snapshot, cancelled);
                heading(doc, "三、数据分析总结及后续优化方向", 1);
                conclusions(doc, snapshot);
                if (!remarks.isEmpty()) {
                    heading(doc, "管理者补充说明", 2);
                    body(doc, remarks);
                }
                heading(doc, "附录：需求概览及完整发送人数据", 1).setPageBreak(true);
                appendix(doc, snapshot, cancelled);
                for (int i = 1; i < charts.size(); i++) {
                    checkCancelled(cancelled);
                    heading(doc, "附录：整体看板续图 " + i, 1).setPageBreak(true);
                    addChart(doc, charts.get(i), "看板续图" + i);
                }
                checkCancelled(cancelled);
                doc.write(output);
                return output.toByteArray();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("生成月报DOCX失败", exception);
        }
    }

    private static void rules(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot, int month) {
        text(doc, "附：数据规则及计算逻辑", "ablt93", 11, true).setKeepNext(true);
        XWPFTable table = table(doc, List.of("调整项", "规则"), 1700, 7371);
        row(table, "数据范围", month + "月系统发放offer数据；状态截至" + snapshot.dataAsOf());
        row(table, "Offer状态权重", "拒绝=0，已入职/已接受/已发=1（视同计分有效，不等同于已入职）");
        row(table, "职级分值", "公开示例G1/G2/G3均为1分，不按职级差别计分");
        row(table, "渠道调整", "所有渠道系数均为1，不按来源差别计分");
    }

    private static void departmentAnalysis(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot) {
        List<DimensionMetricDTO> departments = metrics(snapshot, "department");
        for (int i = 0; i < Math.min(3, departments.size()); i++) {
            DimensionMetricDTO m = departments.get(i);
            body(doc, (i + 1) + ". " + m.key() + "发放" + m.count() + "个offer（占"
                    + percentage(m.count(), snapshot.offerSummary().totalCount()) + "），分值" + number(m.score())
                    + "，平均每条分值" + number(m.averageScore()) + "。已入职" + status(m.statusCounts(), "已入职")
                    + "人，已接受" + status(m.statusCounts(), "已接受") + "人，已拒绝" + status(m.statusCounts(), "已拒绝")
                    + "人，拒绝占比" + percentage(status(m.statusCounts(), "已拒绝"), m.count()) + "。");
        }

    }

    private static void departmentTable(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot, BooleanSupplier cancelled) {
        XWPFTable table = table(doc, List.of("一级部门", "Offer数量", "Offer分值", "已入职", "已接受", "已拒绝", "平均分值"),
                2231, 1140, 1140, 1100, 1100, 1100, 1260);
        for (DimensionMetricDTO m : metrics(snapshot, "department")) {
            checkCancelled(cancelled);
            row(table, m.key(), m.count(), number(m.score()), status(m.statusCounts(), "已入职"),
                    status(m.statusCounts(), "已接受"), status(m.statusCounts(), "已拒绝"), number(m.averageScore()));
        }
    }

    private static void focusSenders(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot, BooleanSupplier cancelled) {
        List<DimensionMetricDTO> focus = metrics(snapshot, "sender");
        if (focus.isEmpty()) {
            body(doc, "报告月没有发送人记录。");
            return;
        }
        int total = focus.stream().mapToInt(DimensionMetricDTO::count).sum();
        List<DimensionMetricDTO> top = focus.stream().limit(3).toList();
        int topCount = top.stream().mapToInt(DimensionMetricDTO::count).sum();
        bullet(doc, "全部小组数量前" + top.size() + "位发送人（" + top.stream().map(DimensionMetricDTO::key)
                .collect(Collectors.joining("、")) + "）合计发放" + topCount + "个offer，占发送总量的"
                + percentage(topCount, total) + "。以下展示重点发送人，全部小组及发送人见附录。");
        DimensionMetricDTO first = top.getFirst();
        bullet(doc, first.key() + "发放" + first.count() + "个offer，其中已接受及已入职占比"
                + percentage(status(first.statusCounts(), "已接受") + status(first.statusCounts(), "已入职"), first.count())
                + "，已拒绝" + status(first.statusCounts(), "已拒绝") + "个。该比例按当前状态快照计算。");
        XWPFTable table = table(doc, List.of("发送人", "小组", "数量", "分值", "特点分析"), 1300, 1450, 850, 1000, 4471);
        for (DimensionMetricDTO m : top) {
            checkCancelled(cancelled);
            row(table, m.key(), m.group(), m.count(), number(m.score()), "已入职" + status(m.statusCounts(), "已入职")
                    + "，已接受" + status(m.statusCounts(), "已接受") + "，已拒绝" + status(m.statusCounts(), "已拒绝")
                    + "，已发未确认" + status(m.statusCounts(), "已发") + "；平均分值" + number(m.averageScore()));
        }
    }

    private static void conclusions(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot) {
        heading(doc, "（一）核心结论", 2);
        text(doc, "1. 招聘数量与折算分值结构", "ablt93", 11, true);
        for (DimensionMetricDTO m : metrics(snapshot, "group").stream().limit(3).toList()) {
            bullet(doc, m.key() + "发放" + m.count() + "个offer（数量占比" + percentage(m.count(), snapshot.offerSummary().totalCount())
                    + "），贡献" + number(m.score()) + "分（分值占比" + percentage(m.score(), snapshot.offerSummary().totalScore())
                    + "），平均每条分值" + number(m.averageScore()) + "。");
        }
        text(doc, "2. 渠道贡献与折算规则", "ablt93", 11, true);
        for (DimensionMetricDTO m : metrics(snapshot, "channel").stream().limit(3).toList()) {
            bullet(doc, m.key() + "渠道" + m.count() + "个offer，贡献" + number(m.score()) + "分，占总分值"
                    + percentage(m.score(), snapshot.offerSummary().totalScore()) + "。");
        }
        body(doc, "公开示例不区分渠道权重。数量、分值和后续留存应分别观察，不能把分值直接解释为候选人质量。");
        text(doc, "3. Offer拒绝与入职跟进", "ablt93", 11, true);
        int rejected = status(snapshot.offerSummary().statusCounts(), "已拒绝");
        List<DimensionMetricDTO> byRejection = metrics(snapshot, "department").stream()
                .filter(m -> status(m.statusCounts(), "已拒绝") > 0)
                .sorted(Comparator.<DimensionMetricDTO>comparingInt(m -> status(m.statusCounts(), "已拒绝")).reversed()
                        .thenComparing(DimensionMetricDTO::key)).limit(2).toList();
        body(doc, "整体已拒绝" + rejected + "个offer，拒绝占比" + percentage(rejected, snapshot.offerSummary().totalCount()) + "。"
                + (byRejection.isEmpty() ? "当前快照没有已拒绝记录。" : "拒绝数量最多的部门为" + byRejection.stream()
                .map(m -> m.key() + "（" + status(m.statusCounts(), "已拒绝") + "个）").collect(Collectors.joining("、"))
                + "，合计占拒绝总量" + percentage(byRejection.stream().mapToInt(m -> status(m.statusCounts(), "已拒绝")).sum(), rejected) + "。"));
        heading(doc, "（二）后续关注与优化调整建议", 2);
        heading(doc, "1. 渠道结构优化", 3);
        bullet(doc, "对主要渠道按岗位与职级复盘发放、接受及入职表现，沉淀可复用的职位说明和沟通方法。跨渠道比较时同时呈现数量与折算规则。");
        bullet(doc, "对各渠道补充入职后留存与业务反馈，再评估渠道投入；本次导出数据不支持留存率或长期价值结论。");
        heading(doc, "2. 部门与小组跟进", 3);
        bullet(doc, "优先复盘拒绝记录集中的部门，补充薪酬、地点和岗位预期等原因，再制定改进动作；当前快照不包含原因证据。");
        bullet(doc, "本月已接受未入职" + status(snapshot.offerSummary().statusCounts(), "已接受") + "人，已发未确认"
                + status(snapshot.offerSummary().statusCounts(), "已发") + "人，建议逐项确认预计到岗日和跟进负责人，后续按同一口径观察状态变化。");
    }

    private static void appendix(XWPFDocument doc, MonthlyReportSnapshotDTO snapshot, BooleanSupplier cancelled) {
        heading(doc, "（一）需求概览", 2);
        XWPFTable demand = table(doc, List.of("口径", "需求数", "招聘人数"), 4671, 2200, 2200);
        row(demand, "上传文件内审批通过", snapshot.demandSummary().approvedDemandCount(), snapshot.demandSummary().approvedHeadcount());
        row(demand, "报告月申请", snapshot.demandSummary().monthAppliedDemandCount(), snapshot.demandSummary().monthAppliedHeadcount());
        row(demand, "报告月期望到岗", snapshot.demandSummary().monthExpectedDemandCount(), snapshot.demandSummary().monthExpectedHeadcount());
        body(doc, "需求数据独立展示。两份输入没有稳定的共同需求编号，不计算需求完成率、缺口或招聘周期；以上三种口径不能相加。");
        heading(doc, "（二）完整发送人明细", 2);
        body(doc, "按导出名称与小组汇总，同名不同小组分别展示；未将名称绑定系统账号。");
        XWPFTable senders = table(doc, List.of("发送人", "小组", "数量", "分值", "平均分值"), 2000, 3171, 1100, 1400, 1400);
        senders.setCellMargins(40, 100, 40, 100);
        for (DimensionMetricDTO m : metrics(snapshot, "sender")) {
            checkCancelled(cancelled);
            row(senders, m.key(), m.group(), m.count(), number(m.score()), number(m.averageScore()));
        }
        text(doc, "报告月：" + snapshot.reportMonth() + "；状态截至：" + snapshot.dataAsOf() + "；规则："
                + snapshot.ruleVersion() + "；模板：" + MonthlyReportEngine.TEMPLATE_VERSION + "。", "ablt93", 9, false);
    }

    private static List<DimensionMetricDTO> metrics(MonthlyReportSnapshotDTO snapshot, String dimension) {
        return snapshot.dimensions().stream().filter(m -> dimension.equals(m.dimension())).sorted(RANKING).toList();
    }

    private static int status(Map<String, Integer> counts, String status) {
        return counts.getOrDefault(status + "offer", 0);
    }

    private static String number(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String percentage(int count, int total) { return percentage(BigDecimal.valueOf(count), BigDecimal.valueOf(total)); }
    private static String percentage(BigDecimal count, BigDecimal total) {
        return total.signum() == 0 ? "—" : count.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static void addChart(XWPFDocument doc, byte[] image, String description) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(0);
        p.setSpacingAfter(0);
        try {
            p.createRun().addPicture(new ByteArrayInputStream(image), PictureType.PNG, description,
                    Units.toEMU(CONTENT_WIDTH / 20.0), Units.toEMU(CONTENT_WIDTH / 20.0 * 2500 / 3000));
        } catch (Exception exception) {
            throw new IllegalStateException("月报看板写入失败", exception);
        }
    }

    private static XWPFParagraph heading(XWPFDocument doc, String value, int level) {
        XWPFParagraph p = text(doc, value, level == 1 ? "7yyd7k" : level == 2 ? "0qbh3o" : "ympv2x", 18 - level * 2, true);
        p.setKeepNext(true);
        return p;
    }
    private static void body(XWPFDocument doc, String value) { text(doc, value, "ablt93", 11, false); }
    private static void bullet(XWPFDocument doc, String value) {
        XWPFParagraph p = text(doc, "•  " + value, "ablt93", 11, false);
        p.setIndentationLeft(240);
        p.setIndentationHanging(240);
    }
    private static XWPFParagraph text(XWPFDocument doc, String value, String style, int size, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle(style);
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setText(value);
        font(run, size, bold);
        return p;
    }
    private static void font(XWPFRun run, int size, boolean bold) {
        run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.ascii);
        run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.hAnsi);
        run.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(size);
        run.setColor("333333");
        run.setBold(bold);
    }
    private static XWPFTable table(XWPFDocument doc, List<String> headers, int... widths) {
        XWPFTable table = doc.createTable(1, headers.size());
        table.setWidth(CONTENT_WIDTH);
        table.getCTTbl().getTblPr().addNewTblLayout().setType(STTblLayoutType.FIXED);
        table.setCellMargins(90, 100, 90, 100);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "DDDDDD");
        var grid = table.getCTTbl().getTblGrid();
        if (grid == null) grid = table.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() > 0) grid.removeGridCol(0);
        for (int width : widths) grid.addNewGridCol().setW(BigInteger.valueOf(width));
        fill(table.getRow(0), headers.toArray());
        table.getRow(0).setRepeatHeader(true);
        for (int i = 0; i < widths.length; i++) {
            XWPFTableCell cell = table.getRow(0).getCell(i);
            cell.setWidth(Integer.toString(widths[i]));
            cell.setColor("F5F5F5");
            cell.getParagraphs().forEach(p -> p.getRuns().forEach(r -> r.setBold(true)));
        }
        return table;
    }
    private static void row(XWPFTable table, Object... values) {
        XWPFTableRow row = table.createRow();
        fill(row, values);
        for (int i = 0; i < row.getTableCells().size(); i++) row.getCell(i).setWidth(Integer.toString(table.getRow(0).getCell(i).getWidth()));
    }
    private static void fill(XWPFTableRow row, Object... values) {
        row.setCantSplitRow(true);
        for (int i = 0; i < values.length; i++) {
            XWPFParagraph p = row.getCell(i).getParagraphs().getFirst();
            p.setSpacingBefore(0);
            p.setSpacingAfter(0);
            p.setSpacingBetween(1.3);
            XWPFRun run = p.createRun();
            run.setText(values[i] == null ? "—" : values[i].toString());
            font(run, 9, false);
        }
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new MonthlyReportInterruptedException();
    }
}
