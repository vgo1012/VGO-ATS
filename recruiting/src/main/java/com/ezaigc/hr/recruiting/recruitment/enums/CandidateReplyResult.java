package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 能够续期跟进锁的候选人回复结果。 */
@Getter
@RequiredArgsConstructor
public enum CandidateReplyResult implements CodeDescEnum {
  CONTINUE("继续沟通"),
  DECLINED("候选人拒绝");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
