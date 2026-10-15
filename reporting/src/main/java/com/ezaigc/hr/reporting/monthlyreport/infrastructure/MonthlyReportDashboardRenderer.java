package com.ezaigc.hr.reporting.monthlyreport.infrastructure;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DimensionMetricDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportSnapshotDTO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/** 固定十图看板；仅使用已冻结的汇总事实，不引入人员能力评价。 */
public final class MonthlyReportDashboardRenderer {
    private static final Color[] COLORS = {new Color(0xff6666), new Color(0x4ec9c3),
            new Color(0x45b3ca), new Color(0x96cbb5), new Color(0xffebaa), new Color(0xdaa0dc)};
    private static final Color INK = new Color(0x252525);
    private static final Color GRID = new Color(0xe9ecef);
    private static final int WIDTH = 1500;
    private static final int HEIGHT = 1250;
    private static final int PAGE_SIZE = 20;

    public List<byte[]> render(MonthlyReportSnapshotDTO snapshot, BooleanSupplier cancelled) {
        if (snapshot == null || cancelled == null) throw new IllegalArgumentException("快照和取消回调不能为空");
        checkCancelled(cancelled);
        Map<String, Color> groups = new LinkedHashMap<>();
        dimension(snapshot, "group").forEach(value -> groups.put(value.key(), COLORS[groups.size() % COLORS.length]));
        List<DimensionMetricDTO> senders = dimension(snapshot, "sender");
        BufferedImage image = canvas();
        Graphics2D g = graphics(image);
        try {
            center(g, snapshot.reportMonth().replace("-", "年") + "月招聘团队Offer数据 — 整体看板", 750, 35, 25, true);
            pie(g, new Rectangle(20, 100, 350, 340), "① 雇佣关系分布", dimension(snapshot, "employment"));
            bars(g, new Rectangle(390, 100, 350, 340), "② Offer小组分布", first(dimension(snapshot, "group"), 8), false, false, groups);
            List<DimensionMetricDTO> statuses = snapshot.offerSummary().statusCounts().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(entry -> new DimensionMetricDTO("status", entry.getKey(), null,
                            entry.getValue(), BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO)).toList();
            pie(g, new Rectangle(760, 100, 350, 340), "③ Offer状态分布", statuses);
            bars(g, new Rectangle(1130, 100, 350, 340), "④ 人员来源分布", first(dimension(snapshot, "channel"), 8), false, false, Map.of());
            bars(g, new Rectangle(20, 475, 350, 340), "⑤ 各小组综合分值", first(byScore(dimension(snapshot, "group")), 8), true, true, groups);
            bars(g, new Rectangle(390, 475, 350, 340), "⑥ 各来源综合分值（已调整）", first(byScore(dimension(snapshot, "channel")), 8), true, true, Map.of());
            bars(g, new Rectangle(760, 475, 350, 340), "⑦ 职级分值分布", first(dimension(snapshot, "gradeBand").stream()
                    .sorted(Comparator.comparingDouble(value -> gradeOrder(value.key()))).toList(), 8), true, false, Map.of());
            dual(g, new Rectangle(1130, 475, 350, 340), first(dimension(snapshot, "employment"), 8));
            String suffix = senders.size() > PAGE_SIZE ? "（前20名，余项见续图）" : "（颜色＝小组）";
            bars(g, new Rectangle(20, 865, 720, 355), "⑨ Offer发送人排名" + suffix, first(senders, PAGE_SIZE), false, false, groups);
            scatter(g, new Rectangle(760, 865, 720, 355), "⑩ 发送人数量与平均分" + suffix, first(senders, PAGE_SIZE), groups);
            if (List.of("group", "channel", "gradeBand", "employment").stream().anyMatch(key -> dimension(snapshot, key).size() > 8)) {
                center(g, "超过8类的维度在主图展示前8项，完整数据见续图与正文表格。", 750, 1240, 11, false);
            }
        } finally { g.dispose(); }
        List<byte[]> pages = new ArrayList<>();
        pages.add(encode(image, cancelled));
        for (int start = PAGE_SIZE; start < senders.size(); start += PAGE_SIZE) {
            checkCancelled(cancelled);
            List<DimensionMetricDTO> values = senders.subList(start, Math.min(start + PAGE_SIZE, senders.size()));
            BufferedImage continuation = canvas();
            Graphics2D extra = graphics(continuation);
            try {
                center(extra, "发送人看板续图 · 第" + (start + 1) + "—" + (start + values.size()) + "项", 750, 55, 25, true);
                bars(extra, new Rectangle(60, 140, 1380, 500), "Offer数量排名（颜色＝小组）", values, false, false, groups);
                scatter(extra, new Rectangle(60, 700, 1380, 480), "数量与平均分（气泡面积＝数量）", values, groups);
            } finally { extra.dispose(); }
            pages.add(encode(continuation, cancelled));
        }
        for (String key : List.of("group", "channel", "gradeBand", "employment")) {
            List<DimensionMetricDTO> values = dimension(snapshot, key);
            if (values.size() <= 8) continue;
            for (int start = 0; start < values.size(); start += PAGE_SIZE) {
                checkCancelled(cancelled);
                BufferedImage continuation = canvas();
                Graphics2D extra = graphics(continuation);
                try {
                    List<DimensionMetricDTO> batch = values.subList(start, Math.min(start + PAGE_SIZE, values.size()));
                    String label = Map.of("group", "小组", "channel", "人员来源", "gradeBand", "职级分档", "employment", "雇佣关系").get(key);
                    center(extra, label + "完整看板 · 第" + (start + 1) + "—" + (start + batch.size()) + "项", 750, 55, 25, true);
                    bars(extra, new Rectangle(60, 140, 1380, 480), "数量分布", batch, false, false, groups);
                    bars(extra, new Rectangle(60, 700, 1380, 480), "综合分值", batch, false, true, groups);
                } finally { extra.dispose(); }
                pages.add(encode(continuation, cancelled));
            }
        }
        return List.copyOf(pages);
    }

