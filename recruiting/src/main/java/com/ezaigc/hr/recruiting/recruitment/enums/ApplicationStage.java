package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Application 当前阶段。 */
@Getter
@RequiredArgsConstructor
public enum ApplicationStage implements CodeDescEnum {
  PATH_SELECTION("路径选择"),
  IN_PROCESS("流程进行中"),
  DECISION("人工决定"),
  OFFER("Offer 阶段"),
  TERMINATED("已终止"),
  ONBOARDED("已入职");

  private final String desc;

  public static boolean isValid(String code) {
    return Arrays.stream(values()).anyMatch(value -> value.matches(code));
  }

  public static boolean allowsDecisionCorrection(String code) {
    return DECISION.matches(code) || OFFER.matches(code);
  }

  public static boolean isTerminal(String code) {
    return TERMINATED.matches(code) || ONBOARDED.matches(code);
  }
}
