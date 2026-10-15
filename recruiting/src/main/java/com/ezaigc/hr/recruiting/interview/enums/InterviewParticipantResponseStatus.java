package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试参与人的响应状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewParticipantResponseStatus implements CodeDescEnum {
  PENDING("待响应"),
  ACCEPTED("已接受"),
  DECLINED("已拒绝");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
