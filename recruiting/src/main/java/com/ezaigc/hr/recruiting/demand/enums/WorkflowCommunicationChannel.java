package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程模板允许配置的默认沟通渠道。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowCommunicationChannel implements CodeDescEnum {
  PHONE("电话"),
  WECHAT("微信"),
  EMAIL("邮件");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
