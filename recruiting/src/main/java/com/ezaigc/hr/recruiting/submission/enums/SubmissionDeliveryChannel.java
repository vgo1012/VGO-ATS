package com.ezaigc.hr.recruiting.submission.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户推荐发送渠道。 */
@Getter
@RequiredArgsConstructor
public enum SubmissionDeliveryChannel implements CodeDescEnum {
  EMAIL("邮件"),
  CLIENT_SYSTEM("客户系统"),
  WECHAT("微信"),
  ENTERPRISE_WECHAT("企业微信"),
  PHONE("电话"),
  MANUAL_OTHER("其他人工渠道");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
