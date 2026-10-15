package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeLifecycleAdvanceResult implements CodeDescEnum {
  SKIPPED("跳过"),
  BLOCKED("日历暂停"),
  CLOSED("关闭周期"),
  DEFERRED("升级延后"),
  SHARED("转共享"),
  SUCCEEDED("结算成功");

  private final String desc;
}
