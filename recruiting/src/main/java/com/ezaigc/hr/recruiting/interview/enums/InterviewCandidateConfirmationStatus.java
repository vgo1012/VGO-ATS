package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人对面试安排的确认状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewCandidateConfirmationStatus implements CodeDescEnum {
  PENDING("待确认"),
  CONFIRMED("已确认"),
  DECLINED("已拒绝");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
