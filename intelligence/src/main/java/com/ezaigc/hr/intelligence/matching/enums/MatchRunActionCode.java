package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 匹配运行幂等动作编码。 */
@Getter
@RequiredArgsConstructor
public enum MatchRunActionCode implements CodeDescEnum {
  CREATE_MATCH_RUN("创建匹配任务"),
  ADMIT_SCREENING_CANDIDATES("加入智能筛选"),
  RETURN_SCREENING_CANDIDATES("退回我的简历");

  private final String desc;
}
