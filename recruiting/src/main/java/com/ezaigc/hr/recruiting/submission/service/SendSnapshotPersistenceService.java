package com.ezaigc.hr.recruiting.submission.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.recruiting.submission.domain.entity.SendSnapshot;
import java.time.Instant;

/** SendSnapshot 实体的持久化服务。 */
public interface SendSnapshotPersistenceService extends IService<SendSnapshot> {

  /** 新增发送快照。 */
  int insertSendSnapshot(
      String id,
      String submissionId,
      String resumeVersionId,
      String recipient,
      String channel,
      String fileName,
      String sha256,
      String actorId,
      Instant now);

  SendSnapshot snapshot(String submissionId);
}
