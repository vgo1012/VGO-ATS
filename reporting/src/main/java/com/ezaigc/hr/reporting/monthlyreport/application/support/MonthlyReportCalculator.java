package com.ezaigc.hr.reporting.monthlyreport.application.support;

import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DemandSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.DimensionMetricDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.MonthlyReportAnalysisDTO;
import com.ezaigc.hr.reporting.monthlyreport.domain.dto.OfferSummaryDTO;
import com.ezaigc.hr.reporting.monthlyreport.enums.MonthlyReportGrade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.Accessors;

public final class MonthlyReportCalculator {

    public static final String RULE_VERSION = "monthly-report-public-rules-v2";
    private static final BigDecimal ONE = BigDecimal.ONE;

    public Result calculate(List<OfferRow> offers, List<DemandRow> demands, YearMonth month) {
        List<ScoredOffer> scored = offers.stream().map(this::score).toList();
        BigDecimal totalScore = scored.stream().map(ScoredOffer::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Integer> statusCounts = count(scored, ScoredOffer::status);
        int total = scored.size();
        int scoredCount = total - statusCounts.getOrDefault("已拒绝offer", 0);
        LocalDate min = offers.stream().map(OfferRow::sentDate).min(LocalDate::compareTo).orElse(null);
        LocalDate max = offers.stream().map(OfferRow::sentDate).max(LocalDate::compareTo).orElse(null);
        OfferSummaryDTO offerSummary = new OfferSummaryDTO(
                total,
                scoredCount,
                money(totalScore),
                statusCounts,
                rate(statusCounts.getOrDefault("已拒绝offer", 0), total),
                rate(scoredCount, total),
                rate(statusCounts.getOrDefault("已接受offer", 0) + statusCounts.getOrDefault("已入职offer", 0), total),
                rate(statusCounts.getOrDefault("已入职offer", 0), total),
                min == null ? null : min.toString(),
                max == null ? null : max.toString(),
                Map.of());

        DemandSummaryDTO demandSummary = new DemandSummaryDTO(
                demands.size(),
                demands.stream().mapToLong(DemandRow::headcount).sum(),
                (int) demands.stream().filter(row -> YearMonth.from(row.appliedDate()).equals(month)).count(),
                demands.stream().filter(row -> YearMonth.from(row.appliedDate()).equals(month)).mapToLong(DemandRow::headcount).sum(),
                (int) demands.stream().filter(row -> YearMonth.from(row.expectedDate()).equals(month)).count(),
                demands.stream().filter(row -> YearMonth.from(row.expectedDate()).equals(month)).mapToLong(DemandRow::headcount).sum());

        List<DimensionMetricDTO> dimensions = new ArrayList<>();
        addDimension(dimensions, "employment", scored, ScoredOffer::employment, ignored -> null, total, totalScore);
        addDimension(dimensions, "group", scored, ScoredOffer::group, ignored -> null, total, totalScore);
        addDimension(dimensions, "department", scored, ScoredOffer::department, ignored -> null, total, totalScore);
        addDimension(dimensions, "channel", scored, ScoredOffer::channel, ignored -> null, total, totalScore);
        addDimension(dimensions, "gradeBand", scored, ScoredOffer::gradeBand, ignored -> null, total, totalScore);
        addDimension(dimensions, "sender", scored, ScoredOffer::sender, ScoredOffer::group, total, totalScore);
        dimensions.sort(Comparator.comparing(DimensionMetricDTO::dimension)
                .thenComparing(DimensionMetricDTO::count, Comparator.reverseOrder())
                .thenComparing(DimensionMetricDTO::score, Comparator.reverseOrder())
                .thenComparing(metric -> metric.group() == null ? "" : metric.group())
                .thenComparing(DimensionMetricDTO::key));
        return new Result(offerSummary, demandSummary, List.copyOf(dimensions), analysis(offerSummary, dimensions));
    }

    private ScoredOffer score(OfferRow row) {
        BigDecimal base = gradeScore(row.grade());
        BigDecimal statusFactor = "已拒绝offer".equals(row.status()) ? BigDecimal.ZERO : ONE;
        return new ScoredOffer(row.department(), row.employment(), row.status(), row.sender(), row.group(),
                row.channel(), gradeBand(base), base.multiply(statusFactor));
    }

    public static BigDecimal gradeScore(String grade) {
        return MonthlyReportGrade.valueOf(grade).getScore();
    }

    private static String gradeBand(BigDecimal base) {
        return base.stripTrailingZeros().toPlainString() + "分";
    }

    private static <T> Map<String, Integer> count(List<T> values, Function<T, String> classifier) {
        Map<String, Integer> result = new TreeMap<>();
        values.forEach(value -> result.merge(classifier.apply(value), 1, Integer::sum));
        return Map.copyOf(result);
    }

    private static void addDimension(
            List<DimensionMetricDTO> target,
            String dimension,
            List<ScoredOffer> offers,
            Function<ScoredOffer, String> key,
            Function<ScoredOffer, String> group,
            int total,
            BigDecimal totalScore) {
        Map<GroupKey, List<ScoredOffer>> grouped = new LinkedHashMap<>();
        for (ScoredOffer offer : offers) {
            grouped.computeIfAbsent(new GroupKey(group.apply(offer), key.apply(offer)), ignored -> new ArrayList<>()).add(offer);
        }
        grouped.forEach((groupKey, rows) -> {
            BigDecimal score = rows.stream().map(ScoredOffer::score).reduce(BigDecimal.ZERO, BigDecimal::add);
            target.add(new DimensionMetricDTO(
                    dimension,
                    groupKey.key(),
                    groupKey.group(),
                    rows.size(),
                    money(score),
                    rows.isEmpty() ? null : money(score.divide(BigDecimal.valueOf(rows.size()), 8, RoundingMode.HALF_UP)),
                    count(rows, ScoredOffer::status),
                    rate(rows.size(), total),
                    totalScore.signum() == 0 ? null : rate(score, totalScore)));
        });
    }

    private static List<MonthlyReportAnalysisDTO> analysis(OfferSummaryDTO summary, List<DimensionMetricDTO> dimensions) {
        List<MonthlyReportAnalysisDTO> result = new ArrayList<>();
        List<DimensionMetricDTO> groups = dimensions.stream().filter(metric -> metric.dimension().equals("group")).toList();
        if (!groups.isEmpty()) {
            int maximum = groups.stream().mapToInt(DimensionMetricDTO::count).max().orElse(0);
            List<String> leaders = groups.stream().filter(metric -> metric.count() == maximum).map(DimensionMetricDTO::key).sorted().toList();
            String lead = String.join("、", leaders);
            result.add(new MonthlyReportAnalysisDTO("GROUP_VOLUME_LEADER", "offer.count", "group",
                    Integer.toString(maximum), "本月" + lead + "发放" + maximum + "条，占" + percent(rate(maximum, summary.totalCount()))
                            + (leaders.size() > 1 ? "，并列各小组最高。" : "，为各小组最高。"), null));
        }
        int rejected = summary.statusCounts().getOrDefault("已拒绝offer", 0);
        if (rejected > 0) {
            List<DimensionMetricDTO> rejectionLeaders = dimensions.stream().filter(metric -> metric.dimension().equals("department"))
                    .filter(metric -> metric.statusCounts().getOrDefault("已拒绝offer", 0) > 0)
                    .sorted(Comparator.comparingInt((DimensionMetricDTO metric) -> metric.statusCounts().getOrDefault("已拒绝offer", 0)).reversed()
                            .thenComparing(DimensionMetricDTO::key)).toList();
            String leaderText = rejectionLeaders.isEmpty() ? "" : "；拒绝数量最多的一级部门为" + rejectionLeaders.getFirst().key()
                    + "（" + rejectionLeaders.getFirst().statusCounts().getOrDefault("已拒绝offer", 0) + "条）";
            result.add(new MonthlyReportAnalysisDTO("REJECTED_OFFER", "offer.rejected.count", "overall",
                    Integer.toString(rejected), "已拒绝Offer共" + rejected + "条，占" + percent(summary.rejectedRate()) + leaderText + "。",
                    "建议结合拒绝原因明细进行后续核查。"));
        }
        int accepted = summary.statusCounts().getOrDefault("已接受offer", 0);
        int issued = summary.statusCounts().getOrDefault("已发offer", 0);
        if (accepted + issued > 0) {
            result.add(new MonthlyReportAnalysisDTO("PENDING_FOLLOW_UP", "offer.pending.count", "overall",
                    Integer.toString(accepted + issued), "已接受待入职" + accepted + "条，已发待确认" + issued + "条。", "建议持续跟进当前状态。"));
        }
        Map<String, List<DimensionMetricDTO>> sendersByGroup = dimensions.stream()
                .filter(metric -> metric.dimension().equals("sender"))
                .collect(Collectors.groupingBy(DimensionMetricDTO::group));
        sendersByGroup.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<DimensionMetricDTO> senders = entry.getValue().stream()
                    .sorted(Comparator.comparing(DimensionMetricDTO::count).reversed()
                            .thenComparing(DimensionMetricDTO::score, Comparator.reverseOrder())
                            .thenComparing(DimensionMetricDTO::key)).toList();
            int groupCount = senders.stream().mapToInt(DimensionMetricDTO::count).sum();
            int topCount = senders.stream().limit(3).mapToInt(DimensionMetricDTO::count).sum();
            if (topCount > 0) {
                result.add(new MonthlyReportAnalysisDTO("SENDER_TOP3_CONCENTRATION", "offer.sender.top3.share", "sender",
                        rate(topCount, groupCount).toPlainString(), entry.getKey() + "小组内前三名发送人合计发放" + topCount
                                + "条，占该小组" + percent(rate(topCount, groupCount)) + "，呈现数量集中情况。", null));
            }
        });
        return List.copyOf(result);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(int numerator, int denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() == 0 ? null : numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP);
    }

    private static String percent(BigDecimal value) {
        return value == null ? "—" : value.toPlainString() + "%";
    }

    @Value
    @Accessors(fluent = true)
    public static class OfferRow {
        String department;
        String employment;
        String grade;
        String status;
        String sender;
        String group;
        LocalDate sentDate;
        String channel;
    }

    @Value
    @Accessors(fluent = true)
    public static class DemandRow {
        String department;
        LocalDate appliedDate;
        String position;
        int headcount;
        LocalDate expectedDate;
    }

    @Value
    @Accessors(fluent = true)
    public static class Result {
        OfferSummaryDTO offerSummary;
        DemandSummaryDTO demandSummary;
        List<DimensionMetricDTO> dimensions;
        List<MonthlyReportAnalysisDTO> analysis;
    }

    @Value
    @Accessors(fluent = true)
    private static class ScoredOffer {
        String department;
        String employment;
        String status;
        String sender;
        String group;
        String channel;
        String gradeBand;
        BigDecimal score;
    }

    @Value
    @Accessors(fluent = true)
    private static class GroupKey {
        String group;
        String key;
    }
}
