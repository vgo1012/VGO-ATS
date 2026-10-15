package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JdTitleSuggestionStatus implements CodeDescEnum {
  PENDING("待审核"), APPROVED("已共享"), REJECTED("已驳回");

  private final String desc;

  @Override public String getCode() { return name(); }
}
