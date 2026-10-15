package com.ezaigc.hr.intelligence.search.domain.response;

import lombok.Value;

/** 只提供从精确任务批次导出的目标；不表示已证明该外部对象属于本应用。 */
@Value
public class LegacySearchIndexTargetResponse {
  String indexName;
  int attempt;
  String runVersion;
  String taskId;
  String taskStatus;
}
