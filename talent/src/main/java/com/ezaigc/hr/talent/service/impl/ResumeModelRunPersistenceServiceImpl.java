package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.talent.domain.entity.ResumeModelRun;
import com.ezaigc.hr.talent.enums.TalentProcessingRunStatus;
import com.ezaigc.hr.talent.mapper.ResumeModelRunMapper;
import com.ezaigc.hr.talent.service.ResumeModelRunPersistenceService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** ResumeModelRun 实体的 MyBatis-Plus 持久化实现。 */
@Service
public class ResumeModelRunPersistenceServiceImpl
    extends ServiceImpl<ResumeModelRunMapper, ResumeModelRun>
    implements ResumeModelRunPersistenceService {

  @Override
  public int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId) {
    return baseMapper.terminateWithoutWorker(taskId, versionId, TalentProcessingRunStatus.RUNNING.getCode(),
        TalentProcessingRunStatus.FAILED.getCode(), errorCode, now, actorId);
  }

  /** 新增模型运行。 */
  @Override
  public int insertModelRun(
      String id,
      String versionId,
      String extractionId,
      String schemaVersion,
      String promptVersion,
      Instant now) {
    ResumeModelRun run = new ResumeModelRun();
    run.setId(id);
    run.setResumeVersionId(versionId);
    run.setExtractionRunId(extractionId);
    run.setSchemaVersion(schemaVersion);
    run.setPromptVersion(promptVersion);
    run.setStatus(TalentProcessingRunStatus.RUNNING.getCode());
    run.setValidationStatus(AiOutputValidationStatus.PENDING.getCode());
    run.setCreateTime(now);
    return baseMapper.insert(run);
  }

  /** 完成模型运行。 */
  @Override
  public int completeModelRun(
      String id,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now) {
    return baseMapper.update(
        Wrappers.lambdaUpdate(ResumeModelRun.class)
            .eq(ResumeModelRun::getId, id)
            .eq(ResumeModelRun::getStatus, TalentProcessingRunStatus.RUNNING.getCode())
            .set(ResumeModelRun::getModelProvider, provider)
            .set(ResumeModelRun::getModelVersion, modelVersion)
            .set(ResumeModelRun::getStatus, TalentProcessingRunStatus.SUCCEEDED.getCode())
            .set(
                ResumeModelRun::getValidationStatus,
                AiOutputValidationStatus.VALID.getCode())
            .set(ResumeModelRun::getRawOutputObjectKey, objectKey)
            .set(ResumeModelRun::getRawOutputHash, hash)
            .set(ResumeModelRun::getRawOutputSize, size)
            .set(ResumeModelRun::getEncryptionVersion, encryptionVersion)
            .set(ResumeModelRun::getInputTokens, inputTokens)
            .set(ResumeModelRun::getOutputTokens, outputTokens)
            .set(ResumeModelRun::getDurationMillis, durationMillis)
            .set(ResumeModelRun::getFinishedAt, now)
            .set(ResumeModelRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(ResumeModelRun::getUpdateTime, now));
  }

  /** 标记失败模型运行。 */
  @Override
  public int failModelRun(
      String id,
      AiOutputValidationStatus validationStatus,
      String errorCode,
      String provider,
      String modelVersion,
      String objectKey,
      String hash,
      Long size,
      String encryptionVersion,
      int inputTokens,
      int outputTokens,
      long durationMillis,
      Instant now) {
    var update =
        Wrappers.lambdaUpdate(ResumeModelRun.class)
            .eq(ResumeModelRun::getId, id)
            .eq(ResumeModelRun::getStatus, TalentProcessingRunStatus.RUNNING.getCode())
            .set(provider != null, ResumeModelRun::getModelProvider, provider)
            .set(modelVersion != null, ResumeModelRun::getModelVersion, modelVersion)
            .set(ResumeModelRun::getStatus, TalentProcessingRunStatus.FAILED.getCode())
            .set(ResumeModelRun::getValidationStatus, validationStatus.getCode())
            .set(ResumeModelRun::getRawOutputObjectKey, objectKey)
            .set(ResumeModelRun::getRawOutputHash, hash)
            .set(ResumeModelRun::getRawOutputSize, size)
            .set(ResumeModelRun::getEncryptionVersion, encryptionVersion)
            .set(ResumeModelRun::getInputTokens, inputTokens)
            .set(ResumeModelRun::getOutputTokens, outputTokens)
            .set(ResumeModelRun::getDurationMillis, durationMillis)
            .set(ResumeModelRun::getErrorCode, errorCode)
            .set(ResumeModelRun::getFinishedAt, now)
            .set(ResumeModelRun::getUpdateId, PlatformActorConstants.SYSTEM_ACTOR_ID)
            .set(ResumeModelRun::getUpdateTime, now);
    return baseMapper.update(update);
  }
}
