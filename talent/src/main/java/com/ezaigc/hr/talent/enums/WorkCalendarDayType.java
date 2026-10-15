package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 工作日历日期类型。 */
@Getter
@RequiredArgsConstructor
public enum WorkCalendarDayType implements CodeDescEnum {
  WORKDAY("工作日"),
  RESTDAY("休息日"),
  HOLIDAY("节假日");

  private final String desc;

  /** Java 注解参数要求编译期常量，仅供 Mapper SQL 拼接使用。 */
  public static final class Codes {
    public static final String WORKDAY = "WORKDAY";
    public static final String RESTDAY = "RESTDAY";
    public static final String HOLIDAY = "HOLIDAY";

    private Codes() {}
  }
}
