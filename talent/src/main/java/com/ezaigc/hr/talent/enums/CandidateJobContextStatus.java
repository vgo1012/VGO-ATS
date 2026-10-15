package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 特定岗位与简历版本的工作进度。 */
@Getter
@RequiredArgsConstructor
public enum CandidateJobContextStatus implements CodeDescEnum {
  PENDING("待筛选"),
  AI_SCREENED("AI 已筛选"),
  ADVANCED("已推进"),
  FINISHED("流程已结束");

  private final String desc;
}
