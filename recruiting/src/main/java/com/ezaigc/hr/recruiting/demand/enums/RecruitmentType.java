package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘需求类型。 */
@Getter
@RequiredArgsConstructor
public enum RecruitmentType implements CodeDescEnum {
  INTERNAL("内部招聘"),
  EXTERNAL("外派招聘");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
