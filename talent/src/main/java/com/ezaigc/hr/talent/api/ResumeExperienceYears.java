package com.ezaigc.hr.talent.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/** 使用可证明的时间下界合并重叠任职；缺失结束时间不会当作在职。 */
public final class ResumeExperienceYears {
    private ResumeExperienceYears() {}
    public static BigDecimal calculate(List<ResumeFactSnapshotData.Work> work, Clock clock) {
        if (work.isEmpty()) return null;
        var today = LocalDate.now(clock);
        List<Range> ranges = new ArrayList<>();
        for (var job : work) {
            var start = ResumeDateValue.parse(job.startDateText());
            var end = ResumeDateValue.parse(job.endDateText());
            if (start == null || end == null || start.isPresent()) return null;
            var a = start.latest(today);
            var b = end.earliest(today);
            if (b.isAfter(today)) b = today;
            if (a.isAfter(b)) return null;
            ranges.add(new Range(a, b));
        }
        ranges.sort(Comparator.comparing(Range::start));
        long days = 0;
        var start = ranges.getFirst().start();
        var end = ranges.getFirst().end();
        for (var range : ranges.subList(1, ranges.size())) {
            if (!range.start().isAfter(end.plusDays(1))) {
                if (range.end().isAfter(end)) end = range.end();
            } else {
                days += ChronoUnit.DAYS.between(start, end.plusDays(1));
                start = range.start(); end = range.end();
            }
        }
        days += ChronoUnit.DAYS.between(start, end.plusDays(1));
        return BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365.2425), 2, RoundingMode.DOWN);
    }
    @Value
    @Accessors(fluent = true)
    private static class Range { LocalDate start; LocalDate end; }
}
