package com.ezaigc.hr.integration.storage.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对象下载响应体及其截止看门狗的生命周期。 */
@Getter
@RequiredArgsConstructor
public enum ObjectStorageBodyState implements CodeDescEnum {
  OPEN("OPEN", "读取中"),
  EOF("EOF", "已读完"),
  CLOSED("CLOSED", "已关闭"),
  TIMED_OUT("TIMED_OUT", "已超时"),
  DESTROYED("DESTROYED", "存储适配器已销毁");

  private final String code;
  private final String desc;
}
