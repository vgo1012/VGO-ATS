package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** JD 受控条件中的显式性别要求。 */
@Getter
@RequiredArgsConstructor
public enum JdSexRequirement implements CodeDescEnum {
  ANY("不限"),
  MALE("男"),
  FEMALE("女");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.name().equals(code));
  }
}
