package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历日期的原文精度及明确在职标记。 */
@Getter
@RequiredArgsConstructor
public enum ResumeDatePrecision implements CodeDescEnum {
    YEAR("年"), MONTH("年月"), DAY("年月日"), PRESENT("至今");
    private final String desc;
}
