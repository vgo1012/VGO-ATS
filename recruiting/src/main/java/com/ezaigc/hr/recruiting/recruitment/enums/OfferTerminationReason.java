package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Offer 终止原因及其默认 Application 结果。 */
@Getter
public enum OfferTerminationReason implements CodeDescEnum {
  CANDIDATE_DECLINED("候选人拒绝", ApplicationDecision.WITHDRAW),
  EXPIRED("Offer 过期", ApplicationDecision.WITHDRAW),
  COMPANY_WITHDREW("公司撤回", ApplicationDecision.REJECT),
  OTHER("其他", null);

  private final String desc;
  @Accessors(fluent = true)
  private final ApplicationDecision defaultDecision;

  OfferTerminationReason(String desc, ApplicationDecision defaultDecision) {
    this.desc = desc;
    this.defaultDecision = defaultDecision;
  }
  public static OfferTerminationReason fromCode(String code) {
    return OfferTerminationReason.valueOf(code);
  }
}
