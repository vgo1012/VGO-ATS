package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人沟通渠道。 */
@Getter
@RequiredArgsConstructor
public enum CommunicationChannel implements CodeDescEnum {
  PHONE("电话"),
  EMAIL("邮件"),
  WECHAT("微信"),
  ENTERPRISE_WECHAT("企业微信"),
  IN_PERSON("当面沟通"),
  CLIENT_SYSTEM("客户系统"),
  OTHER("其他");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
