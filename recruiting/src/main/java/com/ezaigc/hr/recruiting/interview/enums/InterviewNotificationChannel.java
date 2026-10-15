package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试通知渠道。 */
@Getter
@RequiredArgsConstructor
public enum InterviewNotificationChannel implements CodeDescEnum {
  IN_APP("站内通知"),
  EMAIL("邮件"),
  WECHAT("微信"),
  ENTERPRISE_WECHAT("企业微信"),
  PHONE("电话"),
  MANUAL_OTHER("其他人工渠道");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
