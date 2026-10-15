package com.ezaigc.hr.intelligence.search.application.support;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.intelligence.search.constant.SearchConstants;
import com.ezaigc.hr.intelligence.search.enums.SearchRebuildStatus;
import com.ezaigc.hr.intelligence.search.service.RebuildRunService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 没有执行器参与时，在共享任务终结事务内收敛搜索重建状态。 */
@Component
public class SearchRebuildTerminalHandler implements AsyncTaskTerminalHandler {
  @Resource private RebuildRunService rebuilds;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void terminalWithoutWorker(String taskType, String taskId, String resourceId,
      AsyncTaskStatus status, String errorCode, Instant finishedAt, String actorId) {
    if (!SearchConstants.REBUILD_TASK_TYPE.equals(taskType)) return;
    SearchRebuildStatus terminal = switch (status) {
      case FAILED -> SearchRebuildStatus.FAILED;
      case CANCELLED -> SearchRebuildStatus.CANCELLED;
      default -> throw new IllegalArgumentException("workerless completion requires a terminal task");
    };
    // 零行表示绑定不匹配、记录已终结或不存在；不得跨任务覆盖既有终态。
    rebuilds.terminateWithoutWorker(resourceId, taskId, terminal, errorCode, actorId, finishedAt);
  }
}
