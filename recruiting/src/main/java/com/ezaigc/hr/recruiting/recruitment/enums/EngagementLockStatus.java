package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 候选人跟进锁状态。 */
@Getter
@RequiredArgsConstructor
public enum EngagementLockStatus implements CodeDescEnum {
  ACTIVE("生效"),
  RELEASED("已释放"),
  EXPIRED("已过期"),
  TRANSFERRED("已转交"),
  OVERRIDDEN("已覆盖");

  private final String desc;
}
