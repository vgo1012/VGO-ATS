package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 自动完成收件时可由并发处理造成的冲突。 */
@Getter
@RequiredArgsConstructor
public enum ResumeIntakeCompletionConflict implements CodeDescEnum {
  INTAKE_NOT_PARSING("收件已由其他处理推进");

  private final String desc;
}
