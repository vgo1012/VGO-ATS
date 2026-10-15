package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试参与人记录当前支持的业务角色。 */
@Getter
@RequiredArgsConstructor
public enum InterviewParticipantRole implements CodeDescEnum {
  INTERVIEWER("面试官");

  private final String desc;
}
