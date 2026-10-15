package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResumeLifecycleRouteReason implements CodeDescEnum {
  SCREENING_ADMITTED("智能筛选准入"),
  RECRUITMENT_CREATED("创建招聘流程"),
  MANUAL_SHARED("手动共享");

  private final String desc;
}
