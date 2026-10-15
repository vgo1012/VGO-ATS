package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 重复候选人处理方式。 */
@Getter
@RequiredArgsConstructor
public enum DuplicateResolution implements CodeDescEnum {
  USE_EXISTING("使用现有候选人"),
  CREATE_SEPARATE("新建独立候选人"),
  MERGE("合并候选人");

  private final String desc;
  public static boolean reusesExistingCandidate(String code) {
    return USE_EXISTING.matches(code) || MERGE.matches(code);
  }

  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
