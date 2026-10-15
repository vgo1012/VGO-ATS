package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 已启用的面试通知模板类型。 */
@Getter
@RequiredArgsConstructor
public enum InterviewNotificationType implements CodeDescEnum {
  INTERVIEW_INVITATION("面试邀请"),
  INTERVIEW_RESCHEDULE("面试改期"),
  INTERVIEW_CANCEL("面试取消");

  private final String desc;
}
