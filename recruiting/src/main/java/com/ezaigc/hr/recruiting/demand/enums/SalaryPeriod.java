package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 薪酬周期。 */
@Getter
@RequiredArgsConstructor
public enum SalaryPeriod implements CodeDescEnum {
  MONTH("月薪"),
  YEAR("年薪"),
  DAY("日薪"),
  HOUR("时薪");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
