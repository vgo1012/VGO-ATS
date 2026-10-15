package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 事实快照来源。 */
@Getter
@RequiredArgsConstructor
public enum JdFactSourceType implements CodeDescEnum {
  AI_PARSE("AI 解析"),
  HUMAN_CORRECTION("人工更正");

  private final String desc;
}
