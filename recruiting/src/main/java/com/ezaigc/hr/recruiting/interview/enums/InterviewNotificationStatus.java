package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试通知意图状态。 */
@Getter
@RequiredArgsConstructor
public enum InterviewNotificationStatus implements CodeDescEnum {
  PENDING_MANUAL("待人工发送"),
  SENT_MANUAL("已人工发送"),
  SENDING_EXTERNAL("外部渠道发送中"),
  SENT_EXTERNAL("外部渠道已发送"),
  FAILED_EXTERNAL("外部渠道发送失败");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }
}
