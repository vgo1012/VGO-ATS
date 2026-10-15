package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeLifecyclePhase implements CodeDescEnum {
  PENDING_SCREENING("待筛选"),
  PENDING_DISPOSAL("待处置"),
  SHARED("已共享"),
  CLOSED("已关闭");

  private final String desc;

  public static boolean isPersonalPool(String code) {
    return PENDING_SCREENING.matches(code) || PENDING_DISPOSAL.matches(code);
  }
}
