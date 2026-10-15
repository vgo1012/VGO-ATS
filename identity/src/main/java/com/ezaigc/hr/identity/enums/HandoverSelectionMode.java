package com.ezaigc.hr.identity.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 账号交接资源选择模式。 */
@Getter
@RequiredArgsConstructor
public enum HandoverSelectionMode implements CodeDescEnum {
  ALL("全部资源"),
  SELECTED("指定资源");

  private final String desc;
  public static HandoverSelectionMode fromCode(String code) {
    try {
      return valueOf(code);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("未知交接选择模式: " + code, exception);
    }
  }
}
