package com.ezaigc.hr.intelligence.search.domain.dto;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IndexPage {
  /** 条目列表。 */
  private List<SearchDocument> items;
  /** 下一步游标。 */
  private String nextCursor;
  /** 是否还有下一页数据。 */
  private boolean hasMore;

  public IndexPage(List<SearchDocument> items, String nextCursor, boolean hasMore) {
    items = List.copyOf(items);
    this.items = items;
    this.nextCursor = nextCursor;
    this.hasMore = hasMore;
  }
}
