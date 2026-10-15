package com.ezaigc.hr.recruiting.demand.application.support;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.recruiting.demand.constant.DemandConstants;
import com.ezaigc.hr.recruiting.demand.service.JdAiModelRunPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在共享任务终结事务内结束仍运行的 JD 模型记录。 */
@Component
public class JdParseTerminalHandler implements AsyncTaskTerminalHandler {
  @Resource private JdAiModelRunPersistenceService runs;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void terminalWithoutWorker(String taskType, String taskId, String resourceId,
      AsyncTaskStatus status, String errorCode, Instant finishedAt, String actorId) {
    if (!DemandConstants.JD_PARSE_TASK_TYPE.equals(taskType)) return;
    if (status != AsyncTaskStatus.FAILED && status != AsyncTaskStatus.CANCELLED) {
      throw new IllegalArgumentException("parse completion requires a failed or cancelled task");
    }
    runs.terminateWithoutWorker(taskId, resourceId,
        status == AsyncTaskStatus.CANCELLED ? "TASK_CANCELLED" : errorCode, finishedAt, actorId);
  }
}
