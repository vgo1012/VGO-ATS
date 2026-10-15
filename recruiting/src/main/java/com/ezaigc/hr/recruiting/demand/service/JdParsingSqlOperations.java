package com.ezaigc.hr.recruiting.demand.service;

import com.ezaigc.hr.recruiting.demand.domain.entity.JdFactSnapshotEntity;
import java.time.Instant;

public interface JdParsingSqlOperations {
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
      String validationStatus,
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

  /** 查询当前用于更新。 */
  JdFactSnapshotEntity findCurrentForUpdate(String versionId);
}
