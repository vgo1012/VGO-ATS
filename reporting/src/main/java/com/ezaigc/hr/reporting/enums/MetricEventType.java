package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 可由业务事实采集的报表指标类型。 */
@Getter
@RequiredArgsConstructor
public enum MetricEventType implements CodeDescEnum {
  RESUME_INGESTED("简历入库"),
  RESUME_PARSED("简历解析"),
  APPLICATION_CREATED("招聘活动创建"),
  HUMAN_SCREENED("人工筛选"),
  SUBMISSION_SENT("客户推荐发送"),
  INTERVIEW_PASSED("面试通过"),
  ONBOARDED("入职");

  private final String desc;
}
