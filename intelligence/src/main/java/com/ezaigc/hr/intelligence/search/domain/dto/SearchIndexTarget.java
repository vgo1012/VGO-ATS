package com.ezaigc.hr.intelligence.search.domain.dto;

import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;

/** MySQL 批准的实际索引及代际；空索引只表示待首次批准，不得用于查询。 */
@Value
@Accessors(fluent = true)
public class SearchIndexTarget {
  String indexName;
  long generation;
  /** 仅当前进程的请求截止，不参与目标/代际语义或游标。 */
  @EqualsAndHashCode.Exclude
  long deadlineNanos;

  /** 未绑定公开请求的目标只能用于持久创建/任务生命周期保护的 IO。 */
  public SearchIndexTarget(String indexName, long generation) {
    this(indexName, generation, Long.MAX_VALUE);
  }

  public SearchIndexTarget(String indexName, long generation, long deadlineNanos) {
    this.indexName = indexName;
    this.generation = generation;
    this.deadlineNanos = deadlineNanos;
  }

  public SearchIndexTarget withDeadline(long deadline) {
    return new SearchIndexTarget(indexName, generation, deadline);
  }

  /** adapter 在每个 IO 开始前重新取剩余期限，整个响应体使用该总截止。 */
  public Duration remainingTimeout(Duration configured) {
    if (deadlineNanos == Long.MAX_VALUE) return configured;
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining <= 0) return Duration.ZERO;
    Duration budget = Duration.ofNanos(remaining);
    return configured.compareTo(budget) <= 0 ? configured : budget;
  }
}
