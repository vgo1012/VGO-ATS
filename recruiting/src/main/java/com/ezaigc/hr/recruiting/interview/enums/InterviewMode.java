package com.ezaigc.hr.recruiting.interview.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 面试进行方式。 */
@Getter
@RequiredArgsConstructor
public enum InterviewMode implements CodeDescEnum {
  ONLINE("线上"),
  ONSITE("现场"),
  PHONE("电话"),
  CLIENT_MANAGED("客户安排");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
