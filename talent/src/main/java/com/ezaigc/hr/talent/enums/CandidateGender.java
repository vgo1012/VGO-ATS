package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历显式声明的候选人性别。 */
@Getter
@RequiredArgsConstructor
public enum CandidateGender implements CodeDescEnum {
  MALE("男"),
  FEMALE("女"),
  OTHER("其他");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
