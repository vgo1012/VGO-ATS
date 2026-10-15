package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 学历核验记录类型。 */
@Getter
@RequiredArgsConstructor
public enum EducationRecordType implements CodeDescEnum {
  VERIFICATION("学历核验"),
  STUDY_MODE_SUPPLEMENT("学习形式补充"),
  CORRECTION("更正");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
