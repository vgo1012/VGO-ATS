package com.ezaigc.hr.common.web.pagination;

import lombok.Getter;

/** 有界计数批次；调用方累计 count，直至 hasMore 为 false 后才得到完整总数。 */
@Getter
public final class CursorCount {
  private final long count;
  private final String nextCursor;
  private final boolean hasMore;

  public CursorCount(long count, String nextCursor, boolean hasMore) {
    this.count = count;
    this.nextCursor = hasMore ? nextCursor : null;
    this.hasMore = hasMore;
  }
}
