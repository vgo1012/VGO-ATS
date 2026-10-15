package com.ezaigc.hr.recruiting.demand.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.recruiting.demand.domain.entity.JdAiModelRunEntity;
import com.ezaigc.hr.recruiting.demand.enums.JdAiModelRunStatus;
import com.ezaigc.hr.recruiting.demand.mapper.JdAiModelRunMapper;
import com.ezaigc.hr.recruiting.demand.service.JdAiModelRunPersistenceService;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** JdAiModelRunEntity 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class JdAiModelRunPersistenceServiceImpl
    extends ServiceImpl<JdAiModelRunMapper, JdAiModelRunEntity>
    implements JdAiModelRunPersistenceService {

  @Override
  public int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId) {
    return baseMapper.update(Wrappers.lambdaUpdate(JdAiModelRunEntity.class)
        .eq(JdAiModelRunEntity::getTaskId, taskId).eq(JdAiModelRunEntity::getJdVersionId, versionId)
        .eq(JdAiModelRunEntity::getStatus, JdAiModelRunStatus.RUNNING.getCode())
        .set(JdAiModelRunEntity::getStatus, JdAiModelRunStatus.FAILED.getCode())
        .set(JdAiModelRunEntity::getErrorCode, errorCode).set(JdAiModelRunEntity::getFinishedAt, now)
        .set(JdAiModelRunEntity::getUpdateId, actorId).set(JdAiModelRunEntity::getUpdateTime, now)
        .setIncrBy(JdAiModelRunEntity::getVersion, 1));
  }

  /** 新增模型运行。 */
  @Override
  public int insertModelRun(
      String id,
      String versionId,
      String taskId,
      String schemaVersion,
      String promptVersion,
      Instant now) {
    JdAiModelRunEntity run = new JdAiModelRunEntity();
    run.setId(id);
    run.setJdVersionId(versionId);
    run.setTaskId(taskId);
    run.setSchemaVersion(schemaVersion);
    run.setPromptVersion(promptVersion);
    run.setStatus(JdAiModelRunStatus.RUNNING.getCode());
    run.setValidationStatus(AiOutputValidationStatus.PENDING.getCode());
    run.setInputTokens(0);
    run.setOutputTokens(0);
    run.setDurationMillis(0L);
    run.setCreateTime(now);
    return baseMapper.insert(run);
  }
}
