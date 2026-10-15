package com.ezaigc.hr.intelligence.matching.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 稳定游标分页的匹配结果集合。 */
@Data
@AllArgsConstructor
public class MatchResultPageResponse {
  /** 条目列表。 */
  private List<MatchResultResponse> items;
  /** 下一步游标。 */
  private String nextCursor;
  /** 是否还有下一页数据。 */
  private boolean hasMore;
}
