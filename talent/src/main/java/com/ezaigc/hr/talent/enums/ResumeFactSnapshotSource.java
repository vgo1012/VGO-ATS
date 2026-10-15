package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历事实快照的生成方式，与人工更正依据分开建模。 */
@Getter
@RequiredArgsConstructor
public enum ResumeFactSnapshotSource implements CodeDescEnum {
  AI_PARSE("AI 解析"),
  HUMAN_CORRECTION("人工更正");

  private final String desc;
}
