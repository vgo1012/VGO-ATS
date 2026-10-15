package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public interface ResumeIntakeProcessingPersistenceService extends IService<ResumeIntakeProcessing> {
  PersistentTaskBacklogSnapshot findReadyBacklog(Instant now);
  @Transactional
  int insertProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now);
  @Transactional
  int insertReparseProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now);
  ResumeIntakeProcessing findByHash(String hash);
  ResumeIntakeProcessing findProcessing(String id);
  List<ResumeIntakeProcessing> findClaimable(Instant now, int limit);
  int claim(String id, long version, String worker, Instant leaseUntil, Instant now);
  @Transactional
  int succeed(String id, String worker, String pagesJson, String factsJson, String provider,
      String modelVersion, String archiveKey, String archiveHash, long archiveSize,
      String encryptionVersion, Instant now);
  @Transactional
  int fail(String id, String worker, String failureCode, boolean retryable,
      Instant nextRetryAt, Instant now);
  @Transactional
  int retry(String id, String actorId, Instant now);
  int renewLease(String id, String worker, Instant leaseUntil, Instant now);
  int expireExhausted(Instant now, int limit);
  ResumeIntakeProcessing findByResumeVersion(String resumeVersionId);
}
