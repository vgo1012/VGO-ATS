package com.ezaigc.hr.intelligence.search.domain.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TalentSearchPageResponse {
  /** 条目列表。 */
  private List<TalentSearchResultResponse> items;
  /** 下一步游标。 */
  private String nextCursor;
  /** 是否还有可返回结果或待继续扫描的索引页；须与 nextCursor 一起使用。 */
  private boolean hasMore;
  /** 本次扫描预算已用尽，尚未确认剩余索引中是否存在当前可见结果。 */
  private boolean scanIncomplete;

  public TalentSearchPageResponse(
      List<TalentSearchResultResponse> items, String nextCursor, boolean hasMore) {
    this(items, nextCursor, hasMore, false);
  }
}
