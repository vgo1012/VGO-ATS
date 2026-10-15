package com.ezaigc.hr.recruiting.submission.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 客户明确禁推意图，与现有页面提交编码一致。 */
@Getter
@RequiredArgsConstructor
public enum ClientRestrictionIntent implements CodeDescEnum {
  DO_NOT_SUBMIT("禁止再次推荐"),
  CLIENT_EXPLICIT_BAN("客户明确禁推");

  public static final String INPUT_PATTERN = "DO_NOT_SUBMIT|CLIENT_EXPLICIT_BAN";
  private final String desc;

  public static boolean supports(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }
}
