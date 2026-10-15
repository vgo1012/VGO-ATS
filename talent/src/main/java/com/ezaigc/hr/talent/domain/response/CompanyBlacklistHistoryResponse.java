package com.ezaigc.hr.talent.domain.response;

import com.ezaigc.hr.common.web.pagination.CursorPage;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前黑名单投影与不可变事实历史，翻页不改变当前记录的含义。 */
@Data
@AllArgsConstructor
public class CompanyBlacklistHistoryResponse {
  private CompanyBlacklistRecordResponse current;
  private boolean active;
  private CursorPage<CompanyBlacklistRecordResponse> records;
}
