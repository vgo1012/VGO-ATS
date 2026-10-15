package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人沟通结果。 */
@Getter
@RequiredArgsConstructor
public enum CommunicationResult implements CodeDescEnum {
  CONTINUE("继续推进"),
  DECLINED("候选人拒绝"),
  NO_RESPONSE("未回复"),
  FOLLOW_UP("待跟进");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
