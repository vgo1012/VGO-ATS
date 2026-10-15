package com.ezaigc.hr.talent.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.common.infrastructure.task.PersistentTaskBacklogSnapshot;
import com.ezaigc.hr.talent.domain.entity.ResumeIntakeOutboxEvent;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public interface ResumeIntakeOutboxService extends IService<ResumeIntakeOutboxEvent> {
  @Transactional
  ResumeIntakeOutboxEvent enqueue(
      String processingId,
      long processingVersion,
      String taskType,
      String actorUserId,
      Instant now);

  @Transactional
  ResumeIntakeOutboxEvent claimOne(Instant now);

  @Transactional
  void published(ResumeIntakeOutboxEvent event, Instant now);

  @Transactional
  void failed(
      ResumeIntakeOutboxEvent event,
      String code,
      boolean retryable,
      Instant nextRetryAt,
      Instant now);

  @Transactional(readOnly = true)
  ResumeIntakeOutboxEvent get(String id);

  @Transactional(readOnly = true)
  PersistentTaskBacklogSnapshot findReadyBacklog(Instant now);
}
