package com.ezaigc.hr.talent.application.support;

import com.ezaigc.hr.common.infrastructure.task.AsyncTaskTerminalHandler;
import com.ezaigc.hr.common.infrastructure.task.enums.AsyncTaskStatus;
import com.ezaigc.hr.talent.constant.TalentConstants;
import com.ezaigc.hr.talent.service.ExtractionRunPersistenceService;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 解析任务最终结束时收敛仍运行的抽取和模型记录，保留已提交的解析事实。 */
@Component
public class ResumeParseTerminalHandler implements AsyncTaskTerminalHandler {
  @Resource private ExtractionRunPersistenceService extractions;
  @Resource private ResumeModelRunPersistenceService models;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void terminalWithoutWorker(String taskType, String taskId, String resourceId,
      AsyncTaskStatus status, String errorCode, Instant finishedAt, String actorId) {
    if (!TalentConstants.RESUME_PARSE_TASK_TYPE.equals(taskType)) return;
    if (status != AsyncTaskStatus.FAILED && status != AsyncTaskStatus.CANCELLED) {
      throw new IllegalArgumentException("parse completion requires a failed or cancelled task");
    }
    String code = status == AsyncTaskStatus.CANCELLED ? "TASK_CANCELLED" : errorCode;
    models.terminateWithoutWorker(taskId, resourceId, code, finishedAt, actorId);
    extractions.terminateWithoutWorker(taskId, resourceId, code, finishedAt, actorId);
  }
}
