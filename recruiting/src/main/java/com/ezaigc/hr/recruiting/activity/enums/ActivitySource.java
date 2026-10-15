package com.ezaigc.hr.recruiting.activity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 活动记录的事实来源，区分用户业务记录与流程自动说明。 */
@Getter
@RequiredArgsConstructor
public enum ActivitySource implements CodeDescEnum {
  COMMUNICATION("实际沟通"),
  FOLLOW_UP_PLAN("沟通预约"),
  FOLLOW_UP("步骤沟通"),
  WORKFLOW_STEP("步骤结果"),
  PATH("流程选择"),
  STAGE("阶段变更"),
  DECISION("人工招聘决定"),
  OFFER("录用进展"),
  ONBOARDING("确认入职"),
  INTERVIEW_SCHEDULE("面试排期"),
  INTERVIEW_FEEDBACK("正式面评"),
  RESTRICTION("指定公司限制");

  private final String desc;

  public static boolean isSystemExplanation(String code) {
    return PATH.matches(code) || STAGE.matches(code) || WORKFLOW_STEP.matches(code);
  }
}
