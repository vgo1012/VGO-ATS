package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.EnumSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Offer 当前状态及其单调迁移规则。 */
@Getter
@RequiredArgsConstructor
public enum OfferStatus implements CodeDescEnum {
  NOT_STARTED("未开始"),
  IN_PROGRESS("处理中"),
  ISSUED("已发放"),
  ACCEPTED("已接受"),
  TERMINATED("已终止"),
  ONBOARDED("已入职");

  /** 只有真实已入职才占用 HC；两侧空格保留文本块拼接的词边界。 */
  public static final String OCCUPIED_SQL_PREDICATE = " o.status = 'ONBOARDED' ";

  /** 交付完成只计真实入职。 */
  public static final String ONBOARDED_SQL_PREDICATE = " o.status = 'ONBOARDED' ";

  private final String desc;
  public boolean canTransitionTo(OfferStatus target) {
    return switch (this) {
      case NOT_STARTED -> EnumSet.of(IN_PROGRESS, ISSUED, TERMINATED).contains(target);
      case IN_PROGRESS -> EnumSet.of(ISSUED, TERMINATED).contains(target);
      case ISSUED -> target == TERMINATED;
      case ACCEPTED -> target == TERMINATED;
      case TERMINATED, ONBOARDED -> false;
    };
  }

  public boolean isCorrectionTarget() {
    return this != ACCEPTED && this != ONBOARDED;
  }

  public boolean keepsApplicationInOffer() {
    return this == IN_PROGRESS || this == ISSUED || this == ACCEPTED;
  }

  /** ACCEPTED 仅兼容历史事实；新流程在已发放后可直接确认真实入职。 */
  public boolean allowsOnboarding() {
    return this == ISSUED || this == ACCEPTED;
  }

  public static OfferStatus fromCode(String code) {
    return OfferStatus.valueOf(code);
  }
}