    private static List<DimensionMetricDTO> dimension(MonthlyReportSnapshotDTO snapshot, String dimension) {
        return snapshot.dimensions().stream().filter(value -> dimension.equals(value.dimension()))
                .sorted(Comparator.comparingInt(DimensionMetricDTO::count).reversed().thenComparing(DimensionMetricDTO::key))
                .toList();
    }

    private static List<DimensionMetricDTO> byScore(List<DimensionMetricDTO> values) {
        return values.stream().sorted(Comparator.comparing(DimensionMetricDTO::score).reversed().thenComparing(DimensionMetricDTO::key)).toList();
    }

    private static List<DimensionMetricDTO> first(List<DimensionMetricDTO> values, int size) {
        return values.subList(0, Math.min(size, values.size()));
    }

    private static BufferedImage canvas() { return new BufferedImage(WIDTH * 2, HEIGHT * 2, BufferedImage.TYPE_INT_RGB); }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.scale(2, 2);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        return g;
    }

    private static void pie(Graphics2D g, Rectangle box, String title, List<DimensionMetricDTO> values) {
        title(g, box, title);
        int total = values.stream().mapToInt(DimensionMetricDTO::count).sum();
        if (total == 0) { empty(g, box); return; }
        // 雇佣关系最多八类在饼图展开，其余合计明确展示；完整数据在续图保留。
        List<DimensionMetricDTO> shown = new ArrayList<>(first(values, 8));
        if (values.size() > 8) {
            int other = values.subList(8, values.size()).stream().mapToInt(DimensionMetricDTO::count).sum();
            shown.add(new DimensionMetricDTO("other", "其他（见续图）", null, other, BigDecimal.ZERO, BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        int size = 230;
        int x = box.x + (box.width - size) / 2;
        int y = box.y + 35;
        double angle = 90;
        for (int i = 0; i < shown.size(); i++) {
            DimensionMetricDTO value = shown.get(i);
            double extent = 360.0 * value.count() / total;
            g.setColor(pieColor(value, i));
            Arc2D arc = new Arc2D.Double(x, y, size, size, angle, -extent, Arc2D.PIE);
            g.fill(arc);
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(3)); g.draw(arc);
            if (extent >= 26) {
                double middle = Math.toRadians(angle - extent / 2);
                int tx = x + size / 2 + (int) (size * .30 * Math.cos(middle));
                int ty = y + size / 2 - (int) (size * .30 * Math.sin(middle));
                center(g, format(value.count() * 100.0 / total) + "%", tx, ty, 12, true);
                center(g, "（" + value.count() + "人）", tx, ty + 15, 11, true);
            }
            angle -= extent;
        }
        for (int i = 0; i < shown.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int lx = box.x + col * 175;
            int ly = box.y + 275 + row * 14;
            g.setColor(pieColor(shown.get(i), i)); g.fillRect(lx, ly - 9, 9, 9);
            fit(g, shown.get(i).key() + " " + shown.get(i).count() + "人 "
                    + format(shown.get(i).count() * 100.0 / total) + "%", lx + 13, ly, 159, 10);
        }
    }

    private static void bars(Graphics2D g, Rectangle box, String title, List<DimensionMetricDTO> values,
                             boolean vertical, boolean score, Map<String, Color> groups) {
        title(g, box, title);
        if (values.isEmpty()) { empty(g, box); return; }
        ToDoubleFunction<DimensionMetricDTO> metric = value -> score ? value.score().doubleValue() : value.count();
        double max = niceMaximum(Math.max(1, values.stream().mapToDouble(metric).max().orElse(1)) * 1.08);
        int left = box.x + (vertical ? 36 : box.width > 400 ? 110 : 86);
        int top = box.y + 36;
        int width = box.x + box.width - left - 25;
        int height = box.height - (vertical ? 105 : 67);
        grid(g, left, top, width, height, max, vertical);
        double band = (vertical ? width : height) / (double) values.size();
        for (int i = 0; i < values.size(); i++) {
            DimensionMetricDTO value = values.get(i);
            double amount = metric.applyAsDouble(value);
            g.setColor(color(value, i, groups));
            if (vertical) {
                int bx = left + (int) (i * band + band * .15);
                int bh = (int) (height * amount / max);
                g.fillRect(bx, top + height - bh, Math.max(2, (int) (band * .7)), bh);
                center(g, format(amount), bx + (int) (band * .35), top + height - bh - 6, 11, true);
                Graphics2D label = (Graphics2D) g.create();
                try {
                    label.translate(bx + band * .30, top + height + 13);
                    label.rotate(-Math.PI / 7);
                    fit(label, value.key(), -35, 0, Math.min(95, (int) band + 36), 10);
                } finally { label.dispose(); }
            } else {
                int by = top + (int) (i * band + band * .12);
                int length = (int) (width * amount / max);
                g.fillRect(left, by, length, Math.max(2, (int) (band * .74)));
                fit(g, value.key() + (value.group() == null ? "" : " · " + value.group()), box.x, by + (int) (band * .55), left - box.x - 7, 11);
                text(g, format(amount), left + length + 4, by + (int) (band * .55), 10, true);
            }
        }
        if (!vertical) center(g, score ? "综合分值" : "Offer数量（人）", left + width / 2, top + height + 30, 11, false);
    }

    private static void dual(Graphics2D g, Rectangle box, List<DimensionMetricDTO> values) {
        title(g, box, "⑧ 雇佣关系分值贡献");
        if (values.isEmpty()) { empty(g, box); return; }
        int x = box.x + 35; int y = box.y + 48; int w = box.width - 75; int h = box.height - 99;
        double maxScore = niceMaximum(Math.max(1, values.stream().mapToDouble(value -> value.score().doubleValue()).max().orElse(1)) * 1.10);
        double maxAverage = niceMaximum(Math.max(.1, values.stream().mapToDouble(value -> average(value)).max().orElse(1)) * 1.10);
        grid(g, x, y, w, h, maxScore, true);
        for (int tick = 0; tick <= 5; tick++) text(g, format(maxAverage * tick / 5), x + w + 3, y + h - h * tick / 5 + 4, 9, false);
        double band = w / (double) values.size();
        for (int i = 0; i < values.size(); i++) {
            DimensionMetricDTO value = values.get(i);
            double[] amounts = {value.score().doubleValue(), average(value)};
            for (int j = 0; j < 2; j++) {
                int bx = x + (int) ((i + .10 + j * .40) * band);
                int bh = (int) (h * amounts[j] / (j == 0 ? maxScore : maxAverage));
                g.setColor(COLORS[j == 0 ? 1 : 0]); g.fillRect(bx, y + h - bh, (int) (band * .36), bh);
                String number = j == 0 ? format(amounts[j]) : value.count() == 0 ? "0.000"
                        : value.score().divide(BigDecimal.valueOf(value.count()), 3, RoundingMode.HALF_UP).toPlainString();
                center(g, number, bx + (int) (band * .18), y + h - bh - 5, 10, true);
            }
            fit(g, value.key(), x + (int) (i * band), y + h + 18, (int) band - 3, 11);
        }
        center(g, "左轴：综合分值    右轴：平均分", box.x + box.width / 2, box.y + 36, 10, false);
        center(g, "青色＝综合分值    红色＝平均分", box.x + box.width / 2, box.y + box.height - 6, 10, false);
    }

    private static void scatter(Graphics2D g, Rectangle box, String title, List<DimensionMetricDTO> values, Map<String, Color> groups) {
        title(g, box, title);
        if (values.isEmpty()) { empty(g, box); return; }
        int x = box.x + 45; int y = box.y + 40; int w = box.width - 70; int h = box.height - 85;
        double maxCount = niceMaximum(Math.max(1, values.stream().mapToInt(DimensionMetricDTO::count).max().orElse(1)) * 1.12);
        double maxAverage = niceMaximum(Math.max(1, values.stream().mapToDouble(value -> average(value)).max().orElse(1)) * 1.15);
        grid(g, x, y, w, h, maxCount, false);
        for (int tick = 0; tick <= 5; tick++) {
            int ty = y + h - h * tick / 5;
            g.setColor(GRID); g.drawLine(x, ty, x + w, ty);
            text(g, format(maxAverage * tick / 5), box.x, ty + 4, 10, false);
        }
        List<Rectangle> occupied = new ArrayList<>();
        // 先保留所有气泡范围，再寻找姓名位置，避免姓名盖住其他数据点。
        for (DimensionMetricDTO value : values) {
            int px = x + (int) (w * value.count() / maxCount);
            int py = y + h - (int) (h * average(value) / maxAverage);
            int radius = Math.max(3, (int) (Math.sqrt(value.count() / maxCount) * 15));
            occupied.add(new Rectangle(px - radius - 2, py - radius - 2, radius * 2 + 4, radius * 2 + 4));
        }
        for (int i = 0; i < values.size(); i++) {
            DimensionMetricDTO value = values.get(i);
            Rectangle bubble = occupied.get(i);
            int px = bubble.x + bubble.width / 2; int py = bubble.y + bubble.height / 2;
            g.setColor(color(value, i, groups)); g.fillOval(bubble.x + 2, bubble.y + 2, bubble.width - 4, bubble.height - 4);
            g.setColor(new Color(0x57716c)); g.setStroke(new BasicStroke(1)); g.drawOval(bubble.x + 2, bubble.y + 2, bubble.width - 4, bubble.height - 4);
            g.setFont(MonthlyReportChartFonts.deriveFont(Font.PLAIN, 10));
            String name = value.key();
            int labelWidth = Math.min(110, g.getFontMetrics().stringWidth(name) + 6);
            Rectangle label = null;
            for (int distance = 0; distance < h && label == null; distance += 14) {
                for (int direction : new int[]{-1, 1}) {
                    int ly = py + direction * (bubble.height / 2 + 5 + distance) - 6;
                    int lx = Math.max(x, Math.min(x + w - labelWidth, px - labelWidth / 2));
                    Rectangle candidate = new Rectangle(lx, ly, labelWidth, 13);
                    if (ly >= y && ly + 13 <= y + h && occupied.stream().noneMatch(candidate::intersects)) { label = candidate; break; }
                }
            }
            if (label == null) {
                for (int ly = y; ly + 13 <= y + h && label == null; ly += 14) {
                    for (int lx = x; lx + labelWidth <= x + w; lx += 20) {
                        Rectangle candidate = new Rectangle(lx, ly, labelWidth, 13);
                        if (occupied.stream().noneMatch(candidate::intersects)) { label = candidate; break; }
                    }
                }
            }
            if (label != null) {
                occupied.add(label);
                if (Math.abs(label.y - py) > 25) { g.setColor(new Color(0xb8c1c4)); g.drawLine(px, py, label.x + label.width / 2, label.y + 6); }
                fit(g, name, label.x + 3, label.y + 10, label.width - 6, 10);
            } else {
                throw new IllegalStateException("发送人图表姓名布局空间不足");
            }
        }
        center(g, "Offer数量（气泡面积＝数量；颜色＝小组）", x + w / 2, y + h + 31, 11, false);
        text(g, "平均分", box.x, y - 8, 10, false);
    }

    private static Color color(DimensionMetricDTO value, int index, Map<String, Color> groups) {
        String group = value.group() == null ? value.key() : value.group();
        return groups.getOrDefault(group, COLORS[index % COLORS.length]);
    }

    private static Color pieColor(DimensionMetricDTO value, int index) {
        if ("内部员工".equals(value.key()) || value.key().contains("入职")) return COLORS[1];
        if ("实习生".equals(value.key()) || value.key().contains("拒绝")) return COLORS[0];
        if (value.key().contains("接受")) return COLORS[2];
        if (value.key().contains("已发")) return COLORS[4];
        return COLORS[index % COLORS.length];
    }

    private static void grid(Graphics2D g, int x, int y, int width, int height, double max, boolean vertical) {
        g.setStroke(new BasicStroke(.7f));
        for (int tick = 0; tick <= 5; tick++) {
            g.setColor(GRID);
            if (vertical) {
                int ty = y + height - height * tick / 5; g.drawLine(x, ty, x + width, ty);
                text(g, format(max * tick / 5), x - 31, ty + 4, 9, false);
            } else {
                int tx = x + width * tick / 5; g.drawLine(tx, y, tx, y + height);
                center(g, format(max * tick / 5), tx, y + height + 13, 9, false);
            }
        }
    }

    private static void title(Graphics2D g, Rectangle box, String value) { center(g, value, box.x + box.width / 2, box.y + 16, 14, true); }
    private static void empty(Graphics2D g, Rectangle box) { center(g, "暂无数据", box.x + box.width / 2, box.y + box.height / 2, 13, false); }
    private static void text(Graphics2D g, String value, int x, int y, int size, boolean bold) {
        g.setColor(INK); g.setFont(MonthlyReportChartFonts.deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); g.drawString(value, x, y);
    }
    private static void center(Graphics2D g, String value, int x, int y, int size, boolean bold) {
        g.setFont(MonthlyReportChartFonts.deriveFont(bold ? Font.BOLD : Font.PLAIN, size));
        text(g, value, x - g.getFontMetrics().stringWidth(value) / 2, y, size, bold);
    }
    private static void fit(Graphics2D g, String value, int x, int y, int width, int size) {
        g.setColor(INK); g.setFont(MonthlyReportChartFonts.deriveFont(Font.PLAIN, size));
        int measured = g.getFontMetrics().stringWidth(value);
        Graphics2D label = (Graphics2D) g.create();
        try { label.translate(x, y); if (measured > width) label.scale(width / (double) measured, 1); label.drawString(value, 0, 0); }
        finally { label.dispose(); }
    }
    private static String format(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static double average(DimensionMetricDTO value) {
        return value.count() == 0 ? 0 : value.score().doubleValue() / value.count();
    }
    private static double gradeOrder(String key) {
        try { return Double.parseDouble(key.replace("分", "").strip()); }
        catch (NumberFormatException exception) { return Double.MAX_VALUE; }
    }
    private static double niceMaximum(double maximum) {
        double targetStep = maximum / 5;
        double magnitude = Math.pow(10, Math.floor(Math.log10(targetStep)));
        for (double factor : new double[]{1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10}) {
            if (factor * magnitude >= targetStep) return factor * magnitude * 5;
        }
        return maximum;
    }
    private static byte[] encode(BufferedImage image, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output); checkCancelled(cancelled); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("月报看板图片生成失败", exception); }
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new MonthlyReportInterruptedException();
    }
}
