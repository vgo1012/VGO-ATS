package com.ezaigc.hr.identity.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ezaigc.hr.identity.domain.entity.IdentityAuditEvent;
import java.time.Instant;

/** IdentityAuditEvent 实体的持久化服务。 */
public interface IdentityAuditEventPersistenceService extends IService<IdentityAuditEvent> {

  /** 新增审计。 */
  int insertAudit(
      String id,
      String eventType,
      String actorId,
      String targetType,
      String targetId,
      String reason,
      String beforeSnapshot,
      String afterSnapshot,
      String traceId,
      Instant now);
}
