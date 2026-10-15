package com.ezaigc.hr.talent.api.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 搜索意图绑定的业务聚合，由人才模块对生产者和消费者共享稳定编码。 */
@Getter
@RequiredArgsConstructor
public enum ResumeSearchResourceType implements CodeDescEnum {
  CANDIDATE("候选人");

  private final String desc;
}
