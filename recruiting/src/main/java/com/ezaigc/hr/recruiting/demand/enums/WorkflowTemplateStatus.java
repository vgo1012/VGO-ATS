package com.ezaigc.hr.recruiting.demand.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 招聘流程模板状态。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowTemplateStatus implements CodeDescEnum {
  DRAFT("草稿"),
  ACTIVE("启用"),
  SUPERSEDED("已被新版本取代");

  private final String desc;
}
