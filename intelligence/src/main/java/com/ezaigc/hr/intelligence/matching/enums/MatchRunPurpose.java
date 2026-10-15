package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配运行用途；公司默认评分只有人工准入后才进入智能筛选。 */
@Getter
@RequiredArgsConstructor
public enum MatchRunPurpose implements CodeDescEnum {
  COMPANY_DEFAULT("公司默认评分"),
  CUSTOM("用户自定义筛选");

  private final String desc;

  public static final class Codes {
    public static final String COMPANY_DEFAULT = "COMPANY_DEFAULT";
    public static final String CUSTOM = "CUSTOM";

    private Codes() {}
  }
}
