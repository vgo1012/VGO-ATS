package com.ezaigc.hr.integration.mail.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailRouteRuleStatus implements CodeDescEnum {
  ACTIVE("启用"),
  DISABLED("停用");

  private final String desc;
}
