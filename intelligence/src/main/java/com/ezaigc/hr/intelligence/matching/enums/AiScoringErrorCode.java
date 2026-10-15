package com.ezaigc.hr.intelligence.matching.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** AI 评分失败与恢复分支使用的稳定错误码。 */
@Getter
@RequiredArgsConstructor
public enum AiScoringErrorCode implements CodeDescEnum {
  MATCH_AI_ASSESSMENT_REQUIRED("尚无已保存的 AI 评分"),
  MATCH_AI_INPUT_TOO_LARGE("评分事实超出输入上限"),
  MATCH_AI_MODEL_UNAVAILABLE("评分模型未启用"),
  MATCH_AI_ASSESSMENT_BUSY("评分正在处理中"),
  MATCH_AI_ASSESSMENT_LEASE_LOST("评分保存租约失效"),
  MATCH_AI_SCORING_FAILED("评分未完成"),
  MATCH_LEGACY_RUN_REQUIRES_NEW_SCORING("历史任务需要新建 AI 评分");

  private final String desc;
}
