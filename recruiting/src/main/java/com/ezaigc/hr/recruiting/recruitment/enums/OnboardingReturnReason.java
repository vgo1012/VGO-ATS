package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import com.ezaigc.hr.common.core.util.CommonUtils;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 入职流程退回共享简历库的受控原因。 */
@Getter
public enum OnboardingReturnReason implements CodeDescEnum {
  LIBRARY_RETURN("入职库退回", ApplicationDecision.REJECT),
  CANDIDATE("候选人原因", ApplicationDecision.WITHDRAW),
  POSITION("岗位原因", ApplicationDecision.REJECT),
  OTHER("其它", ApplicationDecision.REJECT);

  private final String desc;
  @Accessors(fluent = true)
  private final ApplicationDecision outcome;

  OnboardingReturnReason(String desc, ApplicationDecision outcome) {
    this.desc = desc;
    this.outcome = outcome;
  }

  public static OnboardingReturnReason fromNullableCode(String code) {
    return valueOf(CommonUtils.upperOrElse(code, LIBRARY_RETURN.getCode()));
  }
}
