package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人身份冲突处理状态。 */
@Getter
@RequiredArgsConstructor
public enum IdentityConflictStatus implements CodeDescEnum {
  OPEN("待处理"),
  RESOLVED("已解决");

  private final String desc;

  @Override
  public String getCode() {
    return name();
  }

  public static final class Codes {
    public static final String OPEN = "OPEN";
    public static final String RESOLVED = "RESOLVED";

    private Codes() {}
  }
}
