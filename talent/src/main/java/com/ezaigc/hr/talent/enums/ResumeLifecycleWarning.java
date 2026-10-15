package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeLifecycleWarning implements CodeDescEnum {
  NORMAL("正常"),
  YELLOW("黄色预警"),
  RED("红色预警");

  private final String desc;
}
