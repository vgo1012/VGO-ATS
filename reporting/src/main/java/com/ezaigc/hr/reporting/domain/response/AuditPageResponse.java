package com.ezaigc.hr.reporting.domain.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditPageResponse {
  /** 条目列表。 */
  private List<AuditEventResponse> items;
  /** 下一步游标。 */
  private String nextCursor;
  /** 是否仍有下一页。 */
  private boolean hasMore;
  /** 数据更新时间。 */
  private Instant dataUpdatedAt;
}
