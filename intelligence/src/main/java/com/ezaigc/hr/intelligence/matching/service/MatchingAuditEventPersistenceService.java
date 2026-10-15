package com.ezaigc.hr.intelligence.matching.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.intelligence.matching.domain.entity.MatchingAuditEvent;
import java.time.Instant;

/** MatchingAuditEvent 实体的持久化服务。 */
public interface MatchingAuditEventPersistenceService extends IService<MatchingAuditEvent> {

  /** 新增审计。 */
  int insertAudit(
      String id,
      String eventType,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      Instant now);
}
