package com.ezaigc.hr.recruiting.recruitment.enums;
import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum FollowUpPlanStatus implements CodeDescEnum {
  SCHEDULED("待沟通"),
  COMPLETED("已完成"),
  CANCELLED("已取消");
  private final String desc;
}
