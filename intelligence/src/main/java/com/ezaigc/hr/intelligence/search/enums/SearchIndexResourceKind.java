package com.ezaigc.hr.intelligence.search.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SearchIndexResourceKind implements CodeDescEnum {
  BOOTSTRAP("受管初始索引"),
  REBUILD("受管重建目标"),
  LEGACY_APPROVED("历史批准目标"),
  LEGACY_ATTEMPT("人工纳管历史批次");
  private final String desc;
}
