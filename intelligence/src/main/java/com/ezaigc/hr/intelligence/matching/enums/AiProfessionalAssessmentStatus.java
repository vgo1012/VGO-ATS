package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** AI 专业评分缓存状态。 */
@Getter
@RequiredArgsConstructor
public enum AiProfessionalAssessmentStatus implements CodeDescEnum {
  PROCESSING("处理中"),
  SUCCEEDED("已成功"),
  FAILED("已失败");

  private final String desc;

  public static AiProfessionalAssessmentStatus fromCode(String code) {
    return AiProfessionalAssessmentStatus.valueOf(code);
  }
}
