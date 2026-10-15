package com.ezaigc.hr.integration.mail.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailResumeStatus implements CodeDescEnum {
  REVIEW_REQUIRED("待人工校验"),
  MATCHED("已匹配"),
  DISPATCHED("已下发"),
  DISPATCH_FAILED("下发失败"),
  REJECTED("已拒绝"),
  FAILED("失败");

  private final String desc;
}
