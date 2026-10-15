package com.ezaigc.hr.intelligence.search.domain.dto;

import java.util.Locale;
import lombok.Value;
import lombok.experimental.Accessors;

/** 重建任务的领取批次；目标名称同时供持久化记录和搜索发布围栏使用。 */
@Value
@Accessors(fluent = true)
public class SearchRebuildAttempt {
  /** 重建任务标识。 */
  String runId;
  /** 共享任务的领取次数。 */
  int attempt;

  public SearchRebuildAttempt(String runId, int attempt) {
    if (runId == null || !runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || attempt < 1) {
      throw new IllegalArgumentException("invalid search rebuild attempt");
    }
    this.runId = runId;
    this.attempt = attempt;
  }

  public String indexName() {
    return indexPrefix() + attempt;
  }

  /** 从同一任务的目标名称读取批次，拒绝未知索引或无效批次。 */
  public int attemptOf(String indexName) {
    if (indexName == null || !indexName.startsWith(indexPrefix())) {
      throw new IllegalStateException("search rebuild fence belongs to an unknown index");
    }
    String value = indexName.substring(indexPrefix().length());
    if (!value.matches("[1-9][0-9]{0,9}")) {
      throw new IllegalStateException("invalid search rebuild fence attempt");
    }
    return Integer.parseInt(value);
  }

  private String indexPrefix() {
    return "resume-facts-v" + runId.toLowerCase(Locale.ROOT) + "-attempt-";
  }
}
