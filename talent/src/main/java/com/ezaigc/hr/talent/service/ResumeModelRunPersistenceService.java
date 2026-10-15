package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.common.infrastructure.ai.enums.AiOutputValidationStatus;
import com.ezaigc.hr.talent.domain.entity.ResumeModelRun;
import java.time.Instant;

/** ResumeModelRun 实体的持久化服务。 */
public interface ResumeModelRunPersistenceService extends IService<ResumeModelRun> {

  /** 经抽取运行精确关联共享任务，仅终结相同版本且仍运行的模型记录。 */
  int terminateWithoutWorker(String taskId, String versionId, String errorCode, Instant now, String actorId);

  /** 新增模型运行。 */
  int insertModelRun(
      String id,
      String versionId,
      String extractionId,
      String schemaVersion,
      String promptVersion,
      Instant now);

  /** 完成模型运行。 */
  int completeModelRun(
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
      Instant now);

  /** 标记失败模型运行。 */
  int failModelRun(
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
      Instant now);
}
