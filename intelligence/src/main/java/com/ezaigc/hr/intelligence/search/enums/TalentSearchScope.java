package com.ezaigc.hr.intelligence.search.enums;

import com.ezaigc.hr.common.core.enums.CodeDescEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 简历搜索实时权限回查范围。 */
@Getter
@RequiredArgsConstructor
public enum TalentSearchScope implements CodeDescEnum {
  SHARED("共享简历库"),
  PERSONAL_POOL("管理范围待筛选"),
  MY_TASKS("本人待处置");

  private final String desc;

  public static TalentSearchScope fromCode(String code) {
    return TalentSearchScope.valueOf(code);
  }
}
