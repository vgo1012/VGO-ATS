package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.constant.ResumeIntakeWorkerConstants;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeProcessing;
import com.ezaigc.hr.talent.enums.ResumeIntakeProcessingStatus;
import com.ezaigc.hr.talent.mapper.ResumeIntakeProcessingMapper;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import com.ezaigc.hr.talent.service.ResumeIntakeProcessingPersistenceService;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ResumeIntakeProcessingPersistenceServiceImpl
    extends ServiceImpl<ResumeIntakeProcessingMapper, ResumeIntakeProcessing>
    implements ResumeIntakeProcessingPersistenceService {
  private static final String SHARED_SCOPE_KEY = "00000000000000000000000000";
  @Resource private ApplicationEventPublisher events;
  @Resource private ObjectProvider<ResumeIntakeOutboxService> outboxProvider;
  @Override public PersistentTaskBacklogSnapshot findReadyBacklog(Instant now) {
    return baseMapper.findReadyBacklog(now);
  }
  @Override public int insertProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now) {
    int changed = baseMapper.insertProcessing(id, hash, fileName, mediaType, objectKey, actorId, now);
    if (changed > 0) {
      outbox().enqueue(id, 0, ResumeIntakeWorkerConstants.PARSE, actorId, now);
      events.publishEvent(new AsyncTaskAvailable(ResumeIntakeWorkerConstants.PARSE, id, null));
    }
    return changed;
  }
  @Override public int insertReparseProcessing(String id, String hash, String fileName, String mediaType,
      String objectKey, String actorId, Instant now) {
    int changed = baseMapper.insertReparseProcessing(id, hash, fileName, mediaType, objectKey, actorId, now);
    if (changed > 0) {
      outbox().enqueue(id, 0, ResumeIntakeWorkerConstants.PARSE, actorId, now);
      events.publishEvent(new AsyncTaskAvailable(ResumeIntakeWorkerConstants.PARSE, id, null));
    }
    return changed;
  }
  @Override public ResumeIntakeProcessing findByHash(String hash) {
    // 唯一键竞争后读取最新提交行，不能沿用入库事务开始时的旧快照。
    return baseMapper.selectOne(Wrappers.lambdaQuery(ResumeIntakeProcessing.class)
        .eq(ResumeIntakeProcessing::getContentSha256, hash)
        .eq(ResumeIntakeProcessing::getProcessingScopeKey, SHARED_SCOPE_KEY).last("FOR UPDATE"));
  }
  @Override public ResumeIntakeProcessing findProcessing(String id) { return baseMapper.selectById(id); }
  @Override public List<ResumeIntakeProcessing> findClaimable(Instant now, int limit) {
    var query = Wrappers.lambdaQuery(ResumeIntakeProcessing.class)
        .apply("attempt_count < max_attempts")
        .and(states -> states.eq(ResumeIntakeProcessing::getStatus, ResumeIntakeProcessingStatus.QUEUED.getCode())
            .or(retry -> retry.eq(ResumeIntakeProcessing::getStatus, ResumeIntakeProcessingStatus.FAILED_RETRYABLE.getCode())
                .and(time -> time.isNull(ResumeIntakeProcessing::getNextRetryAt)
                    .or().le(ResumeIntakeProcessing::getNextRetryAt, now)))
            .or(expired -> expired.eq(ResumeIntakeProcessing::getStatus, ResumeIntakeProcessingStatus.RUNNING.getCode())
                .le(ResumeIntakeProcessing::getLeaseExpiresAt, now)))
        .orderByAsc(ResumeIntakeProcessing::getCreateTime, ResumeIntakeProcessing::getId);
    return baseMapper.selectPage(new Page<>(1, Math.min(100, Math.max(1, limit)), false), query).getRecords();
  }
  @Override public int claim(String id, long version, String worker, Instant leaseUntil, Instant now) {
    return baseMapper.claim(id, version, worker, leaseUntil, now);
  }
  @Override public int renewLease(String id, String worker, Instant leaseUntil, Instant now) {
    return baseMapper.renewLease(id, worker, leaseUntil, now);
  }
  @Override public int succeed(String id, String worker, String pagesJson, String factsJson, String provider,
      String modelVersion, String archiveKey, String archiveHash, long archiveSize,
      String encryptionVersion, Instant now) {
    int changed = baseMapper.succeed(id, worker, pagesJson, factsJson, provider, modelVersion,
        archiveKey, archiveHash, archiveSize, encryptionVersion, now);
    if (changed > 0) {
      ResumeIntakeProcessing saved = findProcessing(id);
      outbox().enqueue(id, saved.getVersion(), ResumeIntakeWorkerConstants.COMPLETE,
          saved.getCreateId(), now);
      events.publishEvent(new AsyncTaskAvailable(ResumeIntakeWorkerConstants.COMPLETE, id, null));
    }
    return changed;
  }
  @Override public int fail(String id, String worker, String failureCode, boolean retryable,
      Instant nextRetryAt, Instant now) {
    int changed = baseMapper.fail(id, worker, failureCode, retryable, nextRetryAt, now);
    if (changed > 0) {
      ResumeIntakeProcessing saved = findProcessing(id);
      if (ResumeIntakeProcessingStatus.Codes.FAILED_FINAL.equals(saved.getStatus())) {
        outbox().enqueue(id, saved.getVersion(), ResumeIntakeWorkerConstants.COMPLETE,
            saved.getCreateId(), now);
        events.publishEvent(new AsyncTaskAvailable(ResumeIntakeWorkerConstants.COMPLETE, id, null));
      }
    }
    return changed;
  }
  @Override public int retry(String id, String actorId, Instant now) {
    int changed = baseMapper.retry(id, actorId, now);
    if (changed > 0) {
      ResumeIntakeProcessing saved = findProcessing(id);
      outbox().enqueue(id, saved.getVersion(), ResumeIntakeWorkerConstants.PARSE, actorId, now);
      events.publishEvent(new AsyncTaskAvailable(ResumeIntakeWorkerConstants.PARSE, id, null));
    }
    return changed;
  }
  @Override public int expireExhausted(Instant now, int limit) { return baseMapper.expireExhausted(now, limit); }
  @Override public ResumeIntakeProcessing findByResumeVersion(String resumeVersionId) {
    String id = baseMapper.findProcessingIdByResumeVersion(resumeVersionId);
    return id == null ? null : baseMapper.selectById(id);
  }
  private ResumeIntakeOutboxService outbox() { return outboxProvider.getObject(); }
}
