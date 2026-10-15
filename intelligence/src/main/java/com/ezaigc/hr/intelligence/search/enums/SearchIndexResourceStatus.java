package com.ezaigc.hr.intelligence.search.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchIndexResourceStatus implements CodeDescEnum {
  CREATING("创建请求已登记"),
  CREATE_UNKNOWN("创建结果待核对"),
  READY("创建已确定结束"),
  RETIRING("持久退役中"),
  DELETED("已回收");
  private final String desc;
}
