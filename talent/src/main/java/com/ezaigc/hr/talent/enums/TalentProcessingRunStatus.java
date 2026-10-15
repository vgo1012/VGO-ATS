package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历抽取与模型处理运行状态。 */
@Getter
@RequiredArgsConstructor
public enum TalentProcessingRunStatus implements CodeDescEnum {
  RUNNING("执行中"),
  SUCCEEDED("已成功"),
  FAILED("已失败");

  private final String desc;

  public static TalentProcessingRunStatus fromCode(String code) {
    return TalentProcessingRunStatus.valueOf(code);
  }
}
