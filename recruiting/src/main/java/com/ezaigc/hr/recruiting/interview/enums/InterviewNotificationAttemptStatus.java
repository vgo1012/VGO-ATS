package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试通知发送尝试状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewNotificationAttemptStatus implements CodeDescEnum {
  SENT("已发送"),
  FAILED("发送失败");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
