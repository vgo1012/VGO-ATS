package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeLifecycleAction implements CodeDescEnum {
  RESUME_LIFECYCLE_RETRY("重试简历流转任务");

  private final String desc;
}
