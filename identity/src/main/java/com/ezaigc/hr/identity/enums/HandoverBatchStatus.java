package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 账号交接批次状态。 */
@Getter
@RequiredArgsConstructor
public enum HandoverBatchStatus implements CodeDescEnum {
  PREVIEWED("已预览"),
  RUNNING("执行中"),
  SUCCEEDED("已成功"),
  PARTIAL_SUCCESS("部分成功"),
  FAILED("已失败");

  private final String desc;

  public static HandoverBatchStatus fromCode(String code) {
    return HandoverBatchStatus.valueOf(code);
  }
}
