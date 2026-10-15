package com.ezaigc.hr.intelligence.search.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.search.domain.entity.RebuildRun;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import java.time.Instant;

public interface RebuildRunService extends IService<RebuildRun> {
  RebuildRun findByTaskId(String taskId);

  RebuildRun currentForUpdate(String id);

  RebuildRun findSucceededTarget(String targetIndex);

  boolean finishApproved(String id, String taskId, String targetIndex, long count,
      String actorId, Instant now);

  boolean start(String id, String targetIndex, String updateId, Instant startedAt);

  /** 仅终结与共享任务精确绑定的未完成重建，保留已有计数和目标索引。 */
  boolean terminateWithoutWorker(String id, String taskId, SearchRebuildStatus status,
      String errorCode, String updateId, Instant finishedAt);

  boolean finish(
      String id,
      SearchRebuildStatus status,
      long projectedCount,
      String errorCode,
      String updateId,
      Instant finishedAt);
}
