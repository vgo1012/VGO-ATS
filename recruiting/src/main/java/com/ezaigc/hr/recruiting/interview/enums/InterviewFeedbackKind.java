package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试评价来源类型。 */
@Getter
@RequiredArgsConstructor
public enum InterviewFeedbackKind implements CodeDescEnum {
  INTERNAL("内部评价"),
  EXTERNAL_RECORDED("外部评价代录");

  private final String desc;
}
