package com.ezaigc.hr.talent.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历来源渠道状态。 */
@Getter
@RequiredArgsConstructor
public enum SourceChannelStatus implements CodeDescEnum {
  ACTIVE("启用"),
  DISABLED("停用");

  private final String desc;
}
