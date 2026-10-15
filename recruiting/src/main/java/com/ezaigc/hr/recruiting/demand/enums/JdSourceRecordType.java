package com.ezaigc.hr.recruiting.demand.enums;
import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum JdSourceRecordType implements CodeDescEnum {
  MANUAL("手动录入"),
  MAIL("邮件");
  private final String desc;
}
