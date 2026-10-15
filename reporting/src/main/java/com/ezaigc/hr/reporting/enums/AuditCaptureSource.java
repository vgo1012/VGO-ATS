package com.ezaigc.hr.reporting.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;

/** 审计投影来源。 */
public enum AuditCaptureSource implements CodeDescEnum {
  IDENTITY("身份审计"),
  DEMAND("招聘需求审计"),
  MATCHING("匹配审计"),
  TALENT("人才审计"),
  RECRUITMENT_STAGE("招聘阶段审计"),
  RECRUITMENT_DECISION("招聘决定审计"),
  INTERVIEW("面试审计"),
  SUBMISSION_SEND("客户推荐发送审计");

  private final String desc;

  AuditCaptureSource(String desc) {
    this.desc = desc;
  }

  @Override
  public String getDesc() {
    return desc;
  }

  public String checkpointName(AuditCaptureLane lane) {
    return getCode() + ':' + lane.getCode();
  }
}
