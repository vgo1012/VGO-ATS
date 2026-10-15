package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 学历学习形式。 */
@Getter
@RequiredArgsConstructor
public enum EducationAttendanceMode implements CodeDescEnum {
  UNKNOWN("未知"),
  FULL_TIME("全日制"),
  PART_TIME("非全日制"),
  CORRESPONDENCE("函授"),
  OTHER("其他");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
