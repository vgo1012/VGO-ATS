package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 应聘记录的只读范围筛选，不是新增业务状态。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationLifecycle implements CodeDescEnum {
  ACTIVE("进行中"),
  ARCHIVED("历史归档"),
  ALL("全部");

  private final String desc;

  public static boolean isValid(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
