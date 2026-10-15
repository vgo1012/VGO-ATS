package com.ezaigc.hr.common.infrastructure.task;

import java.time.Instant;
import java.util.List;

/** 模块按调度入口贡献自己的持久可领取积压，允许一个入口汇总多个持久来源。 */
public interface PersistentTaskBacklogProbe {
  boolean supports(String scheduleCode, List<String> businessTypes);

  PersistentTaskBacklogSnapshot snapshot(
      String scheduleCode, List<String> businessTypes, Instant now);
}
