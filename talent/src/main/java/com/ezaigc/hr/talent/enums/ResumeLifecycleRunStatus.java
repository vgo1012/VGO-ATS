package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历定时流转的持久化运行状态。 */
@Getter
@RequiredArgsConstructor
public enum ResumeLifecycleRunStatus implements CodeDescEnum {
  RETRY_PENDING("待重试"),
  RUNNING("执行中"),
  SUCCEEDED("已完成"),
  PARTIAL("部分失败"),
  BLOCKED("日历暂停");

  private final String desc;
}
