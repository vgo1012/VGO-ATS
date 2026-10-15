package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import java.util.EnumSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘需求生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum DemandStatus implements CodeDescEnum {
  DRAFT("草稿"),
  ACTIVE("招聘中"),
  PAUSED("已暂停"),
  CLOSED("已关闭");

  private final String desc;
  public boolean canTransitionTo(DemandStatus target) {
    return switch (this) {
      case DRAFT -> target == ACTIVE;
      case ACTIVE -> EnumSet.of(PAUSED, CLOSED).contains(target);
      case PAUSED -> EnumSet.of(ACTIVE, CLOSED).contains(target);
      case CLOSED -> false;
    };
  }

  public static DemandStatus fromCode(String code) {
    return DemandStatus.valueOf(code);
  }
}
