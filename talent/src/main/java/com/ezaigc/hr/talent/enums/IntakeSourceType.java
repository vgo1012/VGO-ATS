package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 人才收件来源类型。 */
@Getter
@RequiredArgsConstructor
public enum IntakeSourceType implements CodeDescEnum {
  MANUAL("手工上传"),
  BATCH("批量上传"),
  MAIL("邮件采集"),
  BOSS("Boss 采集");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
