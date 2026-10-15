package com.ezaigc.hr.integration.mail.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MailIngestRunStatus implements CodeDescEnum {
  QUEUED("排队中"),
  RUNNING("运行中"),
  SUCCEEDED("成功"),
  PARTIAL("部分失败"),
  FAILED("失败");

  private final String desc;
}
