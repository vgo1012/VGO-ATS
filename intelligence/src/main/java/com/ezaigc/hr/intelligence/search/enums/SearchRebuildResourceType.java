package com.ezaigc.hr.intelligence.search.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 搜索重建与共享任务精确绑定的业务资源编码。 */
@Getter
@RequiredArgsConstructor
public enum SearchRebuildResourceType implements CodeDescEnum {
  SEARCH_INDEX_REBUILD("搜索索引重建");

  private final String desc;
}
