package com.ezaigc.hr.recruiting.recruitment.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationBatchItemStatus implements CodeDescEnum {
  SUCCEEDED("已推荐到招聘流程"), ALREADY_EXISTS("已在招聘流程"), FAILED("推荐失败");
  private final String desc;
}
