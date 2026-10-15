package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人记录状态。 */
@Getter
@RequiredArgsConstructor
public enum CandidateStatus implements CodeDescEnum {
  ACTIVE("在用"),
  DELETED("已删除");

  private final String desc;

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String ACTIVE = "ACTIVE";
    public static final String DELETED = "DELETED";

    private Codes() {}
  }
}
