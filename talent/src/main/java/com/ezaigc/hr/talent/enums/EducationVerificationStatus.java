package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 学历核验状态。 */
@Getter
@RequiredArgsConstructor
public enum EducationVerificationStatus implements CodeDescEnum {
  UNVERIFIED("未核验"),
  IN_PROGRESS("核验中"),
  VERIFIED("已核验"),
  MISMATCH("信息不一致"),
  UNABLE_TO_VERIFY("无法核验");

  private final String desc;
  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
