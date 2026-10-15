package com.ezaigc.hr.talent.api;

import com.ezaigc.hr.talent.enums.ResumeDatePrecision;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import lombok.Value;
import lombok.experimental.Accessors;

/** 简历日期保留原文精度；边界仅用于区间计算，不作为原文日期持久化。 */
@Value
@Accessors(fluent = true)
public class ResumeDateValue {
    String text;
    public boolean isPresent() { return ResumeDatePrecision.PRESENT.getCode().equals(text); }
    public static ResumeDateValue parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (ResumeDatePrecision.PRESENT.getCode().equals(value)) return new ResumeDateValue(value);
            if (value.matches("\\d{4}")) Year.parse(value);
            else if (value.matches("\\d{4}-\\d{2}")) YearMonth.parse(value);
            else if (value.matches("\\d{4}-\\d{2}-\\d{2}")) LocalDate.parse(value);
            else throw new IllegalArgumentException("Unsupported resume date precision");
            return new ResumeDateValue(value);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("Invalid resume calendar date", error);
        }
    }

    public LocalDate completeDate() { return text.length() == 10 ? LocalDate.parse(text) : null; }
    public LocalDate earliest(LocalDate today) {
        if (isPresent()) return today;
        if (text.length() == 4) return Year.parse(text).atDay(1);
        if (text.length() == 7) return YearMonth.parse(text).atDay(1);
        return LocalDate.parse(text);
    }
    public LocalDate latest(LocalDate today) {
        if (isPresent()) return today;
        if (text.length() == 4) return Year.parse(text).atMonth(12).atEndOfMonth();
        if (text.length() == 7) return YearMonth.parse(text).atEndOfMonth();
        return LocalDate.parse(text);
    }
}
