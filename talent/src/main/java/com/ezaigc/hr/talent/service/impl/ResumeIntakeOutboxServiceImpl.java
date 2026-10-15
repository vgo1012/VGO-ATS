package com.ezaigc.hr.talent.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ezaigc.hr.common.core.constant.PlatformActorConstants;
import com.ezaigc.hr.common.core.id.PlatformIdGenerator;
import com.ezaigc.hr.common.infrastructure.logging.TraceIds;
import com.ezaigc.hr.common.infrastructure.task.AsyncTaskAvailable;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.constant.ResumeIntakeOutboxConstants;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import com.ezaigc.hr.talent.enums.ResumeIntakeOutboxStatus;
import com.ezaigc.hr.talent.mapper.ResumeIntakeOutboxMapper;
import com.ezaigc.hr.talent.service.ResumeIntakeOutboxService;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Lazy
public class ResumeIntakeOutboxServiceImpl
    extends ServiceImpl<ResumeIntakeOutboxMapper, ResumeIntakeOutboxEvent>
    implements ResumeIntakeOutboxService {
  private static final Duration LEASE = Duration.ofMinutes(1);
  @Resource private PlatformIdGenerator ids;
  @Resource private ApplicationEventPublisher events;

  @Override
  public ResumeIntakeOutboxEvent enqueue(
      String processingId,
      long processingVersion,
      String taskType,
      String actorUserId,
      Instant now) {
    String actor =
        actorUserId == null || actorUserId.isBlank()
            ? PlatformActorConstants.SYSTEM_ACTOR_ID
            : actorUserId;
    var event = new ResumeIntakeOutboxEvent();
    event.setId(ids.newId());
    event.setProcessingId(processingId);
    event.setProcessingVersion(processingVersion);
    event.setEventType(ResumeIntakeOutboxConstants.EVENT_TYPE);
    event.setEventVersion(ResumeIntakeOutboxConstants.EVENT_VERSION);
    event.setTaskType(taskType);
    event.setOccurredAt(now);
    event.setTraceId(TraceIds.acceptOrCreate(MDC.get("traceId")));
    event.setActorUserId(actor);
    event.setStatus(ResumeIntakeOutboxStatus.Codes.PENDING);
    event.setAttemptCount(0);
    event.setMaxAttempts(5);
    event.setCreateId(actor);
    event.setCreateTime(now);
    event.setUpdateId(actor);
    event.setUpdateTime(now);
    ResumeIntakeOutboxEvent saved =
        baseMapper.insertIgnore(event) == 1
            ? event
            : baseMapper.findByBinding(processingId, processingVersion, taskType);
    verifyBinding(event, saved);
    events.publishEvent(new AsyncTaskAvailable("OUTBOX_PUBLISH", saved.getId(), saved.getTraceId()));
    return saved;
  }

  @Override
  public ResumeIntakeOutboxEvent claimOne(Instant now) {
    ResumeIntakeOutboxEvent candidate = baseMapper.findClaimable(now);
    if (candidate == null) return null;
    String leaseOwner = ids.newId();
    if (baseMapper.claim(
            candidate.getId(), candidate.getVersion(), leaseOwner, now.plus(LEASE), now)
        != 1) {
      return null;
    }
    return getById(candidate.getId());
  }

  @Override
  public void published(ResumeIntakeOutboxEvent event, Instant now) {
    requireSingleWrite(baseMapper.markPublished(event.getId(), event.getLeaseOwner(), now));
  }

  @Override
  public void failed(
      ResumeIntakeOutboxEvent event,
      String code,
      boolean retryable,
      Instant nextRetryAt,
      Instant now) {
    requireSingleWrite(
        baseMapper.markFailed(
            event.getId(), event.getLeaseOwner(), code, retryable, nextRetryAt, now));
  }

  @Override
  public ResumeIntakeOutboxEvent get(String id) {
    return getById(id);
  }

  @Override
  public PersistentTaskBacklogSnapshot findReadyBacklog(Instant now) {
    return baseMapper.findReadyBacklog(now);
  }

  private static void verifyBinding(
      ResumeIntakeOutboxEvent expected, ResumeIntakeOutboxEvent saved) {
    if (saved == null
        || !Objects.equals(expected.getProcessingId(), saved.getProcessingId())
        || expected.getProcessingVersion() != saved.getProcessingVersion()
        || !Objects.equals(expected.getTaskType(), saved.getTaskType())
        || !Objects.equals(expected.getEventType(), saved.getEventType())
        || expected.getEventVersion() != saved.getEventVersion()) {
      throw new IllegalStateException("resume intake outbox binding changed");
    }
  }

  private static void requireSingleWrite(int affected) {
    if (affected != 1) throw new IllegalStateException("resume intake outbox lease lost");
  }
}
