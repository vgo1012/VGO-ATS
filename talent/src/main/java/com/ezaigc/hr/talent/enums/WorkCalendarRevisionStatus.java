package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 工作日历版本状态。 */
@Getter
@RequiredArgsConstructor
public enum WorkCalendarRevisionStatus implements CodeDescEnum {
  DRAFT("草稿"),
  PUBLISHED("已发布"),
  SUPERSEDED("已替代");

  private final String desc;

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String SUPERSEDED = "SUPERSEDED";

    private Codes() {}
  }
}
